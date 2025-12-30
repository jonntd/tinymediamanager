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

      // WebDAV 即使启用多线程也建议保持较低的并发，这里设为 2 线程
      int threadCount = hasWebDav ? 2 : Math.min(4, Runtime.getRuntime().availableProcessors());
      if (hasWebDav) {
        LOGGER.info("WebDAV data source detected, using {} threads for parallel rename", threadCount);
      }
      initThreadPool(threadCount, "rename");

      // 注意：多线程模式下不使用共享客户端，每个工作线程创建自己的连接
      // 这样可以并发执行多个重命名操作

      try {

        // 注意：预检测已移除以提高性能。如需检测冲突，请使用"重命名预览"功能。
        // 运行时的冲突通过自愈机制处理（如自动生成唯一路径）。

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
        // 多线程模式下，每个工作线程管理自己的 WebDAV 连接
        // 不需要在这里清理共享客户端
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
