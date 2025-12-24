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
package org.tinymediamanager.core.tvshow.tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.tvshow.TvShowRenamer;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.webdav.WebDavClient;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;

/**
 * The class MovieRenameTask. rename all chosen movies
 * 
 * @author Manuel Laggner
 */
public class TvShowRenameTask extends TmmThreadPool {
  private static final Logger       LOGGER           = LoggerFactory.getLogger(TvShowRenameTask.class);

  private final List<TvShow>        tvShowsToRename  = new ArrayList<>();
  private final List<TvShowEpisode> episodesToRename = new ArrayList<>();

  /**
   * Rename just the given {@link TvShow} root (and {@link org.tinymediamanager.core.entities.MediaFile}s)
   *
   * @param tvShowToRename
   *          the {@link TvShow} to rename
   */
  public TvShowRenameTask(TvShow tvShowToRename) {
    this(Collections.singletonList(tvShowToRename), null);
  }

  /**
   * Rename just the given {@link TvShow} roots (and {@link org.tinymediamanager.core.entities.MediaFile}s)
   *
   * @param tvShowsToRename
   *          the {@link TvShow}s to rename
   */
  public TvShowRenameTask(Collection<TvShow> tvShowsToRename) {
    this(tvShowsToRename, null);
  }

  /**
   * Rename {@link TvShow}s and {@link TvShowEpisode}s together
   *
   * @param tvShowsToRename
   *          the {@link TvShow}s to rename (only TV show MFs and root folder)
   * @param episodesToRename
   *          the {@link TvShowEpisode}s to rename
   */
  public TvShowRenameTask(Collection<TvShow> tvShowsToRename, Collection<TvShowEpisode> episodesToRename) {
    super(TmmResourceBundle.getString("tvshow.rename"));
    if (tvShowsToRename != null) {
      this.tvShowsToRename.addAll(tvShowsToRename);
    }

    if (episodesToRename != null) {
      this.episodesToRename.addAll(episodesToRename);
    }
  }

  @Override
  protected void doInBackground() {
    try {
      LOGGER.info("Renaming '{}' TV shows / '{}' episodes", tvShowsToRename.size(), episodesToRename.size());

      // 检测是否有 WebDAV 数据源，如果有则使用单线程避免并发冲突（123pan等服务器对并发支持较差）
      boolean hasWebDav = TvShowModuleManager.getInstance()
          .getSettings()
          .getTvShowDataSource()
          .stream()
          .anyMatch(ds -> ds != null && ds.toLowerCase().startsWith("webdav://"));

      // WebDAV 数据源使用单线程避免 423 Locked 和 500 错误，本地文件系统使用多线程提高性能
      int threadCount = hasWebDav ? 1 : Math.min(4, Runtime.getRuntime().availableProcessors());
      if (hasWebDav) {
        LOGGER.info("WebDAV data source detected, using single thread to avoid concurrency conflicts");
      }
      initThreadPool(threadCount, "rename");

      // 性能优化：在 Task 级别初始化共享 WebDAV 客户端，跨多个 episode 复用同一连接
      String webDavSourceId = null;
      WebDavClient sharedClient = null;

      if (hasWebDav && !episodesToRename.isEmpty()) {
        try {
          // 获取第一个 episode 的数据源来初始化共享客户端
          TvShowEpisode firstEpisode = episodesToRename.get(0);
          String episodePath = firstEpisode.getPath();
          if (WebDavDataSourceHelper.isWebDavPath(episodePath)) {
            String[] parsed = WebDavDataSourceHelper.parseWebDavPath(episodePath);
            if (parsed != null && parsed.length >= 1) {
              LOGGER.info("Connecting to WebDAV server for batch rename (this may take a moment)...");
              sharedClient = WebDavDataSourceHelper.getClientForPath(episodePath);

              if (sharedClient != null) {
                webDavSourceId = parsed[0];
                TvShowRenamer.initSharedWebDavClient(sharedClient, webDavSourceId);
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
        // 1. episodes first (to get the right season folders for moving season artwork)
        for (TvShowEpisode tvEpisodesToRename : episodesToRename) {
          if (cancel) {
            break;
          }
          submitTask(new RenameEpisodeTask(tvEpisodesToRename));
        }

        waitForCompletionOrCancel();
        if (cancel) {
          return;
        }

        // 2. rename TV show root
        for (TvShow tvShow : tvShowsToRename) {
          TvShowRenamer.renameTvShow(tvShow); // rename root and artwork and update ShowMFs
        }

        LOGGER.info("Finished renaming TV shows/episodes - took {} ms", getRuntime());
      }
      finally {
        // 清理 Task 级别的共享客户端
        if (sharedClient != null) {
          try {
            TvShowRenamer.clearSharedWebDavClient();
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
      LOGGER.error("Could not rename TV shows - '{}'", e.getMessage());
      MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, "Settings.renamer", "message.renamer.threadcrashed"));
    }
  }

  /**
   * ThreadpoolWorker to work off ONE episode
   */
  private static class RenameEpisodeTask implements Callable<Object> {
    private final TvShowEpisode episode;

    public RenameEpisodeTask(TvShowEpisode episode) {
      this.episode = episode;
    }

    @Override
    public String call() {
      TvShowRenamer.renameEpisode(episode);
      return episode.getTitle();
    }
  }

  @Override
  public void callback(Object obj) {
    publishState((String) obj, progressDone);
  }
}
