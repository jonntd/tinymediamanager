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

      // WebDAV 即使启用多线程也建议保持较低的并发，这里设为 2 线程
      int threadCount = hasWebDav ? 2 : Math.min(4, Runtime.getRuntime().availableProcessors());
      if (hasWebDav) {
        LOGGER.info("WebDAV data source detected, using {} threads for parallel rename", threadCount);
      }
      initThreadPool(threadCount, "rename");

      // 注意：多线程模式下不使用共享客户端，每个工作线程创建自己的连接

      try {
        List<MediaFile> imageFiles = new ArrayList<>();

        // 注意：预检测已移除以提高性能。如需检测冲突，请使用“重命名预览”功能。
        // 运行时的冲突通过自愈机制处理（如自动生成唯一路径）。

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
        // 多线程模式下，每个工作线程管理自己的 WebDAV 连接
        // 不需要在这里清理共享客户端
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
