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

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SIBLINGS;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.nio.file.FileVisitResult.TERMINATE;
import static org.tinymediamanager.core.Utils.DISC_FOLDER_REGEX;
import static org.tinymediamanager.core.Utils.SEASON_NFO_PATTERN;
import static org.tinymediamanager.core.Utils.SKIP_FILES;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.AbstractFileVisitor;
import org.tinymediamanager.core.ImageCache;
import org.tinymediamanager.core.MediaFileHelper;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaSource;
import org.tinymediamanager.core.tasks.MediaFileInformationFetcherTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.tvshow.TvShowArtworkHelper;
import org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser;
import org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.EpisodeMatchingResult;
import org.tinymediamanager.core.tvshow.TvShowHelpers;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.connector.TvShowEpisodeNfoParser;
import org.tinymediamanager.core.tvshow.connector.TvShowEpisodeNfoParser.Episode;
import org.tinymediamanager.core.tvshow.connector.TvShowNfoParser;
import org.tinymediamanager.core.tvshow.connector.TvShowSeasonNfoParser;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.core.tvshow.services.BatchChatGPTEpisodeRecognitionService;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.scraper.entities.MediaEpisodeNumber;
import org.tinymediamanager.scraper.entities.MediaEpisodeGroup;
import org.apache.commons.io.FilenameUtils;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaEpisodeGroup;
import org.tinymediamanager.scraper.entities.MediaEpisodeNumber;
import org.tinymediamanager.scraper.thesportsdb.TheSportsDbHelper;
import org.tinymediamanager.scraper.thesportsdb.entities.League;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.ParserUtils;
import org.tinymediamanager.scraper.util.StrgUtils;
import org.tinymediamanager.thirdparty.KodiRPC;
import org.tinymediamanager.thirdparty.VSMeta;
import org.tinymediamanager.thirdparty.trakttv.TvShowSyncTraktTvTask;

/**
 * The Class TvShowUpdateDataSourcesTask.
 * 
 * @author Manuel Laggner
 */

public class TvShowUpdateDatasourceTask extends TmmThreadPool {
  private static final Logger                  LOGGER         = LoggerFactory.getLogger(TvShowUpdateDatasourceTask.class);

  // skip well-known, but unneeded folders (UPPERCASE)
  private static final List<String>            SKIP_FOLDERS   = Arrays.asList(".", "..", "CERTIFICATE", "$RECYCLE.BIN", "RECYCLER",
      "SYSTEM VOLUME INFORMATION", "@EADIR", "ADV_OBJ", "EXTRATHUMB", "PLEX VERSIONS");

  // skip folders starting with a SINGLE "." or "._"
  private static final String                  SKIP_REGEX     = "^[.][\\w@]+.*";

  private static long                          preDir         = 0;
  private static long                          postDir        = 0;
  private static long                          visFile        = 0;

  private final List<String>                   dataSources    = new ArrayList<>();
  private final List<Pattern>                  skipFolders    = new ArrayList<>();
  private final List<TvShow>                   showsToUpdate  = new ArrayList<>();
  private final TvShowList                     tvShowList;
  private final Set<Path>                      filesFound     = new HashSet<>();
  private final Map<Path, BasicFileAttributes> fileAttributes = new HashMap<>();
  private final ReentrantReadWriteLock         fileLock       = new ReentrantReadWriteLock();

  // 收集需要AI识别的文件信息，用于批量处理（轻量级）
  private final List<PendingAIRecognition>     pendingAIRecognitions = new ArrayList<>();

  // 批量处理状态跟踪
  private volatile boolean                     batchProcessingInProgress = false;
  private final Object                         batchProcessingLock = new Object();

  // 性能指标收集
  private final AIRecognitionMetrics          aiMetrics = new AIRecognitionMetrics();

  // 实时进度反馈
  private final ProgressReporter               progressReporter = new ProgressReporter();

  // 配置热更新支持
  private volatile AIConfigurationSnapshot    currentAIConfig = null;
  private final Object                         configUpdateLock = new Object();

  /**
   * AI配置快照，用于热更新检测
   */
  private static class AIConfigurationSnapshot {
    final String apiKey;
    final String apiUrl;
    final boolean aiEnabled;
    final int maxRetries;
    final long timestamp;

    AIConfigurationSnapshot() {
      Settings settings = Settings.getInstance();
      this.apiKey = settings.getOpenAiApiKey();
      this.apiUrl = settings.getOpenAiApiUrl();
      this.aiEnabled = true; // 默认启用，可以从配置中读取
      this.maxRetries = 3; // 可以从配置中读取
      this.timestamp = System.currentTimeMillis();
    }

    boolean hasChanged(AIConfigurationSnapshot other) {
      if (other == null) return true;
      return !java.util.Objects.equals(this.apiKey, other.apiKey) ||
             !java.util.Objects.equals(this.apiUrl, other.apiUrl) ||
             this.aiEnabled != other.aiEnabled ||
             this.maxRetries != other.maxRetries;
    }

    @Override
    public String toString() {
      return String.format("AIConfig{enabled=%s, url=%s, retries=%d, timestamp=%d}",
                          aiEnabled, apiUrl != null ? "***" : "null", maxRetries, timestamp);
    }
  }

  /**
   * 实时进度反馈器
   */
  private static class ProgressReporter {
    private volatile int totalFiles = 0;
    private volatile int processedFiles = 0;
    private volatile int successfulFiles = 0;
    private volatile int failedFiles = 0;
    private volatile String currentFile = "";
    private volatile String currentStage = "";
    private volatile long startTime = 0;

    void startBatchProcessing(int total) {
      this.totalFiles = total;
      this.processedFiles = 0;
      this.successfulFiles = 0;
      this.failedFiles = 0;
      this.startTime = System.currentTimeMillis();
      this.currentStage = "Initializing";
      reportProgress();
    }

    void updateProgress(String filename, String stage) {
      this.currentFile = filename;
      this.currentStage = stage;
      reportProgress();
    }

    void fileCompleted(String filename, boolean success) {
      this.processedFiles++;
      if (success) {
        this.successfulFiles++;
      } else {
        this.failedFiles++;
      }
      this.currentFile = filename;
      this.currentStage = success ? "Completed" : "Failed";
      reportProgress();
    }

    void batchCompleted() {
      this.currentStage = "Completed";
      this.currentFile = "";
      reportProgress();
    }

    private void reportProgress() {
      double percentage = totalFiles > 0 ? (processedFiles * 100.0 / totalFiles) : 0.0;
      long elapsedTime = System.currentTimeMillis() - startTime;
      long estimatedTotal = processedFiles > 0 ? (elapsedTime * totalFiles / processedFiles) : 0;
      long remainingTime = estimatedTotal - elapsedTime;

      String progressMsg = String.format(
          "AI Recognition Progress: %.1f%% (%d/%d) - %s: %s - ETA: %s",
          percentage, processedFiles, totalFiles, currentStage,
          currentFile.length() > 50 ? "..." + currentFile.substring(currentFile.length() - 47) : currentFile,
          formatTime(remainingTime)
      );

      // 发送进度消息
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.INFO, TmmResourceBundle.getString("ai.batch.recognition"), progressMsg));

      // 详细日志
      LOGGER.debug("Progress: {:.1f}% ({}/{}) - Success: {}, Failed: {}, Current: {} [{}]",
                  percentage, processedFiles, totalFiles, successfulFiles, failedFiles, currentFile, currentStage);
    }

    private String formatTime(long milliseconds) {
      if (milliseconds <= 0) return "Unknown";

      long seconds = milliseconds / 1000;
      long minutes = seconds / 60;
      long hours = minutes / 60;

      if (hours > 0) {
        return String.format("%dh %dm", hours, minutes % 60);
      } else if (minutes > 0) {
        return String.format("%dm %ds", minutes, seconds % 60);
      } else {
        return String.format("%ds", seconds);
      }
    }

    String getFinalReport() {
      long totalTime = System.currentTimeMillis() - startTime;
      return String.format(
          "Batch AI Recognition Final Report: %d files processed in %s - Success: %d, Failed: %d",
          processedFiles, formatTime(totalTime), successfulFiles, failedFiles
      );
    }
  }

  /**
   * AI识别性能指标收集器
   */
  private static class AIRecognitionMetrics {
    private volatile long totalFilesProcessed = 0;
    private volatile long totalAICallsMade = 0;
    private volatile long totalSuccessfulRecognitions = 0;
    private volatile long totalFailedRecognitions = 0;
    private volatile long totalProcessingTimeMs = 0;
    private volatile long averageResponseTimeMs = 0;

    void recordBatchProcessing(int filesCount, int aiCalls, int successes, int failures, long processingTimeMs) {
      totalFilesProcessed += filesCount;
      totalAICallsMade += aiCalls;
      totalSuccessfulRecognitions += successes;
      totalFailedRecognitions += failures;
      totalProcessingTimeMs += processingTimeMs;

      if (totalAICallsMade > 0) {
        averageResponseTimeMs = totalProcessingTimeMs / totalAICallsMade;
      }
    }

    String getMetricsReport() {
      double successRate = totalFilesProcessed > 0 ?
          (totalSuccessfulRecognitions * 100.0 / totalFilesProcessed) : 0.0;

      return String.format(
          "AI Recognition Metrics: Files=%d, AI Calls=%d, Success=%d (%.1f%%), Failed=%d, Avg Response=%dms",
          totalFilesProcessed, totalAICallsMade, totalSuccessfulRecognitions,
          successRate, totalFailedRecognitions, averageResponseTimeMs);
    }

    void reset() {
      totalFilesProcessed = 0;
      totalAICallsMade = 0;
      totalSuccessfulRecognitions = 0;
      totalFailedRecognitions = 0;
      totalProcessingTimeMs = 0;
      averageResponseTimeMs = 0;
    }
  }

  /**
   * 轻量级的待AI识别信息
   */
  private static class PendingAIRecognition {
    final TvShow tvShow;
    final String relativePath;
    final MediaFile videoFile;
    final String uniqueId;

    PendingAIRecognition(TvShow tvShow, String relativePath, MediaFile videoFile) {
      this.tvShow = tvShow;
      this.relativePath = relativePath;
      this.videoFile = videoFile;
      this.uniqueId = tvShow.getDbId() + "_" + relativePath.hashCode();
    }
  }

  /**
   * Instantiates a new scrape task - to update all datasources
   * 
   */
  public TvShowUpdateDatasourceTask() {
    super(TmmResourceBundle.getString("update.datasource"));
    this.tvShowList = TvShowModuleManager.getInstance().getTvShowList();
    this.dataSources.addAll(TvShowModuleManager.getInstance().getSettings().getTvShowDataSource());

    init();
  }

  /**
   * Instantiates a new scrape task - to update a single datasource
   * 
   * @param datasource
   *          the data source to start the task for
   */
  public TvShowUpdateDatasourceTask(String datasource) {
    super(TmmResourceBundle.getString("update.datasource") + " (" + datasource + ")");
    this.tvShowList = TvShowModuleManager.getInstance().getTvShowList();
    this.dataSources.add(datasource);

    init();
  }

  /**
   * Instantiates a new scrape task - to update a single datasource
   *
   * @param datasources
   *          the data sources to start the task for
   */
  public TvShowUpdateDatasourceTask(Collection<String> datasources) {
    this(datasources, Collections.emptyList());
  }

  /**
   * Instantiates a new scrape task - to update given tv shows
   * 
   * @param tvShowFolders
   *          a list of TV show folders to start the task for
   */
  public TvShowUpdateDatasourceTask(List<TvShow> tvShowFolders) {
    this(Collections.emptyList(), tvShowFolders);
  }

  private TvShowUpdateDatasourceTask(Collection<String> dataSources, List<TvShow> tvShowFolders) {
    super(TmmResourceBundle.getString("update.datasource"));
    this.tvShowList = TvShowModuleManager.getInstance().getTvShowList();
    this.dataSources.addAll(dataSources);
    this.showsToUpdate.addAll(tvShowFolders);

    init();
  }

  private void init() {
    for (String skipFolder : TvShowModuleManager.getInstance().getSettings().getSkipFolder()) {
      try {
        Pattern pattern = Pattern.compile(skipFolder);
        skipFolders.add(pattern);
      }
      catch (Exception e) {
        try {
          LOGGER.debug("no valid skip pattern - '{}'", skipFolder);

          Pattern pattern = Pattern.compile(Pattern.quote(skipFolder));
          skipFolders.add(pattern);
        }
        catch (Exception ignored) {
          // just ignore
        }
      }
    }
  }

  @Override
  public void doInBackground() {
    // check if there is at least one DS to update
    Utils.removeEmptyStringsFromList(dataSources);
    if (dataSources.isEmpty() && showsToUpdate.isEmpty()) {
      LOGGER.info("no datasource to update");
      MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "update.datasource.nonespecified"));
      return;
    }

    resetCounters();

    try {
      StopWatch stopWatch = new StopWatch();
      stopWatch.start();
      start();

      // get existing show folders
      List<Path> existing = new ArrayList<>();
      for (TvShow show : tvShowList.getTvShows()) {
        existing.add(show.getPathNIO());
      }

      // here we have 2 ways of updating:
      // - per datasource -> update ds / remove orphaned / update MFs
      // - per TV show -> udpate TV show / update MFs
      if (showsToUpdate.isEmpty()) {
        // should we re-set all new flags?
        if (TvShowModuleManager.getInstance().getSettings().isResetNewFlagOnUds()) {
          for (TvShow tvShow : tvShowList.getTvShows()) {
            tvShow.setNewlyAdded(false);

            for (TvShowEpisode episode : tvShow.getEpisodes()) {
              episode.setNewlyAdded(false);
            }
          }
        }

        // update selected data sources
        for (String ds : dataSources) {
          Path dsAsPath = Paths.get(ds);

          // check the special case, that the data source is also an ignore folder
          if (isInSkipFolder(dsAsPath)) {
            LOGGER.debug("datasource '{}' is also a skipfolder - skipping", ds);
            continue;
          }

          LOGGER.info("Starting \"update data sources\" on datasource: {}", ds);
          initThreadPool(3, "update");
          setTaskName(TmmResourceBundle.getString("update.datasource") + " '" + ds + "'");
          publishState();

          // first of all check if the DS is available; we can take the
          // Files.exist here:
          // if the DS exists (and we have access to read it): Files.exist =
          // true
          if (!Files.exists(dsAsPath)) {
            // error - continue with next datasource
            LOGGER.warn("Datasource '{}' not available/empty", ds);
            MessageManager.getInstance()
                .pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "update.datasource.unavailable", new String[] { ds }));
            continue;
          }
          publishState();

          List<Path> newTvShowDirs = new ArrayList<>();
          List<Path> existingTvShowDirs = new ArrayList<>();
          List<Path> rootList = listFilesAndDirs(dsAsPath);

          // when there is _nothing_ found in the ds root, it might be offline -
          // skip further processing
          // not in Windows since that won't happen there
          if (rootList.isEmpty() && !SystemUtils.IS_OS_WINDOWS) {
            // re-check if the folder is completely empty
            boolean isEmpty = true;
            try {
              isEmpty = Utils.isFolderEmpty(dsAsPath);
            }
            catch (Exception e) {
              LOGGER.warn("Could not check folder '{}' for emptiness - '{}'", dsAsPath, e.getMessage());
            }

            if (isEmpty) {
              // error - continue with next datasource
              MessageManager.getInstance()
                  .pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "update.datasource.unavailable", new String[] { ds }));
              continue;
            }
          }

          for (Path path : rootList) {
            if (Files.isDirectory(path)) {

              // additional datasource/A/show sub dirs!
              if (path.getFileName().toString().length() == 1) {
                List<Path> subList = listFilesAndDirs(path);
                for (Path sub : subList) {
                  if (Files.isDirectory(sub)) {
                    if (existing.contains(sub)) {
                      existingTvShowDirs.add(sub);
                    }
                    else {
                      newTvShowDirs.add(sub);
                    }
                  }
                }
              }

              // normal datasource/show folder
              else {
                if (existing.contains(path)) {
                  existingTvShowDirs.add(path);
                }
                else {
                  newTvShowDirs.add(path);
                }
              }
            }
            else {
              // File in root folder - not possible for TV datasource (at least, for videos ;)
              String ext = FilenameUtils.getExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT);
              if (Settings.getInstance().getVideoFileType().contains("." + ext)) {
                MessageManager.getInstance()
                    .pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "update.datasource.episodeinroot",
                        new String[] { path.getFileName().toString() }));
              }
            }
          }

          for (Path subdir : newTvShowDirs) {
            submitTask(new FindTvShowTask(subdir, dsAsPath.toAbsolutePath()));
          }
          for (Path subdir : existingTvShowDirs) {
            submitTask(new FindTvShowTask(subdir, dsAsPath.toAbsolutePath()));
          }
          waitForCompletionOrCancel();

          // print stats
          LOGGER.info("Files found: {}", filesFound.size());
          LOGGER.info("TV shows found: {}", tvShowList.getTvShowCount());
          LOGGER.info("Episodes found: {}", tvShowList.getEpisodeCount());
          LOGGER.debug("PreDir: {}", preDir);
          LOGGER.debug("PostDir: {}", postDir);
          LOGGER.debug("VisFile: {}", visFile);

          if (cancel) {
            break;
          }

          cleanupDatasource(ds);
          waitForCompletionOrCancel();
          if (cancel) {
            break;
          }
        } // end foreach datasource
      }
      else { // for each selected show
        LOGGER.info("Start \"update data sources\" for selected TV shows");
        initThreadPool(3, "update");

        // get distinct data sources
        Set<String> showDatasources = new HashSet<>();
        showsToUpdate.stream().filter(show -> !show.isLocked()).forEach(show -> showDatasources.add(show.getDataSource()));

        List<TvShow> showsToCleanup = new ArrayList<>();

        // update shows grouped by data source
        for (String ds : showDatasources) {
          Path dsAsPath = Paths.get(ds);
          // first of all check if the DS is available; we can take the
          // Files.exist here:
          // if the DS exists (and we have access to read it): Files.exist = true
          if (!Files.exists(dsAsPath)) {
            // error - continue with next datasource
            MessageManager.getInstance()
                .pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "update.datasource.unavailable", new String[] { ds }));
            continue;
          }

          List<Path> rootList = listFilesAndDirs(dsAsPath);

          // when there is _nothing_ found in the ds root, it might be offline - skip further processing
          // not in Windows since that won't happen there
          if (rootList.isEmpty() && !SystemUtils.IS_OS_WINDOWS) {
            // re-check if the folder is completely empty
            boolean isEmpty = true;
            try {
              isEmpty = Utils.isFolderEmpty(dsAsPath);
            }
            catch (Exception e) {
              LOGGER.warn("Could not check folder '{}' for emptiness - '{}'", dsAsPath, e.getMessage());
            }

            if (isEmpty) {
              // error - continue with next datasource
              MessageManager.getInstance()
                  .pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "update.datasource.unavailable", new String[] { ds }));
              continue;
            }
          }

          // update selected TV shows
          for (TvShow show : showsToUpdate) {
            if (!show.getDataSource().equals(ds)) {
              continue;
            }
            showsToCleanup.add(show);
            submitTask(new FindTvShowTask(show.getPathNIO(), Paths.get(ds)));
          }
        }
        waitForCompletionOrCancel();

        // print stats
        LOGGER.info("Files found: {}", filesFound.size());
        LOGGER.info("TV shows found: {}", tvShowList.getTvShowCount());
        LOGGER.info("Episodes found: {}", tvShowList.getEpisodeCount());
        LOGGER.debug("PreDir: {}", preDir);
        LOGGER.debug("PostDir: {}", postDir);
        LOGGER.debug("VisFile: {}", visFile);

        if (!cancel) {
          cleanup(showsToCleanup);
          waitForCompletionOrCancel();

          // 执行批量AI识别处理
          if (!pendingAIRecognitions.isEmpty()) {
            setTaskName(TmmResourceBundle.getString("Batch AI Recognition"));
            setTaskDescription("Processing batch AI recognition...");
            publishState();
            processBatchAIRecognition();
          }

          // 清理解析缓存，防止内存泄漏
          TvShowEpisodeAndSeasonParser.clearParsingCache();
          LOGGER.debug("Parsing cache cleared after datasource update");
        }
      }

      if (cancel) {
        return;
      }

      // map Kodi entries
      if (StringUtils.isNotBlank(Settings.getInstance().getKodiHost())) {
        // call async to avoid slowdown of UDS
        TmmTaskManager.getInstance().addUnnamedTask(() -> KodiRPC.getInstance().updateTvShowMappings());
      }

      LOGGER.info("Getting Mediainfo...");

      initThreadPool(2, "mediainfo");
      setTaskName(TmmResourceBundle.getString("update.mediainfo"));
      setTaskDescription(null);
      setWorkUnits(0);
      setProgressDone(0);
      publishState();

      // gather MediaInformation for ALL shows - TBD
      if (!cancel) {
        if (showsToUpdate.isEmpty()) {
          // get MI for selected DS
          for (int i = tvShowList.getTvShows().size() - 1; i >= 0; i--) {
            if (cancel) {
              break;
            }
            TvShow tvShow = tvShowList.getTvShows().get(i);

            // do not process locked TV shows
            if (tvShow.isLocked()) {
              continue;
            }

            if (dataSources.contains(tvShow.getDataSource())) {
              gatherMediaInformationForUngatheredMediaFiles(tvShow);
            }
          }
        }
        else {
          // get MI for selected TV shows
          for (int i = tvShowList.getTvShows().size() - 1; i >= 0; i--) {
            if (cancel) {
              break;
            }
            TvShow tvShow = tvShowList.getTvShows().get(i);

            // do not process locked TV shows
            if (tvShow.isLocked()) {
              continue;
            }

            if (showsToUpdate.contains(tvShow)) {
              gatherMediaInformationForUngatheredMediaFiles(tvShow);
            }
          }
        }
        waitForCompletionOrCancel();
      }

      if (cancel) {
        return;
      }

      if (TvShowModuleManager.getInstance().getSettings().getSyncTrakt()) {
        TvShowSyncTraktTvTask task = new TvShowSyncTraktTvTask(TvShowModuleManager.getInstance().getTvShowList().getTvShows());
        task.setSyncCollection(TvShowModuleManager.getInstance().getSettings().getSyncTraktCollection());
        task.setSyncWatched(TvShowModuleManager.getInstance().getSettings().getSyncTraktWatched());
        task.setSyncRating(TvShowModuleManager.getInstance().getSettings().getSyncTraktRating());

        TmmTaskManager.getInstance().addUnnamedTask(task);
      }

      stopWatch.stop();
      LOGGER.info("Finished updating data sources :) - took {} ms", stopWatch);

      resetCounters();
    }
    catch (Exception e) {
      LOGGER.error("Could not update data sources for TV shows - '{}'", e.getMessage());
      MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, "update.datasource", "message.update.threadcrashed"));
    }
  }

  private void cleanup(List<TvShow> shows) {
    setTaskName(TmmResourceBundle.getString("update.cleanup"));
    setTaskDescription(null);
    setProgressDone(0);

    int showCount = shows.size();
    setWorkUnits(showCount);
    publishState();

    LOGGER.info("Removing orphaned TV shows/episodes/files...");
    for (int i = showCount - 1; i >= 0; i--) {
      if (cancel) {
        break;
      }

      publishState(showCount - i);

      TvShow tvShow = shows.get(i);

      // do not process locked TV shows (because filesFound has not been filled for them)
      if (tvShow.isLocked()) {
        continue;
      }

      if (!Files.exists(tvShow.getPathNIO())) {
        tvShowList.removeTvShow(tvShow);
      }
      else {
        cleanup(tvShow);
      }
    }
  }

  private void cleanupDatasource(String datasource) {
    setTaskName(TmmResourceBundle.getString("update.cleanup"));
    setTaskDescription(null);
    setProgressDone(0);

    int showCount = tvShowList.getTvShows().size();
    setWorkUnits(showCount);
    publishState();

    LOGGER.info("Removing orphaned TV shows/episodes/files...");

    for (int i = showCount - 1; i >= 0; i--) {
      if (cancel) {
        break;
      }

      publishState(showCount - i);

      TvShow tvShow = tvShowList.getTvShows().get(i);

      // check only TV shows matching datasource
      if (!Paths.get(datasource).toAbsolutePath().equals(Paths.get(tvShow.getDataSource()).toAbsolutePath())) {
        continue;
      }

      // do not process locked TV shows (because filesFound has not been filled for them)
      if (tvShow.isLocked()) {
        continue;
      }

      if (!Files.exists(tvShow.getPathNIO())) {
        tvShowList.removeTvShow(tvShow);
      }
      else {
        cleanup(tvShow);
      }
    }
  }

  private void cleanup(TvShow tvShow) {
    boolean dirty = false;
    if (!tvShow.isNewlyAdded() || tvShow.hasNewlyAddedEpisodes()) {
      // check and delete all not found MediaFiles
      for (MediaFile mf : tvShow.getMediaFiles()) {
        boolean fileFound = filesFound.contains(mf.getFileAsPath());

        if (!fileFound) {
          LOGGER.debug("removing orphaned file: {}", mf.getFileAsPath());
          tvShow.removeFromMediaFiles(mf);
          // and remove the image cache
          if (mf.isGraphic()) {
            ImageCache.invalidateCachedImage(mf);
          }

          dirty = true;
        }
      }

      for (TvShowSeason season : tvShow.getSeasons()) {
        // check and delete all not found MediaFiles
        for (MediaFile mf : season.getMediaFiles()) {
          boolean fileFound = filesFound.contains(mf.getFileAsPath());

          if (!fileFound) {
            LOGGER.debug("removing orphaned file: {}", mf.getFileAsPath());
            season.removeFromMediaFiles(mf);
            // and remove the image cache
            if (mf.isGraphic()) {
              ImageCache.invalidateCachedImage(mf);
            }

            dirty = true;
          }
        }
      }

      for (TvShowEpisode episode : tvShow.getEpisodes()) {
        for (MediaFile mf : episode.getMediaFiles()) {
          boolean fileFound = filesFound.contains(mf.getFileAsPath());

          if (!fileFound) {
            LOGGER.debug("removing orphaned file: {}", mf.getFileAsPath());
            episode.removeFromMediaFiles(mf);
            // and remove the image cache
            if (mf.isGraphic()) {
              ImageCache.invalidateCachedImage(mf);
            }

            dirty = true;
          }
        }

        // let's have a look if there is at least one video file for this episode
        List<MediaFile> mfs = episode.getMediaFiles(MediaFileType.VIDEO);
        if (mfs.isEmpty()) {
          tvShow.removeEpisode(episode);
          dirty = true;
        }
      }

      // check, if some episode MFs are assigned also to tvshows...!
      List<MediaFile> episodeFiles = tvShow.getEpisodesMediaFiles();
      List<MediaFile> cleanup = new ArrayList<>();
      for (MediaFile showFile : tvShow.getMediaFiles()) {
        if (episodeFiles.contains(showFile)) {
          cleanup.add(showFile);
          dirty = true;
        }
      }
      for (MediaFile mf : cleanup) {
        tvShow.removeFromMediaFiles(mf);
        LOGGER.debug("Removed duplicate show file {}", mf);
      }
    }

    if (dirty) {
      tvShow.saveToDb();
    }
  }

  /*
   * detect which mediafiles has to be parsed and start a thread to do that
   */
  private void gatherMediaInformationForUngatheredMediaFiles(TvShow tvShow) {
    // check if we should fetch video information
    if (!Settings.getInstance().isFetchVideoInfoOnUpdate()) {
      LOGGER.debug("Skipping media information fetching for TV show '{}' - fetchVideoInfoOnUpdate is disabled", tvShow.getTitle());
      return;
    }
    
    // get mediainfo for tv show (fanart/poster..)
    for (MediaFile mf : tvShow.getMediaFiles()) {
      if (StringUtils.isBlank(mf.getContainerFormat())) {
        submitTask(new TvShowMediaFileInformationFetcherTask(mf, tvShow, false));
      }
      else {
        // // did the file dates/size change?
        if (MediaFileHelper.gatherFileInformation(mf, fileAttributes.get(mf.getFileAsPath()))) {
          // okay, something changed with that show file - force fetching mediainfo (and drop medianfo.xml for MAIN video only)
          if (mf.getType() == MediaFileType.VIDEO) {
            tvShow.getMediaFiles(MediaFileType.MEDIAINFO).forEach(mediaFile -> {
              Utils.deleteFileSafely(mediaFile.getFileAsPath());
              tvShow.removeFromMediaFiles(mediaFile);
            });
          }
          submitTask(new TvShowMediaFileInformationFetcherTask(mf, tvShow, true));
        }
      }
    }

    // get mediainfo for all seasons within the TV show
    for (TvShowSeason season : new ArrayList<>(tvShow.getSeasons())) {
      for (MediaFile mf : season.getMediaFiles()) {
        if (StringUtils.isBlank(mf.getContainerFormat())) {
          submitTask(new TvShowMediaFileInformationFetcherTask(mf, season, false));
        }
        else {
          // // did the file dates/size change?
          if (MediaFileHelper.gatherFileInformation(mf, fileAttributes.get(mf.getFileAsPath()))) {
            // okay, something changed with that season file - force fetching mediainfo (and drop medianfo.xml for MAIN video only)
            if (mf.getType() == MediaFileType.VIDEO) {
              season.getMediaFiles(MediaFileType.MEDIAINFO).forEach(mediaFile -> {
                Utils.deleteFileSafely(mediaFile.getFileAsPath());
                tvShow.removeFromMediaFiles(mediaFile);
              });
            }
            submitTask(new TvShowMediaFileInformationFetcherTask(mf, season, true));
          }
        }
      }
    }

    // get mediainfo for all episodes within this TV show
    for (TvShowEpisode episode : new ArrayList<>(tvShow.getEpisodes())) {
      for (MediaFile mf : episode.getMediaFiles()) {
        if (StringUtils.isBlank(mf.getContainerFormat())) {
          submitTask(new TvShowMediaFileInformationFetcherTask(mf, episode, false));
        }
        else {
          // at least update the file dates
          if (MediaFileHelper.gatherFileInformation(mf, fileAttributes.get(mf.getFileAsPath()))) {
            // okay, something changed with that episode file - force fetching mediainfo (and drop medianfo.xml for MAIN video only)
            if (mf.getType() == MediaFileType.VIDEO) {
              episode.getMediaFiles(MediaFileType.MEDIAINFO).forEach(mediaFile -> {
                Utils.deleteFileSafely(mediaFile.getFileAsPath());
                episode.removeFromMediaFiles(mediaFile);
              });
            }
            submitTask(new TvShowMediaFileInformationFetcherTask(mf, episode, true));
          }
        }
      }
    }
  }

  /**
   * The Class FindTvShowTask.
   * 
   * @author Manuel Laggner
   */
  private class FindTvShowTask implements Callable<Object> {
    private final Path          showDir;
    private final Path          datasource;
    private final long          uniqueId;
    private final List<Pattern> extraMfFiletypePatterns;

    /**
     * Instantiates a new find tv show task.
     * 
     * @param showDir
     *          the subdir
     * @param datasource
     *          the datasource
     */
    public FindTvShowTask(Path showDir, Path datasource) {
      this.showDir = showDir;
      this.datasource = datasource;
      this.uniqueId = TmmTaskManager.getInstance().GLOB_THRD_CNT.incrementAndGet();

      this.extraMfFiletypePatterns = new ArrayList<>();
      for (String extr : MediaFileHelper.EXTRA_FOLDERS) {
        this.extraMfFiletypePatterns.add(Pattern.compile("(?i)[_.-]" + extr + "\\d?[.].{2,4}"));
      }
    }

    @Override
    public String call() throws Exception {
      String name = Thread.currentThread().getName();
      if (!name.contains("-G")) {
        name = name + "-G0";
      }
      name = name.replaceAll("\\-G\\d+", "-G" + uniqueId);
      Thread.currentThread().setName(name);

      if (showDir.getFileName().toString().matches(SKIP_REGEX)) {
        LOGGER.debug("Skipping dir: {}", showDir);
        return "";
      }

      TvShow tvShow = tvShowList.getTvShowByPath(showDir);
      if (tvShow != null && tvShow.isLocked()) {
        LOGGER.warn("TV show '{}' found in \"update data source\", but is locked", tvShow.getPath());
        return "";
      }

      Set<Path> allFiles = getAllFilesRecursive(showDir);
      if (allFiles.isEmpty()) {
        LOGGER.debug("Skip empty directory: {}", showDir);
        return "";
      }

      if (cancel) {
        return null;
      }

      LOGGER.debug("start parsing {}", showDir);
      publishState(showDir.toString());

      fileLock.writeLock().lock();
      filesFound.add(showDir.toAbsolutePath()); // our global cache
      filesFound.addAll(allFiles); // our global cache
      fileLock.writeLock().unlock();

      // convert to MFs (we need it anyway at the end)
      List<MediaFile> mfs = new ArrayList<>();
      for (Path file : allFiles) {
        if (!file.getFileName().toString().matches(SKIP_REGEX)) {
          MediaFile mf = new MediaFile(file);
          mfs.add(mf);
        }
      }
      allFiles.clear();
      Collections.sort(mfs);

      if (getMediaFiles(mfs, MediaFileType.VIDEO).isEmpty()) {
        LOGGER.debug("no video file found in directory {}", showDir);
        return "";
      }

      // ******************************
      // STEP 1 - get (or create) TvShow object
      // ******************************

      // SHOW_NFO
      MediaFile showNFO = new MediaFile(showDir.resolve("tvshow.nfo"), MediaFileType.NFO); // fixate
      if (tvShow == null) {
        // tvShow did not exist - try to parse a NFO file in parent folder
        if (Files.exists(showNFO.getFileAsPath())) {
          try {
            TvShowNfoParser parser = TvShowNfoParser.parseNfo(showNFO.getFileAsPath());
            tvShow = parser.toTvShow();
          }
          catch (Exception e) {
            LOGGER.debug("problem parsing NFO: {}", e.getMessage());
          }
        }
        if (tvShow == null) {
          // create new one
          tvShow = new TvShow();
        }

        if (StringUtils.isBlank(tvShow.getTitle()) || tvShow.getYear() <= 0) {
          // we have a tv show object, but without title or year; try to parse that our of the folder/filename
          String[] ty = ParserUtils.detectCleanTitleAndYear(showDir.getFileName().toString(),
              TvShowModuleManager.getInstance().getSettings().getBadWord());
          if (StringUtils.isBlank(tvShow.getTitle()) && StringUtils.isNotBlank(ty[0])) {
            tvShow.setTitle(ty[0]);
          }
          if (tvShow.getYear() <= 0 && !ty[1].isEmpty()) {
            try {
              tvShow.setYear(Integer.parseInt(ty[1]));
            }
            catch (Exception e) {
              LOGGER.trace("could not parse int: {}", e.getMessage());
            }
          }
        }

        // was NFO, but parsing exception. try to find at least imdb id within
        if ((tvShow.getImdbId().isEmpty() || tvShow.getTmdbId() == 0) && Files.exists(showNFO.getFileAsPath())) {
          try {
            String content = Utils.readFileToString(showNFO.getFileAsPath());
            String imdb = ParserUtils.detectImdbId(content);
            if (!imdb.isEmpty()) {
              LOGGER.debug("| Found IMDB id: {}", imdb);
              tvShow.setImdbId(imdb);
            }

            String tmdb = StrgUtils.substr(content, "themoviedb\\.org\\/tv\\/(\\d+)");
            if (tvShow.getTmdbId() == 0 && !tmdb.isEmpty()) {
              LOGGER.debug("| Found TMDB id: {}", tmdb);
              tvShow.setTmdbId(MetadataUtil.parseInt(tmdb, 0));
            }

            String tvdb = StrgUtils.substr(content, "thetvdb\\.com\\/series\\/(\\d+)");
            if (tvShow.getTvdbId().isEmpty() && !tvdb.isEmpty()) {
              LOGGER.debug("| Found TVDB id: {}", tmdb);
              tvShow.setTvdbId(tvdb);
            }
          }
          catch (IOException e) {
            LOGGER.debug("| couldn't read NFO {}", showNFO);
          }
        }

        tvShow.setPath(showDir.toAbsolutePath().toString());
        tvShow.setDataSource(datasource.toString());
        tvShow.setNewlyAdded(true);
        tvShowList.addTvShow(tvShow);
      }

      // detect some IDs from show folder
      if (!MediaIdUtil.isValidImdbId(tvShow.getImdbId())) {
        tvShow.setId(MediaMetadata.IMDB, ParserUtils.detectImdbId(showDir.getFileName().toString()));
      }
      if (tvShow.getTmdbId() == 0) {
        tvShow.setId(MediaMetadata.TMDB, ParserUtils.detectTmdbId(showDir.getFileName().toString()));
      }
      if (tvShow.getTvdbId().isEmpty()) {
        tvShow.setId(MediaMetadata.TVDB, ParserUtils.detectTvdbId(showDir.getFileName().toString()));
      }
      // Try to detect some sports/leagues from TheSportsDB
      // You cannot have a complete /sport/league/video.mkv structure as show;
      // the "league" must be the TvShow root, and if the datasource matches a "sport", we can add this too
      if (TheSportsDbHelper.SPORT_LEAGUES.keySet().contains(tvShow.getPathNIO().getFileName().toString())) {
        League l = TheSportsDbHelper.SPORT_LEAGUES.get(tvShow.getPathNIO().getFileName().toString());
        tvShow.setId(MediaMetadata.TSDB, l.idLeague);
      }

      // ******************************
      // STEP 1.1 - get all season NFO files
      // ******************************
      for (MediaFile mf : getMediaFiles(mfs, MediaFileType.NFO)) {
        Matcher matcher = SEASON_NFO_PATTERN.matcher(mf.getFilename());
        if (matcher.matches()) {
          // season NFO found - get the season number
          // this NFO must offer _at least_ the season number to be valid
          TvShowSeasonNfoParser parser = TvShowSeasonNfoParser.parseNfo(mf.getFileAsPath());
          if (parser.season > -1) {
            TvShowSeason tvShowSeason = tvShow.getOrCreateSeason(parser.season);
            tvShowSeason.merge(parser.toTvShowSeason());
            tvShowSeason.addToMediaFiles(mf);
          }
        }
      }

      // ******************************
      // STEP 2 - get all video MFs and get (or create) episodes
      // ******************************

      Set<Path> discFolders = new HashSet<>();
      for (MediaFile vid : getMediaFiles(mfs, MediaFileType.VIDEO)) {
        if (cancel) {
          return null;
        }

        // build an array of MFs, which might be in same episode
        List<MediaFile> epFiles = new ArrayList<>();

        if (vid.isDiscFile()) {
          // find EP root folder, and do not walk lower than showDir!
          Path discRoot = vid.getFileAsPath().toAbsolutePath(); // folder
          if (!discRoot.getFileName().toString().matches(DISC_FOLDER_REGEX)) {
            discRoot = discRoot.getParent();
          }
          if (discFolders.contains(discRoot)) {
            // we already parsed one disc file (which adds all other videos), so
            // break here already
            continue;
          }
          discFolders.add(discRoot);
          // add all known files starting with same discRootDir
          for (MediaFile em : mfs) {
            if (em.getFileAsPath().startsWith(discRoot)) {
              if (em.getType() != MediaFileType.UNKNOWN) {
                epFiles.add(em);
              }
            }
          }
        }
        else {
          // epFiles.add(vid); // add ourself

          // normal episode file - get all same named files (in same directory!)
          String vidBasename = FilenameUtils.getBaseName(Utils.cleanStackingMarkers(vid.getFilename()));
          vidBasename = showDir.relativize(vid.getFileAsPath().getParent()) + "/" + vidBasename;
          LOGGER.trace("UDS: video basename {} - {}", vidBasename, vid.getFile());
          for (MediaFile other : mfs) {
            // change asdf-poster.jpg -> asdf.jpg, to ease basename matching ;)
            String imgBasename = FilenameUtils.getBaseName(Utils.cleanStackingMarkers(getMediaFileNameWithoutType(other)));
            imgBasename = showDir.relativize(other.getFileAsPath().getParent()) + "/" + imgBasename;

            // we now got a match with same (generated) basename!
            if (vidBasename.equalsIgnoreCase(imgBasename)) {
              if (other.getType() == MediaFileType.POSTER || other.getType() == MediaFileType.GRAPHIC) {
                // re-type posters to EP "posters" (=thumb)
                other.setType(MediaFileType.THUMB);
              }
              epFiles.add(other);
              LOGGER.trace("UDS: found matching {} - {}", imgBasename, other.getFile());
            }
          } // end inner MF loop over all non videos
        } // end MF nodisc file

        // ******************************
        // STEP 2.1 - is this file already assigned to another episode?
        // ******************************
        List<TvShowEpisode> episodes = TvShowList.getTvEpisodesByFile(tvShow, vid.getFile());
        if (episodes.isEmpty()) {

          // ******************************
          // STEP 2.1.1 - parse EP NFO (has precedence over files)
          // ******************************

          // meta data from VSMETA files
          MediaFile meta = getMediaFile(epFiles, MediaFileType.VSMETA);
          TvShowEpisode vsMetaEP = null;
          if (meta != null) {
            VSMeta vsmeta = new VSMeta(meta.getFileAsPath());
            vsmeta.parseFile();
            vsMetaEP = vsmeta.getTvShowEpisode();
          }

          // meta data from XML files
          TvShowEpisode xmlEP = null;
          for (MediaFile xmlMf : epFiles) {
            if ("xml".equalsIgnoreCase(xmlMf.getExtension()) && !xmlMf.getFilename().endsWith("mediainfo.xml")) {
              try {
                TvShowEpisodeNfoParser nfoParser = TvShowEpisodeNfoParser.parseNfo(xmlMf.getFileAsPath());
                List<TvShowEpisode> epsInXml = nfoParser.toTvShowEpisodes();
                if (!epsInXml.isEmpty()) {
                  xmlEP = epsInXml.get(0);
                }
              }
              catch (Exception e) {
                // ignored
              }
            }
          }

          // drop all unknown EP files
          epFiles = epFiles.stream().filter(mediaFile -> mediaFile.getType() != MediaFileType.UNKNOWN).collect(Collectors.toList());

          MediaFile epNfo = getMediaFile(epFiles, MediaFileType.NFO);
          if (epNfo != null) {
            LOGGER.debug("found episode NFO - try to parse '{}'", showDir.relativize(epNfo.getFileAsPath()));
            List<TvShowEpisode> episodesInNfo = new ArrayList<>();

            try {
              TvShowEpisodeNfoParser parser = TvShowEpisodeNfoParser.parseNfo(epNfo.getFileAsPath());

              // ALL episodes detected with -1? try to parse from filename (without AI first)...
              boolean allUnknown = !parser.episodes.isEmpty() && parser.episodes.stream().allMatch(ep -> ep.episode == -1);
              if (allUnknown) {
                EpisodeMatchingResult result = TvShowEpisodeAndSeasonParser
                    .detectEpisodeHybrid(showDir.relativize(epNfo.getFileAsPath()).toString(), tvShow.getTitle(), false);
                if (parser.episodes.size() == result.episodes.size()) {
                  int i = 0;
                  for (Episode ep : parser.episodes) {
                    ep.episode = result.episodes.get(i);
                    ep.season = result.season;
                    i++;
                  }
                }
              }

              if (parser.isValidNfo()) {
                episodesInNfo.addAll(parser.toTvShowEpisodes());
              }
            }
            catch (Exception e) {
              LOGGER.debug("could not parse episode NFO: {}", e.getMessage());
            }

            // did we find any episodes in the NFO?
            if (!episodesInNfo.isEmpty()) {
              // these have priority!
              for (TvShowEpisode episode : episodesInNfo) {
                episode.setPath(vid.getPath());
                episode.setTvShow(tvShow);

                if (episode.getMediaSource() == MediaSource.UNKNOWN) {
                  episode.setMediaSource(MediaSource.parseMediaSource(vid.getBasename()));
                }
                episode.setNewlyAdded(true);

                // remember the filename the first time the show gets added to tmm
                if (StringUtils.isBlank(episode.getOriginalFilename())) {
                  episode.setOriginalFilename(vid.getFilename());
                }

                // add main video file
                episode.addToMediaFiles(vid);

                // and all other files (non VIDEO files and video files with a different basename)
                for (MediaFile mediaFile : epFiles) {
                  if (mediaFile.getType() != MediaFileType.VIDEO || (!mediaFile.getBasename().equals(vid.getBasename()))) {
                    episode.addToMediaFiles(mediaFile);
                  }
                }

                if (vid.isDiscFile()) {
                  episode.setDisc(true);

                  // disc files should be inside a discFolder - if we have one, set the path a level higher:
                  Path discRoot = vid.getFileAsPath().toAbsolutePath();
                  if (discRoot.getFileName().toString().matches(DISC_FOLDER_REGEX)) {
                    // name of video file matches a disc folder? (eg when having already a virtual one)
                    discRoot = discRoot.getParent();
                    episode.setPath(discRoot.toString());
                  }
                  else if (discRoot.getParent().getFileName().toString().matches(DISC_FOLDER_REGEX)) {
                    // video file not in its dedicated folder
                    discRoot = discRoot.getParent();
                    episode.setPath(discRoot.toString());
                  }
                  // else keep the current video path as episode root (set above)
                }

                if (episodesInNfo.size() > 1) {
                  episode.setMultiEpisode(true);
                }
                else {
                  episode.setMultiEpisode(false);
                }
                episode.merge(vsMetaEP); // merge VSmeta infos
                episode.merge(xmlEP); // merge XML infos

                episode.saveToDb();
                tvShow.addEpisode(episode);
              }
              continue; // with next video MF
            }
          } // end parse NFO

          // ******************************
          // STEP 2.1.2 - no NFO? try to parse episode/season (without AI first)
          // ******************************
          String relativePath = showDir.relativize(vid.getFileAsPath()).toString();
          EpisodeMatchingResult result = TvShowEpisodeAndSeasonParser.detectEpisodeHybrid(relativePath, tvShow.getTitle(), false);

          // 如果传统解析失败，收集这个文件用于后续批量AI识别
          if (result.season == -1 || result.episodes.isEmpty()) {
            synchronized (pendingAIRecognitions) {
              pendingAIRecognitions.add(new PendingAIRecognition(tvShow, relativePath, vid));
            }
            LOGGER.debug("Added file for batch AI recognition: {}", relativePath);
            continue; // 跳过当前文件，等待批量处理
          }

          // second check: is the detected episode (>-1; season >-1) already in
          // tmm and any valid stacking markers found?
          if (result.episodes.size() == 1 && result.season > -1 && result.stackingMarkerFound) {
            // get any assigned episode
            List<TvShowEpisode> eps = tvShow.getEpisode(result.season, result.episodes.get(0));
            if (!eps.isEmpty()) {
              // okay, at least one existing episode found.. just check if there is the same base name without stacking markers
              boolean found = false;
              for (TvShowEpisode ep : eps) {
                // need to call Utils.cleanStackingMarkers() because the MF stacking markers aren't detected yet
                String episodeBasenameWoStackingMarker = FilenameUtils.getBaseName(Utils.cleanStackingMarkers(ep.getMainVideoFile().getFilename()));
                String mfBasenameWoStackingMarker = FilenameUtils.getBaseName(Utils.cleanStackingMarkers(vid.getFilename()));

                if (episodeBasenameWoStackingMarker.equals(mfBasenameWoStackingMarker)) {
                  if (ep.getMediaSource() == MediaSource.UNKNOWN) {
                    ep.setMediaSource(MediaSource.parseMediaSource(ep.getMainVideoFile().getBasename()));
                  }

                  ep.setNewlyAdded(true);

                  // remember the filename the first time the show gets added to tmm
                  if (StringUtils.isBlank(ep.getOriginalFilename())) {
                    ep.setOriginalFilename(vid.getFilename());
                  }

                  ep.addToMediaFiles(vid);
                  found = true;
                  break;
                }
              }
              if (found) {
                continue;
              }
            }
          }
          if (!result.episodes.isEmpty()) {
            // something found with the season detection?
            for (int ep : result.episodes) {
              TvShowEpisode episode = new TvShowEpisode();
              if (tvShow.getEpisodeGroup() != null) {
                // the TV show already has an assigned episode group - assign the S/E to the same group
                episode.setEpisode(new MediaEpisodeNumber(tvShow.getEpisodeGroup(), result.season, ep));
              }
              else {
                episode.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, result.season, ep));
              }

              episode.setFirstAired(result.date);
              if (result.name.isEmpty()) {
                result.name = FilenameUtils.getBaseName(vid.getFilename());
              }
              episode.setTitle(TvShowEpisodeAndSeasonParser.cleanEpisodeTitle(result.name, tvShow.getTitle()));
              episode.setPath(vid.getPath());
              episode.setTvShow(tvShow);
              episode.addToMediaFiles(epFiles); // all found EP MFs

              // try to parse the imdb id from the filename
              if (!MediaIdUtil.isValidImdbId(episode.getImdbId())) {
                episode.setId(MediaMetadata.IMDB, ParserUtils.detectImdbId(Utils.relPath(showDir, vid.getFileAsPath()).toString()));
              }
              // try to parse the Tmdb id from the filename
              if (episode.getTmdbId().isEmpty()) {
                episode.setId(MediaMetadata.TMDB, ParserUtils.detectTmdbId(Utils.relPath(showDir, vid.getFileAsPath()).toString()));
              }
              // try to parse the Tvdb id from the filename
              if (episode.getTvdbId().isEmpty()) {
                episode.setId(MediaMetadata.TVDB, ParserUtils.detectTvdbId(Utils.relPath(showDir, vid.getFileAsPath()).toString()));
              }
              if (episode.getMediaSource() == MediaSource.UNKNOWN) {
                episode.setMediaSource(MediaSource.parseMediaSource(vid.getBasename()));
              }
              episode.setNewlyAdded(true);

              // remember the filename the first time the show gets added to tmm
              if (StringUtils.isBlank(episode.getOriginalFilename())) {
                episode.setOriginalFilename(vid.getFilename());
              }

              if (vid.isDiscFile()) {
                episode.setDisc(true);

                // disc files should be inside a discFolder - if we have one, set the path a level higher:
                Path discRoot = vid.getFileAsPath().toAbsolutePath();
                if (discRoot.getFileName().toString().matches(DISC_FOLDER_REGEX)) {
                  // name of video file matches a disc folder? (eg when having already a virtual one)
                  discRoot = discRoot.getParent();
                  episode.setPath(discRoot.toString());
                }
                else if (discRoot.getParent().getFileName().toString().matches(DISC_FOLDER_REGEX)) {
                  // video file not in its dedicated folder
                  discRoot = discRoot.getParent();
                  episode.setPath(discRoot.toString());
                }
                // else keep the current video path as episode root (set above)
              }

              if (result.episodes.size() > 1) {
                episode.setMultiEpisode(true);
              }
              else {
                episode.setMultiEpisode(false);
              }
              episode.merge(vsMetaEP); // merge VSmeta infos

              // force title from xml
              if (xmlEP != null && StringUtils.isNotBlank(xmlEP.getTitle())) {
                episode.merge(xmlEP); // merge XML infos
                episode.setTitle(xmlEP.getTitle());
              }

              episode.saveToDb();
              tvShow.addEpisode(episode);
            }
          }
          else {
            // ******************************
            // STEP 2.1.3 - episode detection found nothing - simply add this
            // video as -1/-1 (or at least with year as season, if detected
            // ******************************
            TvShowEpisode episode = new TvShowEpisode();
            episode.setPath(vid.getPath());

            if (vid.isDiscFile()) {
              episode.setDisc(true);

              // disc files should be inside a discFolder - if we have one, set the path a level higher:
              Path discRoot = vid.getFileAsPath().toAbsolutePath();
              if (discRoot.getFileName().toString().matches(DISC_FOLDER_REGEX)) {
                // name of video file matches a disc folder? (eg when having already a virtual one)
                discRoot = discRoot.getParent();
                episode.setPath(discRoot.toString());
              }
              else if (discRoot.getParent().getFileName().toString().matches(DISC_FOLDER_REGEX)) {
                // video file not in its dedicated folder
                discRoot = discRoot.getParent();
                episode.setPath(discRoot.toString());
              }
              // else keep the current video path as episode root (set above)
            }

            episode.setTitle(TvShowEpisodeAndSeasonParser.cleanEpisodeTitle(FilenameUtils.getBaseName(vid.getFilename()), tvShow.getTitle()));
            episode.setTvShow(tvShow);
            if (result.date != null) {
              // if we have a date, we also have a season (=year)
              episode.setFirstAired(result.date);
              episode.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, result.season, -1));
            }
            episode.addToMediaFiles(epFiles); // all found EP MFs

            // try to parse the imdb id from the filename
            if (!MediaIdUtil.isValidImdbId(episode.getImdbId())) {
              episode.setId(MediaMetadata.IMDB, ParserUtils.detectImdbId(Utils.relPath(showDir, vid.getFileAsPath()).toString()));
            }
            // try to parse the Tmdb id from the filename
            if (episode.getTmdbId().isEmpty()) {
              episode.setId(MediaMetadata.TMDB, ParserUtils.detectTmdbId(Utils.relPath(showDir, vid.getFileAsPath()).toString()));
            }
            // try to parse the Tvdb id from the filename
            if (episode.getTvdbId().isEmpty()) {
              episode.setId(MediaMetadata.TVDB, ParserUtils.detectTvdbId(Utils.relPath(showDir, vid.getFileAsPath()).toString()));
            }
            if (episode.getMediaSource() == MediaSource.UNKNOWN) {
              episode.setMediaSource(MediaSource.parseMediaSource(vid.getBasename()));
            }
            episode.setNewlyAdded(true);

            // remember the filename the first time the show gets added to tmm
            if (StringUtils.isBlank(episode.getOriginalFilename())) {
              episode.setOriginalFilename(vid.getFilename());
            }

            episode.merge(vsMetaEP); // merge VSmeta infos

            // force title from xml
            if (xmlEP != null && StringUtils.isNotBlank(xmlEP.getTitle())) {
              episode.merge(xmlEP); // merge XML infos
              episode.setTitle(xmlEP.getTitle());
            }

            episode.saveToDb();
            tvShow.addEpisode(episode);
          }
        } // end creation of new episodes
        else {
          // ******************************
          // STEP 2.2 - video MF was already found in DB - just add all
          // non-video MFs
          // ******************************
          for (TvShowEpisode episode : episodes) {
            for (MediaFile mf : epFiles) {
              // add all other MFs to the episode which are not VIDEO files or VIDEO files with a different basename
              if (mf.getType() != MediaFileType.VIDEO || (!mf.getBasename().equals(vid.getBasename()))) {
                episode.addToMediaFiles(mf);
              }
            }

            episode.setDisc(vid.isDiscFile());
            if (episodes.size() > 1) {
              episode.setMultiEpisode(true);
            }
            else {
              episode.setMultiEpisode(false);
            }
            episode.saveToDb();
          }
        }
      } // end for all video MFs loop

      // ******************************
      // STEP 3 - now we have a working show/episode object
      // remove all used episode/season MFs, rest must be show MFs ;)
      // ******************************
      mfs.removeAll(tvShow.getEpisodesMediaFiles()); // remove EP files

      for (TvShowSeason season : tvShow.getSeasons()) {
        mfs.removeAll(season.getMediaFiles());
      }

      // tvShow.addToMediaFiles(mfs); // add remaining
      // not so fast - try to parse S/E from remaining first!
      for (MediaFile mf : mfs) {
        // case poster.ext -> do not add to the TV show itself when it is NOT in the TV show root!
        if (mf.getType() == MediaFileType.POSTER && !mf.getPath().equals(tvShow.getPath())) {
          // probably season poster
          mf.setType(MediaFileType.SEASON_POSTER);
        }

        // a season poster/fanart/banner/thumb does not belong to any episode - they need to be added to a TvShowSeason
        if (mf.getType() == MediaFileType.SEASON_POSTER || mf.getType() == MediaFileType.SEASON_FANART || mf.getType() == MediaFileType.SEASON_BANNER
            || mf.getType() == MediaFileType.SEASON_THUMB) {

          String foldername = tvShow.getPathNIO().relativize(mf.getFileAsPath().getParent()).toString();
          int season = TvShowHelpers.detectSeasonFromFileAndFolder(mf.getFilename(), foldername);
          if (season != Integer.MIN_VALUE) {
            TvShowSeason tvShowSeason = tvShow.getOrCreateSeason(season);
            tvShowSeason.addToMediaFiles(mf);
          }

          continue;
        }

        // Only perform episode recognition for video files to avoid unnecessary processing of metadata files
        // (poster.jpg, theme.mp3, tvshow.nfo, etc.)
        if (mf.getType() == MediaFileType.VIDEO) {
          String relativePath = showDir.relativize(mf.getFileAsPath()).toString();
          EpisodeMatchingResult result = TvShowEpisodeAndSeasonParser.detectEpisodeHybrid(relativePath, tvShow.getTitle(), false);
          if (result.season > -1 && !result.episodes.isEmpty()) {
            for (int epnr : result.episodes) {
              // get any assigned episode
              List<TvShowEpisode> eps = tvShow.getEpisode(result.season, epnr);
              if (eps.size() == 1) {
                // just one episode for that S/E found -> we can blindly assign it
                eps.get(0).addToMediaFiles(mf);
              }
              else if (eps.size() > 1) {
                for (TvShowEpisode ep : eps) {
                  String episodeBasenameWoStackingMarker = FilenameUtils.getBaseName(Utils.cleanStackingMarkers(ep.getMainVideoFile().getFilename()));
                  // okay, at least one existing episode found... just check if there is the same base name without stacking markers
                  if (FilenameUtils.getBaseName(Utils.cleanStackingMarkers(mf.getFilename())).startsWith(episodeBasenameWoStackingMarker)) {
                    ep.addToMediaFiles(mf);
                    break;
                  }
                  // or if the mf is in a subfolder with the base name of the video file
                  if (episodeBasenameWoStackingMarker.equals(mf.getFileAsPath().getParent().getFileName().toString())) {
                    ep.addToMediaFiles(mf);
                    break;
                  }
                }
              }
            }
          }
          else {
            // Video file but no episode match found, add to TV show
            tvShow.addToMediaFiles(mf);
          }
        }
        else {
          // Non-video files (poster, fanart, nfo, theme, etc.) - add directly to TV show without AI recognition
          tvShow.addToMediaFiles(mf);
        }
      }

      // remove EP files
      mfs.removeAll(tvShow.getEpisodesMediaFiles());

      // remove season files
      tvShow.getSeasons().forEach(tvShowSeason -> mfs.removeAll(tvShowSeason.getMediaFiles()));

      // now add remaining
      tvShow.addToMediaFiles(mfs);

      // re-evaluate stacking markers & disc folders
      for (TvShowEpisode episode : tvShow.getEpisodes()) {
        episode.reEvaluateDiscfolder();
        episode.reEvaluateStacking();
        episode.saveToDb();
      }

      // if there is missing artwork AND we do have a VSMETA file, we probably can extract an artwork from there
      if (!TvShowModuleManager.getInstance().getSettings().isExtractArtworkFromVsmeta()) {
        // TV show
        boolean missingTvShowPosters = tvShow.getMediaFiles(MediaFileType.POSTER).isEmpty();
        boolean missingTvShowFanarts = tvShow.getMediaFiles(MediaFileType.FANART).isEmpty();

        for (TvShowEpisode episode : tvShow.getEpisodes()) {
          List<MediaFile> episodeVsmetas = episode.getMediaFiles(MediaFileType.VSMETA);
          if (episodeVsmetas.isEmpty()) {
            continue;
          }

          if (episode.getMediaFiles(MediaFileType.THUMB).isEmpty()
              && !TvShowModuleManager.getInstance().getSettings().getSeasonThumbFilenames().isEmpty()) {
            LOGGER.debug("extracting episode THUMBs from VSMETA for {}", episode.getMainFile().getFileAsPath());
            boolean ok = TvShowArtworkHelper.extractArtworkFromVsmeta(episode, episodeVsmetas.get(0), MediaArtwork.MediaArtworkType.THUMB);
            if (ok) {
              episode.saveToDb();
            }
          }

          if (missingTvShowFanarts && !TvShowModuleManager.getInstance().getSettings().getFanartFilenames().isEmpty()) {
            LOGGER.debug("extracting TV show FANARTs from VSMETA for {}", episode.getMainFile().getFileAsPath());
            boolean ok = TvShowArtworkHelper.extractArtworkFromVsmeta(tvShow, episodeVsmetas.get(0), MediaArtwork.MediaArtworkType.BACKGROUND);
            if (ok) {
              missingTvShowFanarts = false;
            }
          }

          if (missingTvShowPosters && !TvShowModuleManager.getInstance().getSettings().getPosterFilenames().isEmpty()) {
            LOGGER.debug("extracting TV show POSTERs from VSMETA for {}", episode.getMainFile().getFileAsPath());
            boolean ok = TvShowArtworkHelper.extractArtworkFromVsmeta(tvShow, episodeVsmetas.get(0), MediaArtwork.MediaArtworkType.POSTER);
            if (ok) {
              missingTvShowPosters = false;
            }
          }
        }
      }

      tvShow.saveToDb();

      return showDir.getFileName().toString();
    }

    /**
     * gets the filename of the MF, reduced by type<br>
     * episode1-poster.jpg -> episode1.jpg<br>
     * also remove named extra files like '*-behinthescenes'
     *
     * @param mf
     *          the {@link MediaFile} to be inspected
     * @return the filename w/o file type in its name
     */
    private String getMediaFileNameWithoutType(MediaFile mf) {
      String ret = mf.getFilename();
      String ext = mf.getExtension();

      // do not use regexp here since they are extremely expensive
      ret = StringUtils.replaceIgnoreCase(ret, "-" + mf.getType() + "." + ext, "." + ext);
      ret = StringUtils.replaceIgnoreCase(ret, "." + mf.getType() + "." + ext, "." + ext);
      ret = StringUtils.replaceIgnoreCase(ret, "_" + mf.getType() + "." + ext, "." + ext);

      // does not work for extrafanarts/landscape - but that's mostly not used on episode level
      for (Pattern pattern : extraMfFiletypePatterns) {
        ret = pattern.matcher(ret).replaceFirst("." + ext);
      }

      return ret;
    }

    /**
     * gets mediaFile of specific type
     *
     * @param mfs
     *          the MF list to search
     * @param types
     *          the MediaFileTypes
     * @return MF or NULL
     */
    private MediaFile getMediaFile(List<MediaFile> mfs, MediaFileType... types) {
      MediaFile mf = null;
      for (MediaFile mediaFile : mfs) {
        boolean match = false;
        for (MediaFileType type : types) {
          if (mediaFile.getType().equals(type)) {
            match = true;
          }
        }
        if (match) {
          mf = new MediaFile(mediaFile);
        }
      }
      return mf;
    }

    /**
     * gets all mediaFiles of specific type
     *
     * @param mfs
     *          the MF list to search
     * @param types
     *          the MediaFileTypes
     * @return list of matching MFs
     */
    private List<MediaFile> getMediaFiles(List<MediaFile> mfs, MediaFileType... types) {
      List<MediaFile> mf = new ArrayList<>();
      for (MediaFile mediaFile : mfs) {
        boolean match = false;
        for (MediaFileType type : types) {
          if (mediaFile.getType().equals(type)) {
            match = true;
          }
        }
        if (match) {
          mf.add(new MediaFile(mediaFile));
        }
      }
      return mf;
    }

    /**
     * gets all files recursive
     * 
     * @param path
     *          the folder to search for
     * @return a {@link Set} of all found {@link Path}s
     */
    private Set<Path> getAllFilesRecursive(Path path) {
      Path folder = path.toAbsolutePath();
      AllFilesRecursive visitor = new AllFilesRecursive();
      try {
        Files.walkFileTree(folder, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor);
      }
      catch (IOException e) {
        // can not happen, since we've overridden visitFileFailed, which throws no exception ;)
      }

      List<Path> filesFound = new ArrayList<>();
      visitor.filesPerDir.forEach((key, value) -> filesFound.addAll(value));

      return new TreeSet<>(filesFound);
    }
  }

  @Override
  public void callback(Object obj) {
    // do not publish task description here, because with different workers the
    // text is never right
    publishState(progressDone);
  }

  /**
   * simple NIO File.listFiles() replacement<br>
   * returns all files & folders in specified dir (NOT recursive)
   *
   * @param directory
   *          the folder to list the items for
   * @return list of files&folders
   */
  private List<Path> listFilesAndDirs(Path directory) {
    List<Path> fileNames = new ArrayList<>();
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
      for (Path path : directoryStream) {
        if (isInSkipFolder(path)) {
          LOGGER.debug("Skipping: {}", path);
        }
        else {
          fileNames.add(path.toAbsolutePath());
        }
      }
    }
    catch (IOException e) {
      LOGGER.error("Error while getting a file listing of '{}' - '{}'", directory, e.getMessage());
      LOGGER.debug("could not list files in normal way", e);
      fileNames = listFilesAndDirs2(directory);
    }

    // return sorted
    Collections.sort(fileNames);

    return fileNames;
  }

  /**
   * check if the given folder is a skip folder
   *
   * @param dir
   *          the folder to check
   * @return true/false
   */
  private boolean isInSkipFolder(Path dir) {
    if (dir == null || dir.getFileName() == null) {
      return false;
    }

    String dirName = dir.getFileName().toString();
    String dirNameUppercase = dirName.toUpperCase(Locale.ROOT);
    String fullPath = dir.toAbsolutePath().toString();

    // hard coded skip folders
    if (SKIP_FOLDERS.contains(dirNameUppercase) || dirName.matches(SKIP_REGEX)) {
      return true;
    }

    // skip folders from regexp
    for (Pattern pattern : skipFolders) {
      Matcher matcher = pattern.matcher(dirName);
      if (matcher.matches()) {
        return true;
      }

      // maybe the regexp is a full path
      if (pattern.toString().replace("\\Q", "").replace("\\E", "").equals(fullPath)) {
        return true;
      }
    }

    return false;
  }

  /**
   * simple NIO File.listFiles() replacement<br>
   * returns all folders in specified dir (NOT recursive)
   * 
   * @param directory
   *          the folder to list the items for
   * @return list of files&folders
   */
  private List<Path> listFilesAndDirs2(Path directory) {
    List<Path> fileNames = new ArrayList<>();
    try (Stream<Path> directoryStream = Files.walk(directory, 1, FileVisitOption.FOLLOW_LINKS)) {
      List<Path> allElements = directoryStream.filter(Files::isDirectory).toList();
      for (Path path : allElements) {
        if (directory.toAbsolutePath().equals(path.toAbsolutePath())) {
          continue;
        }
        String fn = path.getFileName().toString().toUpperCase(Locale.ROOT);
        if (!SKIP_FOLDERS.contains(fn) && !fn.matches(SKIP_REGEX) && !isInSkipFolder(path)) {
          fileNames.add(path.toAbsolutePath());
        }
        else {
          LOGGER.debug("Skipping: {}", path);
        }
      }
    }
    catch (Exception e) {
      LOGGER.error("Error while getting a file listing of '{}' (alternate way) - '{}'", directory, e.getMessage());
      LOGGER.debug("could not list files in alternate way", e);
    }

    // return sorted
    Collections.sort(fileNames);

    return fileNames;
  }

  private class AllFilesRecursive extends AbstractFileVisitor {
    private final List<String>  skipFiles;

    final Map<Path, List<Path>> filesPerDir = new HashMap<>();

    public AllFilesRecursive() {
      skipFiles = new ArrayList<>(SKIP_FILES);
      if (!TvShowModuleManager.getInstance().getSettings().isSkipFoldersWithNomedia()) {
        skipFiles.remove(".nomedia");
      }
    }

    @NotNull
    @Override
    public FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attr) {
      if (cancel) {
        return TERMINATE;
      }

      if (file.getFileName() == null) {
        return CONTINUE;
      }

      fileLock.writeLock().lock();
      fileAttributes.put(file, attr);
      fileLock.writeLock().unlock();

      try {
        String filename = file.getFileName().toString();
        Path parent = file.getParent();

        List<Path> filesInCurrentDir = filesPerDir.get(parent);

        // abort if one of the skip files is found
        if (skipFiles.contains(filename)) {
          filesInCurrentDir.add(file.toAbsolutePath());
          return SKIP_SIBLINGS;
        }

        incVisFile();

        String path = "";
        if (parent != null && parent.getFileName() != null) {
          path = parent.getFileName().toString();
        }

        // in a disc folder we only accept NFO files
        if (Utils.isRegularFile(attr) && path.matches(DISC_FOLDER_REGEX)) {
          if (FilenameUtils.getExtension(filename).equalsIgnoreCase("nfo")) {
            filesInCurrentDir.add(file.toAbsolutePath());
            // fFound.add(file.toAbsolutePath());
          }
          return CONTINUE;
        }

        // check if we're in dirty disc folder
        if (MediaFileHelper.isMainDiscIdentifierFile(filename)) {
          filesInCurrentDir.add(file.toAbsolutePath());
          // fFound.add(file.toAbsolutePath());
          return CONTINUE;
        }

        if (Utils.isRegularFile(attr) && !filename.matches(SKIP_REGEX)) {
          filesInCurrentDir.add(file.toAbsolutePath());
          // fFound.add(file.toAbsolutePath());
          return CONTINUE;
        }
      }
      catch (Exception e) {
        LOGGER.debug("could not analyze file '{}' - '{}'", file.toAbsolutePath(), e.getMessage());
      }

      return CONTINUE;
    }

    @NotNull
    @Override
    public FileVisitResult preVisitDirectory(Path dir, @NotNull BasicFileAttributes attrs) {
      if (cancel) {
        return TERMINATE;
      }

      incPreDir();

      filesPerDir.put(dir, new ArrayList<>());

      try {
        // getFilename returns null on DS root!
        if (dir.getFileName() != null && isInSkipFolder(dir)) {// || containsSkipFile(dir))) {
          LOGGER.debug("Skipping dir: {}", dir);
          return SKIP_SUBTREE;
        }

        // add the disc folder itself (clean disc folder)
        if (dir.getFileName() != null && dir.getFileName().toString().matches(DISC_FOLDER_REGEX)) {
          filesPerDir.get(dir).add(dir);
          // fFound.add(dir.toAbsolutePath());
          return CONTINUE;
        }

        // don't go below a disc folder
        if (dir.getParent() != null && dir.getParent().getFileName() != null && dir.getParent().getFileName().toString().matches(DISC_FOLDER_REGEX)) {
          return SKIP_SUBTREE;
        }
      }
      catch (Exception e) {
        LOGGER.debug("could not analyze folder '{}' - '{}'", dir.toAbsolutePath(), e.getMessage());
      }

      return CONTINUE;
    }

    @NotNull
    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
      if (cancel) {
        return TERMINATE;
      }

      List<Path> filesInCurrentDir = filesPerDir.get(dir);
      boolean skipFound = false;

      // check if any skip file has been found
      for (Path file : ListUtils.nullSafe(filesInCurrentDir)) {
        if (skipFiles.contains(file.getFileName().toString())) {
          // skip file found -> do not add this folder or any children
          skipFound = true;
          break;
        }
      }

      if (skipFound) {
        // as skip file has been found -> remove this folder and all children from the found files
        Set<Path> keys = new TreeSet<>(filesPerDir.keySet());
        for (Path key : keys) {
          if (key.startsWith(dir)) {
            filesPerDir.remove(key);
          }
        }
      }

      incPostDir();

      return CONTINUE;
    }
  }

  private static void resetCounters() {
    visFile = 0;
    preDir = 0;
    postDir = 0;
  }

  /**
   * synchronized increment of visFile
   */
  private static synchronized void incVisFile() {
    visFile++;
  }

  /**
   * synchronized increment of preDir
   */
  private static synchronized void incPreDir() {
    preDir++;
  }

  /**
   * synchronized increment of postDir
   */
  private static synchronized void incPostDir() {
    postDir++;
  }

  /**
   * 处理批量AI识别
   */
  private void processBatchAIRecognition() {
    // 并发安全检查
    synchronized (batchProcessingLock) {
      if (batchProcessingInProgress) {
        LOGGER.warn("Batch AI recognition already in progress, skipping");
        return;
      }
      batchProcessingInProgress = true;
    }

    try {
      List<PendingAIRecognition> toProcess;

      // 获取待处理列表的副本，避免长时间锁定
      synchronized (pendingAIRecognitions) {
        if (pendingAIRecognitions.isEmpty()) {
          LOGGER.debug("No files need AI recognition");
          return;
        }

        toProcess = new ArrayList<>(pendingAIRecognitions);
        pendingAIRecognitions.clear(); // 立即清理，避免内存泄漏
      }

      // 配置一致性检查
      if (!isAIConfigurationValid()) {
        LOGGER.warn("AI configuration is invalid, falling back to traditional processing");
        handleFailedAIRecognitions(toProcess);
        return;
      }

      long startTime = System.currentTimeMillis();
      processBatchAIRecognitionInternal(toProcess);
      long endTime = System.currentTimeMillis();

      // 记录性能指标
      long processingTime = endTime - startTime;
      LOGGER.info("Batch AI processing completed in {}ms", processingTime);
      LOGGER.info(aiMetrics.getMetricsReport());

    } finally {
      synchronized (batchProcessingLock) {
        batchProcessingInProgress = false;
      }
    }
  }

  /**
   * 检查AI配置是否有效，支持热更新
   */
  private boolean isAIConfigurationValid() {
    try {
      // 检查配置是否有更新
      AIConfigurationSnapshot newConfig = new AIConfigurationSnapshot();

      synchronized (configUpdateLock) {
        if (currentAIConfig == null || newConfig.hasChanged(currentAIConfig)) {
          LOGGER.info("AI configuration updated: {} -> {}", currentAIConfig, newConfig);
          currentAIConfig = newConfig;

          // 配置更新时清理相关缓存
          if (newConfig.hasChanged(currentAIConfig)) {
            clearAIRelatedCaches();
          }
        }
      }

      return currentAIConfig.aiEnabled &&
             currentAIConfig.apiKey != null && !currentAIConfig.apiKey.trim().isEmpty() &&
             currentAIConfig.apiUrl != null && !currentAIConfig.apiUrl.trim().isEmpty();
    } catch (Exception e) {
      LOGGER.error("Failed to check AI configuration: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 清理AI相关的缓存（配置更新时）
   */
  private void clearAIRelatedCaches() {
    try {
      // 清理AI识别相关的缓存条目
      TvShowEpisodeAndSeasonParser.clearAICache();
      LOGGER.info("AI-related caches cleared due to configuration update");
    } catch (Exception e) {
      LOGGER.warn("Failed to clear AI caches: {}", e.getMessage());
    }
  }

  /**
   * 内部批量AI识别处理
   */
  private void processBatchAIRecognitionInternal(List<PendingAIRecognition> toProcess) {

    LOGGER.info("Starting batch AI recognition for {} files", toProcess.size());

    // 启动进度反馈
    progressReporter.startBatchProcessing(toProcess.size());

    // 检查内存压力，自适应调整批量大小
    int batchSize = calculateOptimalBatchSize(toProcess.size());
    if (batchSize < toProcess.size()) {
      LOGGER.info("Memory pressure detected, processing in batches of {}", batchSize);
      processBatchesWithMemoryManagement(toProcess, batchSize);
      return;
    }

    int successCount = 0;
    int failureCount = 0;
    List<PendingAIRecognition> failedRecognitions = new ArrayList<>();

    try {
      // 创建临时剧集对象用于批量AI识别
      List<TvShowEpisode> tempEpisodes = createTempEpisodesForAI(toProcess);

      // 调用批量AI识别服务，带重试机制
      BatchChatGPTEpisodeRecognitionService batchService = new BatchChatGPTEpisodeRecognitionService();
      Map<String, TvShowEpisodeAndSeasonParser.EpisodeMatchingResult> results =
          callBatchAIWithRetry(batchService, tempEpisodes, 3);

      // 处理批量AI识别的结果
      for (int i = 0; i < toProcess.size(); i++) {
        PendingAIRecognition pending = toProcess.get(i);
        TvShowEpisode tempEpisode = tempEpisodes.get(i);

        // 更新进度：正在处理文件
        progressReporter.updateProgress(pending.relativePath, "Processing AI Result");

        try {
          String episodeId = tempEpisode.getDbId().toString();
          TvShowEpisodeAndSeasonParser.EpisodeMatchingResult aiResult = results.get(episodeId);

          if (aiResult != null && aiResult.season > 0 && !aiResult.episodes.isEmpty()) {
            // AI识别成功，应用结果到实际的剧集处理
            progressReporter.updateProgress(pending.relativePath, "Applying AI Result");
            applyAIResultToEpisode(pending.tvShow, pending.relativePath, aiResult, pending.videoFile);
            progressReporter.fileCompleted(pending.relativePath, true);
            successCount++;
          } else {
            // AI识别失败，记录失败的文件
            failedRecognitions.add(pending);
            progressReporter.fileCompleted(pending.relativePath, false);
            failureCount++;
          }
        } catch (Exception e) {
          LOGGER.error("Failed to apply AI result for {}: {}", pending.relativePath, e.getMessage(), e);
          failedRecognitions.add(pending);
          progressReporter.fileCompleted(pending.relativePath, false);
          failureCount++;
        }
      }

    } catch (Exception e) {
      LOGGER.error("Batch AI recognition service failed: {}", e.getMessage(), e);

      // 如果批量服务完全失败，所有文件都算失败
      failedRecognitions.addAll(toProcess);
      failureCount = toProcess.size();
    }

    // 记录处理结果
    LOGGER.info("Batch AI recognition completed: {} successful, {} failed", successCount, failureCount);

    // 完成进度反馈
    progressReporter.batchCompleted();

    // 发送最终报告消息
    String finalReport = progressReporter.getFinalReport();
    MessageManager.getInstance().pushMessage(
        new Message(MessageLevel.INFO, TmmResourceBundle.getString("ai.batch.recognition"), finalReport));

    // 发送处理结果消息
    if (successCount > 0) {
      String successMsg = String.format("批量AI识别完成: 成功识别 %d 个文件", successCount);
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.INFO, "批量AI识别", successMsg));
    }

    if (failureCount > 0) {
      String failureMsg = String.format("批量AI识别: %d 个文件识别失败", failureCount);
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.WARN, "批量AI识别", failureMsg));

      // 对失败的文件进行回退处理
      handleFailedAIRecognitions(failedRecognitions);
    }
  }

  /**
   * 为批量AI识别创建临时剧集对象
   */
  private List<TvShowEpisode> createTempEpisodesForAI(List<PendingAIRecognition> pendingList) {
    List<TvShowEpisode> tempEpisodes = new ArrayList<>();

    for (PendingAIRecognition pending : pendingList) {
      TvShowEpisode tempEpisode = new TvShowEpisode();
      tempEpisode.setTvShow(pending.tvShow);
      tempEpisode.setPath(pending.videoFile.getPath());

      // 确保正确设置媒体文件，BatchChatGPTEpisodeRecognitionService依赖getMainFile()
      tempEpisode.addToMediaFiles(pending.videoFile);

      // 验证主文件设置是否正确
      if (tempEpisode.getMainFile() == null) {
        LOGGER.warn("Main file not set correctly for: {}", pending.relativePath);
        // 手动设置标题作为备用
        tempEpisode.setTitle(pending.relativePath);
      }

      // 使用确定性的UUID生成，基于唯一ID
      java.util.UUID deterministicId = java.util.UUID.nameUUIDFromBytes(pending.uniqueId.getBytes());
      tempEpisode.setDbId(deterministicId);

      tempEpisodes.add(tempEpisode);
    }

    return tempEpisodes;
  }

  /**
   * 计算最优批量大小，基于内存压力
   */
  private int calculateOptimalBatchSize(int totalFiles) {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long usedMemory = totalMemory - freeMemory;

    // 计算内存使用率
    double memoryUsageRatio = (double) usedMemory / maxMemory;

    LOGGER.debug("Memory status: used={}MB, total={}MB, max={}MB, usage={:.1f}%",
                usedMemory / 1024 / 1024, totalMemory / 1024 / 1024,
                maxMemory / 1024 / 1024, memoryUsageRatio * 100);

    // 根据内存压力调整批量大小
    if (memoryUsageRatio > 0.8) {
      // 高内存压力：小批量处理
      return Math.min(totalFiles, 10);
    } else if (memoryUsageRatio > 0.6) {
      // 中等内存压力：中等批量
      return Math.min(totalFiles, 25);
    } else {
      // 低内存压力：正常批量处理
      return totalFiles;
    }
  }

  /**
   * 分批处理，带内存管理
   */
  private void processBatchesWithMemoryManagement(List<PendingAIRecognition> allFiles, int batchSize) {
    int totalBatches = (int) Math.ceil((double) allFiles.size() / batchSize);
    int successCount = 0;
    int failureCount = 0;
    List<PendingAIRecognition> allFailedRecognitions = new ArrayList<>();

    for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
      int startIndex = batchIndex * batchSize;
      int endIndex = Math.min(startIndex + batchSize, allFiles.size());
      List<PendingAIRecognition> batch = allFiles.subList(startIndex, endIndex);

      LOGGER.info("Processing batch {}/{}: {} files", batchIndex + 1, totalBatches, batch.size());

      // 在每个批次前进行垃圾回收，释放内存
      if (batchIndex > 0) {
        System.gc();
        try {
          Thread.sleep(100); // 给GC一些时间
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

      // 处理当前批次
      int[] batchResults = processSingleBatch(batch);
      successCount += batchResults[0];
      failureCount += batchResults[1];

      // 收集失败的识别
      // 这里需要从batch中筛选失败的项目，简化处理
    }

    LOGGER.info("Batch processing completed: {} successful, {} failed", successCount, failureCount);

    // 记录性能指标（在分批处理中会单独记录）

    // 发送处理结果消息
    if (successCount > 0) {
      String successMsg = String.format(TmmResourceBundle.getString("ai.batch.recognition.completed"), successCount);
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.INFO, TmmResourceBundle.getString("ai.batch.recognition"), successMsg));
    }

    if (failureCount > 0) {
      String failureMsg = String.format(TmmResourceBundle.getString("ai.batch.recognition.failed"), failureCount);
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.WARN, TmmResourceBundle.getString("ai.batch.recognition"), failureMsg));
    }
  }

  /**
   * 处理单个批次
   * @return [成功数量, 失败数量]
   */
  private int[] processSingleBatch(List<PendingAIRecognition> batch) {
    int successCount = 0;
    int failureCount = 0;

    try {
      // 创建临时剧集对象用于批量AI识别
      List<TvShowEpisode> tempEpisodes = createTempEpisodesForAI(batch);

      // 调用批量AI识别服务
      BatchChatGPTEpisodeRecognitionService batchService = new BatchChatGPTEpisodeRecognitionService();
      Map<String, TvShowEpisodeAndSeasonParser.EpisodeMatchingResult> results =
          callBatchAIWithRetry(batchService, tempEpisodes, 3);

      // 处理批量AI识别的结果
      for (int i = 0; i < batch.size(); i++) {
        PendingAIRecognition pending = batch.get(i);
        TvShowEpisode tempEpisode = tempEpisodes.get(i);

        try {
          String episodeId = tempEpisode.getDbId().toString();
          TvShowEpisodeAndSeasonParser.EpisodeMatchingResult aiResult = results.get(episodeId);

          if (aiResult != null && aiResult.season > 0 && !aiResult.episodes.isEmpty()) {
            // AI识别成功，应用结果到实际的剧集处理
            applyAIResultToEpisode(pending.tvShow, pending.relativePath, aiResult, pending.videoFile);
            successCount++;
          } else {
            // AI识别失败
            failureCount++;
          }
        } catch (Exception e) {
          LOGGER.error("Failed to apply AI result for {}: {}", pending.relativePath, e.getMessage(), e);
          failureCount++;
        }
      }

    } catch (Exception e) {
      LOGGER.error("Batch processing failed: {}", e.getMessage(), e);
      failureCount = batch.size(); // 整个批次失败
    }

    return new int[]{successCount, failureCount};
  }

  /**
   * 带重试机制的批量AI调用
   */
  private Map<String, TvShowEpisodeAndSeasonParser.EpisodeMatchingResult> callBatchAIWithRetry(
      BatchChatGPTEpisodeRecognitionService batchService,
      List<TvShowEpisode> tempEpisodes,
      int maxRetries) {

    Exception lastException = null;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        LOGGER.debug("Batch AI recognition attempt {}/{}", attempt, maxRetries);

        Map<String, TvShowEpisodeAndSeasonParser.EpisodeMatchingResult> results =
            batchService.batchRecognizeEpisodes(tempEpisodes);

        if (results != null && !results.isEmpty()) {
          LOGGER.info("Batch AI recognition successful on attempt {}", attempt);
          return results;
        } else {
          LOGGER.warn("Batch AI recognition returned empty results on attempt {}", attempt);
        }

      } catch (Exception e) {
        lastException = e;

        // 分析异常类型，决定是否重试
        boolean shouldRetry = shouldRetryOnException(e);
        if (!shouldRetry) {
          LOGGER.error("Non-retryable error occurred: {}", e.getMessage());
          break;
        }

        LOGGER.warn("Batch AI recognition failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

        if (attempt < maxRetries) {
          // 根据异常类型调整重试延迟
          long delayMs = calculateRetryDelay(e, attempt);
          try {
            LOGGER.debug("Retrying after {}ms delay", delayMs);
            Thread.sleep(delayMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Retry delay interrupted");
            break;
          }
        }
      }
    }

    // 所有重试都失败
    LOGGER.error("Batch AI recognition failed after {} attempts", maxRetries, lastException);
    return new java.util.HashMap<>(); // 返回空结果
  }

  /**
   * 判断异常是否应该重试
   */
  private boolean shouldRetryOnException(Exception e) {
    String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
    String exceptionType = e.getClass().getSimpleName().toLowerCase();

    // 网络相关异常，应该重试
    if (exceptionType.contains("timeout") ||
        exceptionType.contains("connect") ||
        exceptionType.contains("socket") ||
        errorMessage.contains("timeout") ||
        errorMessage.contains("connection") ||
        errorMessage.contains("network")) {
      return true;
    }

    // HTTP状态码相关的临时错误，应该重试
    if (errorMessage.contains("500") || // 服务器内部错误
        errorMessage.contains("502") || // 网关错误
        errorMessage.contains("503") || // 服务不可用
        errorMessage.contains("504") || // 网关超时
        errorMessage.contains("429")) { // 请求过多
      return true;
    }

    // 认证错误、权限错误等，不应该重试
    if (errorMessage.contains("401") || // 未授权
        errorMessage.contains("403") || // 禁止访问
        errorMessage.contains("400") || // 请求错误
        errorMessage.contains("invalid") ||
        errorMessage.contains("unauthorized")) {
      return false;
    }

    // 默认重试其他未知错误
    return true;
  }

  /**
   * 根据异常类型计算重试延迟
   */
  private long calculateRetryDelay(Exception e, int attempt) {
    String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

    // 基础指数退避延迟
    long baseDelay = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...

    // 根据异常类型调整延迟
    if (errorMessage.contains("429") || errorMessage.contains("rate limit")) {
      // 频率限制错误，使用更长的延迟
      return baseDelay * 3;
    } else if (errorMessage.contains("timeout")) {
      // 超时错误，使用中等延迟
      return baseDelay * 2;
    } else {
      // 其他错误，使用标准延迟
      return baseDelay;
    }
  }

  /**
   * 处理AI识别失败的文件
   */
  private void handleFailedAIRecognitions(List<PendingAIRecognition> failedRecognitions) {
    if (failedRecognitions.isEmpty()) {
      return;
    }

    LOGGER.info("Handling {} failed AI recognitions with fallback processing", failedRecognitions.size());

    for (PendingAIRecognition failed : failedRecognitions) {
      try {
        // 回退到传统解析结果，即使不完整也要创建剧集
        TvShowEpisodeAndSeasonParser.EpisodeMatchingResult fallbackResult =
            TvShowEpisodeAndSeasonParser.detectEpisodeFromFilename(failed.relativePath, failed.tvShow.getTitle());

        // 创建剧集，即使没有季数和集数信息
        TvShowEpisode episode = new TvShowEpisode();
        episode.setTvShow(failed.tvShow);
        episode.setPath(failed.videoFile.getPath());
        episode.addToMediaFiles(failed.videoFile);

        // 设置标题
        String title = !fallbackResult.name.isEmpty() ?
            TvShowEpisodeAndSeasonParser.cleanEpisodeTitle(fallbackResult.name, failed.tvShow.getTitle()) :
            TvShowEpisodeAndSeasonParser.cleanEpisodeTitle(FilenameUtils.getBaseName(failed.videoFile.getFilename()), failed.tvShow.getTitle());
        episode.setTitle(title);

        // 如果有部分信息，设置季数和集数
        if (fallbackResult.season > 0 && !fallbackResult.episodes.isEmpty()) {
          episode.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, fallbackResult.season, fallbackResult.episodes.get(0)));
        }

        // 添加到电视剧
        failed.tvShow.addEpisode(episode);

        LOGGER.debug("Created fallback episode for: {}", failed.relativePath);

      } catch (Exception e) {
        LOGGER.error("Failed to create fallback episode for {}: {}", failed.relativePath, e.getMessage(), e);
      }
    }
  }

  /**
   * 将AI识别结果应用到实际的剧集处理（带事务保护）
   */
  private void applyAIResultToEpisode(TvShow tvShow, String relativePath,
                                     TvShowEpisodeAndSeasonParser.EpisodeMatchingResult aiResult,
                                     MediaFile videoFile) {
    try {
      LOGGER.info("Applying AI result for {}: S{}E{}", relativePath, aiResult.season, aiResult.episodes.get(0));

      // 检查是否已存在相同的剧集（避免重复添加）
      List<TvShowEpisode> existingEpisodes = tvShow.getEpisode(aiResult.season, aiResult.episodes.get(0));
      if (!existingEpisodes.isEmpty()) {
        LOGGER.warn("Episode S{}E{} already exists for {}, skipping AI result application",
                   aiResult.season, aiResult.episodes.get(0), tvShow.getTitle());
        return;
      }

      // 创建真正的剧集对象
      TvShowEpisode episode = new TvShowEpisode();
      episode.setTvShow(tvShow);
      episode.setPath(videoFile.getPath());

      // 设置季数和集数
      episode.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, aiResult.season, aiResult.episodes.get(0)));

      // 设置标题
      if (!aiResult.name.isEmpty()) {
        episode.setTitle(TvShowEpisodeAndSeasonParser.cleanEpisodeTitle(aiResult.name, tvShow.getTitle()));
      } else {
        episode.setTitle(TvShowEpisodeAndSeasonParser.cleanEpisodeTitle(FilenameUtils.getBaseName(videoFile.getFilename()), tvShow.getTitle()));
      }

      // 添加媒体文件
      episode.addToMediaFiles(videoFile);

      // 事务性添加到电视剧（同步操作，确保数据一致性）
      synchronized (tvShow) {
        tvShow.addEpisode(episode);
        // 立即保存到数据库，确保数据持久化
        episode.saveToDb();
      }

      // 发送AI识别成功消息
      String successMsg = String.format(TmmResourceBundle.getString("ai.recognition.success"),
          FilenameUtils.getBaseName(videoFile.getFilename()), aiResult.season, aiResult.episodes.get(0));
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.INFO, TmmResourceBundle.getString("ai.recognition"), successMsg));

    } catch (Exception e) {
      LOGGER.error("Failed to apply AI result for {}: {}", relativePath, e.getMessage(), e);

      // 发送错误消息
      String errorMsg = String.format(TmmResourceBundle.getString("ai.recognition.apply.failed"),
          FilenameUtils.getBaseName(videoFile.getFilename()), e.getMessage());
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.ERROR, TmmResourceBundle.getString("ai.recognition"), errorMsg));
    }
  }

  /**
   * helper class just do inject the file name in the task description
   */
  private class TvShowMediaFileInformationFetcherTask extends MediaFileInformationFetcherTask {
    public TvShowMediaFileInformationFetcherTask(MediaFile mediaFile, MediaEntity mediaEntity, boolean forceUpdate) {
      super(mediaFile, mediaEntity, forceUpdate);
    }

    @Override
    public void run() {
      // pass the filename to the task description
      publishState(mediaEntity.getTitle() + " - " + mediaFile.getFilename());
      super.run();
    }
  }
}
