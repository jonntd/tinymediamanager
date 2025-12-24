/*
 * Copyright 2012 - 2025 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.core.movie.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.ImageCache;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieRenamer;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.webdav.WebDavClient;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;

/**
 * The Class MovieRenameTask.
 * 
 * @author Manuel Laggner
 */
public class MovieRenameTask extends TmmThreadPool {
  private static final Logger LOGGER = LoggerFactory.getLogger(MovieRenameTask.class);

  private final List<Movie>   moviesToRename;

  /**
   * Instantiates a new movie rename task.
   * 
   * @param moviesToRename
   *          the movies to rename
   */
  public MovieRenameTask(List<Movie> moviesToRename) {
    super(TmmResourceBundle.getString("movie.rename"));
    this.moviesToRename = new ArrayList<>(moviesToRename);
  }

  @Override
  protected void doInBackground() {
    try {
      LOGGER.info("Renaming '{}' movies", moviesToRename.size());

      // 检测是否有 WebDAV 数据源，如果有则使用单线程避免并发冲突
      boolean hasWebDav = MovieModuleManager.getInstance()
          .getSettings()
          .getMovieDataSource()
          .stream()
          .anyMatch(ds -> ds != null && ds.toLowerCase().startsWith("webdav://"));

      // WebDAV 数据源使用单线程避免 423 Locked 和 500 错误，本地文件系统使用多线程提高性能
      int threadCount = hasWebDav ? 1 : Math.min(4, Runtime.getRuntime().availableProcessors());
      if (hasWebDav) {
        LOGGER.info("WebDAV data source detected, using single thread to avoid concurrency conflicts");
      }
      initThreadPool(threadCount, "rename");

      // 性能优化：在 Task 级别初始化共享 WebDAV 客户端，跨多个 movie 复用同一连接
      String webDavSourceId = null;
      WebDavClient sharedClient = null;

      if (hasWebDav && !moviesToRename.isEmpty()) {
        try {
          // 获取第一个 movie 的数据源来初始化共享客户端
          Movie firstMovie = moviesToRename.get(0);
          String moviePath = firstMovie.getPath();
          if (WebDavDataSourceHelper.isWebDavPath(moviePath)) {
            String[] parsed = WebDavDataSourceHelper.parseWebDavPath(moviePath);
            if (parsed != null && parsed.length >= 1) {
              sharedClient = WebDavDataSourceHelper.getClientForPath(moviePath);
              if (sharedClient != null) {
                webDavSourceId = parsed[0];
                MovieRenamer.initSharedWebDavClient(sharedClient, webDavSourceId);
                LOGGER.info("Initialized Task-level shared WebDAV client for batch rename (sourceId: {})", webDavSourceId);
              }
            }
          }
        }
        catch (Exception e) {
          LOGGER.warn("Could not initialize Task-level shared WebDAV client: {}", e.getMessage());
        }
      }

      try {
        List<MediaFile> imageFiles = new ArrayList<>();

        // rename movies
        for (Movie movie : moviesToRename) {
          if (cancel) {
            break;
          }
          submitTask(new RenameMovieTask(movie));
        }
        waitForCompletionOrCancel();

        if (cancel) {
          return;
        }

        for (Movie movie : moviesToRename) {
          imageFiles.addAll(movie.getMediaFiles().stream().filter(MediaFile::isGraphic).toList());
        }
        // re-build the image cache afterward in an own thread
        if (Settings.getInstance().isImageCache() && !imageFiles.isEmpty()) {
          imageFiles.forEach(ImageCache::cacheImageAsync);
        }

        LOGGER.info("Finished renaming movies - took {} ms", getRuntime());
      }
      finally {
        // 清理 Task 级别的共享客户端
        if (sharedClient != null) {
          try {
            MovieRenamer.clearSharedWebDavClient();
            sharedClient.disconnect();
            LOGGER.debug("Disconnected Task-level shared WebDAV client");
          }
          catch (Exception e) {
            LOGGER.warn("Error disconnecting shared WebDAV client: {}", e.getMessage());
          }
        }
      }
    }
    catch (Exception e) {
      LOGGER.error("Could not rename movies - '{}'", e.getMessage());
      MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, "Settings.renamer", "message.renamer.threadcrashed"));
    }
  }

  /**
   * ThreadpoolWorker to work off ONE possible movie from root datasource directory
   * 
   * @author Myron Boyle
   * @version 1.0
   */
  private static class RenameMovieTask implements Callable<Object> {
    private final Movie movie;

    private RenameMovieTask(Movie movie) {
      this.movie = movie;
    }

    @Override
    public String call() {
      MovieRenamer.renameMovie(movie);
      return movie.getTitle();
    }
  }

  @Override
  public void callback(Object obj) {
    publishState((String) obj, progressDone);
  }
}
