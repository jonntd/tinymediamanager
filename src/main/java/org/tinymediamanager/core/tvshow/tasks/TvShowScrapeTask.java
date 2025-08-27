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
import org.tinymediamanager.core.tvshow.services.ChatGPTTvShowRecognitionService;
import org.tinymediamanager.core.tvshow.services.BatchChatGPTTvShowRecognitionService;
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

/**
 * The class TvShowScrapeTask. This starts scraping of TV shows
 *
 * @author Manuel Laggner
 */
public class TvShowScrapeTask extends TmmThreadPool {
  private static final Logger      LOGGER = LoggerFactory.getLogger(TvShowScrapeTask.class);

  private final TvShowScrapeParams tvShowScrapeParams;

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

  @Override
  protected void doInBackground() {
    // set up scrapers
    MediaScraper mediaMetadataScraper = tvShowScrapeParams.scrapeOptions.getMetadataScraper();

    if (!mediaMetadataScraper.isEnabled()) {
      return;
    }

    LOGGER.debug("start scraping tv shows...");
    start();

    // 批量AI识别优化：在启动线程池前统一处理
    Map<String, String> aiRecognitionResults = null;
    if (tvShowScrapeParams.doSearch && !tvShowScrapeParams.tvShowsToScrape.isEmpty()) {
      // 检查是否配置了 OpenAI API Key
      String apiKey = org.tinymediamanager.core.Settings.getInstance().getOpenAiApiKey();
      LOGGER.debug("OpenAI API Key check: {}", apiKey != null && !apiKey.trim().isEmpty() ? "configured" : "not configured");

      if (apiKey != null && !apiKey.trim().isEmpty()) {
        try {
          LOGGER.info("Starting batch AI recognition for {} TV shows", tvShowScrapeParams.tvShowsToScrape.size());

          // 发送批量AI识别开始消息到Message history
          String startMsg = String.format("批量电视剧AI识别开始: %d 部电视剧", tvShowScrapeParams.tvShowsToScrape.size());
          MessageManager.getInstance().pushMessage(
              new Message(MessageLevel.INFO, "批量电视剧AI识别", startMsg));

          BatchChatGPTTvShowRecognitionService batchService = new BatchChatGPTTvShowRecognitionService();
          aiRecognitionResults = batchService.batchRecognizeTvShowTitles(tvShowScrapeParams.tvShowsToScrape);

          LOGGER.info("Batch AI recognition completed for {} TV shows", aiRecognitionResults.size());

          // 发送批量AI识别完成消息到Message history
          int successCount = aiRecognitionResults.size();
          int totalCount = tvShowScrapeParams.tvShowsToScrape.size();
          String completeMsg = String.format("批量电视剧AI识别完成: 成功 %d/%d (%.1f%%)",
              successCount, totalCount, (successCount * 100.0 / totalCount));
          MessageManager.getInstance().pushMessage(
              new Message(MessageLevel.INFO, "批量电视剧AI识别", completeMsg));

        } catch (Exception e) {
          LOGGER.warn("Batch AI recognition failed, falling back to individual recognition: {}", e.getMessage());

          // 发送批量AI识别失败消息到Message history
          String failMsg = String.format("批量电视剧AI识别失败: %s", e.getMessage());
          MessageManager.getInstance().pushMessage(
              new Message(MessageLevel.WARN, "批量电视剧AI识别", failMsg));
        }
      } else {
        LOGGER.debug("OpenAI API key not configured, skipping batch AI recognition");
      }
    } else {
      LOGGER.debug("Batch AI recognition skipped: doSearch={}, tvShowCount={}",
                   tvShowScrapeParams.doSearch, tvShowScrapeParams.tvShowsToScrape.size());
    }

    initThreadPool(3, "scrape");
    for (TvShow tvShow : tvShowScrapeParams.tvShowsToScrape) {
      submitTask(new Worker(tvShow, aiRecognitionResults));
    }

    waitForCompletionOrCancel();

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
    private final TvShowList tvShowList = TvShowModuleManager.getInstance().getTvShowList();
    private final TvShow     tvShow;
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
          } else {
            // 如果AI识别失败，使用原始标题搜索
            List<MediaSearchResult> results = tvShowList.searchTvShow(tvShow.getTitle(), tvShow.getYear(), tvShow.getIds(), mediaMetadataScraper);
            if (ListUtils.isNotEmpty(results)) {
              result1 = results.get(0);
              // check if there is another result with 100% score
              if (results.size() > 1) {
                MediaSearchResult result2 = results.get(1);
                // if both results have the same score - do not take any result
                if (result1.getScore() == result2.getScore()) {
                  LOGGER.warn("Two identical results for '{}', can't decide which to take - ignore result", tvShow.getTitle());
                  MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, tvShow, "tvshow.scrape.nomatchfound"));
                  return;
                }

                // create a threshold of 0.75 - to minimize false positives
                if (result1.getScore() < 0.75) {
                  LOGGER.warn("Score ({}) is lower than minimum score (0.75) for '{}' - ignore result", result1.getScore(), tvShow.getTitle());
                  MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, tvShow, "tvshow.scrape.nomatchfound"));
                  return;
                }
              }
            }
            else {
              LOGGER.info("No result found for {}", tvShow.getTitle());
              MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, tvShow, "tvshow.scrape.nomatchfound"));
              return;
            }
          }
        }

        if (cancel) {
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

            if (cancel) {
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
            if (tvShow.getEpisodeGroup() == MediaEpisodeGroup.DEFAULT_AIRED) {
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

          if (cancel) {
            return;
          }

          // scrape artwork if wanted
          if (ScraperMetadataConfig.containsAnyArtwork(tvShowScrapeParams.tvShowScraperMetadataConfig)) {
            tvShow.setArtwork(getArtwork(tvShow, md), tvShowScrapeParams.tvShowScraperMetadataConfig, tvShowScrapeParams.overwriteExistingItems);
          }

          if (cancel) {
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

          if (cancel) {
            return;
          }

          // download theme
          if (tvShowScrapeParams.tvShowScraperMetadataConfig.contains(TvShowScraperMetadataConfig.THEME)) {
            TmmTaskManager.getInstance()
                .addUnnamedTask(new TvShowThemeDownloadTask(Collections.singletonList(tvShow), tvShowScrapeParams.overwriteExistingItems));
          }

          if (cancel) {
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
              if (cancel) {
                break;
              }

              TvShowEpisodeScrapeTask task = new TvShowEpisodeScrapeTask(Collections.singletonList(episode), options1,
                  tvShowScrapeParams.episodeScraperMetadataConfig, tvShowScrapeParams.overwriteExistingItems);
              // start this task embedded (to the abortable)
              task.run();
            }
          }

          if (cancel) {
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
     * 尝试使用AI识别结果进行搜索
     */
    private MediaSearchResult tryAIRecognition(TvShow tvShow, MediaScraper mediaMetadataScraper) {
      String recognizedTitle = null;

      LOGGER.info("=== TV Show AI Recognition in Scrape Task ===");
      LOGGER.info("TV Show: {} (ID: {})", tvShow.getTitle(), tvShow.getDbId());
      LOGGER.info("Batch results available: {}", aiRecognitionResults != null);
      if (aiRecognitionResults != null) {
        LOGGER.info("Batch results count: {}", aiRecognitionResults.size());
        LOGGER.info("Looking for TV show ID: {}", tvShow.getDbId().toString());
      }

      // 首先尝试使用批量识别结果
      if (aiRecognitionResults != null && aiRecognitionResults.containsKey(tvShow.getDbId().toString())) {
        recognizedTitle = aiRecognitionResults.get(tvShow.getDbId().toString());
        LOGGER.info("=== Using Batch AI Result ===");
        LOGGER.info("Batch AI result: '{}' for TV show: '{}'", recognizedTitle, tvShow.getTitle());

        // 发送单个电视剧识别成功消息到Message history
        String successMsg = String.format("批量AI识别: %s → %s", tvShow.getTitle(), recognizedTitle);
        MessageManager.getInstance().pushMessage(
            new Message(MessageLevel.INFO, "批量电视剧AI识别", successMsg));
      } else {
        // 检查是否应该进行单个识别回退
        String apiKey = org.tinymediamanager.core.Settings.getInstance().getOpenAiApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
          if (aiRecognitionResults != null) {
            LOGGER.debug("TV show '{}' (ID: {}) not found in batch results, falling back to individual recognition",
                        tvShow.getTitle(), tvShow.getDbId());
          } else {
            LOGGER.debug("Batch recognition was not performed, using individual recognition for TV show '{}'", tvShow.getTitle());
          }

          // 回退到单个识别（兼容旧逻辑）
          LOGGER.info("=== Falling back to Individual AI Recognition ===");
          try {
            ChatGPTTvShowRecognitionService chatGPTService = new ChatGPTTvShowRecognitionService();
            recognizedTitle = chatGPTService.recognizeTvShowTitle(tvShow);
            if (recognizedTitle != null) {
              LOGGER.info("=== Individual AI Result ===");
              LOGGER.info("Individual AI result: '{}' for TV show: '{}'", recognizedTitle, tvShow.getTitle());

              // 发送单个电视剧识别成功消息到Message history
              String successMsg = String.format("单个AI识别: %s → %s", tvShow.getTitle(), recognizedTitle);
              MessageManager.getInstance().pushMessage(
                  new Message(MessageLevel.INFO, "电视剧AI识别", successMsg));
            } else {
              LOGGER.warn("Individual AI recognition returned null");
            }
          } catch (Exception e) {
            LOGGER.error("Individual ChatGPT TV show recognition failed: {}", e.getMessage(), e);
          }
        } else {
          LOGGER.debug("OpenAI API key not configured, skipping AI recognition for TV show '{}'", tvShow.getTitle());
        }
      }

      // 使用AI识别的标题重新搜索
      LOGGER.info("=== Processing AI Recognition Result ===");
      LOGGER.info("Final AI result: '{}'", recognizedTitle);

      if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
        // 现在AI只返回 "标题 年份" 格式，不再返回数据库ID
        LOGGER.info("=== Using AI Title for Search ===");
        LOGGER.info("AI returned title format: {}", recognizedTitle);

        // 标准的标题年份格式处理
        String[] aiParserInfo = ParserUtils.detectCleanTitleAndYear(recognizedTitle, java.util.Collections.emptyList());
        String aiProcessedTitle = recognizedTitle;
        Integer aiProcessedYear = tvShow.getYear();

        if (aiParserInfo != null && aiParserInfo.length >= 2) {
          aiProcessedTitle = aiParserInfo[0];
          if (org.apache.commons.lang3.StringUtils.isNotBlank(aiParserInfo[1])) {
            try {
              aiProcessedYear = Integer.parseInt(aiParserInfo[1]);
            } catch (NumberFormatException e) {
              LOGGER.debug("Could not parse year from AI result: {}", aiParserInfo[1]);
            }
          }
          LOGGER.debug("AI processed title '{}' -> '{}' (year: {})", recognizedTitle, aiProcessedTitle, aiProcessedYear);
        }

        try {
          List<MediaSearchResult> aiResults = tvShowList.searchTvShow(aiProcessedTitle, aiProcessedYear, tvShow.getIds(), mediaMetadataScraper);

          if (ListUtils.isNotEmpty(aiResults)) {
            MediaSearchResult aiResult = aiResults.get(0);

            if (aiResult.getScore() >= 0.75) {
              LOGGER.info("AI recognition successful! Found match with score: {}", aiResult.getScore());
              return aiResult;
            } else {
              LOGGER.warn("AI recognized title found, but score ({}) is lower than threshold (0.75)", aiResult.getScore());
            }
          } else {
            LOGGER.info("No results found for AI recognized title: '{}'", aiProcessedTitle);
          }
        } catch (Exception e) {
          LOGGER.warn("Error during AI search for TV show '{}': {}", aiProcessedTitle, e.getMessage());
        }
      }

      return null; // AI识别失败，返回null让调用者使用原始搜索
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
