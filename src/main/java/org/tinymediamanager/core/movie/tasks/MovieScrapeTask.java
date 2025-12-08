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

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.ScraperMetadataConfig;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.entities.MediaTrailer;
import org.tinymediamanager.core.movie.MovieList;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieScraperMetadataConfig;
import org.tinymediamanager.core.movie.MovieSearchAndScrapeOptions;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;

import org.tinymediamanager.scraper.ArtworkSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScraper;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.TrailerSearchAndScrapeOptions;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.IMovieArtworkProvider;
import org.tinymediamanager.scraper.interfaces.IMovieMetadataProvider;
import org.tinymediamanager.scraper.interfaces.IMovieTrailerProvider;
import org.tinymediamanager.scraper.rating.RatingProvider;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.scraper.util.MetadataUtil;

import org.tinymediamanager.scraper.util.ParserUtils;
import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.thirdparty.trakttv.MovieSyncTraktTvTask;
import org.tinymediamanager.ui.movies.dialogs.MovieChooserDialog;
import org.tinymediamanager.core.movie.services.BatchChatGPTMovieRecognitionService;
import org.tinymediamanager.core.movie.services.MovieAIRecognitionManager;

/**
 * The Class MovieScrapeTask.
 *
 * @author Manuel Laggner
 */
public class MovieScrapeTask extends TmmThreadPool {
  private static final Logger LOGGER = LoggerFactory.getLogger(MovieScrapeTask.class);

  final MovieScrapeParams     movieScrapeParams;
  final List<Movie>           smartScrapeList;
  boolean                     runInBackground;

  public MovieScrapeTask(final MovieScrapeParams movieScrapeParams) {
    super(TmmResourceBundle.getString("movie.scraping"));
    this.movieScrapeParams = movieScrapeParams;
    this.smartScrapeList = new ArrayList<>(0);
    this.runInBackground = false;
  }

  public void setRunInBackground(boolean runInBackground) {
    this.runInBackground = runInBackground;
  }

  @Override
  protected void doInBackground() {
    MediaScraper mediaMetadataScraper = movieScrapeParams.searchAndScrapeOptions.getMetadataScraper();
    if (!mediaMetadataScraper.isEnabled()) {
      return;
    }

    LOGGER.info("Scraping {} movies with '{}'", movieScrapeParams.moviesToScrape.size(),
        mediaMetadataScraper.getMediaProvider().getProviderInfo().getId());

    // 添加详细的调试信息
    LOGGER.debug("MovieScrapeTask parameters:");
    LOGGER.debug("  doSearch: {}", movieScrapeParams.doSearch);
    LOGGER.debug("  moviesToScrape.size(): {}", movieScrapeParams.moviesToScrape.size());
    LOGGER.debug("  overwriteExistingItems: {}", movieScrapeParams.overwriteExistingItems);

    // 列出前几个电影的信息
    if (!movieScrapeParams.moviesToScrape.isEmpty()) {
      LOGGER.debug("Movies to scrape:");
      for (int i = 0; i < Math.min(movieScrapeParams.moviesToScrape.size(), 3); i++) {
        Movie movie = movieScrapeParams.moviesToScrape.get(i);
        LOGGER.debug("  [{}] Title: '{}', ID: {}", i, movie.getTitle(), movie.getDbId());
      }
      if (movieScrapeParams.moviesToScrape.size() > 3) {
        LOGGER.debug("  ... and {} more movies", movieScrapeParams.moviesToScrape.size() - 3);
      }
    }

    // 初始化线程池，移到AI识别之前，以便并行处理
    // 减少线程池大小，避免短时间内发起过多AI调用
    initThreadPool(3, "scrape");

    // 使用线程安全的Map存储AI识别结果
    ConcurrentHashMap<String, String> aiRecognitionResults = new ConcurrentHashMap<>();

    // 开始新的AI识别会话，清空缓存和计数器
    MovieAIRecognitionManager.getInstance().startNewSession();

    // 批量AI识别优化：批次处理，识别一部分就刮削一部分
    if (movieScrapeParams.doSearch && !movieScrapeParams.moviesToScrape.isEmpty()) {
      // 检查是否配置了 OpenAI API Key
      String apiKey = org.tinymediamanager.core.Settings.getInstance().getOpenAiApiKey();
      LOGGER.debug("OpenAI API Key check: {}", apiKey != null && !apiKey.trim().isEmpty() ? "configured" : "not configured");

      if (apiKey != null && !apiKey.trim().isEmpty()) {
        try {
          LOGGER.info("Starting batch AI recognition for {} movies", movieScrapeParams.moviesToScrape.size());

          // 发送批量AI识别开始消息到Message history
          String startMsg = String.format("批量电影AI识别开始: %d 部电影", movieScrapeParams.moviesToScrape.size());
          MessageManager.getInstance().pushMessage(new Message(MessageLevel.INFO, "批量电影AI识别", startMsg));

          BatchChatGPTMovieRecognitionService batchService = new BatchChatGPTMovieRecognitionService();

          // 手动拆分批次，从设置中获取批次大小
          int batchSize = Settings.getInstance().getAiBatchSize();
          int totalMovies = movieScrapeParams.moviesToScrape.size();
          int totalBatches = (int) Math.ceil((double) totalMovies / batchSize);

          LOGGER.info("Processing {} movies in {} batches of {} movies each", totalMovies, totalBatches, batchSize);

          // 循环处理每个批次
          for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, totalMovies);
            List<Movie> currentBatch = movieScrapeParams.moviesToScrape.subList(startIndex, endIndex);

            LOGGER.info("Processing batch {}/{} ({} movies)", batchIndex + 1, totalBatches, currentBatch.size());

            // 处理当前批次的AI识别
            Map<String, String> batchResults = batchService.batchRecognizeMovieTitles(currentBatch);

            // 将当前批次的识别结果添加到总结果中
            aiRecognitionResults.putAll(batchResults);

            LOGGER.info("Batch {} AI recognition completed for {} movies", batchIndex + 1, batchResults.size());

            // 立即提交当前批次的刮削任务
            for (Movie movie : currentBatch) {
              submitTask(new Worker(movie, aiRecognitionResults));
            }
          }

          LOGGER.info("All batches AI recognition completed for {} movies", aiRecognitionResults.size());

          // 发送批量AI识别完成消息到Message history
          int successCount = aiRecognitionResults.size();
          int totalCount = movieScrapeParams.moviesToScrape.size();
          String completeMsg = String.format("批量电影AI识别完成: 成功 %d/%d (%.1f%%)", successCount, totalCount, (successCount * 100.0 / totalCount));
          MessageManager.getInstance().pushMessage(new Message(MessageLevel.INFO, "批量电影AI识别", completeMsg));

        }
        catch (Exception e) {
          LOGGER.warn("Batch AI recognition failed, skipping individual fallback to prevent API spam: {}", e.getMessage());

          // 发送批量AI识别失败消息到Message history，但不回退到个体识别
          String failMsg = String.format("批量电影AI识别失败，已跳过个体回退以防止API过度调用: %s", e.getMessage());
          MessageManager.getInstance().pushMessage(new Message(MessageLevel.WARN, "批量电影AI识别", failMsg));

          // 即使AI识别失败，也要提交所有电影的刮削任务
          for (Movie movie : movieScrapeParams.moviesToScrape) {
            submitTask(new Worker(movie, aiRecognitionResults));
          }
        }
      }
      else {
        LOGGER.debug("OpenAI API key not configured, skipping batch AI recognition");

        // 直接提交所有电影的刮削任务
        for (Movie movie : movieScrapeParams.moviesToScrape) {
          submitTask(new Worker(movie, aiRecognitionResults));
        }
      }
    }
    else {
      LOGGER.debug("Batch AI recognition skipped: doSearch={}, movieCount={}", movieScrapeParams.doSearch, movieScrapeParams.moviesToScrape.size());

      // 直接提交所有电影的刮削任务
      for (Movie movie : movieScrapeParams.moviesToScrape) {
        submitTask(new Worker(movie, aiRecognitionResults));
      }
    }

    // 等待所有刮削任务完成
    waitForCompletionOrCancel();

    // 自动重试逻辑：针对由于网络波动或API限制导致的刮削失败，尝试重试2轮
    // 用户需求：未成功的添加到 smartScrapeList 里的最后 统一 再自动刮削 两轮
    LOGGER.info("Checking smart scrape list for retries. List size: {}, Cancelled: {}", smartScrapeList.size(), cancel);
    if (!smartScrapeList.isEmpty() && !cancel) {
      int maxRetries = org.tinymediamanager.core.movie.MovieModuleManager.getInstance().getSettings().getAutomaticScraperRetryCount();
      for (int i = 0; i < maxRetries; i++) {
        // 如果列表为空或取消任务，停止重试
        if (smartScrapeList.isEmpty() || cancel) {
          LOGGER.info("Retry loop aborted. List empty: {}, Cancelled: {}", smartScrapeList.isEmpty(), cancel);
          break;
        }

        LOGGER.info("Automatic scrape retry round {}/{} for {} movies in smart scrape list", i + 1, maxRetries, smartScrapeList.size());

        // 发送重试通知
        MessageManager.getInstance()
            .pushMessage(new Message(MessageLevel.INFO, "自动刮削重试",
                String.format("正在对 %d 部未识别电影进行第 %d/%d 轮自动重试...", smartScrapeList.size(), i + 1, maxRetries)));

        // 创建临时列表并清空主列表，以便Worker可以重新填充失败的项目
        List<Movie> retryMovies;
        synchronized (smartScrapeList) {
          retryMovies = new ArrayList<>(smartScrapeList);
          smartScrapeList.clear();
        }

        LOGGER.info("Submitting {} movies for retry...", retryMovies.size());

        // Re-initialize thread pool because waitForCompletionOrCancel() shuts it down
        initThreadPool(3, "scrape-retry-" + (i + 1));

        // 重新提交刮削任务
        for (Movie movie : retryMovies) {
          // 重新提交Worker，aiRecognitionResults仍然有效，可以重用
          // 如果之前的失败是因为API限流，重试可能会成功
          submitTask(new Worker(movie, aiRecognitionResults));
        }

        // 等待本轮重试完成
        waitForCompletionOrCancel();
        LOGGER.info("Retry round {}/{} completed. New smart scrape list size: {}", i + 1, maxRetries, smartScrapeList.size());
      }
    }

    LOGGER.info("Scraping finished. Preparing for Smart Scrape Dialog. List size: {}, Headless: {}, Cancel: {}", smartScrapeList.size(),
        GraphicsEnvironment.isHeadless(), cancel);

    // initiate smart scrape
    if (!smartScrapeList.isEmpty() && !GraphicsEnvironment.isHeadless()) {
      LOGGER.info("Invoking Smart Scrape Dialog...");
      try {
        SwingUtilities.invokeAndWait(() -> {
          LOGGER.info("Inside SwingUtilities.invokeAndWait - showing dialogs");
          int selectedCount = smartScrapeList.size();
          int index = 0;

          do {
            Movie movie = smartScrapeList.get(index);
            LOGGER.info("Showing dialog for movie: {}", movie.getTitle());
            MovieChooserDialog dialogMovieChooser = new MovieChooserDialog(movie, index, selectedCount);
            dialogMovieChooser.setVisible(true);

            if (!dialogMovieChooser.isContinueQueue()) {
              break;
            }

            if (dialogMovieChooser.isNavigateBack()) {
              index -= 1;
            }
            else {
              index += 1;
            }

          } while (index < selectedCount);
          LOGGER.info("Smart Scrape Dialog loop finished");
        });
      }
      catch (Exception e) {
        LOGGER.error("SmartScrape crashed - '{}'", e.getMessage(), e);
      }
    }
    else {
      LOGGER.info("Skipping Smart Scrape Dialog. Conditions not met.");
    }

    if (MovieModuleManager.getInstance().getSettings().getSyncTrakt()) {
      MovieSyncTraktTvTask task = new MovieSyncTraktTvTask(movieScrapeParams.moviesToScrape);
      task.setSyncCollection(MovieModuleManager.getInstance().getSettings().getSyncTraktCollection());
      task.setSyncWatched(MovieModuleManager.getInstance().getSettings().getSyncTraktWatched());
      task.setSyncRating(MovieModuleManager.getInstance().getSettings().getSyncTraktRating());

      TmmTaskManager.getInstance().addUnnamedTask(task);
    }

    LOGGER.info("Finished scraping movies - took {} ms", getRuntime());
  }

  @Override
  public void callback(Object obj) {
    // do not publish task description here, because with different workers the text is never right
    publishState(progressDone);
  }

  /****************************************************************************************
   * Helper classes
   ****************************************************************************************/
  private class Worker implements Runnable {
    private MovieList                 movieList;
    private final Movie               movie;
    private final Map<String, String> aiRecognitionResults;

    public Worker(Movie movie) {
      this(movie, null);
    }

    public Worker(Movie movie, Map<String, String> aiRecognitionResults) {
      this.movie = movie;
      this.aiRecognitionResults = aiRecognitionResults;
    }

    @Override
    public void run() {
      movieList = MovieModuleManager.getInstance().getMovieList();
      // set up scrapers
      MediaScraper mediaMetadataScraper = movieScrapeParams.searchAndScrapeOptions.getMetadataScraper();
      List<MediaScraper> artworkScrapers = movieScrapeParams.searchAndScrapeOptions.getArtworkScrapers();
      List<MediaScraper> trailerScrapers = movieScrapeParams.searchAndScrapeOptions.getTrailerScrapers();

      try {
        // search movie
        MediaSearchResult result1 = null;
        if (movieScrapeParams.doSearch) {
          LOGGER.info("Searching for movie '{}'", movie.getTitle());

          result1 = searchForMovie(mediaMetadataScraper);
          if (result1 == null) {
            // append this search request to the UI with search & scrape dialog
            synchronized (smartScrapeList) {
              smartScrapeList.add(movie);
              return;
            }
          }
        }

        // get metadata, artwork and trailers
        MovieSearchAndScrapeOptions options = new MovieSearchAndScrapeOptions(movieScrapeParams.searchAndScrapeOptions);
        options.setSearchResult(result1);

        // we didn't do a search - pass imdbid and tmdbid from movie object
        if (result1 != null) {
          options.setIds(result1.getIds());
          // override scraper with one from search result
          mediaMetadataScraper = movieList.getMediaScraperById(result1.getProviderId());
        }
        else {
          options.setIds(movie.getIds());
        }

        // scrape metadata if wanted
        MediaMetadata md = null;

        if (mediaMetadataScraper != null && mediaMetadataScraper.getMediaProvider() != null) {
          LOGGER.info("Scraping movie '{}' with '{}'", movie.getTitle(), mediaMetadataScraper.getMediaProvider().getProviderInfo().getId());

          LOGGER.debug("=====================================================");
          LOGGER.debug("Scrape movie metadata with scraper: " + mediaMetadataScraper.getMediaProvider().getProviderInfo().getId() + ", "
              + mediaMetadataScraper.getMediaProvider().getProviderInfo().getVersion());
          LOGGER.debug(options.toString());
          LOGGER.debug("=====================================================");
          try {
            md = ((IMovieMetadataProvider) mediaMetadataScraper.getMediaProvider()).getMetadata(options);

            if (movieScrapeParams.scraperMetadataConfig.contains(MovieScraperMetadataConfig.COLLECTION) && md.getIdAsInt(MediaMetadata.TMDB_SET) == 0
                && !mediaMetadataScraper.getId().equals(MediaMetadata.TMDB)) {
              int movieSetId = MetadataUtil.getMovieSetId(md.getIds());
              if (movieSetId > 0) {
                md.setId(MediaMetadata.TMDB_SET, movieSetId);
              }
            }

            if (cancel) {
              return;
            }

            // also inject other ids
            MediaIdUtil.injectMissingIds(md.getIds(), MediaType.MOVIE);

            // also fill other ratings if ratings are requested
            if (MovieModuleManager.getInstance().getSettings().isFetchAllRatings()
                && movieScrapeParams.scraperMetadataConfig.contains(MovieScraperMetadataConfig.RATING)) {
              for (MediaRating rating : ListUtils.nullSafe(
                  RatingProvider.getRatings(md.getIds(), MovieModuleManager.getInstance().getSettings().getFetchRatingSources(), MediaType.MOVIE))) {
                if (!md.getRatings().contains(rating)) {
                  md.addRating(rating);
                }
              }
            }
          }
          catch (MissingIdException e) {
            LOGGER.warn("Missing IDs for scraping movie '{}' with '{}'", movie.getTitle(), mediaMetadataScraper.getId());
            MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, movie, "scraper.error.missingid"));
          }
          catch (ScrapeException e) {
            LOGGER.error("Could not scrape movie '{}' with '{}' - '{}'", movie.getTitle(), mediaMetadataScraper.getId(), e.getMessage());
            MessageManager.getInstance()
                .pushMessage(
                    new Message(MessageLevel.ERROR, movie, "message.scrape.metadatamoviefailed", new String[] { ":", e.getLocalizedMessage() }));
          }
          catch (Exception e) {
            LOGGER.error("Unforeseen error in movie scrape for '{}'", movie.getTitle(), e);
          }

          if (md != null && (ScraperMetadataConfig.containsAnyMetadata(movieScrapeParams.scraperMetadataConfig)
              || ScraperMetadataConfig.containsAnyCast(movieScrapeParams.scraperMetadataConfig))) {
            LOGGER.info("Calling setMetadata for movie '{}' with metadata title='{}', year={}, overwrite={}", movie.getTitle(), md.getTitle(),
                md.getYear(), movieScrapeParams.overwriteExistingItems);
            movie.setMetadata(md, movieScrapeParams.scraperMetadataConfig, movieScrapeParams.overwriteExistingItems);
            LOGGER.info("After setMetadata: movie title='{}', year={}", movie.getTitle(), movie.getYear());
            movie.setLastScraperId(movieScrapeParams.searchAndScrapeOptions.getMetadataScraper().getId());
            movie.setLastScrapeLanguage(movieScrapeParams.searchAndScrapeOptions.getLanguage().name());

            if (MovieModuleManager.getInstance().getSettings().isRenameAfterScrape()) {
              TmmTask task = new MovieRenameTask(Collections.singletonList(movie));
              // blocking
              task.run();
            }

            // write actor images after possible rename (to have a good folder structure)
            if (ScraperMetadataConfig.containsAnyCast(movieScrapeParams.scraperMetadataConfig)
                && MovieModuleManager.getInstance().getSettings().isWriteActorImages()) {
              movie.writeActorImages(movieScrapeParams.overwriteExistingItems);
            }
          }
          else if (result1 != null) {
            // 如果搜索结果正常，但刮削失败（md为null），将电影添加到智能刮削列表，触发手动刮削窗口
            LOGGER.warn("Scraping returned null metadata for movie '{}', but search result was found. Adding to smart scrape list.",
                movie.getTitle());
            synchronized (smartScrapeList) {
              smartScrapeList.add(movie);
            }
            return;
          }

          if (cancel) {
            return;
          }

          // scrape artwork if wanted
          if (ScraperMetadataConfig.containsAnyArtwork(movieScrapeParams.scraperMetadataConfig)) {
            movie.setArtwork(getArtwork(movie, md, artworkScrapers), movieScrapeParams.scraperMetadataConfig,
                movieScrapeParams.overwriteExistingItems);
          }

          if (cancel) {
            return;
          }

          // scrape trailer if wanted
          if (movieScrapeParams.scraperMetadataConfig.contains(MovieScraperMetadataConfig.TRAILER)) {
            movie.setTrailers(getTrailers(movie, md, trailerScrapers));
            movie.saveToDb();
            movie.writeNFO();

            // start automatic movie trailer download
            if (MovieModuleManager.getInstance().getSettings().isUseTrailerPreference()
                && MovieModuleManager.getInstance().getSettings().isAutomaticTrailerDownload() && movie.getMediaFiles(MediaFileType.TRAILER).isEmpty()
                && !movie.getTrailer().isEmpty()) {
              TmmTaskManager.getInstance().addDownloadTask(new MovieTrailerDownloadTask(movie));
            }
          }
        }
      }
      catch (Exception e) {
        LOGGER.error("Could not scrape movie '{}' - '{}'", movie.getTitle(), e.getMessage());
        MessageManager.getInstance()
            .pushMessage(
                new Message(MessageLevel.ERROR, "MovieScraper", "message.scrape.threadcrashed", new String[] { ":", e.getLocalizedMessage() }));
        // add to smart scrape list if scraping crashed, so the user can manually scrape it
        synchronized (smartScrapeList) {
          if (!smartScrapeList.contains(movie)) {
            smartScrapeList.add(movie);
          }
        }
      }
    }

    private MediaSearchResult searchForMovie(MediaScraper mediaMetadataProvider) throws ScrapeException {
      // 处理电影标题，提取干净的标题和年份
      String processedTitle = movie.getTitle();
      Integer processedYear = null;

      String[] parserInfo = ParserUtils.detectCleanTitleAndYear(movie.getTitle(), Collections.emptyList());
      if (parserInfo != null && parserInfo.length >= 2) {
        processedTitle = parserInfo[0];
        if (StringUtils.isNotBlank(parserInfo[1])) {
          processedYear = safeParseYear(parserInfo[1]);
        }
      }

      // 备用年份提取：从标题中提取括号内的年份如 (2019) 或末尾的年份
      if (processedYear == null) {
        // 尝试匹配括号内的年份：(2019)
        java.util.regex.Pattern bracketYearPattern = java.util.regex.Pattern.compile("\\((\\d{4})\\)");
        java.util.regex.Matcher bracketMatcher = bracketYearPattern.matcher(movie.getTitle());
        if (bracketMatcher.find()) {
          Integer extractedYear = safeParseYear(bracketMatcher.group(1));
          if (extractedYear != null && isValidMovieYear(extractedYear)) {
            processedYear = extractedYear;
            LOGGER.debug("Extracted year from brackets: {}", processedYear);
          }
        }
      }

      // 如果仍然没有解析出年份，使用电影原始年份作为回退
      if (processedYear == null) {
        processedYear = movie.getYear();
        LOGGER.debug("No year from title parsing, using original movie year: {}", processedYear);
      }

      LOGGER.debug("Processed title '{}' -> '{}' (year: {})", movie.getTitle(), processedTitle, processedYear);

      // 使用AIRecognitionManager统一获取AI识别结果
      MediaSearchResult aiResult = null;
      String recognizedTitle = MovieAIRecognitionManager.getInstance().getRecognizedTitle(movie, aiRecognitionResults);

      if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
        LOGGER.info("AI recognition result for movie '{}': '{}'", movie.getTitle(), recognizedTitle);

        // 发送识别成功消息到Message history
        String successMsg = String.format("AI识别: %s → %s", movie.getTitle(), recognizedTitle);
        MessageManager.getInstance().pushMessage(new Message(MessageLevel.INFO, "电影AI识别", successMsg));
      }
      else {
        LOGGER.debug("No AI recognition result available for movie '{}' (attempts: {})", movie.getTitle(),
            MovieAIRecognitionManager.getInstance().getAttemptCount(movie));
      }

      // 使用AI识别的标题重新搜索
      if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
        String[] aiParserInfo = ParserUtils.detectCleanTitleAndYear(recognizedTitle, Collections.emptyList());
        String aiProcessedTitle = recognizedTitle;
        Integer aiProcessedYear = null;

        // 详细记录 ParserUtils 解析结果
        LOGGER.info("ParserUtils.detectCleanTitleAndYear('{}') returned: title='{}', year='{}'", recognizedTitle,
            aiParserInfo != null && aiParserInfo.length >= 1 ? aiParserInfo[0] : "null",
            aiParserInfo != null && aiParserInfo.length >= 2 ? aiParserInfo[1] : "null");

        if (aiParserInfo != null && aiParserInfo.length >= 2) {
          aiProcessedTitle = aiParserInfo[0];
          if (StringUtils.isNotBlank(aiParserInfo[1])) {
            aiProcessedYear = safeParseYear(aiParserInfo[1]);
          }
        }

        // 备用年份提取：如果 ParserUtils 没有解析出年份，直接从 AI 结果末尾提取
        if (aiProcessedYear == null && recognizedTitle.matches(".*\\s+\\d{4}\\s*$")) {
          java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("(\\d{4})\\s*$");
          java.util.regex.Matcher yearMatcher = yearPattern.matcher(recognizedTitle.trim());
          if (yearMatcher.find()) {
            Integer extractedYear = safeParseYear(yearMatcher.group(1));
            if (extractedYear != null && isValidMovieYear(extractedYear)) {
              aiProcessedYear = extractedYear;
              // 从标题中移除年份
              aiProcessedTitle = recognizedTitle.substring(0, yearMatcher.start()).trim();
              LOGGER.info("Fallback year extraction: title='{}', year={}", aiProcessedTitle, aiProcessedYear);
            }
          }
        }

        // 验证年份是否合理
        if (aiProcessedYear != null && !isValidMovieYear(aiProcessedYear)) {
          LOGGER.warn("AI returned invalid year {} for movie '{}', valid range: 1888-{}", aiProcessedYear, movie.getTitle(),
              java.time.Year.now().getValue() + 2);
          // 不再重试AI，直接使用null年份继续搜索
          aiProcessedYear = null;
        }

        // 如果仍然没有合理的年份，使用null而不是错误的年份
        if (aiProcessedYear != null && !isValidMovieYear(aiProcessedYear)) {
          LOGGER.warn("Using null year instead of invalid year: {} for movie '{}'", aiProcessedYear, movie.getTitle());
          aiProcessedYear = null;
        }

        LOGGER.info("AI processed title '{}' -> '{}' (year: {})", recognizedTitle, aiProcessedTitle, aiProcessedYear);

        // 确保年份不是 null 时正确传递给 searchMovie（int 参数）
        // 注意：AI 识别搜索时不传入电影原有 ID，以避免因旧 ID 返回错误结果
        int searchYear = aiProcessedYear != null ? aiProcessedYear : 0;
        List<MediaSearchResult> aiResults = movieList.searchMovie(aiProcessedTitle, searchYear, null, mediaMetadataProvider);

        if (ListUtils.isNotEmpty(aiResults)) {
          // 1. 首先优先从文件路径解析 TMDB ID（比 movie.getTmdbId() 更可靠）
          int pathTmdbId = 0;
          String moviePath = movie.getPathNIO() != null ? movie.getPathNIO().toString() : "";
          pathTmdbId = ParserUtils.detectTmdbId(moviePath);
          if (pathTmdbId <= 0) {
            // 如果路径中没有，使用电影对象中已存储的 ID 作为备用
            pathTmdbId = movie.getTmdbId();
          }
          LOGGER.info("Movie '{}' TMDB ID from path: {}, from object: {}", movie.getTitle(), ParserUtils.detectTmdbId(moviePath), movie.getTmdbId());
          if (pathTmdbId > 0) {
            for (MediaSearchResult result : aiResults) {
              if (result.getIdAsInt(MediaMetadata.TMDB) == pathTmdbId) {
                aiResult = result;
                LOGGER.info("Found exact TMDB ID match: title='{}', tmdbId={}, score={}", result.getTitle(), pathTmdbId, result.getScore());
                break;
              }
            }
          }

          // 2. 如果没有 ID 匹配，优先选择年份完全匹配的结果
          if (aiResult == null && searchYear > 0) {
            for (MediaSearchResult result : aiResults) {
              if (result.getYear() == searchYear) {
                aiResult = result;
                LOGGER.info("Found exact year match: title='{}', year={}, score={}", result.getTitle(), result.getYear(), result.getScore());
                break;
              }
            }
          }

          // 3. 如果都没有匹配，使用第一个结果
          if (aiResult == null) {
            aiResult = aiResults.get(0);
          }

          final double scraperTreshold = MovieModuleManager.getInstance().getSettings().getScraperThreshold();

          if (aiResult.getScore() >= scraperTreshold) {
            LOGGER.info("AI recognition successful! Found match with score: {}", aiResult.getScore());
            return aiResult;
          }
          else {
            LOGGER.warn("AI recognized title found, but score ({}) is lower than threshold ({})", aiResult.getScore(), scraperTreshold);
            // 尝试单个文件AI识别回退
            MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(movie, processedTitle, processedYear, mediaMetadataProvider);
            if (fallbackResult != null) {
              return fallbackResult;
            }
            aiResult = null; // 重置结果，继续常规搜索
          }
        }
        else {
          LOGGER.info("No results found for AI recognized title: '{}'", aiProcessedTitle);
          // 尝试单个文件AI识别回退
          MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(movie, processedTitle, processedYear, mediaMetadataProvider);
          if (fallbackResult != null) {
            return fallbackResult;
          }
        }
      }

      // 如果AI识别失败或分数不足，进行常规搜索
      List<MediaSearchResult> results = movieList.searchMovie(processedTitle, processedYear, movie.getIds(), mediaMetadataProvider);
      MediaSearchResult result = null;

      if (ListUtils.isNotEmpty(results)) {
        result = results.get(0);
        // check if there is another result with the same score
        if (results.size() > 1) {
          MediaSearchResult result2 = results.get(1);
          // if both results have the same score - do not take any result
          if (result.getScore() == result2.getScore()) {
            LOGGER.warn("Two identical results for '{}', can't decide which to take - attempting individual AI recognition fallback",
                movie.getTitle());
            // 尝试单个文件AI识别回退
            MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(movie, processedTitle, processedYear, mediaMetadataProvider);
            if (fallbackResult != null) {
              return fallbackResult;
            }
            LOGGER.warn("Individual AI recognition also failed, returning null to show smart scrape dialog");
            MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, movie, "movie.scrape.toosimilar"));
            return null;
          }
        }

        // get threshold from settings (default 0.75) - to minimize false positives
        final double scraperTreshold = MovieModuleManager.getInstance().getSettings().getScraperThreshold();
        LOGGER.debug("using threshold from settings of {}", scraperTreshold);
        if (result.getScore() < scraperTreshold) {
          LOGGER.warn("Score ({}) is lower than minimum score ({}) for '{}' - attempting individual AI recognition fallback", result.getScore(),
              scraperTreshold, movie.getTitle());
          // 尝试单个文件AI识别回退
          MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(movie, processedTitle, processedYear, mediaMetadataProvider);
          if (fallbackResult != null) {
            return fallbackResult;
          }
          LOGGER.warn("Individual AI recognition also failed, returning null to show smart scrape dialog");
          MessageManager.getInstance()
              .pushMessage(
                  new Message(MessageLevel.ERROR, movie, "movie.scrape.toolowscore", new String[] { String.format("%.2f", scraperTreshold) }));
          return null;
        }
      }
      else {
        LOGGER.info("No result found for '{}' after regular search - attempting individual AI recognition fallback", movie.getTitle());
        // 尝试单个文件AI识别回退
        MediaSearchResult fallbackResult = fallbackToIndividualAIRecognition(movie, processedTitle, processedYear, mediaMetadataProvider);
        if (fallbackResult != null) {
          return fallbackResult;
        }
        LOGGER.warn("Individual AI recognition also failed, returning null to show smart scrape dialog");
        MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, movie, "movie.scrape.nomatchfound"));
      }

      return result;
    }

    /**
     * 回退到单个文件AI识别 使用AIRecognitionManager统一管理调用次数
     */
    private MediaSearchResult fallbackToIndividualAIRecognition(Movie movie, String processedTitle, Integer processedYear,
        MediaScraper mediaMetadataProvider) {
      try {
        // 使用MovieAIRecognitionManager检查是否还可以进行AI识别
        MovieAIRecognitionManager aiManager = MovieAIRecognitionManager.getInstance();

        if (!aiManager.canAttemptRecognition(movie)) {
          LOGGER.debug("Max AI recognition attempts reached for movie '{}', skipping fallback", movie.getTitle());
          return null;
        }

        LOGGER.info("Attempting individual AI recognition fallback for movie: '{}' (attempt: {})", movie.getTitle(),
            aiManager.getAttemptCount(movie) + 1);

        // 通过AIRecognitionManager获取识别结果（会自动检查用户设置和调用次数）
        String recognizedTitle = aiManager.getRecognizedTitle(movie, null);

        if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
          LOGGER.info("Individual AI recognition successful: '{}' for movie: '{}'", recognizedTitle, movie.getTitle());

          // 解析AI识别结果
          String[] aiParserInfo = ParserUtils.detectCleanTitleAndYear(recognizedTitle, Collections.emptyList());
          String aiProcessedTitle = recognizedTitle;
          Integer aiProcessedYear = null;

          if (aiParserInfo != null && aiParserInfo.length >= 2) {
            aiProcessedTitle = aiParserInfo[0];
            if (StringUtils.isNotBlank(aiParserInfo[1])) {
              aiProcessedYear = safeParseYear(aiParserInfo[1]);
            }
          }

          // 验证年份
          if (aiProcessedYear != null && !isValidMovieYear(aiProcessedYear)) {
            LOGGER.warn("Individual AI returned invalid year: {} for movie '{}', using null", aiProcessedYear, movie.getTitle());
            aiProcessedYear = null;
          }

          // 使用单个AI识别结果进行搜索
          List<MediaSearchResult> aiResults = movieList.searchMovie(aiProcessedTitle, aiProcessedYear, movie.getIds(), mediaMetadataProvider);

          if (ListUtils.isNotEmpty(aiResults)) {
            MediaSearchResult aiResult = aiResults.get(0);
            final double scraperTreshold = MovieModuleManager.getInstance().getSettings().getScraperThreshold();

            if (aiResult.getScore() >= scraperTreshold) {
              LOGGER.info("Individual AI recognition successful! Found match with score: {}", aiResult.getScore());
              // 标记为已验证
              aiManager.markAsValidated(movie, recognizedTitle);
              return aiResult;
            }
            else {
              LOGGER.warn("Individual AI recognized title found, but score ({}) is lower than threshold ({})", aiResult.getScore(), scraperTreshold);
            }
          }
          else {
            LOGGER.info("No results found for individual AI recognized title: '{}'", aiProcessedTitle);
          }
        }
        else {
          LOGGER.debug("No new AI recognition result for movie '{}' from fallback", movie.getTitle());
        }
      }
      catch (Exception e) {
        LOGGER.error("Error during individual AI recognition fallback: {}", e.getMessage());
      }

      return null;
    }

    private Integer safeParseYear(String yearStr) {
      if (StringUtils.isNotBlank(yearStr)) {
        try {
          return Integer.parseInt(yearStr.trim());
        }
        catch (NumberFormatException e) {
          LOGGER.debug("Could not parse year: '{}'", yearStr);
        }
      }
      return null;
    }

    private List<MediaArtwork> getArtwork(Movie movie, MediaMetadata metadata, List<MediaScraper> artworkScrapers) {
      ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
      List<MediaArtwork> artwork = new ArrayList<>();

      ArtworkSearchAndScrapeOptions options = new ArtworkSearchAndScrapeOptions(MediaType.MOVIE);
      options.setDataFromOtherOptions(movieScrapeParams.searchAndScrapeOptions);
      options.setArtworkType(MediaArtworkType.ALL);
      options.setMetadata(metadata);
      if (metadata != null) {
        options.addIds(metadata.getIds());
      }
      if (movie.isStacked()) {
        options.setId("mediaFile", new ArrayList<>(movie.getMediaFiles(MediaFileType.VIDEO)));
      }
      else {
        options.setId("mediaFile", movie.getMainFile());
      }
      options.setLanguage(MovieModuleManager.getInstance().getSettings().getDefaultImageScraperLanguage());
      options.setFanartSize(MovieModuleManager.getInstance().getSettings().getImageFanartSize());
      options.setPosterSize(MovieModuleManager.getInstance().getSettings().getImagePosterSize());

      // scrape providers
      artworkScrapers.parallelStream().forEach(scraper -> {
        IMovieArtworkProvider artworkProvider = (IMovieArtworkProvider) scraper.getMediaProvider();
        try {
          lock.writeLock().lock();
          artwork.addAll(artworkProvider.getArtwork(options));
        }
        catch (MissingIdException ignored) {
          LOGGER.info("Missing IDs for scraping movie artwork of '{}' with '{}'", movie.getTitle(), scraper.getId());
        }
        catch (ScrapeException e) {
          LOGGER.error("Could not scrape movie artwork of '{}' with '{}' - '{}'", movie.getTitle(), scraper.getId(), e.getMessage());
          MessageManager.getInstance()
              .pushMessage(
                  new Message(MessageLevel.ERROR, movie, "message.scrape.movieartworkfailed", new String[] { ":", e.getLocalizedMessage() }));
        }
        catch (Exception e) {
          LOGGER.error("Unforeseen error in movie artwork scrape for '{}'", movie.getTitle(), e);
        }
        finally {
          lock.writeLock().unlock();
        }
      });

      return artwork;
    }

    private List<MediaTrailer> getTrailers(Movie movie, MediaMetadata metadata, List<MediaScraper> trailerScrapers) {
      ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
      List<MediaTrailer> trailers = new ArrayList<>();

      TrailerSearchAndScrapeOptions options = new TrailerSearchAndScrapeOptions(MediaType.MOVIE);
      options.setDataFromOtherOptions(movieScrapeParams.searchAndScrapeOptions);
      options.setMetadata(metadata);
      if (metadata != null) {
        options.addIds(metadata.getIds());
      }

      // scrape trailers
      trailerScrapers.parallelStream().forEach(trailerScraper -> {
        IMovieTrailerProvider trailerProvider = (IMovieTrailerProvider) trailerScraper.getMediaProvider();
        try {
          lock.writeLock().lock();
          trailers.addAll(trailerProvider.getTrailers(options));
        }
        catch (MissingIdException e) {
          LOGGER.info("Missing IDs for scraping movie trailers of '{}' with '{}'", movie.getTitle(), trailerScraper.getId());
        }
        catch (ScrapeException e) {
          LOGGER.error("Could not scrape movie trailers of '{}' with '{}' - '{}'", movie.getTitle(), trailerScraper.getId(), e.getMessage());
          MessageManager.getInstance()
              .pushMessage(new Message(MessageLevel.ERROR, movie, "message.scrape.trailerfailed", new String[] { ":", e.getLocalizedMessage() }));
        }
        catch (Exception e) {
          LOGGER.error("Unforeseen error in movie trailer scrape for '{}'", movie.getTitle(), e);
        }
        finally {
          lock.writeLock().unlock();
        }
      });

      return trailers;
    }
  }

  public static class MovieScrapeParams {
    private final List<Movie>                      moviesToScrape;
    private final List<MovieScraperMetadataConfig> scraperMetadataConfig;
    private final MovieSearchAndScrapeOptions      searchAndScrapeOptions;

    private boolean                                doSearch;
    private boolean                                overwriteExistingItems;

    public MovieScrapeParams(final List<Movie> moviesToScrape, final MovieSearchAndScrapeOptions searchAndScrapeOptions,
        final List<MovieScraperMetadataConfig> scraperMetadataConfig) {
      this.moviesToScrape = new ArrayList<>(moviesToScrape);
      this.searchAndScrapeOptions = searchAndScrapeOptions;
      this.scraperMetadataConfig = new ArrayList<>(scraperMetadataConfig);

      this.doSearch = true;
      this.overwriteExistingItems = true;
    }

    public MovieScrapeParams setDoSearch(boolean doSearch) {
      this.doSearch = doSearch;
      return this;
    }

    public MovieScrapeParams setOverwriteExistingItems(boolean overwriteExistingItems) {
      this.overwriteExistingItems = overwriteExistingItems;
      return this;
    }
  }

  /**
   * 验证电影年份是否合理
   * 
   * @param year
   *          年份
   * @return true如果年份合理，false否则
   */
  private boolean isValidMovieYear(Integer year) {
    if (year == null) {
      LOGGER.debug("Year validation: null year is invalid");
      return false;
    }

    // 电影年份应该在1888年（第一部电影）到当前年份+2年之间
    int currentYear = java.time.Year.now().getValue();
    int minYear = 1888;
    int maxYear = currentYear + 2;

    boolean isValid = year >= minYear && year <= maxYear;

    if (!isValid) {
      LOGGER.error("=== YEAR VALIDATION FAILED ===");
      LOGGER.error("Invalid movie year detected: {}", year);
      LOGGER.error("Valid range: {}-{}", minYear, maxYear);
      LOGGER.error("Current year: {}", currentYear);

      // 特别检查2139这样的异常年份
      if (year > 2100) {
        LOGGER.error("Year {} is far in the future - possible AI parsing error!", year);
      }
      if (year < 1800) {
        LOGGER.error("Year {} is too old - possible AI parsing error!", year);
      }
    }
    else {
      LOGGER.debug("Year validation passed: {} (range: {}-{})", year, minYear, maxYear);
    }

    return isValid;
  }
}
