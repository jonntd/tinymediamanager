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
package org.tinymediamanager.core.tvshow;

import static org.tinymediamanager.core.MediaFileType.SEASON_BANNER;
import static org.tinymediamanager.core.MediaFileType.SEASON_FANART;
import static org.tinymediamanager.core.MediaFileType.SEASON_POSTER;
import static org.tinymediamanager.core.MediaFileType.SEASON_THUMB;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.IFileNaming;
import org.tinymediamanager.core.ImageCache;
import org.tinymediamanager.core.LanguageStyle;
import org.tinymediamanager.core.MediaFileHelper;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;
import org.tinymediamanager.core.webdav.WebDavFileOperations;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaEntityFilenameHistory;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaFileSubtitle;
import org.tinymediamanager.core.jmte.JmteUtils;
import org.tinymediamanager.core.jmte.NamedArrayRenderer;
import org.tinymediamanager.core.jmte.NamedArrayUniqueRenderer;
import org.tinymediamanager.core.jmte.NamedBitrateRenderer;
import org.tinymediamanager.core.jmte.NamedDateRenderer;
import org.tinymediamanager.core.jmte.NamedFilesizeRenderer;
import org.tinymediamanager.core.jmte.NamedFramerateRenderer;
import org.tinymediamanager.core.jmte.NamedLowerCaseRenderer;
import org.tinymediamanager.core.jmte.NamedNumberRenderer;
import org.tinymediamanager.core.jmte.NamedReplacementRenderer;
import org.tinymediamanager.core.jmte.NamedSplitRenderer;
import org.tinymediamanager.core.jmte.NamedTitleCaseRenderer;
import org.tinymediamanager.core.jmte.NamedUpperCaseRenderer;
import org.tinymediamanager.core.jmte.PathRenderer;
import org.tinymediamanager.core.jmte.RegexpProcessor;
import org.tinymediamanager.core.jmte.TmmModelAdaptor;
import org.tinymediamanager.core.jmte.TmmOutputAppender;
import org.tinymediamanager.core.jmte.ZeroNumberRenderer;
import org.tinymediamanager.core.threading.ThreadUtils;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.core.tvshow.filenaming.TvShowEpisodeThumbNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowExtraFanartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonBannerNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonFanartNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonNfoNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonPosterNaming;
import org.tinymediamanager.core.tvshow.filenaming.TvShowSeasonThumbNaming;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.StrgUtils;

import com.floreysoft.jmte.Engine;
import com.floreysoft.jmte.NamedRenderer;
import com.floreysoft.jmte.RenderFormatInfo;
import com.floreysoft.jmte.extended.ChainedNamedRenderer;
import com.floreysoft.jmte.message.DefaultErrorHandler;
import com.floreysoft.jmte.message.ErrorMessage;
import com.floreysoft.jmte.message.ParseException;
import com.floreysoft.jmte.message.ResourceBundleMessage;
import com.floreysoft.jmte.token.Token;

/**
 * The TvShowRenamer Works on per MediaFile basis
 * 
 * @author Myron Boyle
 */
public class TvShowRenamer {
  private static final Logger              LOGGER              = LoggerFactory.getLogger(TvShowRenamer.class);
  private static final Map<String, String> TOKEN_MAP           = createTokenMap();

  private static final String[]            seasonNumbers       = { "seasonNr", "seasonNr2", "seasonNrDvd", "seasonNrDvd2", "episode.season",
      "episode.dvdSeason" };
  private static final String[]            episodeNumbers      = { "episodeNr", "episodeNr2", "episodeNrDvd", "episodeNrDvd2", "episode.episode",
      "episode.dvdEpisode", "absoluteNr", "absoluteNr2", "episode.absoluteNumber" };
  private static final String[]            episodeTitles       = { "title", "originalTitle", "englishTitle", "titleSortable", "episode.title",
      "episode.originalTitle", "episode.titleSortable", "episode.englishTitle" };
  private static final String[]            episodeAired        = { "airedDate", "episode.firstAired" };

  private static final Pattern             epDelimiter         = Pattern.compile("(\\s?(folge|episode|[epx]+)\\s?)\\$\\{.*?\\}",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern             seDelimiter         = Pattern.compile("((staffel|season|s)\\s?)\\$\\{.*?\\}", Pattern.CASE_INSENSITIVE);

  private static final List<String>        DISC_FOLDERS        = Arrays.asList("bdmv", "video_ts", "hvdvd_ts");
  private static final Pattern             MF_STACKING_PATTERN = Pattern.compile(".*?([\\s._-]\\d)$");

  private TvShowRenamer() {
    throw new IllegalAccessError();
  }

  /**
   * initialize the token map for the renamer
   *
   * @return the token map
   */
  private static Map<String, String> createTokenMap() {
    Map<String, String> tokenMap = new HashMap<>();
    // TV show tags
    tokenMap.put("showTitle", "tvShow.title");
    tokenMap.put("showOriginalTitle", "tvShow.originalTitle");
    tokenMap.put("showEnglishTitle", "tvShow.englishTitle");
    tokenMap.put("showTitleSortable", "tvShow.titleSortable");
    tokenMap.put("showYear", "tvShow.year");
    tokenMap.put("parent", "tvShow.parent");
    tokenMap.put("showNote", "tvShow.note");
    tokenMap.put("showStatus", "tvShow.status");
    tokenMap.put("showRating", "tvShow.rating.rating");
    tokenMap.put("showImdb", "tvShow.imdbId");
    tokenMap.put("showTmdb", "tvShow.tmdbId");
    tokenMap.put("showTvdb", "tvShow.tvdbId");
    tokenMap.put("showCertification", "tvShow.certification");
    tokenMap.put("showTags", "tvShow.tags");
    tokenMap.put("showGenres", "tvShow.genres");
    tokenMap.put("showGenresAsString", "tvShow.genresAsString");
    tokenMap.put("showProductionCompany", "tvShow.productionCompany");
    tokenMap.put("showProductionCompanyAsArray", "tvShow.productionCompanyAsArray");

    // Season tags
    tokenMap.put("seasonName", "season.title");

    // episode tags
    tokenMap.put("episodeNr", "episode.episode");
    tokenMap.put("episodeNr2", "episode.episode;number(%02d)");
    tokenMap.put("episodeNrAired", "episode.airedEpisode");
    tokenMap.put("episodeNrAired2", "episode.airedEpisode;number(%02d)");
    tokenMap.put("episodeNrDvd", "episode.dvdEpisode");
    tokenMap.put("episodeNrDvd2", "episode.dvdEpisode;number(%02d)");
    tokenMap.put("seasonNr", "episode.season;number(%d)");
    tokenMap.put("seasonNr2", "episode.season;number(%02d)");
    tokenMap.put("seasonNrAired", "episode.airedSeason;number(%d)");
    tokenMap.put("seasonNrAired2", "episode.airedSeason;number(%02d)");
    tokenMap.put("seasonNrDvd", "episode.dvdSeason;number(%d)");
    tokenMap.put("seasonNrDvd2", "episode.dvdSeason;number(%02d)");
    tokenMap.put("absoluteNr", "episode.absoluteNumber;number(%d)");
    tokenMap.put("absoluteNr2", "episode.absoluteNumber;number(%02d)");
    tokenMap.put("title", "episode.title");
    tokenMap.put("originalTitle", "episode.originalTitle");
    tokenMap.put("englishTitle", "episode.englishTitle");
    tokenMap.put("originalFilename", "episode.originalFilename");
    tokenMap.put("originalBasename", "episode.originalBasename");
    tokenMap.put("titleSortable", "episode.titleSortable");
    tokenMap.put("year", "episode.year");
    tokenMap.put("airedDate", "episode.firstAired;date(yyyy-MM-dd)");
    tokenMap.put("episodeRating", "episode.rating.rating");
    tokenMap.put("episodeImdb", "episode.imdbId");
    tokenMap.put("episodeTmdb", "episode.tmdbId");
    tokenMap.put("episodeTvdb", "episode.tvdbId");
    tokenMap.put("episodeTags", "episode.tags");

    tokenMap.put("videoCodec", "episode.mediaInfoVideoCodec");
    tokenMap.put("videoFormat", "episode.mediaInfoVideoFormat");
    tokenMap.put("videoResolution", "episode.mediaInfoVideoResolution");
    tokenMap.put("aspectRatio", "episode.mediaInfoAspectRatioAsString");
    tokenMap.put("aspectRatio2", "episode.mediaInfoAspectRatio2AsString");
    tokenMap.put("videoBitDepth", "episode.mediaInfoVideoBitDepth");
    tokenMap.put("videoBitRate", "episode.mediaInfoVideoBitrate;bitrate");
    tokenMap.put("framerate", "episode.mediaInfoFrameRate;framerate");

    tokenMap.put("audioCodec", "episode.mediaInfoAudioCodec");
    tokenMap.put("audioCodecList", "episode.mediaInfoAudioCodecList");
    tokenMap.put("audioCodecsAsString", "episode.mediaInfoAudioCodecList;array");
    tokenMap.put("audioChannels", "episode.mediaInfoAudioChannels");
    tokenMap.put("audioChannelList", "episode.mediaInfoAudioChannelList");
    tokenMap.put("audioChannelsAsString", "episode.mediaInfoAudioChannelList;array");
    tokenMap.put("audioChannelsDot", "episode.mediaInfoAudioChannelsDot");
    tokenMap.put("audioChannelDotList", "episode.mediaInfoAudioChannelDotList");
    tokenMap.put("audioChannelsDotAsString", "episode.mediaInfoAudioChannelDotList;array");
    tokenMap.put("audioLanguage", "episode.mediaInfoAudioLanguage");
    tokenMap.put("audioLanguageList", "episode.mediaInfoAudioLanguageList");
    tokenMap.put("audioLanguagesAsString", "episode.mediaInfoAudioLanguageList;array");

    tokenMap.put("subtitleLanguageList", "episode.mediaInfoSubtitleLanguageList");
    tokenMap.put("subtitleLanguagesAsString", "episode.mediaInfoSubtitleLanguageList;array");
    tokenMap.put("3Dformat", "episode.video3DFormat");
    tokenMap.put("hdr", "episode.videoHDR");
    tokenMap.put("hdrformat", "episode.videoHDRFormat");
    tokenMap.put("filesize", "episode.videoFilesize;filesize");

    tokenMap.put("mediaSource", "episode.mediaSource");
    tokenMap.put("note", "episode.note");
    tokenMap.put("crc32", "episode.CRC32");

    return tokenMap;
  }

  /**
   * get the token map in an unmodifiable variant
   *
   * @return the token map
   */
  public static Map<String, String> getTokenMap() {
    return Collections.unmodifiableMap(TOKEN_MAP);
  }

  public static Map<String, String> getTokenMapReversed() {
    return Collections.unmodifiableMap(TOKEN_MAP.entrySet().stream().collect(Collectors.toMap(Entry::getValue, Entry::getKey)));
  }

  /**
   * add leadingZero if only 1 char
   *
   * @param num
   *          the number
   * @return the string with a leading 0
   */
  private static String lz(int num) {
    return String.format("%02d", num);
  }

  /**
   * renames the TvShow root folder and updates all TvShow related mediaFiles
   *
   * @param tvShow
   *          the show
   */
  public static void renameTvShow(TvShow tvShow) {
    String transactionId = null;
    String oldPathname = tvShow.getPathNIO().toString();
    String newPathname = "";
    Path srcDir = null;
    Path destDir = null;
    boolean directoryMoved = false;

    try {
      transactionId = TvShowModuleManager.getInstance().beginTransaction();

      MediaEntityFilenameHistory filenameHistory = new MediaEntityFilenameHistory();

      LOGGER.info("Starting transactional rename for TV show '{}'", tvShow.getTitle());

      filenameHistory.setOldPath(oldPathname);

      newPathname = getTvShowFoldername(TvShowModuleManager.getInstance().getSettings().getRenamerTvShowFoldername(), tvShow);

      if (!newPathname.isEmpty()) {
        srcDir = WebDavDataSourceHelper.getWebDavPath(oldPathname);
        destDir = WebDavDataSourceHelper.getWebDavPath(newPathname);

        String srcNormalized = WebDavDataSourceHelper.normalizeWebDavPath(srcDir.toString());
        String destNormalized = WebDavDataSourceHelper.normalizeWebDavPath(destDir.toString());

        boolean pathsEqual = WebDavDataSourceHelper.isWebDavPath(srcNormalized) ? srcNormalized.equals(destNormalized)
            : srcDir.toAbsolutePath().toString().equals(destDir.toAbsolutePath().toString());

        if (!pathsEqual) {
          directoryMoved = moveDirectory(srcDir, destDir);
          if (directoryMoved) {
            if (WebDavDataSourceHelper.isWebDavPath(srcNormalized) && !srcNormalized.equals(destNormalized)) {
              LOGGER.info("WebDAV Path Rename/Merge successful: '{}' -> '{}'", srcNormalized, destNormalized);
            }

            TvShow existingShow = TvShowList.getInstance().getTvShowByPath(destDir);
            boolean shouldMergeToExisting = existingShow != null && existingShow != tvShow && !existingShow.getDbId().equals(tvShow.getDbId());

            if (shouldMergeToExisting) {
              LOGGER.info("Detected duplicate TvShow after merge: current='{}' (UUID={}), existing='{}' (UUID={}). Merging episodes...",
                  tvShow.getTitle(), tvShow.getDbId(), existingShow.getTitle(), existingShow.getDbId());

              for (TvShowEpisode episode : new ArrayList<>(tvShow.getEpisodes())) {
                List<TvShowEpisode> existingEps = existingShow.getEpisode(episode.getSeason(), episode.getEpisode());
                if (existingEps.isEmpty()) {
                  tvShow.detachEpisode(episode);
                  episode.setTvShow(existingShow);
                  existingShow.addEpisode(episode);
                  episode.saveToDb();
                  LOGGER.debug("Moved episode S{}E{} from '{}' to '{}'", episode.getSeason(), episode.getEpisode(), tvShow.getTitle(),
                      existingShow.getTitle());
                }
                else {
                  TvShowEpisode existingEp = existingEps.get(0);
                  EpisodeComparisonResult comparison = compareEpisodes(episode, existingEp);

                  if (comparison == EpisodeComparisonResult.SOURCE_BETTER || comparison == EpisodeComparisonResult.EQUAL) {
                    LOGGER.debug("Replacing episode S{}E{}: comparison result={}, keeping source (path updated)", episode.getSeason(),
                        episode.getEpisode(), comparison);

                    existingShow.removeEpisode(existingEp);

                    tvShow.detachEpisode(episode);
                    episode.setTvShow(existingShow);
                    existingShow.addEpisode(episode);
                    episode.saveToDb();
                  }
                  else {
                    tvShow.removeEpisode(episode);
                    LOGGER.debug("Removed duplicate episode S{}E{} from '{}': comparison result={}, keeping target", episode.getSeason(),
                        episode.getEpisode(), tvShow.getTitle(), comparison);
                  }
                }
              }

              // 合并源 TvShow 的 MediaFiles（海报、fanart 等）到目标 TvShow
              mergeMediaFilesToExistingShow(tvShow, existingShow, srcDir, destDir);

              TvShowList.getInstance().removeTvShow(tvShow);
              LOGGER.info("Removed duplicate TvShow '{}' after merging episodes to '{}'", tvShow.getTitle(), existingShow.getTitle());

              existingShow.saveToDb();

              existingShow.firePropertyChange("episodeCount", 0, existingShow.getEpisodes().size());
              existingShow.firePropertyChange("seasons", null, existingShow.getSeasons());

              TvShowModuleManager.getInstance().commitTransaction(transactionId);
              return;
            }

            tvShow.updateMediaFilePath(srcDir, destDir);
            tvShow.setPath(newPathname);
            filenameHistory.setNewPath(newPathname);

            for (TvShowSeason tvShowSeason : tvShow.getSeasons()) {
              tvShowSeason.updateMediaFilePath(srcDir, destDir);
            }

            for (TvShowEpisode episode : tvShow.getEpisodes()) {
              episode.replacePathForRenamedTvShowRoot(srcDir, destDir);
              episode.updateMediaFilePath(srcDir, destDir);
              episode.saveToDb();
            }

            if (Settings.getInstance().isImageCache()) {
              for (MediaFile gfx : tvShow.getMediaFiles()) {
                ImageCache.cacheImageSilently(gfx, false);
              }
            }
          }
        }
      }

      if (StringUtils.isBlank(filenameHistory.getNewPath())) {
        filenameHistory.setNewPath(filenameHistory.getOldPath());
      }

      renameTvShowMediaFiles(tvShow, filenameHistory);

      tvShow.setRenameHistory(filenameHistory);

      renameSeasonMediaFiles(tvShow);

      cleanupUnwantedFiles(tvShow);

      tvShow.saveToDb();

      TvShowModuleManager.getInstance().commitTransaction(transactionId);

      LOGGER.info("Successfully completed transactional rename for TV show '{}'", tvShow.getTitle());
    }
    catch (Exception e) {
      LOGGER.error("Error during transactional rename of TV show '{}', rolling back", tvShow.getTitle(), e);

      if (transactionId != null) {
        try {
          TvShowModuleManager.getInstance().rollbackTransaction(transactionId);
        }
        catch (Exception rollbackEx) {
          LOGGER.error("Failed to rollback transaction", rollbackEx);
        }
      }

      if (directoryMoved && srcDir != null && destDir != null) {
        try {
          LOGGER.info("Attempting to rollback directory move: '{}' -> '{}'", destDir, srcDir);
          moveDirectory(destDir, srcDir);
          tvShow.setPath(oldPathname);
          LOGGER.info("Successfully rolled back directory move");
        }
        catch (Exception rollbackEx) {
          LOGGER.error("Failed to rollback directory move", rollbackEx);
        }
      }

      MessageManager.getInstance()
          .pushMessage(new Message(MessageLevel.ERROR, srcDir != null ? srcDir.toString() : oldPathname, "message.renamer.failedrename",
              new String[] { ":", e.getLocalizedMessage() }));

      throw new RuntimeException("Failed to rename TV show: " + e.getMessage(), e);
    }
  }

  /**
   * renames the TvShow root folder and updates all mediaFiles
   *
   * @param show
   *          the show
   */
  private static void renameTvShowRoot(TvShow show, MediaEntityFilenameHistory filenameHistory) {
    // skip renamer, if all templates are empty!
    if (TvShowModuleManager.getInstance().getSettings().getRenamerFilename().isEmpty()
        && TvShowModuleManager.getInstance().getSettings().getRenamerSeasonFoldername().isEmpty()
        && TvShowModuleManager.getInstance().getSettings().getRenamerTvShowFoldername().isEmpty()) {
      LOGGER.warn("NOT renaming TV show '{}' - renaming patterns are empty!", show.getTitle());
      return;
    }

    LOGGER.info("Renaming TV show '{}'", show.getTitle());
    LOGGER.debug("TV show year: {}", show.getYear());
    LOGGER.debug("TV show path: {}", show.getPathNIO());
    String newPathname = getTvShowFoldername(TvShowModuleManager.getInstance().getSettings().getRenamerTvShowFoldername(), show);
    String oldPathname = show.getPathNIO().toString();

    filenameHistory.setOldPath(oldPathname);

    if (!newPathname.isEmpty()) {
      Path srcDir = WebDavDataSourceHelper.getWebDavPath(oldPathname);
      Path destDir = WebDavDataSourceHelper.getWebDavPath(newPathname);

      // Normalize paths for comparison (handles WebDAV path format)
      String srcNormalized = WebDavDataSourceHelper.normalizeWebDavPath(srcDir.toString());
      String destNormalized = WebDavDataSourceHelper.normalizeWebDavPath(destDir.toString());

      // move directory if needed (use string comparison for WebDAV paths)
      boolean pathsEqual = WebDavDataSourceHelper.isWebDavPath(srcNormalized) ? srcNormalized.equals(destNormalized)
          : srcDir.toAbsolutePath().toString().equals(destDir.toAbsolutePath().toString());

      if (!pathsEqual) {
        try {
          boolean ok = moveDirectory(srcDir, destDir);
          if (ok) {
            // Check if it was a merge (source still exists or destination already had content)
            if (WebDavDataSourceHelper.isWebDavPath(srcNormalized) && !srcNormalized.equals(destNormalized)) {
              LOGGER.info("WebDAV Path Rename/Merge successful: '{}' -> '{}'", srcNormalized, destNormalized);
            }

            // ######################################################################
            // ## IMPORTANT: Check for existing TvShow at destination BEFORE updating current show's path
            // ## This must be done before setPath() to correctly identify duplicate
            // ######################################################################
            TvShow existingShow = TvShowList.getInstance().getTvShowByPath(destDir);
            boolean shouldMergeToExisting = existingShow != null && existingShow != show && !existingShow.getDbId().equals(show.getDbId());

            // Now update the current show's paths
            show.updateMediaFilePath(srcDir, destDir); // TvShow MFs
            show.setPath(newPathname);
            filenameHistory.setNewPath(newPathname);

            for (TvShowSeason tvShowSeason : show.getSeasons()) {
              tvShowSeason.updateMediaFilePath(srcDir, destDir);
            }

            for (TvShowEpisode episode : show.getEpisodes()) {
              episode.replacePathForRenamedTvShowRoot(srcDir, destDir);
              episode.updateMediaFilePath(srcDir, destDir);
              episode.saveToDb();
            }

            // ######################################################################
            // ## If we found an existing show at destination, merge episodes and delete current show
            // ######################################################################
            if (shouldMergeToExisting) {
              LOGGER.info("Detected duplicate TvShow after merge: current='{}' (UUID={}), existing='{}' (UUID={}). Merging episodes...",
                  show.getTitle(), show.getDbId(), existingShow.getTitle(), existingShow.getDbId());

              // Move all episodes from current show to existing show
              // IMPORTANT: Use detachEpisode() instead of removeEpisode() to avoid deleting from DB
              for (TvShowEpisode episode : new ArrayList<>(show.getEpisodes())) {
                // Check if existing show already has this episode (by S/E number)
                List<TvShowEpisode> existingEps = existingShow.getEpisode(episode.getSeason(), episode.getEpisode());
                if (existingEps.isEmpty()) {
                  // Episode doesn't exist in target, move it (detach from source, attach to target)
                  show.detachEpisode(episode);
                  episode.setTvShow(existingShow);
                  existingShow.addEpisode(episode);
                  episode.saveToDb();
                  LOGGER.debug("Moved episode S{}E{} from '{}' to '{}'", episode.getSeason(), episode.getEpisode(), show.getTitle(),
                      existingShow.getTitle());
                }
                else {
                  // Episode already exists - use enhanced comparison to determine which one to keep
                  TvShowEpisode existingEp = existingEps.get(0);
                  EpisodeComparisonResult comparison = compareEpisodes(episode, existingEp);

                  if (comparison == EpisodeComparisonResult.SOURCE_BETTER || comparison == EpisodeComparisonResult.EQUAL) {
                    // Source episode is better OR equal - replace target with source (source has updated path)
                    LOGGER.debug("Replacing episode S{}E{}: comparison result={}, keeping source (path updated)", episode.getSeason(),
                        episode.getEpisode(), comparison);

                    // Remove target episode from existingShow and DB
                    existingShow.removeEpisode(existingEp);
                    // NOTE: removeEpisode already calls removeEpisodeFromDb, no need to call again

                    // Move source episode to target show (detach, don't delete)
                    show.detachEpisode(episode);
                    episode.setTvShow(existingShow);
                    existingShow.addEpisode(episode);
                    episode.saveToDb();
                  }
                  else {
                    // Target episode is better - keep target, remove source
                    show.removeEpisode(episode);
                    // NOTE: removeEpisode already calls removeEpisodeFromDb, no need to call again
                    LOGGER.debug("Removed duplicate episode S{}E{} from '{}': comparison result={}, keeping target", episode.getSeason(),
                        episode.getEpisode(), show.getTitle(), comparison);
                  }
                }
              }

              // 合并源 TvShow 的 MediaFiles（海报、fanart 等）到目标 TvShow
              mergeMediaFilesToExistingShow(show, existingShow, srcDir, destDir);

              // Remove current show from database and list
              TvShowList.getInstance().removeTvShow(show);
              LOGGER.info("Removed duplicate TvShow '{}' after merging episodes to '{}'", show.getTitle(), existingShow.getTitle());

              // Save the existing show with merged episodes
              existingShow.saveToDb();

              // Force UI refresh by firing property change events
              existingShow.firePropertyChange("episodeCount", 0, existingShow.getEpisodes().size());
              existingShow.firePropertyChange("seasons", null, existingShow.getSeasons());
              return; // Exit early, we've handled everything
            }

            // ######################################################################
            // ## build up image cache
            // ######################################################################
            if (Settings.getInstance().isImageCache()) {
              for (MediaFile gfx : show.getMediaFiles()) {
                ImageCache.cacheImageSilently(gfx, false);
              }
            }
          }
        }
        catch (Exception e) {
          LOGGER.error("Error moving folder '{}' to '{}' - '{}'", srcDir, destDir, e.getMessage());
          MessageManager.getInstance()
              .pushMessage(new Message(MessageLevel.ERROR, srcDir, "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
        }
      }
    }

    if (StringUtils.isBlank(filenameHistory.getNewPath())) {
      filenameHistory.setNewPath(filenameHistory.getOldPath());
    }
  }

  /**
   * rename all artwork for this TV show
   *
   * @param tvShow
   *          the TV show to rename the artwork for
   */
  private static void renameTvShowMediaFiles(TvShow tvShow, MediaEntityFilenameHistory filenameHistory) {
    // all the good & needed mediafiles
    List<MediaFile> needed = new ArrayList<>();
    List<MediaFile> cleanup = new ArrayList<>(tvShow.getMediaFiles());
    cleanup.removeAll(Collections.singleton((MediaFile) null)); // remove all NULL ones!

    // ######################################################################
    // ## rename NFO (copy 1:N) - only TMM NFOs
    // ######################################################################
    // we need to find the newest, valid TMM NFO
    MediaFile nfo = MediaFile.EMPTY_MEDIAFILE;
    for (MediaFile mf : tvShow.getMediaFiles(MediaFileType.NFO)) {
      if (mf.getFiledate() >= nfo.getFiledate()) {
        nfo = new MediaFile(mf);
      }
    }

    Path newTvShowPath = tvShow.getPathNIO();

    if (nfo != MediaFile.EMPTY_MEDIAFILE) { // one valid found? copy our NFO to all variants
      List<MediaFile> newMFs = generateFilename(tvShow, nfo); // 1:N
      for (MediaFile newMF : newMFs) {
        boolean ok = copyFile(nfo.getFileAsPath(), newMF.getFileAsPath());
        if (ok) {
          filenameHistory.addFilenameHistory(createFilenameHistory(newTvShowPath, nfo.getFileAsPath(), newMF.getFileAsPath()));
          needed.add(newMF);
        }
        else {
          // FIXME: what to do? not copied/exception... keep it for now...
          filenameHistory.addFilenameHistory(createFilenameHistory(newTvShowPath, nfo.getFileAsPath(), nfo.getFileAsPath()));
          needed.add(nfo);
        }
      }
    }
    else {
      LOGGER.trace("No valid NFO found for this TV show");
    }

    // ######################################################################
    // ## rename well known types
    // ######################################################################
    for (MediaFile mf : tvShow.getMediaFiles()) {
      if (mf == null) {
        continue;
      }

      LOGGER.trace("Rename 1:N {} {}", mf.getType(), mf.getFileAsPath());
      List<MediaFile> newMFs = generateFilename(tvShow, mf); // 1:N
      for (MediaFile newMF : newMFs) {
        boolean ok = copyFile(mf.getFileAsPath(), newMF.getFileAsPath());
        if (ok) {
          filenameHistory.addFilenameHistory(createFilenameHistory(newTvShowPath, mf.getFileAsPath(), newMF.getFileAsPath()));
          needed.add(newMF);
        }
        else {
          // FIXME: what to do? not copied/exception... keep it for now...
          filenameHistory.addFilenameHistory(createFilenameHistory(newTvShowPath, mf.getFileAsPath(), mf.getFileAsPath()));
          needed.add(mf);
        }
      }
    }

    // ######################################################################
    // ## invalidate image cache
    // ######################################################################
    for (MediaFile gfx : tvShow.getMediaFiles()) {
      ImageCache.invalidateCachedImage(gfx);
    }

    // remove duplicate MediaFiles
    Set<MediaFile> newMFs = new LinkedHashSet<>(needed);
    needed.clear();
    needed.addAll(newMFs);

    // ######################################################################
    // ## CLEANUP - delete all files marked for cleanup, which are not "needed"
    // ######################################################################
    LOGGER.debug("Cleanup...");
    for (int i = cleanup.size() - 1; i >= 0; i--) {
      // cleanup files which are not needed
      if (!needed.contains(cleanup.get(i))) {
        MediaFile cl = cleanup.get(i);
        if (Files.exists(cl.getFileAsPath())) { // unneeded, but for not displaying wrong deletes in logger...
          LOGGER.debug("Deleting {}", cl.getFileAsPath());
          Utils.deleteFileWithBackup(cl.getFileAsPath(), tvShow.getDataSource());
          // also cleanup the cache for deleted mfs
          if (cl.isGraphic()) {
            ImageCache.invalidateCachedImage(cl);
          }
        }

        // Skip empty directory check for WebDAV paths (Files.newDirectoryStream not supported)
        if (!WebDavDataSourceHelper.isWebDavPath(tvShow.getPath())) {
          try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(cl.getFileAsPath().getParent())) {
            if (!directoryStream.iterator().hasNext()) {
              // no iterator = empty
              LOGGER.debug("Deleting empty Directory {}", cl.getFileAsPath().getParent());
              Files.delete(cl.getFileAsPath().getParent()); // do not use recursive her
            }
          }
          catch (IOException e) {
            LOGGER.error("Error in cleanup of '{}' - '{}'", cl.getFileAsPath(), e.getMessage());
          }
        }
      }
    }

    // delete empty subfolders
    if (WebDavDataSourceHelper.isWebDavPath(tvShow.getPath())) {
      // WebDAV 路径使用专门的方法删除空目录
      int deleted = WebDavFileOperations.deleteEmptyDirectoriesRecursive(tvShow.getPath());
      if (deleted > 0) {
        LOGGER.debug("Deleted {} empty WebDAV directories under '{}'", deleted, tvShow.getPath());
      }
    }
    else {
      try {
        Utils.deleteEmptyDirectoryRecursive(tvShow.getPathNIO());
      }
      catch (Exception e) {
        LOGGER.warn("Could not delete empty subfolders of '{}' - '{}'", tvShow.getPathNIO(), e.getMessage());
      }
    }

    tvShow.removeAllMediaFiles();

    // ######################################################################
    // ## build up image cache
    // ######################################################################
    if (Settings.getInstance().isImageCache()) {
      for (MediaFile gfx : needed) {
        ImageCache.cacheImageSilently(gfx, false);
      }
    }

    // give the file system a bit to write the files
    ThreadUtils.sleep(250);

    tvShow.addToMediaFiles(needed);
  }

  private static MediaEntityFilenameHistory.FilenameHistory createFilenameHistory(Path tvShowRoot, Path oldFilePath, Path newFilePath) {
    String tvShowRootStr = tvShowRoot.toString();
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(tvShowRootStr);

    String oldFilename;
    String newFilename;

    if (isWebDav) {
      // For WebDAV, use string manipulation
      String oldFilePathStr = oldFilePath.toString();
      String newFilePathStr = newFilePath.toString();

      if (oldFilePathStr.startsWith(tvShowRootStr)) {
        oldFilename = oldFilePathStr.substring(tvShowRootStr.length());
        if (oldFilename.startsWith("/")) {
          oldFilename = oldFilename.substring(1);
        }
      }
      else {
        oldFilename = oldFilePathStr;
      }

      if (newFilePathStr.startsWith(tvShowRootStr)) {
        newFilename = newFilePathStr.substring(tvShowRootStr.length());
        if (newFilename.startsWith("/")) {
          newFilename = newFilename.substring(1);
        }
      }
      else {
        newFilename = newFilePathStr;
      }
    }
    else {
      oldFilename = tvShowRoot.relativize(oldFilePath).toString();
      newFilename = tvShowRoot.relativize(newFilePath).toString();
    }

    return new MediaEntityFilenameHistory.FilenameHistory(oldFilename, newFilename);
  }

  private static MediaEntityFilenameHistory.FilenameHistory findFilenameHistoryForMediaFile(MediaEntity entity, MediaFile mediaFile) {
    if (entity.getRenameHistory() == null) {
      return null;
    }

    Path tvShowRoot;

    if (entity instanceof TvShow tvShow) {
      tvShowRoot = tvShow.getPathNIO();
    }
    else if (entity instanceof TvShowSeason season) {
      tvShowRoot = season.getTvShow().getPathNIO();
    }
    else if (entity instanceof TvShowEpisode episode) {
      tvShowRoot = episode.getTvShow().getPathNIO();
    }
    else {
      return null;
    }

    for (MediaEntityFilenameHistory.FilenameHistory filenameHistory : entity.getRenameHistory().getFilenameHistory()) {
      String tvShowRootStr = tvShowRoot.toString();
      boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(tvShowRootStr);

      String relativeFilename;
      if (isWebDav) {
        // For WebDAV, use string manipulation
        String mediaFilePathStr = mediaFile.getFileAsPath().toString();
        if (mediaFilePathStr.startsWith(tvShowRootStr)) {
          relativeFilename = mediaFilePathStr.substring(tvShowRootStr.length());
          if (relativeFilename.startsWith("/")) {
            relativeFilename = relativeFilename.substring(1);
          }
        }
        else {
          relativeFilename = mediaFilePathStr;
        }
      }
      else {
        relativeFilename = tvShowRoot.relativize(mediaFile.getFileAsPath()).toString();
      }

      if (filenameHistory.newFilename().equals(relativeFilename)) {
        return filenameHistory;
      }
    }

    return null;
  }

  /**
   * generate all possible file names (as {@link MediaFile}s) for the given {@link MediaFile} according to the settings
   *
   * @param tvShow
   *          the {@link TvShow}
   * @param original
   *          the original {@link MediaFile}
   * @return a {@link List} of all {@link MediaFile}s which are needed according to the settings
   */
  public static List<MediaFile> generateFilename(TvShow tvShow, MediaFile original) {
    List<MediaFile> neededMediaFiles = new ArrayList<>();
    List<? extends IFileNaming> filenamings = null;

    switch (original.getType()) {
      case POSTER:
        filenamings = TvShowModuleManager.getInstance().getSettings().getPosterFilenames();
        break;

      case FANART:
        filenamings = TvShowModuleManager.getInstance().getSettings().getFanartFilenames();
        break;

      case BANNER:
        filenamings = TvShowModuleManager.getInstance().getSettings().getBannerFilenames();
        break;

      case CLEARLOGO:
      case LOGO:
        filenamings = TvShowModuleManager.getInstance().getSettings().getClearlogoFilenames();
        break;

      case CLEARART:
        filenamings = TvShowModuleManager.getInstance().getSettings().getClearartFilenames();
        break;

      case THUMB:
        filenamings = TvShowModuleManager.getInstance().getSettings().getThumbFilenames();
        break;

      case DISC:
        filenamings = TvShowModuleManager.getInstance().getSettings().getDiscartFilenames();
        break;

      case CHARACTERART:
        filenamings = TvShowModuleManager.getInstance().getSettings().getCharacterartFilenames();
        break;

      case KEYART:
        filenamings = TvShowModuleManager.getInstance().getSettings().getKeyartFilenames();
        break;

      case TRAILER:
        filenamings = TvShowModuleManager.getInstance().getSettings().getTrailerFilenames();
        break;

      case NFO:
        filenamings = TvShowModuleManager.getInstance().getSettings().getNfoFilenames();
        break;

      case EXTRAFANART:
        neededMediaFiles.addAll(generateExtrafanartFilename(tvShow, original));
        break;

      default:
        neededMediaFiles.add(original);
        break;

    }

    if (filenamings != null) {
      for (IFileNaming name : filenamings) {
        String extension = getMediaFileExtension(original);
        String newFilename = name.getFilename(tvShow.getFoldername(), extension);

        if (StringUtils.isNotBlank(newFilename)) {
          // special case: file stacking (multiple trailers, posters, ...)
          Matcher matcher = MF_STACKING_PATTERN.matcher(original.getBasename());
          if (matcher.matches()) {
            String stackingMarker = matcher.group(1);
            if (StringUtils.isNotBlank(stackingMarker)) {
              newFilename = FilenameUtils.getBaseName(newFilename) + stackingMarker + "." + extension;
            }
          }

          MediaFile newMediaFile = new MediaFile(original);
          newMediaFile.setFile(resolvePath(tvShow.getPathNIO(), newFilename));
          neededMediaFiles.add(newMediaFile);
        }
      }
    }

    return neededMediaFiles;
  }

  /**
   * copy the given extrafanarts
   *
   * @param tvShow
   *          the {@link TvShow} to generate the extrafanart filenames for
   *
   * @param original
   *          the original {@link MediaFile} to copy
   *
   * @return a {@link List} of all {@link MediaFile}s created while copying (or the original if no copy needed)
   */
  private static List<MediaFile> generateExtrafanartFilename(TvShow tvShow, MediaFile original) {
    if (TvShowModuleManager.getInstance().getSettings().getExtraFanartFilenames().isEmpty()) {
      return Collections.emptyList();
    }

    int index = TvShowArtworkHelper.getIndexOfArtwork(original.getFilename());
    if (index > 0) {
      // at the moment, we just support 1 naming scheme here! if we decide to enhance that, we will need to enhance the TvShowExtraImageFetcherTask
      // too
      TvShowExtraFanartNaming name = TvShowModuleManager.getInstance().getSettings().getExtraFanartFilenames().get(0);

      String newFilename = name.getFilename("", getMediaFileExtension(original));
      if (StringUtils.isNotBlank(newFilename)) {

        String basename = FilenameUtils.getBaseName(newFilename);
        newFilename = basename + index + "." + getMediaFileExtension(original);

        // create an empty extrafanarts folder if the right naming has been chosen
        Path folder;
        if (name == TvShowExtraFanartNaming.FOLDER_EXTRAFANART) {
          folder = resolvePath(tvShow.getPathNIO(), "extrafanart");
        }
        else {
          folder = tvShow.getPathNIO();
        }

        MediaFile newMediaFile = new MediaFile(original);
        newMediaFile.setFile(resolvePath(folder, newFilename));
        return Collections.singletonList(newMediaFile);
      }
    }

    return Collections.emptyList();
  }

  /**
   * rename the season artwork for this TV show
   * 
   * @param tvShow
   *          the TV show to rename the season artwork for
   */
  private static void renameSeasonMediaFiles(TvShow tvShow) {
    Map<TvShowSeason, MediaEntityFilenameHistory> filenameHistoryMap = new HashMap<>();

    // all the good & needed mediafiles
    Set<MediaFile> needed = new LinkedHashSet<>();
    List<MediaFile> cleanup = new ArrayList<>();

    Path tvShowRoot = tvShow.getPathNIO();

    // NFO
    for (var tvShowSeason : tvShow.getSeasons()) {
      MediaEntityFilenameHistory filenameHistory = new MediaEntityFilenameHistory();
      filenameHistoryMap.put(tvShowSeason, filenameHistory);

      Set<MediaFile> neededSeason = new LinkedHashSet<>();
      List<MediaFile> cleanupSeason = new ArrayList<>();

      MediaFile nfo = MediaFile.EMPTY_MEDIAFILE;
      for (MediaFile mf : tvShowSeason.getMediaFiles(MediaFileType.NFO)) {
        cleanupSeason.add(mf);

        if (mf.getFiledate() >= nfo.getFiledate()) {
          nfo = new MediaFile(mf);
        }
      }

      // one valid found? copy our NFO to all variants
      // and do we want to write those files?
      if (nfo != MediaFile.EMPTY_MEDIAFILE
          && (!tvShowSeason.getEpisodes().isEmpty() || TvShowModuleManager.getInstance().getSettings().isCreateMissingSeasonItems())) {

        for (TvShowSeasonNfoNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonNfoFilenames()) {
          String filename = naming.getFilename(tvShowSeason, "nfo");
          if (StringUtils.isNotBlank(filename)) {
            MediaFile newMf = new MediaFile(nfo);
            // Use getWebDavPath for full path construction to be safe on Windows
            String fullPath = tvShow.getPath() + (tvShow.getPath().endsWith("/") ? "" : "/") + filename;
            newMf.setFile(WebDavDataSourceHelper.getWebDavPath(fullPath));
            boolean ok = copyFile(nfo.getFileAsPath(), newMf.getFileAsPath());
            if (ok) {
              filenameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, nfo.getFileAsPath(), newMf.getFileAsPath()));
              neededSeason.add(newMf);
            }
          }
        }
      }

      cleanupSeason.forEach(tvShowSeason::removeFromMediaFiles);
      neededSeason.forEach(tvShowSeason::addToMediaFiles);

      needed.addAll(neededSeason);
      cleanup.addAll(cleanupSeason);
    }

    // artwork
    List<MediaFileType> types = Arrays.asList(SEASON_POSTER, SEASON_FANART, SEASON_BANNER, SEASON_THUMB);

    for (var type : types) {
      for (var tvShowSeason : tvShow.getSeasons()) {
        MediaEntityFilenameHistory filenameHistory = filenameHistoryMap.get(tvShowSeason);

        Set<MediaFile> neededSeason = new LinkedHashSet<>();
        List<MediaFile> cleanupSeason = new ArrayList<>();

        MediaFile artworkFile = null;

        for (var mf : tvShowSeason.getMediaFiles(type)) {
          cleanupSeason.add(mf);

          if (artworkFile == null) {
            artworkFile = mf;
          }
        }

        // do we ant to write the artwork file at all?
        if (artworkFile != null
            && (!tvShowSeason.getEpisodes().isEmpty() || TvShowModuleManager.getInstance().getSettings().isCreateMissingSeasonItems())) {
          String filename;
          switch (type) {
            case SEASON_POSTER -> {
              for (TvShowSeasonPosterNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonPosterFilenames()) {
                filename = naming.getFilename(tvShowSeason, artworkFile.getExtension());
                if (StringUtils.isNotBlank(filename)) {
                  MediaFile newMf = new MediaFile(artworkFile);
                  // Determine destination folder based on settings
                  Path destinationFolder = getDestinationFolderForTvShowRename(tvShow);
                  newMf.setFile(resolvePath(destinationFolder, filename));
                  boolean ok = copyFile(artworkFile.getFileAsPath(), newMf.getFileAsPath());
                  if (ok) {
                    filenameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, artworkFile.getFileAsPath(), newMf.getFileAsPath()));
                    neededSeason.add(newMf);
                  }
                }
              }
            }
            case SEASON_FANART -> {
              for (TvShowSeasonFanartNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonFanartFilenames()) {
                filename = naming.getFilename(tvShowSeason, artworkFile.getExtension());
                if (StringUtils.isNotBlank(filename)) {
                  MediaFile newMf = new MediaFile(artworkFile);
                  // Determine destination folder based on settings
                  Path destinationFolder = getDestinationFolderForTvShowRename(tvShow);
                  newMf.setFile(resolvePath(destinationFolder, filename));
                  boolean ok = copyFile(artworkFile.getFileAsPath(), newMf.getFileAsPath());
                  if (ok) {
                    filenameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, artworkFile.getFileAsPath(), newMf.getFileAsPath()));
                    neededSeason.add(newMf);
                  }
                }
              }
            }
            case SEASON_BANNER -> {
              for (TvShowSeasonBannerNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonBannerFilenames()) {
                filename = naming.getFilename(tvShowSeason, artworkFile.getExtension());
                if (StringUtils.isNotBlank(filename)) {
                  MediaFile newMf = new MediaFile(artworkFile);
                  // Determine destination folder based on settings
                  Path destinationFolder = getDestinationFolderForTvShowRename(tvShow);
                  newMf.setFile(resolvePath(destinationFolder, filename));
                  boolean ok = copyFile(artworkFile.getFileAsPath(), newMf.getFileAsPath());
                  if (ok) {
                    filenameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, artworkFile.getFileAsPath(), newMf.getFileAsPath()));
                    neededSeason.add(newMf);
                  }
                }
              }
            }
            case SEASON_THUMB -> {
              for (TvShowSeasonThumbNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonThumbFilenames()) {
                filename = naming.getFilename(tvShowSeason, artworkFile.getExtension());
                if (StringUtils.isNotBlank(filename)) {
                  MediaFile newMf = new MediaFile(artworkFile);
                  // Determine destination folder based on settings
                  Path destinationFolder = getDestinationFolderForTvShowRename(tvShow);
                  newMf.setFile(resolvePath(destinationFolder, filename));
                  boolean ok = copyFile(artworkFile.getFileAsPath(), newMf.getFileAsPath());
                  if (ok) {
                    filenameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, artworkFile.getFileAsPath(), newMf.getFileAsPath()));
                    neededSeason.add(newMf);
                  }
                }
              }
            }
            default -> {
              // do nothing
            }
          }
        }

        cleanupSeason.forEach(tvShowSeason::removeFromMediaFiles);
        neededSeason.forEach(tvShowSeason::addToMediaFiles);

        cleanup.addAll(cleanupSeason);
        needed.addAll(neededSeason);
      }
    }

    // ######################################################################
    // ## invalidate image cache
    // ######################################################################
    for (MediaFile gfx : tvShow.getMediaFiles()) {
      ImageCache.invalidateCachedImage(gfx);
    }

    // ######################################################################
    // ## CLEANUP - delete all files marked for cleanup, which are not "needed"
    // ######################################################################
    LOGGER.debug("Cleanup...");
    List<Path> existingFiles;
    if (WebDavDataSourceHelper.isWebDavPath(tvShow.getPath())) {
      // For WebDAV, skip file listing cleanup (not supported for virtual paths)
      existingFiles = new ArrayList<>();
    }
    else {
      existingFiles = Utils.listFilesRecursive(tvShow.getPathNIO());
    }
    for (int i = cleanup.size() - 1; i >= 0; i--) {
      // cleanup files which are not needed
      if (!needed.contains(cleanup.get(i))) {
        MediaFile cl = cleanup.get(i);
        if (existingFiles.contains(cl.getFileAsPath())) {
          LOGGER.debug("Deleting {}", cl.getFileAsPath());
          tvShow.removeFromMediaFiles(cl);

          for (TvShowSeason season : tvShow.getSeasons()) {
            season.removeFromMediaFiles(cl);
          }

          Utils.deleteFileWithBackup(cl.getFileAsPath(), tvShow.getDataSource());
          // also cleanup the cache for deleted mfs
          if (cl.isGraphic()) {
            ImageCache.invalidateCachedImage(cl);
          }
        }

        // Skip empty directory check for WebDAV paths (Files.newDirectoryStream not supported)
        if (!WebDavDataSourceHelper.isWebDavPath(tvShow.getPath())) {
          try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(cl.getFileAsPath().getParent())) {
            if (!directoryStream.iterator().hasNext()) {
              // no iterator = empty
              LOGGER.debug("Deleting empty Directory {}", cl.getFileAsPath().getParent());
              Files.delete(cl.getFileAsPath().getParent()); // do not use recursive her
            }
          }
          catch (IOException e) {
            LOGGER.error("Error in cleanup of '{}' - '{}'", cl.getFileAsPath(), e.getMessage());
          }
        }
      }
    }

    // delete empty subfolders
    if (WebDavDataSourceHelper.isWebDavPath(tvShow.getPath())) {
      // WebDAV 路径使用专门的方法删除空目录
      int deleted = WebDavFileOperations.deleteEmptyDirectoriesRecursive(tvShow.getPath());
      if (deleted > 0) {
        LOGGER.debug("Deleted {} empty WebDAV directories under '{}'", deleted, tvShow.getPath());
      }
    }
    else {
      try {
        Utils.deleteEmptyDirectoryRecursive(tvShow.getPathNIO());
      }
      catch (Exception e) {
        LOGGER.warn("Could not delete empty subfolders of '{}' - '{}'", tvShow.getPathNIO(), e.getMessage());
      }
    }

    // and rebuild the whole artwork maps
    for (MediaFile mf : needed) {
      boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(tvShow.getPath());
      String foldername;

      if (isWebDav) {
        // For WebDAV, use string manipulation
        String tvShowPathStr = tvShow.getPath();
        String mediaFileParentStr = mf.getFileAsPath().getParent().toString();

        if (mediaFileParentStr.startsWith(tvShowPathStr)) {
          foldername = mediaFileParentStr.substring(tvShowPathStr.length());
          if (foldername.startsWith("/")) {
            foldername = foldername.substring(1);
          }
        }
        else {
          foldername = mediaFileParentStr;
        }
      }
      else {
        foldername = tvShow.getPathNIO().relativize(mf.getFileAsPath().getParent()).toString();
      }

      int season = TvShowHelpers.detectSeasonFromFileAndFolder(mf.getFilename(), foldername);

      tvShow.removeFromMediaFiles(mf);

      for (TvShowSeason tvShowSeason : tvShow.getSeasons()) {
        tvShowSeason.removeFromMediaFiles(mf);
      }

      if (season != Integer.MIN_VALUE) {
        TvShowSeason tvShowSeason = tvShow.getSeason(season);
        tvShowSeason.addToMediaFiles(mf);
      }
    }

    // ######################################################################
    // ## build up image cache
    // ######################################################################
    if (Settings.getInstance().isImageCache()) {
      for (MediaFile gfx : tvShow.getMediaFiles()) {
        ImageCache.cacheImageSilently(gfx, false);
      }
    }

    // store history
    for (TvShowSeason tvShowSeason : tvShow.getSeasons()) {
      MediaEntityFilenameHistory filenameHistory = filenameHistoryMap.get(tvShowSeason);
      if (ListUtils.isNotEmpty(filenameHistory.getFilenameHistory())) {
        tvShowSeason.setRenameHistory(filenameHistory);
      }
      else {
        tvShowSeason.setRenameHistory(null);
      }
    }
  }

  /**
   * Rename Episode (PLUS all Episodes having the same MediaFile!!!).
   * 
   * @param episode
   *          the Episode
   */
  public static void renameEpisode(TvShowEpisode episode) {
    // skip renamer, if all episode related templates are empty!
    if (TvShowModuleManager.getInstance().getSettings().getRenamerFilename().isEmpty()
        && TvShowModuleManager.getInstance().getSettings().getRenamerSeasonFoldername().isEmpty()
        && TvShowModuleManager.getInstance().getSettings().getRenamerTvShowFoldername().isEmpty()) {
      LOGGER.warn("NOT renaming TV show '{}', episode S{} E{} - renaming patterns are empty!", episode.getTvShow().getTitle(), episode.getSeason(),
          episode.getEpisode());
      return;
    }

    MediaFile originalVideoMediaFile = new MediaFile(episode.getMainVideoFile());
    // test for valid season/episode number
    if (episode.getSeason() < 0 || episode.getEpisode() < 0) {
      LOGGER.warn("Can not rename episode '{}' (TV show '{}') - invalid season/episode number (S{} E{})", episode.getTitle(),
          episode.getTvShow().getTitle(), episode.getSeason(), episode.getEpisode());
      MessageManager.getInstance()
          .pushMessage(
              new Message(MessageLevel.ERROR, episode.getTvShow().getTitle(), "tvshow.renamer.failedrename", new String[] { episode.getTitle() }));
      return;
    }

    LOGGER.info("Renaming TvShow '{}', episode S{} E{}", episode.getTvShow().getTitle(), episode.getSeason(), episode.getEpisode());

    if (episode.isDisc()) {
      renameEpisodeAsDisc(episode);
      return;
    }

    // store all episodes for this media file
    List<TvShowEpisode> episodes = TvShowList.getTvEpisodesByFile(episode.getTvShow(), originalVideoMediaFile.getFile());

    // make sure we have actual stacking markers
    episode.reEvaluateStacking();

    // all the good & needed mediafiles
    List<MediaFile> needed = new ArrayList<>();
    List<MediaFile> cleanup = new ArrayList<>(episode.getMediaFiles());
    cleanup.removeAll(Collections.singleton((MediaFile) null)); // remove all NULL ones!

    Path tvShowRoot = episode.getTvShow().getPathNIO();
    MediaEntityFilenameHistory fileNameHistory = new MediaEntityFilenameHistory();

    String seasonFoldername = getSeasonFoldername(episode.getTvShow(), episode);
    Path seasonFolder = episode.getTvShow().getPathNIO();

    if (StringUtils.isNotBlank(seasonFoldername)) {
      seasonFolder = resolvePath(episode.getTvShow().getPathNIO(), seasonFoldername);
      if (!WebDavDataSourceHelper.isWebDavPath(seasonFolder.toString()) && !Files.exists(seasonFolder)) {
        try {
          Files.createDirectory(seasonFolder);
        }
        catch (IOException ignored) {
        }
      }
    }

    // BASENAME
    String oldVideoBasename = episode.getVideoBasenameWithoutStacking();

    // ######################################################################
    // ## rename VIDEO (move 1:1)
    // ######################################################################
    for (MediaFile vid : episode.getMediaFiles(MediaFileType.VIDEO)) {
      LOGGER.trace("Rename 1:1 {} {}", vid.getType(), vid.getFileAsPath());

      List<MediaFile> newFilenames = generateEpisodeFilenames(episode.getTvShow(), vid, "");
      if (ListUtils.isEmpty(newFilenames)) {
        LOGGER.warn("Could not rename '{}' - no new filename generated!", vid.getFileAsPath());
        return;
      }

      MediaFile newMF = newFilenames.get(0); // there can be only one
      boolean ok = moveFile(vid.getFileAsPath(), newMF.getFileAsPath());
      if (ok) {
        fileNameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, vid.getFileAsPath(), newMF.getFileAsPath()));
        vid.setFile(newMF.getFileAsPath()); // update
        // if we move the episode in its own folder, we might need to upgrade the path as well!
        episode.setPath(newMF.getPath());
      }
      else {
        // not OK? just abort!
        return;
      }
      needed.add(vid); // add vid, since we're updating existing MF object
    }

    // ######################################################################
    // ## rename POSTER, FANART, BANNER, CLEARART, THUMB, LOGO, CLEARLOGO, DISCART (copy 1:N)
    // ######################################################################
    if (!TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      // we can have multiple ones, just get the newest one and copy(overwrite) them to all needed
      List<MediaFile> mfs = new ArrayList<>();
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.FANART));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.POSTER));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.BANNER));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.CLEARART));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.THUMB));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.LOGO));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.CLEARLOGO));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.DISC));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.CHARACTERART));
      mfs.add(episode.getNewestMediaFilesOfType(MediaFileType.KEYART));
      mfs.removeAll(Collections.singleton((MediaFile) null)); // remove all NULL ones!
      for (MediaFile mf : mfs) {
        LOGGER.trace("Rename 1:N {} {}", mf.getType(), mf.getFileAsPath());
        List<MediaFile> newMFs = generateEpisodeFilenames(episode.getTvShow(), mf, oldVideoBasename); // 1:N
        for (MediaFile newMF : newMFs) {
          boolean ok = copyFile(mf.getFileAsPath(), newMF.getFileAsPath());
          if (ok) {
            fileNameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, mf.getFileAsPath(), newMF.getFileAsPath()));
            needed.add(newMF);

            // update the cached image by just COPYing it around
            if (ImageCache.isImageCached(mf.getFileAsPath())) {
              Path oldCache = ImageCache.getAbsolutePath(mf);
              Path newCache = ImageCache.getAbsolutePath(newMF);
              LOGGER.trace("updating imageCache {} -> {}", oldCache, newCache);
              // just use plain copy here, since we do not need all the safety checks done in our method
              try {
                Files.copy(oldCache, newCache);
              }
              catch (IOException e) {
                LOGGER.warn("Error moving cached file '{}' - '{}'", oldCache, e.getMessage());
              }
            }
          }
        }
      }
    }

    // ######################################################################
    // ## rename NFO (copy 1:N) - only TMM NFOs
    // ######################################################################
    if (!TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      // we need to find the newest, valid TMM NFO
      MediaFile nfo = MediaFile.EMPTY_MEDIAFILE;
      for (MediaFile mf : episode.getMediaFiles(MediaFileType.NFO)) {
        if (mf.getFiledate() >= nfo.getFiledate()) {// && TvShowEpisodeConnectors.isValidNFO(mf.getFileAsPath())) { //FIXME
          nfo = new MediaFile(mf);
        }
      }

      if (nfo != MediaFile.EMPTY_MEDIAFILE) { // one valid found? copy our NFO to all variants
        List<MediaFile> newNFOs = generateEpisodeFilenames(episode.getTvShow(), nfo, oldVideoBasename); // 1:N
        if (!newNFOs.isEmpty()) {
          // ok, at least one has been set up
          for (MediaFile newNFO : newNFOs) {
            boolean ok = copyFile(nfo.getFileAsPath(), newNFO.getFileAsPath());
            if (ok) {
              fileNameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, nfo.getFileAsPath(), newNFO.getFileAsPath()));
              needed.add(newNFO);
            }
          }
        }
        else {
          // list was empty, so even remove this NFO
          cleanup.add(nfo);
        }
      }
      else {
        LOGGER.trace("No valid NFO found for this episode");
      }
    }

    // ######################################################################
    // ## rename subtitles (copy 1:1)
    // ######################################################################
    if (!TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      for (MediaFile subtitle : episode.getMediaFiles(MediaFileType.SUBTITLE)) {
        LOGGER.trace("Rename 1:1 {} {}", subtitle.getType(), subtitle.getFileAsPath());
        MediaFile newMF = generateEpisodeFilenames(episode.getTvShow(), subtitle, oldVideoBasename).get(0); // there can be only one
        boolean ok = moveFile(subtitle.getFileAsPath(), newMF.getFileAsPath());
        if (ok) {
          if (newMF.getFilename().endsWith(".sub")) {
            // when having a .sub, also rename .idx (don't care if error)
            try {
              Path oldidx = subtitle.getFileAsPath().resolveSibling(subtitle.getFilename().replaceFirst("sub$", "idx"));
              Path newidx = newMF.getFileAsPath().resolveSibling(newMF.getFilename().replaceFirst("sub$", "idx"));
              Utils.moveFileSafe(oldidx, newidx);

              MediaFile idx = new MediaFile(newidx);
              needed.add(idx);
              fileNameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, oldidx, newidx));
            }
            catch (Exception e) {
              // no idx found or error - ignore
            }
          }
          needed.add(newMF);
          fileNameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, subtitle.getFileAsPath(), newMF.getFileAsPath()));
        }
        else {
          LOGGER.error("Could not rename subtitle file '{}'", subtitle.getFileAsPath());
          needed.add(subtitle);
        }
      }
    }

    // ######################################################################
    // ## rename all other types (copy 1:1)
    // ######################################################################
    if (!TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      List<MediaFile> mfs = new ArrayList<>(episode.getMediaFilesExceptType(MediaFileType.VIDEO, MediaFileType.NFO, MediaFileType.POSTER,
          MediaFileType.FANART, MediaFileType.BANNER, MediaFileType.CLEARART, MediaFileType.THUMB, MediaFileType.LOGO, MediaFileType.CLEARLOGO,
          MediaFileType.DISC, MediaFileType.CHARACTERART, MediaFileType.KEYART, MediaFileType.SUBTITLE));
      mfs.removeAll(Collections.singleton((MediaFile) null)); // remove all NULL ones!
      for (MediaFile other : mfs) {
        LOGGER.trace("Rename 1:1 {} - {}", other.getType(), other.getFileAsPath());

        if ("idx".equalsIgnoreCase(other.getExtension())) {
          // .idx is a sidecar file for .sub - we handled this above
          continue;
        }

        List<MediaFile> newMFs = generateEpisodeFilenames(episode.getTvShow(), other, oldVideoBasename); // 1:N
        newMFs.removeAll(Collections.singleton((MediaFile) null)); // remove all NULL ones!

        for (MediaFile newMF : newMFs) {
          boolean ok = copyFile(other.getFileAsPath(), newMF.getFileAsPath());
          if (ok) {
            fileNameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, other.getFileAsPath(), newMF.getFileAsPath()));
            needed.add(newMF);
          }
          else {
            // FIXME: what to do? not copied/exception... keep it for now...
            fileNameHistory.addFilenameHistory(createFilenameHistory(tvShowRoot, other.getFileAsPath(), other.getFileAsPath()));
            needed.add(other);
          }
        }
      }
    }

    // ######################################################################
    // ## invalidate image cache
    // ######################################################################
    if (!TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      for (MediaFile gfx : episode.getMediaFiles()) {
        ImageCache.invalidateCachedImage(gfx);
      }
    }

    // remove duplicate MediaFiles
    Set<MediaFile> newMFs = new LinkedHashSet<>(needed);
    needed.clear();
    needed.addAll(newMFs);

    // 保存所有非视频文件的引用，当仅处理视频文件时使用
    List<MediaFile> nonVideoFiles = new ArrayList<>();
    if (TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      // 获取第一个剧集的非视频文件引用
      if (!episodes.isEmpty()) {
        nonVideoFiles.addAll(episodes.get(0).getMediaFilesExceptType(MediaFileType.VIDEO));
      }
    }

    // ######################################################################
    // ## CLEANUP - delete all files marked for cleanup, which are not "needed"
    // ######################################################################
    LOGGER.debug("Cleanup...");
    if (!TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      // get all existing files in the episode dir, since Files.exist is not reliable in OSX
      List<Path> existingFiles;
      if (WebDavDataSourceHelper.isWebDavPath(episode.getPath())) {
        // For WebDAV, skip file listing cleanup (not supported for virtual paths)
        existingFiles = new ArrayList<>();
      }
      else {
        existingFiles = Utils.listFilesRecursive(episode.getPathNIO());
      }

      for (int i = cleanup.size() - 1; i >= 0; i--) {
        // cleanup files which are not needed
        if (!needed.contains(cleanup.get(i))) {
          MediaFile cl = cleanup.get(i);
          if (existingFiles.contains(cl.getFileAsPath())) { // 使用现有文件列表，减少Files.exists()调用
            LOGGER.debug("Deleting {}", cl.getFileAsPath());
            Utils.deleteFileWithBackup(cl.getFileAsPath(), episode.getTvShow().getDataSource());
          }

          // Skip empty directory check for WebDAV paths (Files.newDirectoryStream not supported)
          if (!WebDavDataSourceHelper.isWebDavPath(episode.getPath())) {
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(cl.getFileAsPath().getParent())) {
              if (!directoryStream.iterator().hasNext()) {
                // no iterator = empty
                LOGGER.debug("Deleting empty Directory {}", cl.getFileAsPath().getParent());
                Files.delete(cl.getFileAsPath().getParent()); // do not use recursive her
              }
            }
            catch (IOException e) {
              LOGGER.error("Error in cleanup of '{}' - '{}'", cl.getFileAsPath(), e.getMessage());
            }
          }
        }
      }

      // 调用额外的清理方法，与电影重命名保持一致
      // cleanupUnwantedFiles(episode);
      // removeEmptySubfolders(episode);
    }

    // check if there has been _any_ change (or if that EP has already been renamed before that)
    boolean changeDetected = false;
    for (MediaEntityFilenameHistory.FilenameHistory history : fileNameHistory.getFilenameHistory()) {
      if (!history.oldFilename().equals(history.newFilename())) {
        changeDetected = true;
        break;
      }
    }

    if (changeDetected) {
      // update paths/mfs for all relevant episodes
      for (TvShowEpisode e : episodes) {
        e.removeAllMediaFiles();

        // 当仅处理视频文件时，将非视频文件添加回needed列表
        List<MediaFile> finalNeeded = new ArrayList<>(needed);
        if (TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
          finalNeeded.addAll(nonVideoFiles);
        }

        e.addToMediaFiles(finalNeeded);
        e.setPath(episode.getPath());

        // Update file size information after rename based on TV show settings
        if (Settings.getInstance().isTvShowUpdateFileSizeOnRename()) {
          e.updateFileSizeInformation();
        }

        // Only gather full media information if enabled in settings
        if (Settings.getInstance().isFetchVideoInfoOnUpdate()) {
          e.gatherMediaFileInformation(false);
        }

        // rename history
        e.setRenameHistory(fileNameHistory);

        e.saveToDb();

        // ######################################################################
        // ## build up image cache
        // ######################################################################
        if (Settings.getInstance().isImageCache() && !TvShowModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
          for (MediaFile gfx : e.getMediaFiles()) {
            ImageCache.cacheImageSilently(gfx, false);
          }
        }
      }
    }
  }

  /**
   * renames the episode as disc
   * 
   * @param episode
   *          the episode to be renamed
   */
  private static void renameEpisodeAsDisc(TvShowEpisode episode) {
    MediaEntityFilenameHistory fileNameHistory = new MediaEntityFilenameHistory();

    // get the first MF of this episode
    MediaFile mf = episode.getMainVideoFile();
    List<TvShowEpisode> eps = TvShowList.getTvEpisodesByFile(episode.getTvShow(), mf.getFile());

    // and do some checks
    if (!episode.isDisc() || !mf.isDiscFile()) {
      return;
    }

    Path disc;
    Path epFolder;

    if (DISC_FOLDERS.contains(mf.getFileAsPath().getFileName().toString().toLowerCase(Locale.ROOT))) {
      // hit on new structure
      // \Season 1\S01E02E03\VIDEO_TS
      // ........ \epFolder \disc-mf
      disc = mf.getFileAsPath();
      epFolder = disc.getParent();
    }
    else if (DISC_FOLDERS.contains(mf.getFileAsPath().getParent().getFileName().toString().toLowerCase(Locale.ROOT))) {
      // hit on old structure
      // \Season 1\S01E02E03\VIDEO_TS\VIDEO_TS.VOB
      // ........ \epFolder \disc... \mf
      disc = mf.getFileAsPath().getParent();
      epFolder = disc.getParent();
    }
    else {
      // invalid/no structure - what to do with em? renaming EP folder might work/be enough...
      // \Season 1\S01E02E03\VIDEO_TS.VOB
      // ........ \epFolder \mf
      LOGGER.warn("TV show '{}', Episode S{} E{} is labeled as 'on BD/DVD', but structure seems not to match. Try our best to get this right... o_O",
          episode.getTvShow().getTitle(), episode.getSeason(), episode.getEpisode());
      disc = mf.getFileAsPath().getParent();
      epFolder = disc;
      // return;
    }

    // create SeasonDir
    String seasonFoldername = getSeasonFoldername(episode.getTvShow(), episode);
    Path seasonFolder = episode.getTvShow().getPathNIO();
    if (StringUtils.isNotBlank(seasonFoldername)) {
      seasonFolder = resolvePath(episode.getTvShow().getPathNIO(), seasonFoldername);
      if (!WebDavDataSourceHelper.isWebDavPath(seasonFolder.toString()) && !Files.exists(seasonFolder)) {
        try {
          Files.createDirectory(seasonFolder);
        }
        catch (IOException ignored) {
        }
      }
    }

    // rename epFolder accordingly
    String newFoldername = FilenameUtils.getBaseName(generateFoldername(episode.getTvShow(), mf)); // w/o extension
    if (StringUtils.isBlank(newFoldername)) {
      LOGGER.warn("Empty disc folder name for TV show '{}', Episode S{} E{} - exiting", episode.getTvShow().getTitle(), episode.getSeason(),
          episode.getEpisode());
      return;
    }

    Path newEpFolder = resolvePath(seasonFolder, newFoldername);

    try {
      // Normalize paths for comparison (handles WebDAV path format)
      String epFolderNormalized = WebDavDataSourceHelper.normalizeWebDavPath(epFolder.toString());
      String newEpFolderNormalized = WebDavDataSourceHelper.normalizeWebDavPath(newEpFolder.toString());

      boolean pathsEqual = WebDavDataSourceHelper.isWebDavPath(epFolderNormalized) ? epFolderNormalized.equals(newEpFolderNormalized)
          : epFolder.toAbsolutePath().toString().equals(newEpFolder.toAbsolutePath().toString());

      if (!pathsEqual) {
        boolean ok = false;
        try {
          ok = moveDirectory(epFolder, newEpFolder);
        }
        catch (Exception e) {
          LOGGER.error("Could not rename episode S{} E{} of '{} - '{}'", episode.getSeason(), episode.getEpisode(), episode.getTvShow().getTitle(),
              e.getMessage());
          MessageManager.getInstance()
              .pushMessage(new Message(MessageLevel.ERROR, epFolder, "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
        }
        if (ok) {
          // iterate over all EPs & MFs and fix new path
          LOGGER.debug("updating *all* MFs for new path -> {}", newEpFolder);
          for (TvShowEpisode e : eps) {
            e.updateMediaFilePath(epFolder, newEpFolder);
            // For WebDAV, use normalized path string
            String newPathStr = WebDavDataSourceHelper.isWebDavPath(newEpFolderNormalized) ? newEpFolderNormalized
                : newEpFolder.toAbsolutePath().toString();
            e.setPath(newPathStr);

            fileNameHistory.addFilenameHistory(createFilenameHistory(episode.getTvShow().getPathNIO(), epFolder, newEpFolder));
            e.setRenameHistory(fileNameHistory);
            e.saveToDb();
          }
        }
        // and cleanup
        cleanEmptyDir(epFolder);
      }
      else {
        // old and new folder are equal, do nothing
      }
    }
    catch (Exception e) {
      LOGGER.error("Error moving video file '{}' to '{}' - '{}'", disc, newFoldername, e.getMessage());
      MessageManager.getInstance()
          .pushMessage(
              new Message(MessageLevel.ERROR, mf.getFilename(), "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
    }
  }

  public static void undoRename(TvShow tvShow) {
    if (tvShow.getRenameHistory() != null) {
      // undo the rename of the TV show
      undoRenameTvShow(tvShow);
    }

    for (var season : tvShow.getSeasons()) {
      undoRenameSeason(season);
    }

    tvShow.saveToDb();
  }

  private static void undoRenameTvShow(TvShow tvShow) {
    // TV show root
    if (StringUtils.isNotBlank(tvShow.getRenameHistory().getOldPath())) {
      Path srcDir = WebDavDataSourceHelper.getWebDavPath(tvShow.getRenameHistory().getNewPath());
      Path destDir = WebDavDataSourceHelper.getWebDavPath(tvShow.getRenameHistory().getOldPath());

      // Normalize paths for comparison (handles WebDAV path format)
      String srcNormalized = WebDavDataSourceHelper.normalizeWebDavPath(srcDir.toString());
      String destNormalized = WebDavDataSourceHelper.normalizeWebDavPath(destDir.toString());

      // move directory if needed (use string comparison for WebDAV paths)
      boolean pathsEqual = WebDavDataSourceHelper.isWebDavPath(srcNormalized) ? srcNormalized.equals(destNormalized)
          : srcDir.toAbsolutePath().toString().equals(destDir.toAbsolutePath().toString());

      if (!pathsEqual) {
        try {
          boolean ok = moveDirectory(srcDir, destDir);
          if (ok) {
            tvShow.updateMediaFilePath(srcDir, destDir); // TvShow MFs
            tvShow.setPath(tvShow.getRenameHistory().getOldPath());

            for (TvShowSeason tvShowSeason : tvShow.getSeasons()) {
              tvShowSeason.updateMediaFilePath(srcDir, destDir);
            }

            for (TvShowEpisode episode : tvShow.getEpisodes()) {
              episode.replacePathForRenamedTvShowRoot(srcDir, destDir);
              episode.updateMediaFilePath(srcDir, destDir);
            }

            // ######################################################################
            // ## build up image cache
            // ######################################################################
            if (Settings.getInstance().isImageCache()) {
              for (MediaFile gfx : tvShow.getMediaFiles()) {
                ImageCache.cacheImageSilently(gfx, false);
              }
            }
          }
        }
        catch (Exception e) {
          LOGGER.error("Error moving folder '{}' to '{}' - '{}'", srcDir, destDir, e.getMessage());
          MessageManager.getInstance()
              .pushMessage(new Message(MessageLevel.ERROR, srcDir, "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
        }
      }
    }

    // TV show MFs
    Path tvShowRoot = tvShow.getPathNIO();
    List<MediaFile> needed = new ArrayList<>();

    for (MediaFile mediaFile : tvShow.getMediaFiles()) {
      MediaEntityFilenameHistory.FilenameHistory filenameHistory = findFilenameHistoryForMediaFile(tvShow, mediaFile);
      if (filenameHistory == null) {
        LOGGER.debug("could not undo rename for '{}' - history not found", mediaFile.getFilename());
        continue;
      }

      LOGGER.trace("Rename 1:1 {} - {}", mediaFile.getType(), mediaFile.getFileAsPath());
      MediaFile oldMF = new MediaFile(mediaFile);
      oldMF.setFile(resolvePath(tvShowRoot, filenameHistory.oldFilename()));

      boolean ok = moveFile(mediaFile.getFileAsPath(), oldMF.getFileAsPath());
      if (ok) {
        mediaFile.setFile(oldMF.getFileAsPath()); // update
        needed.add(mediaFile);
      }
    }

    // remove duplicate MediaFiles
    Set<MediaFile> newMFs = new LinkedHashSet<>(needed);
    needed.clear();
    needed.addAll(newMFs);

    tvShow.removeAllMediaFiles();

    // ######################################################################
    // ## build up image cache
    // ######################################################################
    if (Settings.getInstance().isImageCache()) {
      for (MediaFile gfx : needed) {
        ImageCache.cacheImageSilently(gfx, false);
      }
    }

    // give the file system a bit to write the files
    ThreadUtils.sleep(250);

    tvShow.addToMediaFiles(needed);

    // Update file size information after rename based on TV show settings
    if (Settings.getInstance().isTvShowUpdateFileSizeOnRename()) {
      tvShow.updateFileSizeInformation();
    }

    // Only gather full media information if enabled in settings
    if (Settings.getInstance().isFetchVideoInfoOnUpdate()) {
      tvShow.gatherMediaFileInformation(false);
    }

    tvShow.setRenameHistory(null);
  }

  private static void undoRenameSeason(TvShowSeason season) {
    // TV show MFs
    Path tvShowRoot = season.getTvShow().getPathNIO();
    List<MediaFile> needed = new ArrayList<>();

    for (MediaFile mediaFile : season.getMediaFiles()) {
      MediaEntityFilenameHistory.FilenameHistory filenameHistory = findFilenameHistoryForMediaFile(season.getTvShow(), mediaFile);
      if (filenameHistory == null) {
        LOGGER.debug("could not undo rename for '{}' - history not found", mediaFile.getFilename());
        continue;
      }

      LOGGER.trace("Rename 1:1 {} - {}", mediaFile.getType(), mediaFile.getFileAsPath());
      MediaFile oldMF = new MediaFile(mediaFile);
      oldMF.setFile(resolvePath(tvShowRoot, filenameHistory.oldFilename()));

      boolean ok = moveFile(mediaFile.getFileAsPath(), oldMF.getFileAsPath());
      if (ok) {
        mediaFile.setFile(oldMF.getFileAsPath()); // update
        needed.add(mediaFile);
      }
    }

    // remove duplicate MediaFiles
    Set<MediaFile> newMFs = new LinkedHashSet<>(needed);
    needed.clear();
    needed.addAll(newMFs);

    season.removeAllMediaFiles();

    // ######################################################################
    // ## build up image cache
    // ######################################################################
    if (Settings.getInstance().isImageCache()) {
      for (MediaFile gfx : needed) {
        ImageCache.cacheImageSilently(gfx, false);
      }
    }

    // give the file system a bit to write the files
    ThreadUtils.sleep(250);

    season.addToMediaFiles(needed);

    // Update file size information after rename based on TV show settings
    if (Settings.getInstance().isTvShowUpdateFileSizeOnRename()) {
      season.updateFileSizeInformation();
    }

    // Only gather full media information if enabled in settings
    if (Settings.getInstance().isFetchVideoInfoOnUpdate()) {
      season.gatherMediaFileInformation(false);
    }

    season.setRenameHistory(null);
  }

  private static void cleanEmptyDir(Path dir) {
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir)) {
      if (!directoryStream.iterator().hasNext()) {
        // no iterator = empty
        LOGGER.debug("Deleting empty Directory - {}", dir);
        Files.delete(dir); // do not use recursive her
        return;
      }
    }
    catch (IOException ignored) {
    }
  }

  public static void undoRename(TvShowEpisode episode) {
    if (episode.getRenameHistory() == null) {
      LOGGER.debug("could not undo rename - no history available");
      return;
    }

    if (episode.isDisc()) {
      undoRenameEpisodeAsDisc(episode);
      return;
    }

    List<MediaFile> needed = new ArrayList<>();

    Path tvShowRoot = episode.getTvShow().getPathNIO();

    // try the VIDEO file(s) first
    for (MediaFile vid : episode.getMediaFiles(MediaFileType.VIDEO)) {
      MediaEntityFilenameHistory.FilenameHistory filenameHistory = findFilenameHistoryForMediaFile(episode, vid);
      if (filenameHistory == null) {
        LOGGER.debug("could not undo rename - VIDEO file history not found");
        return;
      }

      LOGGER.trace("Rename 1:1 {} - {}", vid.getType(), vid.getFileAsPath());
      MediaFile oldMF = new MediaFile(vid);
      oldMF.setFile(resolvePath(tvShowRoot, filenameHistory.oldFilename()));

      boolean ok = moveFile(vid.getFileAsPath(), oldMF.getFileAsPath());
      if (ok) {
        vid.setFile(oldMF.getFileAsPath()); // update
        // if we move the episode in its own folder, we might need to upgrade the path as well!
        episode.setPath(oldMF.getPath());
      }
      else {
        LOGGER.error("Could not move video file ({}) of episode S{} E{} of '{}' - abort renaming", vid, episode.getSeason(), episode.getEpisode(),
            episode.getTvShow().getTitle());
        return;
      }
      needed.add(vid); // add vid, since we're updating existing MF object
    }

    // and all others
    for (MediaFile mediaFile : episode.getMediaFilesExceptType(MediaFileType.VIDEO)) {
      MediaEntityFilenameHistory.FilenameHistory filenameHistory = findFilenameHistoryForMediaFile(episode, mediaFile);
      if (filenameHistory == null) {
        LOGGER.debug("could not undo rename for '{}' - history not found", mediaFile.getFilename());
        continue;
      }

      LOGGER.trace("Rename 1:1 {} - {}", mediaFile.getType(), mediaFile.getFileAsPath());
      MediaFile oldMF = new MediaFile(mediaFile);
      oldMF.setFile(resolvePath(tvShowRoot, filenameHistory.oldFilename()));

      boolean ok = moveFile(mediaFile.getFileAsPath(), oldMF.getFileAsPath());
      if (ok) {
        mediaFile.setFile(oldMF.getFileAsPath()); // update
        needed.add(mediaFile);
      }
    }

    // remove duplicate MediaFiles
    Set<MediaFile> newMFs = new LinkedHashSet<>(needed);
    needed.clear();
    needed.addAll(newMFs);

    episode.removeAllMediaFiles();

    // ######################################################################
    // ## build up image cache
    // ######################################################################
    if (Settings.getInstance().isImageCache()) {
      for (MediaFile gfx : needed) {
        ImageCache.cacheImageSilently(gfx, false);
      }
    }

    // give the file system a bit to write the files
    ThreadUtils.sleep(250);

    episode.addToMediaFiles(needed);

    // Update file size information after rename based on TV show settings
    if (Settings.getInstance().isTvShowUpdateFileSizeOnRename()) {
      episode.updateFileSizeInformation();
    }

    // Only gather full media information if enabled in settings
    if (Settings.getInstance().isFetchVideoInfoOnUpdate()) {
      episode.gatherMediaFileInformation(false);
    }

    // remove history
    episode.setRenameHistory(null);

    episode.saveToDb();

    // cleanup old path
    String tvShowRootStr = tvShowRoot.toString();
    if (WebDavDataSourceHelper.isWebDavPath(tvShowRootStr)) {
      int deleted = WebDavFileOperations.deleteEmptyDirectoriesRecursive(tvShowRootStr);
      if (deleted > 0) {
        LOGGER.debug("Deleted {} empty WebDAV directories under '{}'", deleted, tvShowRootStr);
      }
    }
    else {
      try {
        Utils.deleteEmptyDirectoryRecursive(tvShowRoot);
      }
      catch (IOException e) {
        LOGGER.warn("Could not delete empty subfolders of '{}' - '{}'", tvShowRoot, e.getMessage());
      }
    }
  }

  private static void undoRenameEpisodeAsDisc(TvShowEpisode episode) {
    // get the first MF of this episode
    MediaFile mf = episode.getMainVideoFile();
    List<TvShowEpisode> eps = TvShowList.getTvEpisodesByFile(episode.getTvShow(), mf.getFile());

    // and do some checks
    if (!episode.isDisc() || !mf.isDiscFile() || ListUtils.isEmpty(episode.getRenameHistory().getFilenameHistory())) {
      return;
    }

    MediaEntityFilenameHistory.FilenameHistory filenameHistory = episode.getRenameHistory().getFilenameHistory().get(0);

    Path newEpFolder = resolvePath(episode.getTvShow().getPathNIO(), filenameHistory.newFilename());
    Path oldEpFolder = resolvePath(episode.getTvShow().getPathNIO(), filenameHistory.oldFilename());

    try {
      boolean ok = false;
      try {
        ok = moveDirectory(newEpFolder, oldEpFolder);
      }
      catch (Exception e) {
        LOGGER.error("Could not move episode files ({}) of episode S{} E{} of '{}'", oldEpFolder, episode.getSeason(), episode.getEpisode(),
            episode.getTvShow().getTitle());
        MessageManager.getInstance()
            .pushMessage(new Message(MessageLevel.ERROR, newEpFolder, "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
      }
      if (ok) {
        // iterate over all EPs & MFs and fix new path
        LOGGER.debug("updating *all* MFs for new path -> {}", newEpFolder);
        // For WebDAV, use normalized path string
        String oldPathNormalized = WebDavDataSourceHelper.normalizeWebDavPath(oldEpFolder.toString());
        String oldPathStr = WebDavDataSourceHelper.isWebDavPath(oldPathNormalized) ? oldPathNormalized : oldEpFolder.toAbsolutePath().toString();
        for (TvShowEpisode e : eps) {
          e.updateMediaFilePath(newEpFolder, oldEpFolder);
          e.setPath(oldPathStr);
          e.setRenameHistory(null);
          e.saveToDb();
        }
      }
      // and cleanup
      cleanEmptyDir(newEpFolder);
    }
    catch (Exception e) {
      LOGGER.error("Could not move episode files ({}) of episode S{} E{} of '{}' - abort renaming", oldEpFolder, episode.getSeason(),
          episode.getEpisode(), episode.getTvShow().getTitle());
      MessageManager.getInstance()
          .pushMessage(
              new Message(MessageLevel.ERROR, mf.getFilename(), "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
    }
  }

  /**
   * generates the foldername of a TvShow MediaFile according to settings <b>(without path)</b><br>
   * Mainly for DISC files
   * 
   * @param tvShow
   *          the tvShow
   * @param mf
   *          the MF for multiepisode
   * @return the file name for media file
   */
  public static String generateFoldername(TvShow tvShow, MediaFile mf) {
    List<TvShowEpisode> eps = TvShowList.getTvEpisodesByFile(tvShow, mf.getFile());
    if (ListUtils.isEmpty(eps)) {
      return "";
    }

    return createDestination(TvShowModuleManager.getInstance().getSettings().getRenamerFilename(), eps);
  }

  /**
   * generates a list of filenames of a TvShow MediaFile according to settings
   *
   * @param tvShow
   *          the tvShow
   * @param mf
   *          the MF for multiepisode
   * @param videoBasename
   *          the original video file name
   * @return the file name for the media file
   */
  public static List<MediaFile> generateEpisodeFilenames(TvShow tvShow, MediaFile mf, String videoBasename) {
    return generateEpisodeFilenames("", tvShow, mf, videoBasename);
  }

  /**
   * generates a list of filenames of a TvShow MediaFile according to settings
   *
   * @param template
   *          the renaming template
   * @param tvShow
   *          the tvShow
   * @param mf
   *          the MF for multiepisode
   * @param oldVideoBasename
   *          the original video file name
   * @return the file name for the media file
   */
  public static List<MediaFile> generateEpisodeFilenames(String template, TvShow tvShow, MediaFile mf, String oldVideoBasename) {
    // return list of all generated MFs
    List<MediaFile> newFiles = new ArrayList<>();

    List<TvShowEpisode> eps = TvShowList.getTvEpisodesByFile(tvShow, mf.getFile());
    if (ListUtils.isEmpty(eps)) {
      return newFiles;
    }

    // sort the episodes
    eps.sort((ep1, ep2) -> {
      if (ep1.getSeason() != ep2.getSeason()) {
        return Integer.compare(ep1.getSeason(), ep2.getSeason());
      }
      return Integer.compare(ep1.getEpisode(), ep2.getEpisode());
    });

    // where there are multiple episodes with the same season/episode, we just need to take the first one (multiple different video files sharing the
    // same sidecar files)
    TvShowEpisode firstEp = eps.get(0);
    boolean singleSE = true;
    for (int i = 1; i < eps.size(); i++) {
      if (eps.get(i).getSeason() != firstEp.getSeason() || eps.get(i).getEpisode() != firstEp.getEpisode()) {
        singleSE = false;
        break;
      }
    }

    if (singleSE) {
      eps.clear();
      eps.add(firstEp);
    }

    String newFilename = "";
    // FIXME: check, where/when the stacking marker gets added, and WHICH (from which stacked video)
    if (StringUtils.isBlank(template)) {
      newFilename = createDestination(TvShowModuleManager.getInstance().getSettings().getRenamerFilename(), eps);
    }
    else {
      newFilename = createDestination(template, eps);
    }

    if (mf.getStacking() > 0) {
      // remove the stacking here, so we have a correct basename
      newFilename = Utils.cleanFolderStackingMarkers(newFilename); // i know, but this needs no extension ;)
    }

    String seasonFoldername = getSeasonFoldername(tvShow, eps.get(0));
    Path seasonFolder = tvShow.getPathNIO();
    if (StringUtils.isNotBlank(seasonFoldername)) {
      seasonFolder = resolvePath(tvShow.getPathNIO(), seasonFoldername);
    }

    // no new filename? just move the file
    if (StringUtils.isBlank(newFilename)) {
      MediaFile mediaFile = new MediaFile(mf);
      mediaFile.setFile(resolvePath(seasonFolder, mf.getFilename()));
      newFiles.add(mediaFile);
      return newFiles;
    }

    switch (mf.getType()) {
      ////////////////////////////////////////////////////////////////////////
      // VIDEO
      ////////////////////////////////////////////////////////////////////////
      case VIDEO:
        MediaFile video = new MediaFile(mf);
        newFilename += getStackingString(mf); // ToDo
        newFilename += "." + mf.getExtension();
        video.setFile(resolvePath(seasonFolder, newFilename));
        newFiles.add(video);
        break;

      ////////////////////////////////////////////////////////////////////////
      // NFO
      ////////////////////////////////////////////////////////////////////////
      case NFO:
        MediaFile nfo = new MediaFile(mf);
        newFilename += "." + mf.getExtension();
        nfo.setFile(resolvePath(seasonFolder, newFilename));
        newFiles.add(nfo);
        break;

      ////////////////////////////////////////////////////////////////////////
      // THUMB
      ////////////////////////////////////////////////////////////////////////
      case THUMB:
        for (TvShowEpisodeThumbNaming thumbNaming : TvShowModuleManager.getInstance().getSettings().getEpisodeThumbFilenames()) {
          String thumbFilename = thumbNaming.getFilename(newFilename, getMediaFileExtension(mf));
          MediaFile thumb = new MediaFile(mf);
          thumb.setFile(resolvePath(seasonFolder, thumbFilename));
          newFiles.add(thumb);
        }
        break;

      ////////////////////////////////////////////////////////////////////////
      // SUBTITLE
      ////////////////////////////////////////////////////////////////////////
      case SUBTITLE:
        List<MediaFileSubtitle> subtitles = mf.getSubtitles();
        newFilename += getStackingString(mf);
        String subtitleFilename = newFilename;
        if (subtitles != null && !subtitles.isEmpty()) {
          MediaFileSubtitle sub = mf.getSubtitles().get(0);
          if (sub != null) {
            if (!sub.getLanguage().isEmpty()) {
              String lang = LanguageStyle.getLanguageCodeForStyle(sub.getLanguage(),
                  TvShowModuleManager.getInstance().getSettings().getSubtitleLanguageStyle());
              if (StringUtils.isBlank(lang)) {
                lang = sub.getLanguage();
              }
              subtitleFilename += "." + lang;
            }

            if (sub.isForced()) {
              subtitleFilename += ".forced";
            }
            if (sub.isSdh()) {
              subtitleFilename += ".sdh"; // double possible?!
            }
            if (StringUtils.isNotBlank(sub.getTitle())) {
              subtitleFilename += "." + sub.getTitle().strip();
            }
          }
        }

        // still no subtitle filename? take at least the whole filename
        if (StringUtils.isBlank(subtitleFilename)) {
          subtitleFilename = newFilename;
        }

        if (StringUtils.isNotBlank(subtitleFilename)) {
          MediaFile subtitle = new MediaFile(mf);
          subtitle.setFile(resolvePath(seasonFolder, subtitleFilename + "." + mf.getExtension()));
          newFiles.add(subtitle);
        }
        break;

      ////////////////////////////////////////////////////////////////////////
      // FANART
      ////////////////////////////////////////////////////////////////////////
      case FANART:
        MediaFile fanart = new MediaFile(mf);
        fanart.setFile(resolvePath(seasonFolder, newFilename + "-fanart." + getMediaFileExtension(mf)));
        newFiles.add(fanart);
        break;

      ////////////////////////////////////////////////////////////////////////
      // TRAILER
      ////////////////////////////////////////////////////////////////////////
      case TRAILER:
        MediaFile trailer = new MediaFile(mf);
        trailer.setFile(resolvePath(seasonFolder, newFilename + "-trailer." + mf.getExtension()));
        newFiles.add(trailer);
        break;

      ////////////////////////////////////////////////////////////////////////
      // MEDIAINFO
      ////////////////////////////////////////////////////////////////////////
      case MEDIAINFO:
        MediaFile mediainfo = new MediaFile(mf);
        newFilename += getStackingString(mf); // ToDo
        mediainfo.setFile(resolvePath(seasonFolder, newFilename + "-mediainfo." + mf.getExtension()));
        newFiles.add(mediainfo);
        break;

      ////////////////////////////////////////////////////////////////////////
      // VSMETA
      ////////////////////////////////////////////////////////////////////////
      case VSMETA:
        MediaFile vsmeta = new MediaFile(mf);
        // HACK: get video extension from "old" name, eg video.avi.vsmeta
        String videoExt = FilenameUtils.getExtension(FilenameUtils.getBaseName(mf.getFilename()));
        newFilename += "." + videoExt + ".vsmeta";
        vsmeta.setFile(resolvePath(seasonFolder, newFilename));
        newFiles.add(vsmeta);
        break;

      ////////////////////////////////////////////////////////////////////////
      // VIDEO_EXTRA
      ////////////////////////////////////////////////////////////////////////
      case EXTRA:
      case VIDEO_EXTRA:
        // this extra is for an episode -> move it at least to the season folder and try to replace the episode tokens
        MediaFile extra = new MediaFile(mf);
        if (MediaFileHelper.isExtraInDedicatedFolder(mf, tvShow)) {
          // do nothing
          newFiles.add(extra);
        }
        else {
          // try to detect the title of the extra file
          String extraTitle = mf.getBasename().replace(oldVideoBasename, "");
          extra.setFile(resolvePath(seasonFolder, newFilename + extraTitle + "." + mf.getExtension()));
          newFiles.add(extra);
        }
        break;

      ////////////////////////////////////////////////////////////////////////
      // SAMPLE
      ////////////////////////////////////////////////////////////////////////
      case SAMPLE:
        MediaFile sample = new MediaFile(mf);
        sample.setFile(resolvePath(seasonFolder, newFilename + "-sample." + mf.getExtension()));
        newFiles.add(sample);
        break;

      ////////////////////////////////////////////////////////////////////////
      // AUDIO / TEXT / UNKNOWN / VIDEO_EXTRA
      // take the unknown part of the file name and attach it to the new file name
      ////////////////////////////////////////////////////////////////////////
      case AUDIO:
      case TEXT:
      case UNKNOWN:
        // this is something extra for an episode -> try to replace the episode tokens and preserve the extra in the filename
        // try to detect the title of the extra file
        MediaFile other = new MediaFile(mf);
        boolean spaceSubstitution = TvShowModuleManager.getInstance().getSettings().isRenamerFilenameSpaceSubstitution();
        String spaceReplacement = TvShowModuleManager.getInstance().getSettings().getRenamerFilenameSpaceReplacement();
        String destination = cleanupDestination(newFilename + StringUtils.difference(oldVideoBasename, FilenameUtils.getBaseName(mf.getFilename())),
            spaceSubstitution, spaceReplacement);
        other.setFile(resolvePath(seasonFolder, destination + "." + mf.getExtension()));
        newFiles.add(other);
        break;

      // missing enums
      case BANNER:
      case CLEARART:
      case CLEARLOGO:
      case DISC:
      case EXTRATHUMB:
      case GRAPHIC:
      case LOGO:
      case POSTER:
      case CHARACTERART:
      case KEYART:
      case SEASON_POSTER:
      case SEASON_FANART:
      case SEASON_BANNER:
      case SEASON_THUMB:
      default:
        break;
    }

    return newFiles;
  }

  /**
   * 1:N mapping
   *
   * @param show
   *          the TvShow (clone) to use (mainly for new/old path)
   * @param season
   * @param mf
   * @return
   */
  public static List<MediaFile> generateSeasonFilenames(TvShow show, TvShowSeason season, MediaFile mf) {
    List<MediaFile> newFiles = new ArrayList<>();

    switch (mf.getType()) {
      case NFO:
        if (!season.getEpisodes().isEmpty() || TvShowModuleManager.getInstance().getSettings().isCreateMissingSeasonItems()) {
          for (TvShowSeasonNfoNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonNfoFilenames()) {
            String filename = naming.getFilename(season, mf.getExtension());
            if (StringUtils.isNotBlank(filename)) {
              MediaFile newMf = new MediaFile(mf);
              newMf.setFile(resolvePath(show.getPathNIO(), filename));
              newFiles.add(newMf);
            }
          }
        }
        break;

      case SEASON_POSTER:
        if (!season.getEpisodes().isEmpty() || TvShowModuleManager.getInstance().getSettings().isCreateMissingSeasonItems()) {
          for (TvShowSeasonPosterNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonPosterFilenames()) {
            String filename = naming.getFilename(season, mf.getExtension());
            if (StringUtils.isNotBlank(filename)) {
              MediaFile newMF = new MediaFile(mf);
              newMF.setFile(resolvePath(show.getPathNIO(), filename));
              newFiles.add(newMF);
            }
          }
        }
        break;

      case SEASON_FANART:
        if (!season.getEpisodes().isEmpty() || TvShowModuleManager.getInstance().getSettings().isCreateMissingSeasonItems()) {
          for (TvShowSeasonFanartNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonFanartFilenames()) {
            String filename = naming.getFilename(season, mf.getExtension());
            if (StringUtils.isNotBlank(filename)) {
              MediaFile newMF = new MediaFile(mf);
              newMF.setFile(resolvePath(show.getPathNIO(), filename));
              newFiles.add(newMF);
            }
          }
        }
        break;

      case SEASON_BANNER:
        if (!season.getEpisodes().isEmpty() || TvShowModuleManager.getInstance().getSettings().isCreateMissingSeasonItems()) {
          for (TvShowSeasonBannerNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonBannerFilenames()) {
            String filename = naming.getFilename(season, mf.getExtension());
            if (StringUtils.isNotBlank(filename)) {
              MediaFile newMF = new MediaFile(mf);
              newMF.setFile(resolvePath(show.getPathNIO(), filename));
              newFiles.add(newMF);
            }
          }
        }
        break;

      case SEASON_THUMB:
        if (!season.getEpisodes().isEmpty() || TvShowModuleManager.getInstance().getSettings().isCreateMissingSeasonItems()) {
          for (TvShowSeasonThumbNaming naming : TvShowModuleManager.getInstance().getSettings().getSeasonThumbFilenames()) {
            String filename = naming.getFilename(season, mf.getExtension());
            if (StringUtils.isNotBlank(filename)) {
              MediaFile newMF = new MediaFile(mf);
              newMF.setFile(resolvePath(show.getPathNIO(), filename));
              newFiles.add(newMF);
            }
          }
        }
        break;

      default:
        break;
    }
    return newFiles;
  }

  /**
   * generate the season folder name according to the settings
   *
   * @param show
   *          the TV show to generate the season folder for
   * @param season
   *          the season to generate the season folder name for
   * @return the folder name of that season
   */
  public static String getSeasonFoldername(TvShow show, TvShowSeason season) {
    TvShowEpisode firstEpisode = ListUtils.getFirst(season.getEpisodes());
    if (firstEpisode == null) {
      return "";
    }
    return getSeasonFoldername(TvShowModuleManager.getInstance().getSettings().getRenamerSeasonFoldername(), show, firstEpisode);
  }

  /**
   * generate the season folder name according to the settings
   *
   * @param show
   *          the TV show to generate the season folder for
   * @param episode
   *          the episode to generate the season folder name for
   * @return the folder name of that season
   */
  public static String getSeasonFoldername(TvShow show, TvShowEpisode episode) {
    return getSeasonFoldername(TvShowModuleManager.getInstance().getSettings().getRenamerSeasonFoldername(), show, episode);
  }

  /**
   * generate the season folder name with the given template
   *
   * @param template
   *          the given template
   * @param show
   *          the TV show to generate the season folder for
   * @param episode
   *          the episode to generate the season folder name for
   * @return the folder name of that season
   */
  public static String getSeasonFoldername(String template, TvShow show, TvShowEpisode episode) {
    String seasonFolderName = template;
    TvShowSeason tvShowSeason = show.getSeason(episode.getSeason());

    // should not happen, but check it
    if (tvShowSeason == null) {
      // return an empty string
      return "";
    }

    // season 0 = Specials
    if (tvShowSeason.getSeason() == 0 && TvShowModuleManager.getInstance().getSettings().isSpecialSeason()
        && !StringUtils.isBlank(TvShowModuleManager.getInstance().getSettings().getRenamerSeasonFoldername())) {
      seasonFolderName = "Specials";
    }
    else {
      // replace all other tokens
      seasonFolderName = createDestination(seasonFolderName, tvShowSeason, episode);
    }

    // only allow empty season dir if the season is in the filename (aka recommended)
    if (StringUtils.isBlank(seasonFolderName)
        && !TvShowRenamer.isRecommended(template, TvShowModuleManager.getInstance().getSettings().getRenamerFilename())) {
      seasonFolderName = "Season " + tvShowSeason.getSeason();
    }

    return seasonFolderName;
  }

  /**
   * generate the TV show folder name according to the settings
   * 
   * @param tvShow
   *          the TV show to generate the folder name for
   * @return the folder name
   */
  public static String getTvShowFoldername(TvShow tvShow) {
    return getTvShowFoldername(TvShowModuleManager.getInstance().getSettings().getRenamerTvShowFoldername(), tvShow);
  }

  /**
   * generate the TV show folder name according to the given template
   *
   * @param template
   *          the template to generate the folder name for
   * @param tvShow
   *          the TV show to generate the folder name for
   * @return the folder name
   */
  public static String getTvShowFoldername(String template, TvShow tvShow) {
    String newPathname;

    try {
      if (StringUtils.isNotBlank(template)) {
        String dataSource = tvShow.getDataSource();

        // Fix for WebDAV paths where datasource might be empty (e.g. migration or DB inconsistency)
        // If we don't fix this, it would fall back to Paths.get(dataSource, destination) which creates a relative path
        // that later gets converted to an absolute LOCAL path, breaking image cache locators.
        if (StringUtils.isBlank(dataSource) && WebDavDataSourceHelper.isWebDavPath(tvShow.getPath())) {
          String[] parts = WebDavDataSourceHelper.parseWebDavPath(tvShow.getPath());
          if (parts != null && parts.length > 0) {
            dataSource = "webdav://" + parts[0];
          }
        }
        String destination = createDestination(template, tvShow);

        // For WebDAV paths, use decoded dataSource and string concatenation
        // to avoid encoding issues with Paths.get()
        if (WebDavDataSourceHelper.isWebDavPath(dataSource)) {
          // Decode the dataSource for consistent path construction
          String decodedDataSource = WebDavDataSourceHelper.decodeWebDavPath(dataSource);
          // Ensure proper path separator
          if (!decodedDataSource.endsWith("/")) {
            decodedDataSource = decodedDataSource + "/";
          }
          newPathname = decodedDataSource + destination;
        }
        else {
          newPathname = Paths.get(dataSource, destination).toString();
        }
      }
      else {
        newPathname = tvShow.getPathNIO().toString();
      }
    }
    catch (Exception e) {
      // could not create a new pathname - stick to old
      newPathname = tvShow.getPathNIO().toString();
    }

    return newPathname;
  }

  /**
   * gets the token value ($x) from specified object
   * 
   * @param show
   *          our show
   * @param episode
   *          our episode
   * @param token
   *          the $x token
   * @return value or empty string
   */
  public static String getTokenValue(TvShow show, TvShowEpisode episode, String token) {
    try {
      Engine engine = createEngine();
      engine.setModelAdaptor(new TmmModelAdaptor());

      engine.setOutputAppender(new TmmOutputAppender() {
        @Override
        protected String replaceInvalidCharacters(String text) {
          if (isUnicodeReplacementEnabled()) {
            // Unicode replacement of forbidden characters
            text = StrgUtils.replaceForbiddenFilesystemCharacters(text);
          }

          return TvShowRenamer.replaceInvalidCharacters(text);
        }

        @Override
        protected boolean isUnicodeReplacementEnabled() {
          return TvShowModuleManager.getInstance().getSettings().isUnicodeReplacement();
        }
      });

      Map<String, Object> root = new HashMap<>();
      if (episode != null) {
        root.put("episode", episode);
        root.put("season", episode.getTvShowSeason());
      }
      root.put("tvShow", show);
      return engine.transform(JmteUtils.morphTemplate(token, TOKEN_MAP), root);
    }
    catch (Exception e) {
      LOGGER.warn("Unable to process token: '{}' - '{}'", token, e.getMessage());
      return token;
    }
  }

  /**
   * create the {@link Engine} to be used with JMTE
   *
   * @return the pre-created Engine
   */
  public static Engine createEngine() {
    Engine engine = Engine.createEngine();
    engine.registerRenderer(Number.class, new ZeroNumberRenderer());
    engine.registerRenderer(Path.class, new PathRenderer());
    engine.registerNamedRenderer(new NamedArrayRenderer());
    engine.registerNamedRenderer(new NamedArrayUniqueRenderer());
    engine.registerNamedRenderer(new NamedBitrateRenderer());
    engine.registerNamedRenderer(new NamedDateRenderer());
    engine.registerNamedRenderer(new NamedFilesizeRenderer());
    engine.registerNamedRenderer(new NamedFramerateRenderer());
    engine.registerNamedRenderer(new NamedLowerCaseRenderer());
    engine.registerNamedRenderer(new NamedNumberRenderer());
    engine.registerNamedRenderer(new NamedReplacementRenderer());
    engine.registerNamedRenderer(new NamedSplitRenderer());
    engine.registerNamedRenderer(new NamedTitleCaseRenderer());
    engine.registerNamedRenderer(new NamedUpperCaseRenderer());
    engine.registerNamedRenderer(new TvShowNamedFirstCharacterRenderer());
    engine.registerNamedRenderer(new ChainedNamedRenderer(engine.getAllNamedRenderers()));

    engine.registerAnnotationProcessor(new RegexpProcessor());
    engine.setErrorHandler(new DefaultErrorHandler() {
      @Override
      public void error(ErrorMessage errorMessage, Token token, Map<String, Object> parameters) throws ParseException {
        throw new ParseException(new ResourceBundleMessage(errorMessage.key).withModel(parameters).onToken(token));
      }
    });

    return engine;
  }

  /**
   * Creates the new TV show folder name according to template string
   *
   * @param template
   *          the template string
   * @param show
   *          the TV show to generate the folder name for
   * @return the TV show folder name
   */
  public static String createDestination(String template, TvShow show) {
    if (StringUtils.isBlank(template)) {
      return "";
    }

    boolean spaceSubstitution = TvShowModuleManager.getInstance().getSettings().isRenamerShowPathnameSpaceSubstitution();
    String spaceReplacement = TvShowModuleManager.getInstance().getSettings().getRenamerShowPathnameSpaceReplacement();

    return cleanupDestination(getTokenValue(show, null, template), spaceSubstitution, spaceReplacement);
  }

  /**
   * Creates the new season folder name according to template string
   *
   * @param template
   *          the template string
   * @param season
   *          the season to generate the folder name for
   * @return the season folder name
   */
  public static String createDestination(String template, TvShowSeason season, TvShowEpisode episode) {
    if (StringUtils.isBlank(template)) {
      return "";
    }

    String newDestination = getTokenValue(season.getTvShow(), episode, template);
    boolean spaceSubstitution = TvShowModuleManager.getInstance().getSettings().isRenamerSeasonPathnameSpaceSubstitution();
    String spaceReplacement = TvShowModuleManager.getInstance().getSettings().getRenamerSeasonPathnameSpaceReplacement();

    newDestination = cleanupDestination(newDestination, spaceSubstitution, spaceReplacement);
    return newDestination;
  }

  /**
   * Creates the new file/folder name according to template string
   * 
   * @param template
   *          the template
   * @param episodes
   *          the TV show episodes; nullable for TV show root foldername
   * @return the string
   */
  public static String createDestination(String template, List<TvShowEpisode> episodes) {
    if (StringUtils.isBlank(template) || episodes.isEmpty()) {
      return "";
    }

    TvShowEpisode firstEp = episodes.get(0);
    String newDestination = template;

    if (episodes.size() == 1) {
      // single episode
      newDestination = getTokenValue(firstEp.getTvShow(), firstEp, template);
    }
    else {
      // multi episodes
      String loopNumbers = "";

      // *******************
      // LOOP 1 - season/episode
      // *******************
      String seasonToken = getTokenFromTemplate(newDestination, seasonNumbers);
      if (StringUtils.isNotBlank(seasonToken)) {
        String seasonPart = "";
        Matcher matcher = seDelimiter.matcher(newDestination);
        if (matcher.find()) {
          seasonPart = matcher.group(0);
        }
        else {
          // no season info found? search for the token itself
          Pattern pattern = Pattern.compile("\\$\\{" + Pattern.quote(seasonToken) + ".*?\\}");
          matcher = pattern.matcher(newDestination);
          if (matcher.find()) {
            seasonPart = matcher.group(0);
          }
        }
        loopNumbers += seasonPart;
      }

      String episodeToken = getTokenFromTemplate(newDestination, episodeNumbers);
      if (StringUtils.isNotBlank(episodeToken)) {
        String episodePart = "";
        Matcher matcher = epDelimiter.matcher(newDestination);
        if (matcher.find()) {
          episodePart += matcher.group(0);
        }
        else {
          // no episode info found? search for the token itself
          Pattern pattern = Pattern.compile("\\$\\{" + Pattern.quote(episodeToken) + ".*?\\}");
          matcher = pattern.matcher(newDestination);
          if (matcher.find()) {
            episodePart = matcher.group(0);
          }
        }

        loopNumbers += episodePart;
      }
      loopNumbers = loopNumbers.strip();

      // foreach episode, replace and append pattern:
      StringBuilder episodeParts = new StringBuilder();
      for (TvShowEpisode episode : episodes) {
        String episodePart = getTokenValue(episode.getTvShow(), episode, loopNumbers);
        episodeParts.append(" ").append(episodePart);
      }

      // replace original pattern, with our combined
      if (StringUtils.isNotBlank(loopNumbers)) {
        newDestination = newDestination.replace(loopNumbers, episodeParts.toString().strip());
      }

      // *******************
      // LOOP 2 - title
      // *******************
      String loopTitles = "";
      String titleToken = getTokenFromTemplate(template, episodeTitles);
      if (StringUtils.isNotBlank(titleToken)) {
        Pattern pattern = Pattern.compile("\\$\\{" + Pattern.quote(titleToken) + ".*?\\}", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(template);
        if (matcher.find()) {
          loopTitles += matcher.group(0);
        }
      }

      loopTitles = loopTitles.strip();

      // foreach episode, replace and append pattern:
      if (StringUtils.isNotBlank(loopTitles)) {
        episodeParts = new StringBuilder();
        String previous = "";
        for (TvShowEpisode episode : episodes) {
          String episodePart = getTokenValue(episode.getTvShow(), episode, loopTitles);

          // do not add the same title twice!
          if (!episodePart.equals(previous)) {
            // separate multiple titles via -
            if (StringUtils.isNotBlank(episodeParts.toString())) {
              episodeParts.append(" -");
            }
            episodeParts.append(" ").append(episodePart);
          }
          previous = episodePart;
        }

        newDestination = newDestination.replace(loopTitles, episodeParts.toString().strip());
      }

      // *******************
      // LOOP 3 - aired
      // *******************
      String loopAired = "";
      String airedToken = getTokenFromTemplate(template, episodeAired);
      if (StringUtils.isNotBlank(airedToken)) {
        Pattern pattern = Pattern.compile("\\$\\{" + Pattern.quote(airedToken) + ".*?\\}", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(template);
        if (matcher.find()) {
          loopAired += matcher.group(0);
        }
      }

      loopAired = loopAired.strip();

      // foreach episode, replace and append pattern:
      if (StringUtils.isNotBlank(loopAired)) {
        episodeParts = new StringBuilder();
        for (TvShowEpisode episode : episodes) {
          String episodePart = getTokenValue(episode.getTvShow(), episode, loopAired);

          // separate multiple titles via -
          if (StringUtils.isNotBlank(episodeParts.toString())) {
            episodeParts.append(" -");
          }
          episodeParts.append(" ").append(episodePart);
        }

        newDestination = newDestination.replace(loopAired, episodeParts.toString().strip());
      }

      newDestination = getTokenValue(firstEp.getTvShow(), firstEp, newDestination);
    } // end multi episodes

    // when renaming with $originalFilename, we get already the extension added!
    if (newDestination.endsWith("." + firstEp.getMainVideoFile().getExtension())) {
      newDestination = FilenameUtils.getBaseName(newDestination);
    }

    boolean spaceSubstitution = TvShowModuleManager.getInstance().getSettings().isRenamerFilenameSpaceSubstitution();
    String spaceReplacement = TvShowModuleManager.getInstance().getSettings().getRenamerFilenameSpaceReplacement();

    newDestination = cleanupDestination(newDestination, spaceSubstitution, spaceReplacement);

    return newDestination;
  }

  /**
   * cleanup the destination (remove empty brackets, space substitution, ..)
   * 
   * @param destination
   *          the string to be cleaned up
   * @param spaceSubstitution
   *          replace spaces (=true)? or not (=false)
   * @param spaceReplacement
   *          the replacement string for spaces
   * @return the cleaned up string
   */
  private static String cleanupDestination(String destination, Boolean spaceSubstitution, String spaceReplacement) {
    // WebDAV 路径特殊处理：先规范化 WebDAV 路径，然后使用统一的正斜杠处理
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(destination);
    if (isWebDav) {
      destination = WebDavDataSourceHelper.normalizeWebDavPath(destination);
    }

    // replace empty brackets
    destination = destination.replaceAll("\\([ ]?\\)", "");
    destination = destination.replaceAll("\\[[ ]?\\]", "");
    destination = destination.replaceAll("\\{[ ]?\\}", "");

    // if there are multiple file separators in a row - strip them out
    if (isWebDav) {
      // WebDAV 路径始终使用正斜杠，不区分操作系统
      destination = destination.replaceAll("/{2,}", "/");
      destination = destination.replaceAll("^/", "");
      // trim whitespace around directory sep
      destination = destination.replaceAll("\\s+/", "/");
      destination = destination.replaceAll("/\\s+", "/");
      // remove separators in front of path separators
      destination = destination.replaceAll("[ \\.\\-_]+/", "/");
    }
    else if (SystemUtils.IS_OS_WINDOWS) {
      // we need to mask it in windows
      destination = destination.replaceAll("\\\\{2,}", "\\\\");
      destination = destination.replaceAll("^\\\\", "");
      // trim whitespace around directory sep
      destination = destination.replaceAll("\\s+\\\\", "\\\\");
      destination = destination.replaceAll("\\\\\\s+", "\\\\");
      // remove separators in front of path separators
      destination = destination.replaceAll("[ \\.\\-_]+\\\\", "\\\\");
    }
    else {
      destination = destination.replaceAll(File.separator + "{2,}", File.separator);
      destination = destination.replaceAll("^" + File.separator, "");
      // trim whitespace around directory sep
      destination = destination.replaceAll("\\s+/", "/");
      destination = destination.replaceAll("/\\s+", "/");
      // remove separators in front of path separators
      destination = destination.replaceAll("[ \\.\\-_]+/", "/");
    }

    // replace spaces with underscores if needed (filename only)
    if (spaceSubstitution) {
      destination = destination.replace(" ", spaceReplacement);

      // also replace now multiple replacements with one to avoid strange looking results
      // example:
      // Abraham Lincoln - Vampire Hunter -> Abraham-Lincoln---Vampire-Hunter
      destination = destination.replaceAll(Pattern.quote(spaceReplacement) + "+", spaceReplacement);
    }

    // ASCII replacement
    if (TvShowModuleManager.getInstance().getSettings().isAsciiReplacement()) {
      destination = StrgUtils.convertToAscii(destination, false);
    }

    // replace all leading/trailing separators
    destination = destination.replaceAll("^[ \\.\\-_]+", "");
    destination = destination.replaceAll("[ \\.\\-_]+$", "");

    // the illegal filesystem characters are handled by JMTE, but it looks like some users are stupid enough to add this to the pattern itself...
    destination = replaceInvalidCharacters(destination);

    // replace new lines
    destination = destination.replaceAll("\r?\n", " ");

    // trim out unnecessary whitespaces
    destination = destination.replaceAll(" +", " ");

    // replace three subsequent dots with the Unicode ellipsis character
    if (TvShowModuleManager.getInstance().getSettings().isUnicodeReplacement()) {
      destination = destination.replace("...", "…");
    }

    return destination.strip();
  }

  /**
   * checks, if the pattern has a recommended structure (S/E numbers, title filled)<br>
   * when false, it might lead to some unpredictable renamings...
   * 
   * @param seasonPattern
   *          the season pattern
   * @param filePattern
   *          the file pattern
   * @return true/false
   */
  public static boolean isRecommended(String seasonPattern, String filePattern) {
    // count em
    int epCnt = count(filePattern, episodeNumbers);
    int titleCnt = count(filePattern, episodeTitles);
    int seCnt = count(filePattern, seasonNumbers);
    int seFolderCnt = count(seasonPattern, seasonNumbers);// check season folder pattern

    // when using ${originalFilename} or ${originalBasename}, we do not check any further
    if (count(filePattern, new String[] { "originalFilename", "originalBasename" }) > 0) {
      return true;
    }

    // check rules
    if (epCnt != 1 || titleCnt > 1 || seCnt > 1 || seFolderCnt > 1 || (seCnt + seFolderCnt) == 0) {
      LOGGER.debug("Too many/less episode/season/title replacer patterns");
      return false;
    }

    int epPos = getPatternPos(filePattern, episodeNumbers);
    int sePos = getPatternPos(filePattern, seasonNumbers);
    int titlePos = getPatternPos(filePattern, episodeTitles);

    if (sePos > epPos) {
      LOGGER.debug("Season pattern should be before episode pattern!");
      return false;
    }

    // check if title not in-between season/episode pattern in file
    if (titleCnt == 1 && seCnt == 1) {
      if (titlePos < epPos && titlePos > sePos) {
        LOGGER.debug("Title should not be between season/episode pattern");
        return false;
      }
    }

    return true;
  }

  /**
   * Count the amount of renamer tokens per group
   * 
   * @param pattern
   *          the pattern to analyze
   * @param possibleValues
   *          an array of possible values
   * @return 0, or amount
   */
  private static int count(String pattern, String[] possibleValues) {
    int count = 0;
    for (String r : possibleValues) {
      if (containsToken(pattern, r)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns first position of any matched patterns
   * 
   * @param pattern
   *          the pattern to get the position for
   * @param possibleValues
   *          an array of all possible values
   * @return the position of the first occurrence
   */
  private static int getPatternPos(String pattern, String[] possibleValues) {
    int pos = -1;
    for (String r : possibleValues) {
      if (containsToken(pattern, r)) {
        pos = pattern.indexOf(r);
      }
    }
    return pos;
  }

  /**
   * returns the first found token from the matched pattern
   *
   * @param template
   *          the template to be searched for
   * @param possibleTokens
   *          the tokens to look for
   * @return the found token or an emtpy string
   */
  private static String getTokenFromTemplate(String template, String[] possibleTokens) {
    for (String token : possibleTokens) {
      if (containsToken(template, token)) {
        return token;
      }
    }
    return "";
  }

  private static boolean containsToken(String template, String token) {
    Pattern pattern = Pattern.compile("\\$\\{" + token + "[\\[\\};]");
    Matcher matcher = pattern.matcher(template);
    return matcher.find();
  }

  private static String getMediaFileExtension(MediaFile mf) {
    String ext = mf.getExtension().replace("jpeg", "jpg"); // we only have one constant and only write jpg
    if (ext.equalsIgnoreCase("tbn")) {
      String cont = mf.getContainerFormat();
      if (cont.equalsIgnoreCase("PNG")) {
        ext = "png";
      }
      else if (cont.equalsIgnoreCase("JPEG")) {
        ext = "jpg";
      }
    }
    return ext;
  }

  /**
   * Deletes "unwanted files" according to settings. Same as the action, but w/o GUI.
   * 
   * @param show
   *          the {@link TvShow} to clean up
   */
  private static void cleanupUnwantedFiles(TvShow show) {
    if (TvShowModuleManager.getInstance().getSettings().renamerCleanupUnwanted) {
      Utils.deleteUnwantedFilesAndFoldersFor(show);
    }
  }

  /**
   * Deletes "unwanted files" according to settings. Same as the action, but w/o GUI.
   * 
   * @param episode
   *          the {@link TvShowEpisode} to clean up
   */
  private static void cleanupUnwantedFiles(TvShowEpisode episode) {
    if (TvShowModuleManager.getInstance().getSettings().renamerCleanupUnwanted) {
      Utils.deleteUnwantedFilesAndFoldersFor(episode);
    }
  }

  /**
   * remove empty subfolders in this folder after renaming
   *
   * @param episode
   *          the episode to clean
   */
  private static void removeEmptySubfolders(TvShowEpisode episode) {
    // check all subfolders if they're empty (recursively)
    if (WebDavDataSourceHelper.isWebDavPath(episode.getPath())) {
      // WebDAV 路径使用专门的方法删除空目录
      int deleted = WebDavFileOperations.deleteEmptyDirectoriesRecursive(episode.getPath());
      if (deleted > 0) {
        LOGGER.debug("Deleted {} empty WebDAV directories under '{}'", deleted, episode.getPath());
      }
    }
    else {
      try {
        Utils.deleteEmptyDirectoryRecursive(episode.getPathNIO());
      }
      catch (IOException e) {
        LOGGER.warn("Could not delete empty subfolders of '{}' - '{}'", episode.getPathNIO(), e.getMessage());
      }
    }
  }

  /**
   * moves a file.
   *
   * @param oldFilename
   *          the old filename
   * @param newFilename
   *          the new filename
   * @return true, when we moved file
   */
  private static boolean moveFile(Path oldFilename, Path newFilename) {
    try {
      String oldPath = WebDavDataSourceHelper.normalizeWebDavPath(oldFilename.toString());
      String newPath = WebDavDataSourceHelper.normalizeWebDavPath(newFilename.toString());

      // Handle WebDAV paths specially
      if (WebDavDataSourceHelper.isWebDavPath(oldPath)) {
        // 先在本地比较路径，相同则直接跳过，避免创建网络连接
        if (oldPath.equals(newPath)) {
          LOGGER.debug("Source and destination are the same, skipping move (local check): {}", oldPath);
          return true;
        }

        LOGGER.debug("Moving WebDAV file '{}' to '{}'", oldPath, newPath);
        String actualPath = WebDavFileOperations.moveWebDavFile(oldPath, newPath);
        if (actualPath != null) {
          if (!actualPath.equals(newPath)) {
            LOGGER.warn("WebDAV file was moved to a different path than expected: expected '{}', actual '{}'", newPath, actualPath);
          }
          return true;
        }
        else {
          LOGGER.error("Could not move WebDAV file '{}' to '{}'", oldPath, newPath);
          return false;
        }
      }

      // Local filesystem handling
      // create parent if needed
      if (!Files.exists(newFilename.getParent())) {
        Files.createDirectory(newFilename.getParent());
      }
      boolean ok = Utils.moveFileSafe(oldFilename, newFilename);
      if (ok) {
        return true;
      }
      else {
        LOGGER.error("Could not move file '{}' to '{}'", oldFilename, newFilename);
        return false; // rename failed
      }
    }
    catch (Exception e) {
      LOGGER.error("Could not move file '{}' to '{}' - '{}'", oldFilename, newFilename, e.getMessage());
      MessageManager.getInstance()
          .pushMessage(new Message(MessageLevel.ERROR, oldFilename, "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
      return false; // rename failed
    }
  }

  /**
   * copies a file.
   *
   * @param oldFilename
   *          the old filename
   * @param newFilename
   *          the new filename
   * @return true, when we copied file OR DEST IS EXISTING
   */
  private static boolean copyFile(Path oldFilename, Path newFilename) {
    // Check if paths are WebDAV paths
    String oldPathStr = oldFilename.toString();
    String newPathStr = newFilename.toString();

    // 检测源路径是否包含本地缓存路径特征（artwork/tvshows, artwork/movies, cache/image）
    // 或者包含嵌入的 tvshows/ movies/ 模式（如 webdav://xxx/.../电影名/tvshows/电影名/poster.jpg）
    // 这些路径是本地缓存目录结构，不应该存在于 WebDAV 上
    boolean isOldPathCacheLike = oldPathStr.contains("/artwork/tvshows/") || oldPathStr.contains("\\artwork\\tvshows\\")
        || oldPathStr.contains("/artwork/movies/") || oldPathStr.contains("\\artwork\\movies\\") || oldPathStr.contains("/cache/image/")
        || oldPathStr.contains("\\cache\\image\\");

    // 额外检测：WebDAV 路径中嵌入了 /tvshows/ 或 /movies/ 子目录
    // 这表明本地缓存目录结构被错误拼接到了 WebDAV 路径中
    // 例如：webdav://aaa/转存1p/.../黑镜 (2011)/tvshows/黑镜 (2011)/poster.jpg
    // 正确应该是：webdav://aaa/转存1p/.../黑镜 (2011)/poster.jpg
    if (WebDavDataSourceHelper.isWebDavPath(oldPathStr) && !isOldPathCacheLike) {
      // 检测是否在 WebDAV 路径中包含 /tvshows/ 或 /movies/ 这种本地目录模式
      // 注意：这里的 tvshows/ movies/ 必须包含斜杠以避免误匹配文件名
      if (oldPathStr.contains("/tvshows/") || oldPathStr.contains("/movies/")) {
        isOldPathCacheLike = true;
      }
    }

    // 如果源路径包含缓存路径特征，即使被格式化为 WebDAV 路径，也跳过复制
    // 因为这个文件实际上不存在于 WebDAV 服务器上
    // 返回 false 以避免 renamer 用错误的路径替换原有的 MediaFile
    if (isOldPathCacheLike) {
      LOGGER.warn(
          "Skipping copy: source path '{}' contains local cache path pattern (artwork/tvshows, artwork/movies, cache/image, or embedded tvshows/movies). "
              + "This file does not exist on WebDAV. Consider re-scraping artwork for this TV show.",
          oldPathStr);
      return false; // 返回 false 让 renamer 知道复制失败，不替换 MediaFile
    }

    if (WebDavDataSourceHelper.isWebDavPath(oldPathStr) && WebDavDataSourceHelper.isWebDavPath(newPathStr)) {
      // Both are WebDAV paths - use WebDAV operations
      if (oldPathStr.equals(newPathStr)) {
        return true; // same file, nothing to do
      }
      LOGGER.debug("Copying WebDAV file from '{}' to '{}'", oldPathStr, newPathStr);
      return WebDavFileOperations.copyWebDavFile(oldPathStr, newPathStr);
    }
    else if (WebDavDataSourceHelper.isWebDavPath(oldPathStr) || WebDavDataSourceHelper.isWebDavPath(newPathStr)) {
      // One is WebDAV and one is local - not supported
      LOGGER.error("Cannot copy files between WebDAV and local file system: {} -> {}", oldPathStr, newPathStr);
      return false;
    }

    // Both are local paths - use standard file operations
    if (!oldFilename.toAbsolutePath().toString().equals(newFilename.toAbsolutePath().toString())) {
      LOGGER.debug("copy file '{}' to '{}'", oldFilename, newFilename);
      if (oldFilename.equals(newFilename)) {
        // windows: name differs, but File() is the same!!!
        // use move in this case, which handles this
        return moveFile(oldFilename, newFilename);
      }
      try {
        // create parent if needed
        if (!Files.exists(newFilename.getParent())) {
          Files.createDirectory(newFilename.getParent());
        }
        Utils.copyFileSafe(oldFilename, newFilename, true);
        return true;
      }
      catch (Exception e) {
        return false;
      }
    }
    else { // file is the same, return true to keep file
      return true;
    }
  }

  /**
   * returns "delimiter + stackingString" for use in filename
   *
   * @param mf
   *          a mediaFile
   * @return eg ".CD1" dependent of settings
   */
  private static String getStackingString(MediaFile mf) {
    String delimiter = ".";
    if (TvShowModuleManager.getInstance().getSettings().isRenamerFilenameSpaceSubstitution()) {
      delimiter = TvShowModuleManager.getInstance().getSettings().getRenamerFilenameSpaceReplacement();
    }
    if (!mf.getStackingMarker().isEmpty()) {
      return delimiter + mf.getStackingMarker();
    }
    else if (mf.getStacking() != 0) {
      return delimiter + "CD" + mf.getStacking();
    }
    return "";
  }

  /**
   * moves a directory.
   *
   * @param oldPath
   *          the old directory path
   * @param newPath
   *          the new directory path
   * @return true, when we moved directory
   */
  private static boolean moveDirectory(Path oldPath, Path newPath) {
    try {
      String oldPathStr = WebDavDataSourceHelper.normalizeWebDavPath(oldPath.toString());
      String newPathStr = WebDavDataSourceHelper.normalizeWebDavPath(newPath.toString());

      // Handle WebDAV paths specially
      if (WebDavDataSourceHelper.isWebDavPath(oldPathStr)) {
        LOGGER.debug("Moving WebDAV directory '{}' to '{}'", oldPathStr, newPathStr);
        String actualPath = WebDavFileOperations.moveWebDavFile(oldPathStr, newPathStr);
        if (actualPath != null) {
          if (!actualPath.equals(newPathStr)) {
            LOGGER.warn("WebDAV directory was moved to a different path than expected: expected '{}', actual '{}'", newPathStr, actualPath);
          }
          return true;
        }
        else {
          LOGGER.error("Could not move WebDAV directory '{}' to '{}'", oldPathStr, newPathStr);
          return false;
        }
      }

      // Local filesystem handling
      // create parent if needed
      if (!Files.exists(newPath.getParent())) {
        Files.createDirectory(newPath.getParent());
      }
      boolean ok = Utils.moveDirectorySafe(oldPath, newPath);
      if (ok) {
        return true;
      }
      else {
        LOGGER.error("Could not move directory '{}' to '{}'", oldPath, newPath);
        return false;
      }
    }
    catch (Exception e) {
      LOGGER.error("Could not move directory '{}' to '{}' - '{}'", oldPath, newPath, e.getMessage());
      return false;
    }
  }

  public static String replaceInvalidCharacters(String source) {
    String result = source;

    if ("-".equals(TvShowModuleManager.getInstance().getSettings().getRenamerColonReplacement())) {
      result = result.replace(": ", " - "); // nicer
      result = result.replace(":", "-"); // nicer
    }
    else {
      result = result.replace(":", TvShowModuleManager.getInstance().getSettings().getRenamerColonReplacement());
    }

    return result.replaceAll("([\":<>|?*])", "");
  }

  /**
   * Helper method to resolve a filename against a base path, handling both WebDAV and local paths
   *
   * @param basePath
   *          the base path
   * @param filename
   *          the filename to resolve
   * @return the resolved path
   */
  private static Path resolvePath(Path basePath, String filename) {
    String basePathStr = basePath.toString();
    if (WebDavDataSourceHelper.isWebDavPath(basePathStr)) {
      // For WebDAV, use string concatenation
      String newPath = basePathStr.endsWith("/") ? basePathStr + filename : basePathStr + "/" + filename;
      return Paths.get(newPath);
    }
    else {
      // For local paths, use Path.resolve()
      return basePath.resolve(filename);
    }
  }

  /**
   * checks supplied renamer pattern against our tokenmap, if everything could be found
   * 
   * @param pattern
   * @return error string, what token(s) are wrong
   */
  public static String isPatternValid(String pattern) {
    String err = "";
    Pattern p = Pattern.compile("\\$\\{(.*?)\\}");
    Matcher matcher = p.matcher(pattern);
    while (matcher.find()) {
      String fulltoken = matcher.group(1);
      String token = "";
      if (fulltoken.contains(",")) {
        // split additional like ${-,token,replace}
        String[] split = fulltoken.split(",");
        token = split[1];
      }
      else if (fulltoken.contains("[")) {
        // strip all after parenthesis
        token = fulltoken.substring(0, fulltoken.indexOf('['));
      }
      else if (fulltoken.contains(";")) {
        // strip all after semicolon like ${title;first}
        token = fulltoken.substring(0, fulltoken.indexOf(';'));
        String first = fulltoken.substring(fulltoken.indexOf(';') + 1);
        if (!first.equals("first")) {
          err += "  " + matcher.group(); // "first" is missing
        }
      }
      else {
        token = fulltoken;
      }
      String tok = TOKEN_MAP.get(token.strip());
      if (tok == null) {
        err += "  " + matcher.group(); // complete token with ${}
      }
    }
    return err;
  }

  public static class TvShowNamedFirstCharacterRenderer implements NamedRenderer {
    private static final Pattern FIRST_ALPHANUM_PATTERN = Pattern.compile("[\\p{L}\\d]");

    @Override
    public String render(Object o, String s, Locale locale, Map<String, Object> map) {
      if (o instanceof String && StringUtils.isNotBlank((String) o)) {
        String source = StrgUtils.convertToAscii((String) o, false);
        Matcher matcher = FIRST_ALPHANUM_PATTERN.matcher(source);
        if (matcher.find()) {
          String first = matcher.group();

          if (first.matches("\\p{L}")) {
            return first.toUpperCase(Locale.ROOT);
          }
          else {
            return TvShowModuleManager.getInstance().getSettings().getRenamerFirstCharacterNumberReplacement();
          }
        }
      }
      if (o instanceof Number) {
        return TvShowModuleManager.getInstance().getSettings().getRenamerFirstCharacterNumberReplacement();
      }
      if (o instanceof Date) {
        return TvShowModuleManager.getInstance().getSettings().getRenamerFirstCharacterNumberReplacement();
      }
      return "";
    }

    @Override
    public String getName() {
      return "first";
    }

    @Override
    public RenderFormatInfo getFormatInfo() {
      return null;
    }

    @Override
    public Class<?>[] getSupportedClasses() {
      return new Class[] { Date.class, String.class, Integer.class, Long.class };
    }
  }

  /**
   * Get the destination folder for TV show artwork during rename based on settings
   *
   * @param tvShow
   *          the TV show entity
   * @return the destination folder path
   */
  private static Path getDestinationFolderForTvShowRename(TvShow tvShow) {
    boolean saveToCache = TvShowModuleManager.getInstance().getSettings().isSaveArtworkToCache();

    if (saveToCache) {
      // Create a structured cache folder: cache/artwork/tvshows
      Path cacheArtworkDir = ImageCache.getCacheDir().resolve("artwork").resolve("tvshows");

      // Create entity-specific subfolder using title and year for uniqueness
      String folderName = tvShow.getTitle();
      if (tvShow.getYear() > 0) {
        folderName += " (" + tvShow.getYear() + ")";
      }
      // Sanitize folder name for filesystem compatibility
      folderName = folderName.replaceAll("[<>:\"/\\\\|?*]", "_");

      Path entityFolder = cacheArtworkDir.resolve(folderName);

      try {
        Files.createDirectories(entityFolder);
        LOGGER.info("Created cache artwork folder for TV show rename '{}': {}", tvShow.getTitle(), entityFolder);
      }
      catch (Exception e) {
        LOGGER.warn("Could not create cache artwork folder '{}', falling back to video folder - '{}'", entityFolder, e.getMessage());
        return tvShow.getPathNIO();
      }

      LOGGER.info("Using cache artwork folder for TV show rename '{}': {}", tvShow.getTitle(), entityFolder);
      return entityFolder;
    }
    else {
      // Default behavior: save to video folder
      LOGGER.debug("Using default video folder for TV show rename '{}': {}", tvShow.getTitle(), tvShow.getPathNIO());
      return tvShow.getPathNIO();
    }
  }

  /**
   * 剧集比较结果枚举
   */
  private enum EpisodeComparisonResult {
    SOURCE_BETTER, // 源剧集更好
    TARGET_BETTER, // 目标剧集更好
    EQUAL // 两者相等
  }

  /**
   * 比较两个剧集的完整性，决定哪个应该被保留 比较策略： 1. 优先比较视频文件数量（视频是最重要的） 2. 视频数量相同，比较总媒体文件数量 3. 总数量相同，比较文件总大小
   *
   * @param source
   *          源剧集
   * @param target
   *          目标剧集
   * @return 比较结果
   */
  private static EpisodeComparisonResult compareEpisodes(TvShowEpisode source, TvShowEpisode target) {
    try {
      // 1. 优先比较视频文件数量（视频是最重要的）
      int sourceVideoCount = source.getMediaFiles(MediaFileType.VIDEO).size();
      int targetVideoCount = target.getMediaFiles(MediaFileType.VIDEO).size();

      if (sourceVideoCount != targetVideoCount) {
        LOGGER.debug("Episode comparison: S{}E{} - source has {} videos, target has {} videos", source.getSeason(), source.getEpisode(),
            sourceVideoCount, targetVideoCount);
        return sourceVideoCount > targetVideoCount ? EpisodeComparisonResult.SOURCE_BETTER : EpisodeComparisonResult.TARGET_BETTER;
      }

      // 2. 视频数量相同，比较总媒体文件数量
      int sourceTotalCount = source.getMediaFiles().size();
      int targetTotalCount = target.getMediaFiles().size();

      if (sourceTotalCount != targetTotalCount) {
        LOGGER.debug("Episode comparison: S{}E{} - source has {} media files, target has {} media files", source.getSeason(), source.getEpisode(),
            sourceTotalCount, targetTotalCount);
        return sourceTotalCount > targetTotalCount ? EpisodeComparisonResult.SOURCE_BETTER : EpisodeComparisonResult.TARGET_BETTER;
      }

      // 3. 总数量相同，比较文件总大小
      long sourceSize = 0;
      long targetSize = 0;

      for (MediaFile mf : source.getMediaFiles()) {
        try {
          sourceSize += mf.getFileAsPath().toFile().length();
        }
        catch (Exception e) {
          LOGGER.warn("Failed to get size for source media file: {}", mf.getFilename());
        }
      }

      for (MediaFile mf : target.getMediaFiles()) {
        try {
          targetSize += mf.getFileAsPath().toFile().length();
        }
        catch (Exception e) {
          LOGGER.warn("Failed to get size for target media file: {}", mf.getFilename());
        }
      }

      if (sourceSize != targetSize) {
        LOGGER.debug("Episode comparison: S{}E{} - source total size {} bytes, target total size {} bytes", source.getSeason(), source.getEpisode(),
            sourceSize, targetSize);
        return sourceSize > targetSize ? EpisodeComparisonResult.SOURCE_BETTER : EpisodeComparisonResult.TARGET_BETTER;
      }

      LOGGER.debug("Episode comparison: S{}E{} - episodes are equal", source.getSeason(), source.getEpisode());
      return EpisodeComparisonResult.EQUAL;
    }
    catch (Exception e) {
      LOGGER.error("Error comparing episodes S{}E{}: {}", source.getSeason(), source.getEpisode(), e.getMessage());
      // 出错时默认返回相等，让原有逻辑处理
      return EpisodeComparisonResult.EQUAL;
    }
  }

  /**
   * 合并源 TvShow 的 MediaFiles 到目标 TvShow 如果目标没有某类型的 MediaFile，则从源复制（更新路径后）
   * 
   * @param source
   *          源 TvShow（将被删除）
   * @param target
   *          目标 TvShow（保留）
   * @param srcDir
   *          源目录路径
   * @param destDir
   *          目标目录路径
   */
  private static void mergeMediaFilesToExistingShow(TvShow source, TvShow target, Path srcDir, Path destDir) {
    // 需要合并的 MediaFile 类型（TvShow 级别的艺术图和元数据文件）
    MediaFileType[] typesToMerge = { MediaFileType.POSTER, MediaFileType.FANART, MediaFileType.BANNER, MediaFileType.THUMB, MediaFileType.CLEARLOGO,
        MediaFileType.CLEARART, MediaFileType.CHARACTERART, MediaFileType.DISC, MediaFileType.KEYART, MediaFileType.NFO, MediaFileType.EXTRAFANART,
        MediaFileType.EXTRATHUMB };

    int mergedCount = 0;
    for (MediaFileType type : typesToMerge) {
      List<MediaFile> targetMfs = target.getMediaFiles(type);
      List<MediaFile> sourceMfs = source.getMediaFiles(type);

      // 如果目标没有这种类型的文件，从源复制
      if (targetMfs.isEmpty() && !sourceMfs.isEmpty()) {
        for (MediaFile sourceMf : sourceMfs) {
          try {
            // 创建新的 MediaFile 副本并更新路径到目标目录
            MediaFile newMf = new MediaFile(sourceMf);
            newMf.replacePathForRenamedFolder(srcDir, destDir);
            target.addToMediaFiles(newMf);
            mergedCount++;
            LOGGER.debug("Merged MediaFile {} from source to target: {}", type, newMf.getFilename());
          }
          catch (Exception e) {
            LOGGER.warn("Failed to merge MediaFile {} from source: {}", type, e.getMessage());
          }
        }
      }
    }

    if (mergedCount > 0) {
      LOGGER.info("Merged {} MediaFiles from source TvShow '{}' to target TvShow '{}'", mergedCount, source.getTitle(), target.getTitle());
    }
  }
}
