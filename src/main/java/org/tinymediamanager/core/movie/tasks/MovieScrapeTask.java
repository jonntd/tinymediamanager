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
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.swing.SwingUtilities;

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
import org.tinymediamanager.core.movie.services.ChatGPTMovieRecognitionService;
import org.tinymediamanager.core.movie.services.BatchChatGPTMovieRecognitionService;

/**
 * The Class MovieScrapeTask.
 *
 * @author Manuel Laggner
 */
public class MovieScrapeTask extends TmmThreadPool {
  private static final Logger     LOGGER = LoggerFactory.getLogger(MovieScrapeTask.class);

  private final MovieScrapeParams movieScrapeParams;
  private final List<Movie>       smartScrapeList;
  private boolean                 runInBackground;

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

    // 批量AI识别优化：在启动线程池前统一处理
    Map<String, String> aiRecognitionResults = null;
    if (movieScrapeParams.doSearch && !movieScrapeParams.moviesToScrape.isEmpty()) {
      // 检查是否配置了 OpenAI API Key
      String apiKey = org.tinymediamanager.core.Settings.getInstance().getOpenAiApiKey();
      LOGGER.debug("OpenAI API Key check: {}", apiKey != null && !apiKey.trim().isEmpty() ? "configured" : "not configured");

      if (apiKey != null && !apiKey.trim().isEmpty()) {
        try {
          LOGGER.info("Starting batch AI recognition for {} movies", movieScrapeParams.moviesToScrape.size());
          BatchChatGPTMovieRecognitionService batchService = new BatchChatGPTMovieRecognitionService();
          aiRecognitionResults = batchService.batchRecognizeMovieTitles(movieScrapeParams.moviesToScrape);
          LOGGER.info("Batch AI recognition completed for {} movies", aiRecognitionResults.size());
        } catch (Exception e) {
          LOGGER.warn("Batch AI recognition failed, falling back to individual recognition: {}", e.getMessage());
        }
      } else {
        LOGGER.debug("OpenAI API key not configured, skipping batch AI recognition");
      }
    } else {
      LOGGER.debug("Batch AI recognition skipped: doSearch={}, movieCount={}",
                   movieScrapeParams.doSearch, movieScrapeParams.moviesToScrape.size());
    }

    initThreadPool(3, "scrape");

    for (Movie movie : movieScrapeParams.moviesToScrape) {
      submitTask(new Worker(movie, aiRecognitionResults));
    }
    waitForCompletionOrCancel();

    // initiate smart scrape
    if (!smartScrapeList.isEmpty() && !GraphicsEnvironment.isHeadless() && !runInBackground) {
      try {
        SwingUtilities.invokeAndWait(() -> {
          int selectedCount = smartScrapeList.size();
          int index = 0;

          do {
            Movie movie = smartScrapeList.get(index);
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
        });
      }
      catch (Exception e) {
        LOGGER.error("SmartScrape crashed - '{}'", e.getMessage());
      }
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
    private MovieList   movieList;
    private final Movie movie;
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
            movie.setMetadata(md, movieScrapeParams.scraperMetadataConfig, movieScrapeParams.overwriteExistingItems);
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
      }
    }

    private MediaSearchResult searchForMovie(MediaScraper mediaMetadataProvider) throws ScrapeException {
      // 处理电影标题，提取干净的标题和年份
      String processedTitle = movie.getTitle();
      Integer processedYear = movie.getYear();
      
      String[] parserInfo = ParserUtils.detectCleanTitleAndYear(movie.getTitle(), Collections.emptyList());
      if (parserInfo != null && parserInfo.length >= 2) {
        processedTitle = parserInfo[0];
        if (StringUtils.isNotBlank(parserInfo[1])) {
          try {
            processedYear = Integer.parseInt(parserInfo[1]);
          } catch (NumberFormatException e) {
            LOGGER.debug("Could not parse year: {}", parserInfo[1]);
          }
        }
        LOGGER.debug("Processed title '{}' -> '{}' (year: {})", movie.getTitle(), processedTitle, processedYear);
      }
      
      // 使用批量AI识别结果
      MediaSearchResult aiResult = null;
      String recognizedTitle = null;
      
      // 首先尝试使用批量识别结果
      if (aiRecognitionResults != null && aiRecognitionResults.containsKey(movie.getDbId().toString())) {
        recognizedTitle = aiRecognitionResults.get(movie.getDbId().toString());
        LOGGER.info("Using batch AI recognition result: '{}' for movie: '{}'", recognizedTitle, movie.getTitle());
      } else {
        // 检查是否应该进行单个识别回退
        String apiKey = org.tinymediamanager.core.Settings.getInstance().getOpenAiApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
          if (aiRecognitionResults != null) {
            LOGGER.debug("Movie '{}' (ID: {}) not found in batch results, falling back to individual recognition",
                        movie.getTitle(), movie.getDbId());
          } else {
            LOGGER.debug("Batch recognition was not performed, using individual recognition for movie '{}'", movie.getTitle());
          }

          // 回退到单个识别（兼容旧逻辑）
          try {
            ChatGPTMovieRecognitionService chatGPTService = new ChatGPTMovieRecognitionService();
            recognizedTitle = chatGPTService.recognizeMovieTitle(movie);
            if (recognizedTitle != null) {
              LOGGER.info("Individual ChatGPT recognition: '{}' for movie: '{}'", recognizedTitle, movie.getTitle());
            }
          } catch (Exception e) {
            LOGGER.warn("Individual ChatGPT movie recognition failed: {}", e.getMessage());
          }
        } else {
          LOGGER.debug("OpenAI API key not configured, skipping AI recognition for movie '{}'", movie.getTitle());
        }
      }
      
      // 使用AI识别的标题重新搜索
      if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
        String[] aiParserInfo = ParserUtils.detectCleanTitleAndYear(recognizedTitle, Collections.emptyList());
        String aiProcessedTitle = recognizedTitle;
        Integer aiProcessedYear = movie.getYear();
        
        if (aiParserInfo != null && aiParserInfo.length >= 2) {
          aiProcessedTitle = aiParserInfo[0];
          if (StringUtils.isNotBlank(aiParserInfo[1])) {
            try {
              aiProcessedYear = Integer.parseInt(aiParserInfo[1]);
            } catch (NumberFormatException e) {
              LOGGER.debug("Could not parse year from AI result: {}", aiParserInfo[1]);
            }
          }
          LOGGER.debug("AI processed title '{}' -> '{}' (year: {})", recognizedTitle, aiProcessedTitle, aiProcessedYear);
        }
        
        List<MediaSearchResult> aiResults = movieList.searchMovie(aiProcessedTitle, aiProcessedYear, movie.getIds(), mediaMetadataProvider);
        
        if (ListUtils.isNotEmpty(aiResults)) {
          aiResult = aiResults.get(0);
          final double scraperTreshold = MovieModuleManager.getInstance().getSettings().getScraperThreshold();
          
          if (aiResult.getScore() >= scraperTreshold) {
            LOGGER.info("AI recognition successful! Found match with score: {}", aiResult.getScore());
            return aiResult;
          } else {
            LOGGER.warn("AI recognized title found, but score ({}) is lower than threshold ({})", aiResult.getScore(), scraperTreshold);
            aiResult = null; // 重置结果，继续常规搜索
          }
        } else {
          LOGGER.info("No results found for AI recognized title: '{}'", aiProcessedTitle);
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
            LOGGER.warn("Two identical results for '{}', can't decide which to take - ignore result", movie.getTitle());
            MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, movie, "movie.scrape.toosimilar"));
            return null;
          }
        }

        // get threshold from settings (default 0.75) - to minimize false positives
        final double scraperTreshold = MovieModuleManager.getInstance().getSettings().getScraperThreshold();
        LOGGER.debug("using threshold from settings of {}", scraperTreshold);
        if (result.getScore() < scraperTreshold) {
          LOGGER.warn("Score ({}) is lower than minimum score ({}) for '{}' - ignore result", result.getScore(), scraperTreshold, movie.getTitle());
          MessageManager.getInstance()
              .pushMessage(
                  new Message(MessageLevel.ERROR, movie, "movie.scrape.toolowscore", new String[] { String.format("%.2f", scraperTreshold) }));
          return null;
        }
      }
      else {
        LOGGER.info("No result found for '{}'", movie.getTitle());
        MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, movie, "movie.scrape.nomatchfound"));
      }

      return result;
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
}
