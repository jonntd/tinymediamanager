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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.ScraperMetadataConfig;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.entities.MediaTrailer;
import org.tinymediamanager.core.entities.Person;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.tvshow.TvShowEpisodeScraperMetadataConfig;
import org.tinymediamanager.core.tvshow.TvShowEpisodeSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.TvShowHelpers;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowScraperMetadataConfig;
import org.tinymediamanager.core.tvshow.TvShowSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.entities.TvShow;

import org.tinymediamanager.core.tvshow.services.BatchChatGPTTvShowRecognitionService;
import org.tinymediamanager.core.tvshow.services.TvShowAIRecognitionManager;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.ParserUtils;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.MediaType;
import java.util.Map;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.ArtworkSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScraper;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.TrailerSearchAndScrapeOptions;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.entities.MediaEpisodeGroup;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.NothingFoundException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.ITvShowArtworkProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowMetadataProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowTrailerProvider;
import org.tinymediamanager.scraper.rating.RatingProvider;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.thirdparty.trakttv.TvShowSyncTraktTvTask;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.tinymediamanager.ui.tvshows.dialogs.TvShowChooserDialog;

/**
 * The class TvShowScrapeTask. This starts scraping of TV shows
 *
 * @author Manuel Laggner
 */
public class TvShowScrapeTask extends TmmThreadPool {
  private static final Logger LOGGER          = LoggerFactory.getLogger(TvShowScrapeTask.class);

  final TvShowScrapeParams    tvShowScrapeParams;
  private final List<TvShow>  smartScrapeList = Collections.synchronizedList(new ArrayList<>());

  /**
   * Instantiates a new tv show scrape task.
   * 
   * @param tvShowScrapeParams
   *          the {@link TvShowScrapeParams} containing all parameters for the scrape
   */
  public TvShowScrapeTask(final TvShowScrapeParams tvShowScrapeParams) {
    super(TmmResourceBundle.getString("tvshow.scraping"));
    this.tvShowScrapeParams = tvShowScrapeParams;
  }

  /**
   * Helper method to access the inherited protected 'cancel' field Used to avoid synthetic accessor generation for inner classes
   */
  boolean isTaskCancelled() {
    return cancel;
  }

  @Override
  protected void doInBackground() {
    // set up scrapers
    MediaScraper mediaMetadataScraper = tvShowScrapeParams.scrapeOptions.getMetadataScraper();

    if (!mediaMetadataScraper.isEnabled()) {
      return;
    }

    LOGGER.debug("start scraping tv shows...");
    start();

    // 初始化线程池，移到AI识别之前，以便并行处理（与电影模块保持一致）
    initThreadPool(3, "scrape");

    // 使用线程安全的Map存储AI识别结果
    java.util.concurrent.ConcurrentHashMap<String, String> aiRecognitionResults = new java.util.concurrent.ConcurrentHashMap<>();

    // 开始新的AI识别会话，清空缓存和计数器
    TvShowAIRecognitionManager.getInstance().startNewSession();

    // 批量AI识别优化：批次处理，识别一部分就刮削一部分（流式分批模式）
    if (tvShowScrapeParams.doSearch && !tvShowScrapeParams.tvShowsToScrape.isEmpty()) {
      // 检查是否配置了 OpenAI API Key
      String apiKey = org.tinymediamanager.core.Settings.getInstance().getOpenAiApiKey();
      LOGGER.debug("OpenAI API Key check: {}", apiKey != null && !apiKey.trim().isEmpty() ? "configured" : "not configured");

      if (apiKey != null && !apiKey.trim().isEmpty()) {
        try {
          LOGGER.info("Starting batch AI recognition for {} TV shows", tvShowScrapeParams.tvShowsToScrape.size());

          // 发送批量AI识别开始消息到Message history
          String startMsg = String.format("批量电视剧AI识别开始: %d 部电视剧", tvShowScrapeParams.tvShowsToScrape.size());
          MessageManager.getInstance().pushMessage(new Message(MessageLevel.INFO, "批量电视剧AI识别", startMsg));

          BatchChatGPTTvShowRecognitionService batchService = new BatchChatGPTTvShowRecognitionService();

          // 手动拆分批次，从设置中获取批次大小
          int batchSize = org.tinymediamanager.core.Settings.getInstance().getAiBatchSize();
          int totalTvShows = tvShowScrapeParams.tvShowsToScrape.size();
          int totalBatches = (int) Math.ceil((double) totalTvShows / batchSize);

          LOGGER.info("Processing {} TV shows in {} batches of {} shows each", totalTvShows, totalBatches, batchSize);

          // 循环处理每个批次（流式分批：识别一批 -> 提交刮削 -> 识别下一批）
          for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, totalTvShows);
            List<TvShow> currentBatch = tvShowScrapeParams.tvShowsToScrape.subList(startIndex, endIndex);

            LOGGER.info("Processing batch {}/{} ({} TV shows)", batchIndex + 1, totalBatches, currentBatch.size());

            // 处理当前批次的AI识别
            Map<String, String> batchResults = batchService.batchRecognizeTvShowTitles(currentBatch);

            // 将当前批次的识别结果添加到总结果中
            aiRecognitionResults.putAll(batchResults);

            LOGGER.info("Batch {} AI recognition completed for {} TV shows", batchIndex + 1, batchResults.size());

            // 立即提交当前批次的刮削任务（流式处理，用户能更快看到进度）
            for (TvShow tvShow : currentBatch) {
              submitTask(new Worker(tvShow, aiRecognitionResults));
            }
          }

          LOGGER.info("All batches AI recognition completed for {} TV shows", aiRecognitionResults.size());

          // 发送批量AI识别完成消息到Message history
          int successCount = aiRecognitionResults.size();
          int totalCount = tvShowScrapeParams.tvShowsToScrape.size();
          String completeMsg = String.format("批量电视剧AI识别完成: 成功 %d/%d (%.1f%%)", successCount, totalCount, (successCount * 100.0 / totalCount));
          MessageManager.getInstance().pushMessage(new Message(MessageLevel.INFO, "批量电视剧AI识别", completeMsg));

        }
        catch (Exception e) {
          LOGGER.warn("Batch AI recognition failed, skipping individual fallback to prevent API spam: {}", e.getMessage());

          // 发送批量AI识别失败消息到Message history，但不回退到个体识别
          String failMsg = String.format("批量电视剧AI识别失败，已跳过个体回退以防止API过度调用: %s", e.getMessage());
          MessageManager.getInstance().pushMessage(new Message(MessageLevel.WARN, "批量电视剧AI识别", failMsg));

          // 即使AI识别失败，也要提交所有电视剧的刮削任务
          for (TvShow tvShow : tvShowScrapeParams.tvShowsToScrape) {
            submitTask(new Worker(tvShow, aiRecognitionResults));
          }
        }
      }
      else {
        LOGGER.debug("OpenAI API key not configured, skipping batch AI recognition");

        // 直接提交所有电视剧的刮削任务
        for (TvShow tvShow : tvShowScrapeParams.tvShowsToScrape) {
          submitTask(new Worker(tvShow, aiRecognitionResults));
        }
      }
    }
    else {
      LOGGER.debug("Batch AI recognition skipped: doSearch={}, tvShowCount={}", tvShowScrapeParams.doSearch,
          tvShowScrapeParams.tvShowsToScrape.size());

      // 直接提交所有电视剧的刮削任务
      for (TvShow tvShow : tvShowScrapeParams.tvShowsToScrape) {
        submitTask(new Worker(tvShow, aiRecognitionResults));
      }
    }

    // 等待所有刮削任务完成
    waitForCompletionOrCancel();

    if (!smartScrapeList.isEmpty() && !cancel) {
      int maxRetries = TvShowModuleManager.getInstance().getSettings().getAutomaticScraperRetryCount();
      LOGGER.info("Checking smart scrape list for retries. List size: {}, Cancelled: {}", smartScrapeList.size(), cancel);

      for (int i = 0; i < maxRetries; i++) {
        if (smartScrapeList.isEmpty() || cancel) {
          LOGGER.info("Retry loop aborted. List empty: {}, Cancelled: {}", smartScrapeList.isEmpty(), cancel);
          break;
        }

        LOGGER.info("Automatic scrape retry round {}/{} for {} TV shows in smart scrape list", i + 1, maxRetries, smartScrapeList.size());

        // 发送重试通知
        MessageManager.getInstance()
            .pushMessage(new Message(MessageLevel.INFO, "自动刮削重试",
                String.format("正在对 %d 部未识别电视剧进行第 %d/%d 轮自动重试...", smartScrapeList.size(), i + 1, maxRetries)));

        List<TvShow> newSmartScrapeList;
        synchronized (smartScrapeList) {
          newSmartScrapeList = new ArrayList<>(smartScrapeList);
          smartScrapeList.clear();
        }

        LOGGER.info("Submitting {} TV shows for retry...", newSmartScrapeList.size());

        // Re-initialize thread pool because waitForCompletionOrCancel() shuts it down
        initThreadPool(3, "scrape-retry-" + (i + 1));

        for (TvShow tvShow : newSmartScrapeList) {
          submitTask(new Worker(tvShow, aiRecognitionResults));
        }
        waitForCompletionOrCancel();
        LOGGER.info("Retry round {}/{} completed. New smart scrape list size: {}", i + 1, maxRetries, smartScrapeList.size());
      }
    }

    if (!smartScrapeList.isEmpty() && !cancel && !GraphicsEnvironment.isHeadless()) {
      AtomicInteger queueIndex = new AtomicInteger(0);
      try {
        SwingUtilities.invokeAndWait(() -> {
          for (TvShow tvShow : smartScrapeList) {
            TvShowChooserDialog dialog = new TvShowChooserDialog(tvShow, queueIndex.getAndIncrement(), smartScrapeList.size());
            dialog.setVisible(true);
            if (!dialog.isContinueQueue()) {
              cancel = true;
              break;
            }
          }
        });
      }
      catch (Exception e) {
        LOGGER.error("Smart scrape dialog failed", e);
      }
    }

    if (TvShowModuleManager.getInstance().getSettings().getSyncTrakt()) {
      TvShowSyncTraktTvTask task = new TvShowSyncTraktTvTask(tvShowScrapeParams.tvShowsToScrape);
      task.setSyncCollection(TvShowModuleManager.getInstance().getSettings().getSyncTraktCollection());
      task.setSyncWatched(TvShowModuleManager.getInstance().getSettings().getSyncTraktWatched());
      task.setSyncRating(TvShowModuleManager.getInstance().getSettings().getSyncTraktRating());

      TmmTaskManager.getInstance().addUnnamedTask(task);
    }

    LOGGER.debug("done scraping tv shows...");
  }

  private class Worker implements Runnable {
    private final TvShowList          tvShowList = TvShowModuleManager.getInstance().getTvShowList();
    private final TvShow              tvShow;
    private final Map<String, String> aiRecognitionResults;

    private Worker(TvShow tvShow, Map<String, String> aiRecognitionResults) {
      this.tvShow = tvShow;
      this.aiRecognitionResults = aiRecognitionResults;
    }

    @Override
    public void run() {
      try {
        // set up scrapers
        MediaScraper mediaMetadataScraper = tvShowScrapeParams.scrapeOptions.getMetadataScraper();
        List<MediaScraper> trailerScrapers = tvShowScrapeParams.scrapeOptions.getTrailerScrapers();

        // scrape tv show

        // search for tv show
        MediaSearchResult result1 = null;
        if (tvShowScrapeParams.doSearch) {
          // 首先尝试使用AI识别结果进行搜索
          MediaSearchResult aiResult = tryAIRecognition(tvShow, mediaMetadataScraper);
          if (aiResult != null) {
            result1 = aiResult;
          }
          else {
            // 如果AI识别失败，使用原始标题搜索
            List<MediaSearchResult> results = tvShowList.searchTvShow(tvShow.getTitle(), tvShow.getYear(), tvShow.getIds(), mediaMetadataScraper);
            if (ListUtils.isNotEmpty(results)) {
              result1 = results.get(0);
              // check if there is another result with 100% score
              if (results.size() > 1) {
                MediaSearchResult result2 = results.get(1);
                // if both results have the same score - do not take any result
                if (result1.getScore() == result2.getScore()) {
                  LOGGER.warn("Two identical results for '{}', attempting AI fallback", tvShow.getTitle());
                  // 尝试单个文件AI识别回退
                  MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(tvShow, mediaMetadataScraper);
                  if (fallbackResult != null) {
                    result1 = fallbackResult;
                  }
                  else {
                    smartScrapeList.add(tvShow);
                    return;
                  }
                }

                // create a threshold of 0.75 - to minimize false positives
                if (result1.getScore() < 0.75) {
                  LOGGER.warn("Score ({}) is lower than threshold for '{}', attempting AI fallback", result1.getScore(), tvShow.getTitle());
                  // 尝试单个文件AI识别回退
                  MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(tvShow, mediaMetadataScraper);
                  if (fallbackResult != null) {
                    result1 = fallbackResult;
                  }
                  else {
                    smartScrapeList.add(tvShow);
                    return;
                  }
                }
              }
            }
            else {
              LOGGER.info("No result found for '{}', attempting AI fallback", tvShow.getTitle());
              // 尝试单个文件AI识别回退
              MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(tvShow, mediaMetadataScraper);
              if (fallbackResult != null) {
                result1 = fallbackResult;
              }
              else {
                smartScrapeList.add(tvShow);
                return;
              }
            }
          }
        }

        if (isTaskCancelled()) {
          return;
        }

        // get metadata and artwork
        try {
          TvShowSearchAndScrapeOptions options = new TvShowSearchAndScrapeOptions(tvShowScrapeParams.scrapeOptions);
          options.setSearchResult(result1);

          if (result1 != null) {
            options.setIds(result1.getIds());
          }
          else {
            options.setIds(tvShow.getIds());
          }

          // override scraper with one from search result
          if (result1 != null) {
            mediaMetadataScraper = tvShowList.getMediaScraperById(result1.getProviderId());
          }

          // scrape metadata if wanted
          MediaMetadata md = null;
          List<MediaMetadata> episodeList = null;

          if (ScraperMetadataConfig.containsAnyMetadata(tvShowScrapeParams.tvShowScraperMetadataConfig)
              || ScraperMetadataConfig.containsAnyCast(tvShowScrapeParams.tvShowScraperMetadataConfig)) {
            LOGGER.info("Scraping movie '{}' with '{}'", tvShow.getTitle(), mediaMetadataScraper.getMediaProvider().getProviderInfo().getId());

            LOGGER.debug("=====================================================");
            LOGGER.debug("Scrape tvShow metadata with scraper: {}", mediaMetadataScraper.getMediaProvider().getProviderInfo().getId());
            LOGGER.debug(options.toString());
            LOGGER.debug("=====================================================");
            md = ((ITvShowMetadataProvider) mediaMetadataScraper.getMediaProvider()).getMetadata(options);

            if (isTaskCancelled()) {
              return;
            }

            // also inject other ids
            MediaIdUtil.injectMissingIds(md.getIds(), MediaType.TV_SHOW);

            // also fill other ratings if ratings are requested
            if (TvShowModuleManager.getInstance().getSettings().isFetchAllRatings()
                && tvShowScrapeParams.tvShowScraperMetadataConfig.contains(TvShowScraperMetadataConfig.RATING)) {
              for (MediaRating rating : ListUtils.nullSafe(RatingProvider.getRatings(md.getIds(),
                  TvShowModuleManager.getInstance().getSettings().getFetchRatingSources(), MediaType.TV_SHOW))) {
                if (!md.getRatings().contains(rating)) {
                  md.addRating(rating);
                }
              }
            }

            // if there is obviously no episode group set, take the best one from the scraper
            // BUT: skip this for WebDAV shows to preserve their season information
            boolean isWebDav = tvShow.getPath() != null && tvShow.getPath().startsWith("webdav://");
            if (!isWebDav && tvShow.getEpisodeGroup() == MediaEpisodeGroup.DEFAULT_AIRED) {
              try {
                episodeList = ((ITvShowMetadataProvider) mediaMetadataScraper.getMediaProvider()).getEpisodeList(options);

                List<MediaEpisodeGroup> episodeGroups = new ArrayList<>(md.getEpisodeGroups());
                Collections.sort(episodeGroups);
                tvShow.setEpisodeGroup(TvShowHelpers.findBestMatchingEpisodeGroup(tvShow, episodeGroups, episodeList));
              }
              catch (Exception e) {
                LOGGER.debug("could not fetch episode list - '{}'", e.getMessage());
                tvShow.setEpisodeGroup(MediaEpisodeGroup.DEFAULT_AIRED);
              }
              finally {
                tvShow.setEpisodeGroups(md.getEpisodeGroups());
              }
            }
            else if (isWebDav) {
              LOGGER.debug("Skipping EpisodeGroup switch for WebDAV show '{}' to preserve season information", tvShow.getTitle());
              // Still set the available episode groups for reference
              tvShow.setEpisodeGroups(md.getEpisodeGroups());
            }

            tvShow.setMetadata(md, tvShowScrapeParams.tvShowScraperMetadataConfig, tvShowScrapeParams.overwriteExistingItems);
            tvShow.setLastScraperId(tvShowScrapeParams.scrapeOptions.getMetadataScraper().getId());
            tvShow.setLastScrapeLanguage(tvShowScrapeParams.scrapeOptions.getLanguage().name());

            // automatic rename? rename the TV show itself
            if (TvShowModuleManager.getInstance().getSettings().isRenameAfterScrape()) {
              TmmTask task = new TvShowRenameTask(tvShow);
              // blocking
              task.run();
            }

            // write actor images after possible rename (to have a good folder structure)
            if (ScraperMetadataConfig.containsAnyCast(tvShowScrapeParams.tvShowScraperMetadataConfig)
                && TvShowModuleManager.getInstance().getSettings().isWriteActorImages()) {
              tvShow.writeActorImages(tvShowScrapeParams.overwriteExistingItems);
            }
          }

          // always add all episode data (for missing episodes and episode list)
          List<TvShowEpisode> episodes = new ArrayList<>();
          try {
            if (episodeList == null) {
              episodeList = ((ITvShowMetadataProvider) mediaMetadataScraper.getMediaProvider()).getEpisodeList(options);
            }
            for (MediaMetadata me : episodeList) {
              TvShowEpisode ep = new TvShowEpisode();
              ep.setEpisodeNumbers(me.getEpisodeNumbers());
              ep.setFirstAired(me.getReleaseDate());
              ep.setTitle(me.getTitle());
              ep.setOriginalTitle(me.getOriginalTitle());
              ep.setPlot(me.getPlot());
              ep.setActors(me.getCastMembers(Person.Type.ACTOR));
              ep.setCrew(me.getCastMembers(Person.Type.DIRECTOR));
              ep.setCrew(me.getCastMembers(Person.Type.WRITER));
              ep.setCrew(me.getCastMembers(Person.Type.PRODUCER));
              ep.setCrew(me.getCastMembers(Person.Type.OTHER));

              Map<String, MediaRating> newRatings = new HashMap<>();

              for (MediaRating mediaRating : me.getRatings()) {
                newRatings.put(mediaRating.getId(), mediaRating);
              }
              ep.setRatings(newRatings);

              episodes.add(ep);
            }
          }
          catch (MissingIdException e) {
            LOGGER.warn("Could not get episode list for TV show '{}' - no IDs available", tvShow.getTitle());
            MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, tvShow, "scraper.error.missingid"));
          }
          catch (ScrapeException e) {
            LOGGER.error("Could not get episode list for TV show '{}' - '{}'", tvShow.getTitle(), e.getMessage());
            MessageManager.getInstance()
                .pushMessage(new Message(Message.MessageLevel.ERROR, tvShow, "message.scrape.episodelistfailed",
                    new String[] { ":", e.getLocalizedMessage() }));
          }
          catch (Exception e) {
            LOGGER.error("Unforeseen error in TV show scrape for '{}'", tvShow.getTitle(), e);
          }

          tvShow.setDummyEpisodes(episodes);
          tvShow.saveToDb();

          if (isTaskCancelled()) {
            return;
          }

          // scrape artwork if wanted
          if (ScraperMetadataConfig.containsAnyArtwork(tvShowScrapeParams.tvShowScraperMetadataConfig)) {
            tvShow.setArtwork(getArtwork(tvShow, md), tvShowScrapeParams.tvShowScraperMetadataConfig, tvShowScrapeParams.overwriteExistingItems);
          }

          if (isTaskCancelled()) {
            return;
          }

          // scrape trailer if wanted
          if (tvShowScrapeParams.tvShowScraperMetadataConfig.contains(TvShowScraperMetadataConfig.TRAILER)) {
            tvShow.setTrailers(getTrailers(tvShow, md, trailerScrapers));
            tvShow.writeNFO();
            tvShow.saveToDb();

            // start automatic movie trailer download
            if (TvShowModuleManager.getInstance().getSettings().isUseTrailerPreference()
                && TvShowModuleManager.getInstance().getSettings().isAutomaticTrailerDownload()
                && tvShow.getMediaFiles(MediaFileType.TRAILER).isEmpty() && !tvShow.getTrailer().isEmpty()) {
              TmmTaskManager.getInstance().addDownloadTask(new TvShowTrailerDownloadTask(tvShow));
            }
          }

          if (isTaskCancelled()) {
            return;
          }

          // download theme
          if (tvShowScrapeParams.tvShowScraperMetadataConfig.contains(TvShowScraperMetadataConfig.THEME)) {
            TmmTaskManager.getInstance()
                .addUnnamedTask(new TvShowThemeDownloadTask(Collections.singletonList(tvShow), tvShowScrapeParams.overwriteExistingItems));
          }

          if (isTaskCancelled()) {
            return;
          }

          // scrape episodes
          if (!tvShowScrapeParams.episodeScraperMetadataConfig.isEmpty()) {
            List<TvShowEpisode> episodesToScrape = tvShow.getEpisodesToScrape();
            // scrape episodes
            TvShowEpisodeSearchAndScrapeOptions options1 = new TvShowEpisodeSearchAndScrapeOptions();
            options1.loadDefaults();
            options1.setDataFromOtherOptions(options);

            for (TvShowEpisode episode : episodesToScrape) {
              if (isTaskCancelled()) {
                break;
              }

              TvShowEpisodeScrapeTask task = new TvShowEpisodeScrapeTask(Collections.singletonList(episode), options1,
                  tvShowScrapeParams.episodeScraperMetadataConfig, tvShowScrapeParams.overwriteExistingItems);
              // start this task embedded (to the abortable)
              task.run();
            }
          }

          if (isTaskCancelled()) {
            return;
          }

          // last but not least - call a further rename task on the TV show root to move the season fanart into the right folders
          // but only if there has been anything scraped
          if (TvShowModuleManager.getInstance().getSettings().isRenameAfterScrape()
              && (!tvShowScrapeParams.tvShowScraperMetadataConfig.isEmpty() || !tvShowScrapeParams.episodeScraperMetadataConfig.isEmpty())) {
            TvShowRenameTask task = new TvShowRenameTask(tvShow);
            // start this task embedded (to the abortable)
            task.run();
          }
        }
        catch (MissingIdException e) {
          LOGGER.warn("Could not scrape TV show '{}' - no ID available", tvShow.getTitle());
          MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, tvShow, "scraper.error.missingid"));
        }
        catch (NothingFoundException e) {
          LOGGER.debug("nothing found for '{}'", tvShow.getTitle());
        }
        catch (ScrapeException e) {
          LOGGER.error("Unforeseen error in TV show scrape for '{}'", tvShow.getTitle(), e);
          MessageManager.getInstance()
              .pushMessage(new Message(Message.MessageLevel.ERROR, tvShow, "message.scrape.metadatatvshowfailed",
                  new String[] { ":", e.getLocalizedMessage() }));
        }
      }

      catch (Exception e) {
        LOGGER.error("Could not scrape TV show '{}' - '{}'", tvShow.getTitle(), e.getMessage());
        MessageManager.getInstance()
            .pushMessage(
                new Message(MessageLevel.ERROR, "TvShowScraper", "message.scrape.threadcrashed", new String[] { ":", e.getLocalizedMessage() }));
      }
    }

    /**
     * Gets the artwork.
     *
     * @param tvShow
     *          the {@link TvShow} to get the artwork for
     * @param metadata
     *          already scraped {@link MediaMetadata}
     * @return the artwork
     */
    private List<MediaArtwork> getArtwork(TvShow tvShow, MediaMetadata metadata) {
      ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
      List<MediaArtwork> artwork = new ArrayList<>();

      ArtworkSearchAndScrapeOptions options = new ArtworkSearchAndScrapeOptions(MediaType.TV_SHOW);
      options.setDataFromOtherOptions(tvShowScrapeParams.scrapeOptions);
      options.setArtworkType(MediaArtworkType.ALL);
      options.setFanartSize(TvShowModuleManager.getInstance().getSettings().getImageFanartSize());
      options.setPosterSize(TvShowModuleManager.getInstance().getSettings().getImagePosterSize());
      options.setThumbSize(TvShowModuleManager.getInstance().getSettings().getImageThumbSize());
      options.setMetadata(metadata);
      options.addIds(tvShow.getIds());

      // scrape providers till one artwork has been found
      tvShowScrapeParams.scrapeOptions.getArtworkScrapers().parallelStream().forEach(artworkScraper -> {
        ITvShowArtworkProvider artworkProvider = (ITvShowArtworkProvider) artworkScraper.getMediaProvider();
        try {
          lock.writeLock().lock();
          artwork.addAll(artworkProvider.getArtwork(options));
        }
        catch (MissingIdException ignored) {
          LOGGER.info("Missing IDs for scraping TV show artwork of '{}' with '{}'", tvShow.getTitle(), artworkScraper.getId());
        }
        catch (NothingFoundException e) {
          LOGGER.debug("did not find artwork for '{}'", tvShow.getTitle());
        }
        catch (ScrapeException e) {
          LOGGER.error("Could not scrape artwork for TV show '{}' with '{}' - '{}'", tvShow.getTitle(), artworkScraper.getId(), e.getMessage());
          MessageManager.getInstance()
              .pushMessage(new Message(Message.MessageLevel.ERROR, tvShow, "message.scrape.tvshowartworkfailed",
                  new String[] { ":", e.getLocalizedMessage() }));
        }
        catch (Exception e) {
          LOGGER.error("Unforeseen error in TV show artwork scrape for '{}'", tvShow.getTitle(), e);
        }
        finally {
          lock.writeLock().unlock();
        }
      });

      return artwork;
    }

    private List<MediaTrailer> getTrailers(TvShow tvShow, MediaMetadata metadata, List<MediaScraper> trailerScrapers) {
      ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
      List<MediaTrailer> trailers = new ArrayList<>();

      TrailerSearchAndScrapeOptions options = new TrailerSearchAndScrapeOptions(MediaType.TV_SHOW);

      options.setDataFromOtherOptions(tvShowScrapeParams.scrapeOptions);
      options.setMetadata(metadata);

      for (Entry<String, Object> entry : tvShow.getIds().entrySet()) {
        options.setId(entry.getKey(), entry.getValue().toString());
      }

      // scrape trailers
      trailerScrapers.parallelStream().forEach(trailerScraper -> {
        ITvShowTrailerProvider trailerProvider = (ITvShowTrailerProvider) trailerScraper.getMediaProvider();
        try {
          lock.writeLock().lock();
          trailers.addAll(trailerProvider.getTrailers(options));
        }
        catch (MissingIdException e) {
          LOGGER.info("Missing IDs for scraping TV show trailer of '{}' with '{}'", tvShow.getTitle(), trailerScraper.getId());
        }
        catch (ScrapeException e) {
          LOGGER.error("Could not scrape trailers for TV show '{}' with '{}' - '{}'", tvShow.getTitle(), trailerScraper.getId(), e.getMessage());
          MessageManager.getInstance()
              .pushMessage(new Message(MessageLevel.ERROR, tvShow, "message.scrape.trailerfailed", new String[] { ":", e.getLocalizedMessage() }));
        }
        catch (Exception e) {
          LOGGER.error("Unforeseen error in TV show trailer scrape for '{}'", tvShow.getTitle(), e);
        }
        finally {
          lock.writeLock().unlock();
        }
      });

      return trailers;
    }

    /**
     * 尝试使用AI识别结果进行搜索 使用TvShowAIRecognitionManager统一管理调用次数
     */
    private MediaSearchResult tryAIRecognition(TvShow tvShow, MediaScraper mediaMetadataScraper) {
      // 使用TvShowAIRecognitionManager统一获取AI识别结果
      TvShowAIRecognitionManager aiManager = TvShowAIRecognitionManager.getInstance();
      String recognizedTitle = aiManager.getRecognizedTitle(tvShow, aiRecognitionResults);

      if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
        LOGGER.info("AI recognition result for TV show '{}': '{}'", tvShow.getTitle(), recognizedTitle);

        // 发送识别成功消息到Message history
        String successMsg = String.format("AI识别: %s → %s", tvShow.getTitle(), recognizedTitle);
        MessageManager.getInstance().pushMessage(new Message(MessageLevel.INFO, "电视剧AI识别", successMsg));
      }
      else {
        LOGGER.debug("No AI recognition result available for TV show '{}' (attempts: {})", tvShow.getTitle(), aiManager.getAttemptCount(tvShow));
        return null;
      }

      // 使用AI识别的标题进行搜索
      String[] aiParserInfo = ParserUtils.detectCleanTitleAndYear(recognizedTitle, java.util.Collections.emptyList());
      String aiProcessedTitle = recognizedTitle;
      Integer aiProcessedYear = null;

      // 详细记录 ParserUtils 解析结果
      LOGGER.info("ParserUtils.detectCleanTitleAndYear('{}') returned: title='{}', year='{}'", recognizedTitle,
          aiParserInfo != null && aiParserInfo.length >= 1 ? aiParserInfo[0] : "null",
          aiParserInfo != null && aiParserInfo.length >= 2 ? aiParserInfo[1] : "null");

      if (aiParserInfo != null && aiParserInfo.length >= 2) {
        aiProcessedTitle = aiParserInfo[0];
        if (org.apache.commons.lang3.StringUtils.isNotBlank(aiParserInfo[1])) {
          try {
            aiProcessedYear = Integer.parseInt(aiParserInfo[1]);
          }
          catch (NumberFormatException e) {
            LOGGER.debug("Could not parse year from AI result: {}", aiParserInfo[1]);
          }
        }
      }

      // 备用年份提取：如果 ParserUtils 没有解析出年份，直接从 AI 结果末尾提取
      if (aiProcessedYear == null && recognizedTitle.matches(".*\\s+\\d{4}\\s*$")) {
        java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("(\\d{4})\\s*$");
        java.util.regex.Matcher yearMatcher = yearPattern.matcher(recognizedTitle.trim());
        if (yearMatcher.find()) {
          try {
            int extractedYear = Integer.parseInt(yearMatcher.group(1));
            int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            if (extractedYear > 1888 && extractedYear <= currentYear + 2) {
              aiProcessedYear = extractedYear;
              aiProcessedTitle = recognizedTitle.substring(0, yearMatcher.start()).trim();
              LOGGER.info("Fallback year extraction: title='{}', year={}", aiProcessedTitle, aiProcessedYear);
            }
          }
          catch (NumberFormatException e) {
            LOGGER.debug("Could not parse year from regex match: {}", yearMatcher.group(1));
          }
        }
      }

      // 如果仍然没有解析出年份，使用电视剧原始年份（作为回退）
      if (aiProcessedYear == null) {
        aiProcessedYear = tvShow.getYear();
        LOGGER.debug("No year from AI, using original TV show year: {}", aiProcessedYear);
      }

      LOGGER.info("AI processed title '{}' -> '{}' (year: {})", recognizedTitle, aiProcessedTitle, aiProcessedYear);

      try {
        // AI 识别搜索时不传入电视剧原有 ID，以避免因旧 ID 返回错误结果
        List<MediaSearchResult> aiResults = tvShowList.searchTvShow(aiProcessedTitle, aiProcessedYear, null, mediaMetadataScraper);

        if (ListUtils.isNotEmpty(aiResults)) {
          // 1. 首先优先从文件路径解析 TMDB ID（比 tvShow.getTmdbId() 更可靠）
          MediaSearchResult aiResult = null;
          int pathTmdbId = 0;
          String showPath = tvShow.getPathNIO() != null ? tvShow.getPathNIO().toString() : "";
          pathTmdbId = ParserUtils.detectTmdbId(showPath);
          if (pathTmdbId <= 0) {
            // 如果路径中没有，使用电视剧对象中已存储的 ID 作为备用
            pathTmdbId = tvShow.getTmdbId();
          }
          LOGGER.info("TV Show '{}' TMDB ID from path: {}, from object: {}", tvShow.getTitle(), ParserUtils.detectTmdbId(showPath),
              tvShow.getTmdbId());
          if (pathTmdbId > 0) {
            for (MediaSearchResult result : aiResults) {
              if (result.getIdAsInt(MediaMetadata.TMDB) == pathTmdbId) {
                aiResult = result;
                LOGGER.info("Found exact TMDB ID match: title='{}', tmdbId={}, score={}", result.getTitle(), pathTmdbId, result.getScore());
                break;
              }
            }
          }

          // 2. 如果没有 ID 匹配，在年份匹配的结果中选择标题相似度最高的
          if (aiResult == null && aiProcessedYear > 0) {
            MediaSearchResult bestYearMatch = null;
            float bestSimilarity = 0.0f;

            for (MediaSearchResult result : aiResults) {
              if (result.getYear() == aiProcessedYear) {
                // 计算标题相似度
                float similarity = org.tinymediamanager.scraper.util.Similarity.compareStrings(aiProcessedTitle, result.getTitle());
                LOGGER.debug("Year match candidate: title='{}', year={}, similarity={}", result.getTitle(), result.getYear(), similarity);

                if (similarity > bestSimilarity) {
                  bestSimilarity = similarity;
                  bestYearMatch = result;
                }
              }
            }

            // 只有当标题相似度超过阈值时才认为是有效匹配
            if (bestYearMatch != null && bestSimilarity >= 0.5f) {
              aiResult = bestYearMatch;
              LOGGER.info("Found best year+title match: title='{}', year={}, similarity={}, score={}", aiResult.getTitle(), aiResult.getYear(),
                  bestSimilarity, aiResult.getScore());
            }
            else if (bestYearMatch != null) {
              LOGGER.warn("Year match found but title similarity too low ({}): expected='{}', got='{}'", bestSimilarity, aiProcessedTitle,
                  bestYearMatch.getTitle());
            }
          }

          // 3. 如果都没有匹配，使用第一个结果
          if (aiResult == null) {
            aiResult = aiResults.get(0);
          }

          if (aiResult.getScore() >= 0.75) {
            LOGGER.info("AI recognition successful! Found match with score: {}", aiResult.getScore());
            // 标记为已验证
            aiManager.markAsValidated(tvShow, recognizedTitle);
            return aiResult;
          }
          else {
            LOGGER.warn("AI recognized title found, but score ({}) is lower than threshold (0.75), attempting individual fallback",
                aiResult.getScore());
            // 尝试单个文件AI识别回退
            MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(tvShow, mediaMetadataScraper);
            if (fallbackResult != null) {
              return fallbackResult;
            }
          }
        }
        else {
          LOGGER.info("No results found for AI recognized title: '{}', attempting individual fallback", aiProcessedTitle);
          // 尝试单个文件AI识别回退
          MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(tvShow, mediaMetadataScraper);
          if (fallbackResult != null) {
            return fallbackResult;
          }
        }
      }
      catch (Exception e) {
        LOGGER.warn("Error during AI search for TV show '{}': {}", aiProcessedTitle, e.getMessage());
      }

      return null; // AI识别失败，返回null让调用者使用原始搜索
    }

    /**
     * 回退到单个文件AI识别 使用TvShowAIRecognitionManager统一管理调用次数
     */
    private MediaSearchResult fallbackToIndividualAIRecognition(TvShow tvShow, MediaScraper mediaMetadataScraper) {
      try {
        // 使用TvShowAIRecognitionManager检查是否还可以进行AI识别
        TvShowAIRecognitionManager aiManager = TvShowAIRecognitionManager.getInstance();

        if (!aiManager.canAttemptRecognition(tvShow)) {
          LOGGER.debug("Max AI recognition attempts reached for TV show '{}', skipping fallback", tvShow.getTitle());
          return null;
        }

        LOGGER.info("Attempting individual AI recognition fallback for TV show: '{}' (attempt: {})", tvShow.getTitle(),
            aiManager.getAttemptCount(tvShow) + 1);

        // 通过TvShowAIRecognitionManager获取识别结果（会自动检查用户设置和调用次数）
        String recognizedTitle = aiManager.getRecognizedTitle(tvShow, null);

        if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
          LOGGER.info("Individual AI recognition successful: '{}' for TV show: '{}'", recognizedTitle, tvShow.getTitle());

          // 解析AI识别结果
          String[] aiParserInfo = ParserUtils.detectCleanTitleAndYear(recognizedTitle, java.util.Collections.emptyList());
          String aiProcessedTitle = recognizedTitle;
          Integer aiProcessedYear = null;

          if (aiParserInfo != null && aiParserInfo.length >= 2) {
            aiProcessedTitle = aiParserInfo[0];
            if (org.apache.commons.lang3.StringUtils.isNotBlank(aiParserInfo[1])) {
              try {
                aiProcessedYear = Integer.parseInt(aiParserInfo[1]);
              }
              catch (NumberFormatException e) {
                LOGGER.debug("Could not parse year from AI result: {}", aiParserInfo[1]);
              }
            }
          }

          // 备用年份提取
          if (aiProcessedYear == null && recognizedTitle.matches(".*\\s+\\d{4}\\s*$")) {
            java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("(\\d{4})\\s*$");
            java.util.regex.Matcher yearMatcher = yearPattern.matcher(recognizedTitle.trim());
            if (yearMatcher.find()) {
              try {
                int extractedYear = Integer.parseInt(yearMatcher.group(1));
                int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                if (extractedYear > 1888 && extractedYear <= currentYear + 2) {
                  aiProcessedYear = extractedYear;
                  aiProcessedTitle = recognizedTitle.substring(0, yearMatcher.start()).trim();
                  LOGGER.info("Fallback year extraction in individual: title='{}', year={}", aiProcessedTitle, aiProcessedYear);
                }
              }
              catch (NumberFormatException e) {
                LOGGER.debug("Could not parse year from regex: {}", yearMatcher.group(1));
              }
            }
          }

          // 回退到原始年份
          if (aiProcessedYear == null) {
            aiProcessedYear = tvShow.getYear();
          }

          // 使用单个AI识别结果进行搜索
          // AI 识别搜索时不传入电视剧原有 ID，以避免因旧 ID 返回错误结果
          List<MediaSearchResult> aiResults = tvShowList.searchTvShow(aiProcessedTitle, aiProcessedYear, null, mediaMetadataScraper);

          if (ListUtils.isNotEmpty(aiResults)) {
            MediaSearchResult aiResult = aiResults.get(0);

            if (aiResult.getScore() >= 0.75) {
              LOGGER.info("Individual AI recognition successful! Found match with score: {}", aiResult.getScore());
              // 标记为已验证
              aiManager.markAsValidated(tvShow, recognizedTitle);
              return aiResult;
            }
            else {
              LOGGER.warn("Individual AI recognized title found, but score ({}) is lower than threshold (0.75)", aiResult.getScore());
            }
          }
          else {
            LOGGER.info("No results found for individual AI recognized title: '{}'", aiProcessedTitle);
          }
        }
        else {
          LOGGER.debug("No new AI recognition result for TV show '{}' from fallback", tvShow.getTitle());
        }
      }
      catch (Exception e) {
        LOGGER.error("Error during individual AI recognition fallback: {}", e.getMessage());
      }

      return null;
    }
  }

  @Override
  public void callback(Object obj) {
    // do not publish task description here, because with different workers the text is never right
    publishState(progressDone);
  }

  public static class TvShowScrapeParams {
    private final List<TvShow>                             tvShowsToScrape;
    private final TvShowSearchAndScrapeOptions             scrapeOptions;
    private final List<TvShowScraperMetadataConfig>        tvShowScraperMetadataConfig  = new ArrayList<>();
    private final List<TvShowEpisodeScraperMetadataConfig> episodeScraperMetadataConfig = new ArrayList<>();

    private boolean                                        doSearch;
    private boolean                                        overwriteExistingItems;

    public TvShowScrapeParams(List<TvShow> tvShowsToScrape, TvShowSearchAndScrapeOptions scrapeOptions,
        List<TvShowScraperMetadataConfig> tvShowScraperMetadataConfig, List<TvShowEpisodeScraperMetadataConfig> episodeScraperMetadataConfig) {
      this.tvShowsToScrape = tvShowsToScrape;
      this.scrapeOptions = scrapeOptions;
      this.tvShowScraperMetadataConfig.addAll(tvShowScraperMetadataConfig);
      this.episodeScraperMetadataConfig.addAll(episodeScraperMetadataConfig);

      this.doSearch = true;
      this.overwriteExistingItems = true;
    }

    public TvShowScrapeParams setDoSearch(boolean doSearch) {
      this.doSearch = doSearch;
      return this;
    }

    public TvShowScrapeParams setOverwriteExistingItems(boolean overwriteExistingItems) {
      this.overwriteExistingItems = overwriteExistingItems;
      return this;
    }
  }
}
