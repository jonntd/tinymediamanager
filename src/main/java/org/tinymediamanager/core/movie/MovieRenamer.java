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
package org.tinymediamanager.core.movie;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.tinymediamanager.core.movie.connector.MovieConnectors;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.filenaming.MovieBannerNaming;
import org.tinymediamanager.core.movie.filenaming.MovieClearartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieClearlogoNaming;
import org.tinymediamanager.core.movie.filenaming.MovieDiscartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieExtraFanartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieFanartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieKeyartNaming;
import org.tinymediamanager.core.movie.filenaming.MovieNfoNaming;
import org.tinymediamanager.core.movie.filenaming.MoviePosterNaming;
import org.tinymediamanager.core.movie.filenaming.MovieThumbNaming;
import org.tinymediamanager.core.movie.filenaming.MovieTrailerNaming;
import org.tinymediamanager.core.movie.jmte.MovieNamedFirstCharacterRenderer;
import org.tinymediamanager.core.movie.jmte.MovieNamedIndexOfMovieSetRenderer;
import org.tinymediamanager.core.movie.jmte.MovieNamedIndexOfMovieSetWithDummyRenderer;
import org.tinymediamanager.core.threading.ThreadUtils;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;
import org.tinymediamanager.core.webdav.WebDavFileOperations;
import org.tinymediamanager.core.webdav.WebDavPath;
import org.tinymediamanager.scraper.util.StrgUtils;

import com.floreysoft.jmte.Engine;
import com.floreysoft.jmte.extended.ChainedNamedRenderer;
import com.floreysoft.jmte.message.DefaultErrorHandler;
import com.floreysoft.jmte.message.ErrorMessage;
import com.floreysoft.jmte.message.ParseException;
import com.floreysoft.jmte.message.ResourceBundleMessage;
import com.floreysoft.jmte.token.Token;

/**
 * The Class MovieRenamer.
 *
 * @author Manuel Laggner / Myron Boyle
 */
public class MovieRenamer {
  private static final Logger              LOGGER                      = LoggerFactory.getLogger(MovieRenamer.class);
  private static final List<String>        KNOWN_IMAGE_FILE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "bmp", "tbn", "gif", "webp");

  /**
   * Normalize WebDAV path format: ensure "webdav://" instead of "webdav:/" This is needed because Path.toString() may output single slash format
   * 
   * @param path
   *          the path to normalize
   * @return the normalized path
   */
  // to not use posix here
  private static final Pattern             TITLE_PATTERN               = Pattern.compile("\\$\\{.*?(title|originalTitle|englishTitle).*?\\}",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern             YEAR_ID_PATTERN             = Pattern.compile("\\$\\{.*?(year|imdb|tmdb).*?\\}", Pattern.CASE_INSENSITIVE);
  private static final Pattern             ORIGINAL_FILENAME_PATTERN   = Pattern.compile("\\$\\{.*?originalFilename.*?\\}", Pattern.CASE_INSENSITIVE);
  private static final Pattern             TRAILER_STACKING_PATTERN    = Pattern.compile(".*?(\\d)$");

  private static final Map<String, String> TOKEN_MAP                   = createTokenMap();

  private MovieRenamer() {
    throw new IllegalAccessError();
  }

  /**
   * initialize the token map for the renamer
   *
   * @return the token map
   */
  private static Map<String, String> createTokenMap() {
    Map<String, String> tokenMap = new HashMap<>();
    tokenMap.put("title", "movie.title");
    tokenMap.put("originalTitle", "movie.originalTitle");
    tokenMap.put("englishTitle", "movie.englishTitle");
    tokenMap.put("originalFilename", "movie.originalFilename");
    tokenMap.put("originalBasename", "movie.originalBasename");
    tokenMap.put("sorttitle", "movie.sortTitle");
    tokenMap.put("year", "movie.year");
    tokenMap.put("releaseDate", "movie.releaseDate;date(yyyy-MM-dd)");
    tokenMap.put("titleSortable", "movie.titleSortable");
    tokenMap.put("rating", "movie.rating.rating");
    tokenMap.put("imdb", "movie.imdbId");
    tokenMap.put("tmdb", "movie.tmdbId");
    tokenMap.put("certification", "movie.certification");
    tokenMap.put("language", "movie.spokenLanguages");

    tokenMap.put("genres", "movie.genres");
    tokenMap.put("genresAsString", "movie.genresAsString");
    tokenMap.put("tags", "movie.tags");
    tokenMap.put("tagsAsString", "movie.tagsAsString");
    tokenMap.put("actors", "movie.actors");
    tokenMap.put("producers", "movie.producers");
    tokenMap.put("directors", "movie.directors");
    tokenMap.put("writers", "movie.writers");
    tokenMap.put("productionCompany", "movie.productionCompany");
    tokenMap.put("productionCompanyAsArray", "movie.productionCompanyAsArray");

    tokenMap.put("videoCodec", "movie.mediaInfoVideoCodec");
    tokenMap.put("videoFormat", "movie.mediaInfoVideoFormat");
    tokenMap.put("aspectRatio", "movie.mediaInfoAspectRatioAsString");
    tokenMap.put("aspectRatio2", "movie.mediaInfoAspectRatio2AsString");
    tokenMap.put("videoResolution", "movie.mediaInfoVideoResolution");
    tokenMap.put("videoBitDepth", "movie.mediaInfoVideoBitDepth");
    tokenMap.put("videoBitRate", "movie.mediaInfoVideoBitrate;bitrate");
    tokenMap.put("framerate", "movie.mediaInfoFrameRate;framerate");

    tokenMap.put("audioCodec", "movie.mediaInfoAudioCodec");
    tokenMap.put("audioCodecList", "movie.mediaInfoAudioCodecList");
    tokenMap.put("audioCodecsAsString", "movie.mediaInfoAudioCodecList;array");
    tokenMap.put("audioChannels", "movie.mediaInfoAudioChannels");
    tokenMap.put("audioChannelList", "movie.mediaInfoAudioChannelList");
    tokenMap.put("audioChannelsAsString", "movie.mediaInfoAudioChannelList;array");
    tokenMap.put("audioChannelsDot", "movie.mediaInfoAudioChannelsDot");
    tokenMap.put("audioChannelDotList", "movie.mediaInfoAudioChannelDotList");
    tokenMap.put("audioChannelsDotAsString", "movie.mediaInfoAudioChannelDotList;array");
    tokenMap.put("audioLanguage", "movie.mediaInfoAudioLanguage");
    tokenMap.put("audioLanguageList", "movie.mediaInfoAudioLanguageList");
    tokenMap.put("audioLanguagesAsString", "movie.mediaInfoAudioLanguageList;array");

    tokenMap.put("subtitleLanguageList", "movie.mediaInfoSubtitleLanguageList");
    tokenMap.put("subtitleLanguagesAsString", "movie.mediaInfoSubtitleLanguageList;array");
    tokenMap.put("3Dformat", "movie.video3DFormat");
    tokenMap.put("3Dformat2", "movie.video3DFormat2");
    tokenMap.put("hdr", "movie.videoHDR");
    tokenMap.put("hdrformat", "movie.videoHDRFormat");
    tokenMap.put("filesize", "movie.videoFilesize;filesize");

    tokenMap.put("mediaSource", "movie.mediaSource");
    tokenMap.put("edition", "movie.edition");
    tokenMap.put("parent", "movie.parent");
    tokenMap.put("note", "movie.note");
    tokenMap.put("decadeLong", "movie.decadeLong");
    tokenMap.put("decadeShort", "movie.decadeShort");
    tokenMap.put("movieSetIndex", "movie;indexOfMovieSet");
    tokenMap.put("movieSetIndex2", "movie;indexOfMovieSetWithDummy");

    tokenMap.put("crc32", "movie.CRC32");

    return tokenMap;
  }

  public static Map<String, String> getTokenMap() {
    return Collections.unmodifiableMap(TOKEN_MAP);
  }

  public static Map<String, String> getTokenMapReversed() {
    return Collections.unmodifiableMap(TOKEN_MAP.entrySet().stream().collect(Collectors.toMap(Entry::getValue, Entry::getKey)));
  }

  /**
   * remove empty subfolders in this folder after renaming; only valid if we're in a single movie folder!
   *
   * @param movie
   *          the movie to clean
   */
  private static void removeEmptySubfolders(Movie movie) {
    if (movie.isMultiMovieDir()) {
      return;
    }

    // check all subfolders if they're empty (recursively)
    // Skip for WebDAV paths (not supported for virtual paths)
    if (!WebDavDataSourceHelper.isWebDavPath(movie.getPath())) {
      try {
        Utils.deleteEmptyDirectoryRecursive(movie.getPathNIO());
      }
      catch (IOException e) {
        LOGGER.warn("Could not delete empty subfolders of '{}' - '{}'", movie.getPathNIO(), e.getMessage());
      }
    }
  }

  /**
   * Deletes "unwanted files" according to settings. Same as the action, but w/o GUI.
   * 
   * @param movie
   *          the {@link Movie} to clean up
   */
  private static void cleanupUnwantedFiles(Movie movie) {
    if (movie.isMultiMovieDir()) {
      return;
    }
    if (MovieModuleManager.getInstance().getSettings().renamerCleanupUnwanted) {
      Utils.deleteUnwantedFilesAndFoldersFor(movie);
    }
  }

  /**
   * Get movie path as Path object, handling WebDAV paths correctly For WebDAV paths, returns a virtual Path that can be used with string operations
   *
   * @param movie
   *          the movie
   * @return Path object representing the movie path
   */
  private static Path getMoviePath(Movie movie) {
    if (WebDavDataSourceHelper.isWebDavPath(movie.getPath())) {
      // For WebDAV paths, create a virtual Path using the string representation
      // This avoids the toAbsolutePath() issue that adds local filesystem prefix
      return WebDavDataSourceHelper.getWebDavPath(movie.getPath());
    }
    else {
      return movie.getPathNIO();
    }
  }

  /**
   * Rename movie inside the actual datasource.
   *
   * @param movie
   *          the movie
   */
  public static void renameMovie(Movie movie) {
    // skip renamer, if all templates are empty!
    if (MovieModuleManager.getInstance().getSettings().getRenamerPathname().isEmpty()
        && MovieModuleManager.getInstance().getSettings().getRenamerFilename().isEmpty()) {
      LOGGER.warn("NOT renaming Movie '{}' - renaming patterns are empty!", movie.getTitle());
      return;
    }

    // FIXME: what? when?
    boolean posterRenamed = false;
    boolean fanartRenamed = false;

    // check if a datasource is set
    if (StringUtils.isEmpty(movie.getDataSource())) {
      LOGGER.error("No data source set for movie '{}' - aborting!", movie.getTitle());
      return;
    }

    if (movie.getTitle().isEmpty()) {
      LOGGER.error("Won't rename movie '{}' - not even title is set?", movie.getPathNIO());
      return;
    }

    // all the good & needed mediafiles & hbistory
    List<MediaFile> needed = new ArrayList<>();
    List<MediaFile> cleanup = new ArrayList<>();
    MediaEntityFilenameHistory fileNameHistory = new MediaEntityFilenameHistory();

    LOGGER.info("Renaming movie: {}", movie.getTitle());
    LOGGER.debug("movie year: {}", movie.getYear());

    // For WebDAV paths, use getPath() directly to avoid Path conversion issues
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(movie.getPath());
    String moviePath = isWebDav ? movie.getPath() : movie.getPathNIO().toString();
    LOGGER.debug("movie path: {}", moviePath);
    LOGGER.debug("movie isWebDav: {}", isWebDav);

    LOGGER.debug("movie isDisc?: {}", movie.isDisc());
    LOGGER.debug("movie isMulti?: {}", movie.isMultiMovieDir());
    if (movie.getMovieSet() != null) {
      LOGGER.debug("movieset: {}", movie.getMovieSet().getTitle());
    }
    LOGGER.debug("path expression: {}", MovieModuleManager.getInstance().getSettings().getRenamerPathname());
    LOGGER.debug("file expression: {}", MovieModuleManager.getInstance().getSettings().getRenamerFilename());

    String newPathname = createDestinationForFoldername(MovieModuleManager.getInstance().getSettings().getRenamerPathname(), movie);
    String oldPathname = moviePath;

    if (!newPathname.isEmpty()) {
      try {
        if (isWebDav) {
          // For WebDAV, construct path using string concatenation
          String[] parts = WebDavDataSourceHelper.parseWebDavPath(movie.getDataSource());
          if (parts != null) {
            String sourceId = parts[0];
            newPathname = "webdav://" + sourceId + "/" + newPathname;
          }
          if (movie.isDisc()) {
            // Disc folders are creating by a separate logic
            // But valid for WebDAV?
            String separator = movie.getDataSource().endsWith("/") ? "" : "/";
            newPathname = WebDavDataSourceHelper.getWebDavPath(movie.getDataSource() + separator + newPathname).toString();
          }
          else {
            newPathname = WebDavDataSourceHelper.getWebDavPath(movie.getDataSource()).resolve(newPathname).toString();
          }
        }
        else {
          newPathname = Paths.get(movie.getDataSource(), newPathname).toString();
        }

        if (!renameMovieFolder(movie, newPathname)) {
          return;
        }
      }
      catch (Exception e) {
        LOGGER.warn("New movie folder name '{}' is not allowed - '{}'", newPathname, e.getMessage());
        newPathname = moviePath;
      }
    } // folder pattern empty
    else {
      LOGGER.debug("Folder rename settings were empty - NOT renaming folder");
      // set it to current for file renaming
      newPathname = moviePath;
    }

    // make sure we have actual stacking markers
    movie.reEvaluateStacking();

    // ######################################################################
    // ## mark ALL existing and known files for cleanup (clone!!)
    // ######################################################################
    for (MovieNfoNaming s : MovieNfoNaming.values()) {
      String nfoFilename = movie.getNfoFilename(s);
      if (StringUtils.isBlank(nfoFilename)) {
        continue;
      }
      // mark all known variants for cleanup
      MediaFile del;
      if (isWebDav) {
        // For WebDAV, construct path using string concatenation
        String nfoPath = moviePath.endsWith("/") ? moviePath + nfoFilename : moviePath + "/" + nfoFilename;
        del = new MediaFile(WebDavDataSourceHelper.getWebDavPath(nfoPath), MediaFileType.NFO);
      }
      else {
        del = new MediaFile(movie.getPathNIO().resolve(nfoFilename), MediaFileType.NFO);
      }
      cleanup.add(del);
    }
    List<IFileNaming> fileNamings = new ArrayList<>();
    fileNamings.addAll(Arrays.asList(MoviePosterNaming.values()));
    fileNamings.addAll(Arrays.asList(MovieFanartNaming.values()));
    fileNamings.addAll(Arrays.asList(MovieBannerNaming.values()));
    fileNamings.addAll(Arrays.asList(MovieClearartNaming.values()));
    fileNamings.addAll(Arrays.asList(MovieClearlogoNaming.values()));
    fileNamings.addAll(Arrays.asList(MovieThumbNaming.values()));
    fileNamings.addAll(Arrays.asList(MovieDiscartNaming.values()));
    fileNamings.addAll(Arrays.asList(MovieKeyartNaming.values()));

    for (IFileNaming fileNaming : fileNamings) {
      for (String ext : KNOWN_IMAGE_FILE_EXTENSIONS) {
        String artworkFilename = MovieArtworkHelper.getArtworkFilename(movie, fileNaming, ext);
        MediaFile del;
        if (isWebDav) {
          // For WebDAV, construct path using string concatenation
          String artworkPath = moviePath.endsWith("/") ? moviePath + artworkFilename : moviePath + "/" + artworkFilename;
          del = new MediaFile(WebDavDataSourceHelper.getWebDavPath(artworkPath));
        }
        else {
          del = new MediaFile(movie.getPathNIO().resolve(artworkFilename));
        }
        cleanup.add(del);
      }
    }

    // cleanup ALL MFs
    for (MediaFile del : movie.getMediaFiles()) {
      cleanup.add(new MediaFile(del));
    }
    cleanup.removeAll(Collections.singleton(null)); // remove all NULL ones!

    // BASENAME
    String oldVideoBasename = Utils.cleanStackingMarkers(movie.getMainVideoFile().getBasename());
    String newVideoBasename = generateNewVideoBasename(movie);

    // ######################################################################
    // ## rename VIDEO (move 1:1)
    // ######################################################################
    for (MediaFile vid : movie.getMediaFiles(MediaFileType.VIDEO)) {
      LOGGER.trace("Rename 1:1 {} - {}", vid.getType(), vid.getFileAsPath());
      MediaFile newMF = generateFilename(movie, vid, newVideoBasename).get(0); // there can be only one
      boolean ok = moveFile(vid.getFileAsPath(), newMF.getFileAsPath());
      if (ok) {
        fileNameHistory.addFilenameHistory(createFilenameHistory(newPathname, vid.getFileAsPath(), newMF.getFileAsPath()));
        vid.setFile(newMF.getFileAsPath()); // update
      }
      else {
        LOGGER.error("Could not move video file ({}) of movie '{}' - abort renaming", vid, movie.getTitle());
        // could not move main video file - abort!
        // if we're in a MMD, we did not do anything before, just reset the path
        if (movie.isMultiMovieDir()) {
          movie.setPath(oldPathname);
        }

        // Even if rename failed, try to update file size information for existing files
        // Only update if enabled for movies
        if (Settings.getInstance().isMovieUpdateFileSizeOnRename()) {
          LOGGER.info("=== DEBUGGING: Rename failed, but updating file size information anyway ===");
          movie.updateFileSizeInformation();
        }

        return;
      }
      needed.add(vid); // add vid, since we're updating existing MF object
    }

    // ######################################################################
    // ## rename POSTER, FANART, BANNER, CLEARART, THUMB, LOGO, CLEARLOGO, DISCART, KEYART (copy 1:N)
    // ######################################################################
    // we can have multiple ones, just get the newest one and copy(overwrite) them to all needed
    if (!MovieModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      List<MediaFile> mfs = new ArrayList<>();
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.FANART));
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.POSTER));
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.BANNER));
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.CLEARART));
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.THUMB));
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.LOGO));
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.CLEARLOGO));
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.DISC));
      mfs.add(movie.getNewestMediaFilesOfType(MediaFileType.KEYART));
      mfs.removeAll(Collections.singleton(null)); // remove all NULL ones!
      for (MediaFile mf : mfs) {
        LOGGER.trace("Rename 1:N {} - {}", mf.getType(), mf.getFileAsPath());
        List<MediaFile> newMFs = generateFilename(movie, mf, newVideoBasename); // 1:N
        for (MediaFile newMF : newMFs) {
          posterRenamed = true;
          fanartRenamed = true;
          boolean ok = copyFile(mf.getFileAsPath(), newMF.getFileAsPath());
          if (ok) {
            needed.add(newMF);
            fileNameHistory.addFilenameHistory(createFilenameHistory(newPathname, mf.getFileAsPath(), newMF.getFileAsPath()));

            // update the cached image by just COPYing it around (1:N)
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
    if (!MovieModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      // we need to find the newest, valid TMM NFO
      MediaFile nfo = MediaFile.EMPTY_MEDIAFILE;
      for (MediaFile mf : movie.getMediaFiles(MediaFileType.NFO)) {
        if (mf.getFiledate() >= nfo.getFiledate() && MovieConnectors.isValidNFO(mf.getFileAsPath())) {
          nfo = new MediaFile(mf);
        }
      }

      if (nfo != MediaFile.EMPTY_MEDIAFILE) { // one valid found? copy our NFO to all variants
        List<MediaFile> newNFOs = generateFilename(movie, nfo, newVideoBasename); // 1:N
        if (!newNFOs.isEmpty()) {
          // ok, at least one has been set up
          for (MediaFile newNFO : newNFOs) {
            boolean ok = copyFile(nfo.getFileAsPath(), newNFO.getFileAsPath());
            if (ok) {
              needed.add(newNFO);
              fileNameHistory.addFilenameHistory(createFilenameHistory(newPathname, nfo.getFileAsPath(), newNFO.getFileAsPath()));
            }
          }
        }
        else {
          // list was empty, so even remove this NFO
          cleanup.add(nfo);
        }
      }
      else {
        LOGGER.trace("No valid NFO found for this movie");
      }

      // now iterate over all non-tmm NFOs, and add them for cleanup or not
      for (MediaFile mf : movie.getMediaFiles(MediaFileType.NFO)) {
        if (MovieConnectors.isValidNFO(mf.getFileAsPath())) {
          cleanup.add(mf);
        }
        else {
          if (MovieModuleManager.getInstance().getSettings().isRenamerNfoCleanup()) {
            cleanup.add(mf);
          }
          else {
            needed.add(mf);
            fileNameHistory.addFilenameHistory(createFilenameHistory(newPathname, mf.getFileAsPath(), mf.getFileAsPath()));
          }
        }
      }
    }

    // ######################################################################
    // ## rename all other types (copy 1:1)
    // ######################################################################
    if (!MovieModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      List<MediaFile> mfs = new ArrayList<>(movie.getMediaFilesExceptType(MediaFileType.VIDEO, MediaFileType.NFO, MediaFileType.POSTER,
          MediaFileType.FANART, MediaFileType.BANNER, MediaFileType.CLEARART, MediaFileType.THUMB, MediaFileType.LOGO, MediaFileType.CLEARLOGO,
          MediaFileType.DISC, MediaFileType.KEYART, MediaFileType.SUBTITLE));
      mfs.removeAll(Collections.singleton(null)); // remove all NULL ones!
      for (MediaFile other : mfs) {
        LOGGER.trace("Rename 1:1 {} - {}", other.getType(), other.getFileAsPath());

        List<MediaFile> newMFs = generateFilename(movie, other, newVideoBasename, oldVideoBasename); // 1:N
        newMFs.removeAll(Collections.singleton(null)); // remove all NULL ones!
        for (MediaFile newMF : newMFs) {
          boolean ok = copyFile(other.getFileAsPath(), newMF.getFileAsPath());
          if (ok) {
            fileNameHistory.addFilenameHistory(createFilenameHistory(newPathname, other.getFileAsPath(), newMF.getFileAsPath()));
            needed.add(newMF);
          }
          else {
            // FIXME: what to do? not copied/exception... keep it for now...
            fileNameHistory.addFilenameHistory(createFilenameHistory(newPathname, other.getFileAsPath(), other.getFileAsPath()));
            needed.add(other);
          }
        }
      }
    }

    // ######################################################################
    // ## rename SUBTITLEs (copy 1:1)
    // ######################################################################
    if (!MovieModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      for (MediaFile sub : movie.getMediaFiles(MediaFileType.SUBTITLE)) {
        LOGGER.trace("Rename 1:1 {} - {}", sub.getType(), sub.getFileAsPath());
        MediaFile newMF = generateFilename(movie, sub, newVideoBasename, oldVideoBasename).get(0);
        boolean ok = moveFile(sub.getFileAsPath(), newMF.getFileAsPath());
        if (ok) {
          if (sub.getFilename().endsWith(".sub")) {
            // when having a .sub, also rename .idx (don't care if error)
            try {
              Path oldidx = sub.getFileAsPath().resolveSibling(sub.getFilename().replaceFirst("sub$", "idx"));
              Path newidx = newMF.getFileAsPath().resolveSibling(newMF.getFilename().toString().replaceFirst("sub$", "idx"));
              Utils.moveFileSafe(oldidx, newidx);
              fileNameHistory.addFilenameHistory(createFilenameHistory(newPathname, oldidx, newidx));
            }
            catch (Exception e) {
              // no idx found or error - ignore
            }
          }
          needed.add(newMF);
          fileNameHistory.addFilenameHistory(createFilenameHistory(newPathname, sub.getFileAsPath(), newMF.getFileAsPath()));
        }
        else {
          LOGGER.warn("Could not rename subtitle file '{}'", sub.getFileAsPath());
          needed.add(sub);
        }
      }
    }

    // ######################################################################
    // ## invalidate image cache
    // ######################################################################
    for (MediaFile gfx : movie.getMediaFiles()) {
      if (gfx.isGraphic() && !needed.contains(gfx)) {
        ImageCache.invalidateCachedImage(gfx);
      }
    }

    // remove duplicate MediaFiles
    Set<MediaFile> newMFs = new LinkedHashSet<>(needed);
    needed.clear();
    needed.addAll(newMFs);

    // 保存所有非视频文件的引用，当仅处理视频文件时使用
    List<MediaFile> nonVideoFiles = new ArrayList<>();
    if (MovieModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      nonVideoFiles.addAll(movie.getMediaFilesExceptType(MediaFileType.VIDEO));
    }

    movie.removeAllMediaFiles();

    // ######################################################################
    // ## build up image cache
    // ######################################################################
    if (Settings.getInstance().isImageCache() && !MovieModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      for (MediaFile gfx : needed) {
        ImageCache.cacheImageSilently(gfx, false);
      }
    }

    // give the file system a bit to write the files
    ThreadUtils.sleep(250);

    // 当仅处理视频文件时，将非视频文件添加回needed列表
    if (MovieModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      needed.addAll(nonVideoFiles);
    }

    movie.addToMediaFiles(needed);
    movie.setPath(newPathname);

    // Update file size information after rename only if enabled for movies
    if (Settings.getInstance().isMovieUpdateFileSizeOnRename()) {
      movie.updateFileSizeInformation();
    }

    // Only gather full media information if enabled in settings
    if (Settings.getInstance().isFetchVideoInfoOnUpdate()) {
      movie.gatherMediaFileInformation(false);
    }

    // rewrite NFO if it's a MP NFO and there was a change with poster/fanart
    if (MovieModuleManager.getInstance().getSettings().getMovieConnector() == MovieConnectors.MP && (posterRenamed || fanartRenamed)) {
      movie.writeNFO();
    }

    // ######################################################################
    // ## CLEANUP - delete all files marked for cleanup, which are not "needed"
    // ######################################################################
    if (!MovieModuleManager.getInstance().getSettings().isRenamerOnlyVideoFiles()) {
      LOGGER.debug("Cleanup...");

      // get all existing files in the movie dir, since Files.exist is not reliable in OSX
      List<Path> existingFiles;
      if (isWebDav) {
        // For WebDAV, skip file listing cleanup (not supported for virtual paths)
        existingFiles = new ArrayList<>();
      }
      else if (movie.isMultiMovieDir()) {
        // no recursive search in MMD needed
        existingFiles = Utils.listFiles(movie.getPathNIO());
      }
      else {
        // search all files recursive for deeper cleanup
        existingFiles = Utils.listFilesRecursive(movie.getPathNIO());
      }

      // also add all files from the old path (if upgraded from MMD)
      if (!isWebDav) {
        existingFiles.addAll(Utils.listFiles(Paths.get(oldPathname)));
      }

      for (int i = cleanup.size() - 1; i >= 0; i--) {
        MediaFile cl = cleanup.get(i);

        // cleanup files which are not needed
        if (!needed.contains(cl)) {
          // For WebDAV, use string comparison instead of Path.equals()
          boolean isDataSourcePath = isWebDav
              ? WebDavDataSourceHelper.normalizeWebDavPath(cl.getFileAsPath().toString()).equals(movie.getDataSource())
              : cl.getFileAsPath().equals(Paths.get(movie.getDataSource()));
          boolean isMoviePath = isWebDav ? WebDavDataSourceHelper.normalizeWebDavPath(cl.getFileAsPath().toString()).equals(moviePath)
              : cl.getFileAsPath().equals(movie.getPathNIO());
          boolean isOldPath = isWebDav ? WebDavDataSourceHelper.normalizeWebDavPath(cl.getFileAsPath().toString()).equals(oldPathname)
              : cl.getFileAsPath().equals(Paths.get(oldPathname));

          if (isDataSourcePath || isMoviePath || isOldPath) {
            LOGGER.warn("Wohoo! We tried to remove complete datasource / movie folder. Nooo way...! '{}' / '{}'", cl.getType(), cl.getFileAsPath());
            // happens when iterating eg over the getNFONaming and we return a "" string.
            // then the path+filename = movie path and we want to delete :/
            continue;
          }

          movie.removeFromMediaFiles(cl);

          if (existingFiles.contains(cl.getFileAsPath())) {
            LOGGER.debug("Deleting {}", cl.getFileAsPath());
            Utils.deleteFileWithBackup(cl.getFileAsPath(), movie.getDataSource());
            // also cleanup the cache for deleted mfs
            if (cl.isGraphic()) {
              ImageCache.invalidateCachedImage(cl);
            }
          }

          try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(cl.getFileAsPath().getParent())) {
            if (!directoryStream.iterator().hasNext()) {
              // no iterator = empty
              LOGGER.debug("Deleting empty Directory {}", cl.getFileAsPath().getParent());
              Files.delete(cl.getFileAsPath().getParent()); // do not use recursive her
            }
          }
          catch (IOException e) {
            LOGGER.debug("could not search for empty dir: {}", e.getMessage());
          }
        }
      }

      cleanupUnwantedFiles(movie);
      removeEmptySubfolders(movie);
    }

    // rename history
    fileNameHistory.setOldPath(oldPathname);
    fileNameHistory.setNewPath(newPathname);
    movie.setRenameHistory(fileNameHistory);

    movie.saveToDb();
  }

  public static void undoRename(Movie movie) {
    if (movie.getRenameHistory() == null) {
      LOGGER.debug("could not undo rename - no history available");
      return;
    }

    List<MediaFile> needed = new ArrayList<>();

    Path oldMoviePath = WebDavDataSourceHelper.getWebDavPath(movie.getRenameHistory().getOldPath());
    Path newMoviePath = WebDavDataSourceHelper.getWebDavPath(movie.getRenameHistory().getNewPath());

    // try the VIDEO file(s) first
    for (MediaFile vid : movie.getMediaFiles(MediaFileType.VIDEO)) {
      MediaEntityFilenameHistory.FilenameHistory filenameHistory = findFilenameHistoryForMediaFile(movie, vid);
      if (filenameHistory == null) {
        LOGGER.debug("could not undo rename - VIDEO file history not found");
        return;
      }

      LOGGER.trace("Rename 1:1 {} - {}", vid.getType(), vid.getFileAsPath());
      MediaFile oldMF = new MediaFile(vid);
      oldMF.replacePathForRenamedFolder(newMoviePath, oldMoviePath);
      oldMF.setFile(oldMoviePath.resolve(filenameHistory.oldFilename()));

      if (movie.isDisc() && Files.isDirectory(vid.getFile())) {
        boolean ok = moveDirectory(vid.getFileAsPath(), oldMF.getFileAsPath());
        if (ok) {
          vid.setFile(oldMF.getFileAsPath()); // update
        }
        else {
          LOGGER.error("Could not move video file ({}) of movie '{}' - abort renaming", vid, movie.getTitle());
          return;
        }
      }
      else {
        boolean ok = moveFile(vid.getFileAsPath(), oldMF.getFileAsPath());
        if (ok) {
          vid.setFile(oldMF.getFileAsPath()); // update
        }
        else {
          LOGGER.error("Could not move video file ({}) of movie '{}' - abort renaming", vid, movie.getTitle());
          return;
        }
      }

      needed.add(vid); // add vid, since we're updating existing MF object
    }

    // and all others
    for (MediaFile mediaFile : movie.getMediaFilesExceptType(MediaFileType.VIDEO)) {
      MediaEntityFilenameHistory.FilenameHistory filenameHistory = findFilenameHistoryForMediaFile(movie, mediaFile);
      if (filenameHistory == null) {
        LOGGER.debug("could not undo rename for '{}' - history not found", mediaFile.getFilename());
        continue;
      }

      LOGGER.trace("Rename 1:1 {} - {}", mediaFile.getType(), mediaFile.getFileAsPath());
      MediaFile oldMF = new MediaFile(mediaFile);
      oldMF.replacePathForRenamedFolder(newMoviePath, oldMoviePath);
      oldMF.setFile(oldMoviePath.resolve(filenameHistory.oldFilename()));

      if (movie.isDisc() && !Files.exists(mediaFile.getFileAsPath())) {
        // this file probably has been merged into the disc folder -> we need to adopt the path
        Path newPath = movie.getMainVideoFile().getFileAsPath().resolve(mediaFile.getFilename());
        mediaFile.setFile(newPath);
      }

      boolean ok = moveFile(mediaFile.getFileAsPath(), oldMF.getFileAsPath());
      if (ok) {
        mediaFile.setFile(oldMF.getFileAsPath()); // update
        needed.add(mediaFile);
      }
      else {
        // probably a 1:n rename - just remove that file
        Utils.deleteFileWithBackup(mediaFile.getFileAsPath(), movie.getDataSource());
      }
    }

    // remove duplicate MediaFiles
    Set<MediaFile> newMFs = new LinkedHashSet<>(needed);
    needed.clear();
    needed.addAll(newMFs);

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

    movie.addToMediaFiles(needed);
    movie.setPath(movie.getRenameHistory().getOldPath());

    // Update file size information after rename only if enabled for movies
    if (Settings.getInstance().isMovieUpdateFileSizeOnRename()) {
      movie.updateFileSizeInformation();
    }

    // Only gather full media information if enabled in settings
    if (Settings.getInstance().isFetchVideoInfoOnUpdate()) {
      movie.gatherMediaFileInformation(false);
    }

    // remove history
    movie.setRenameHistory(null);

    movie.saveToDb();

    // cleanup old path
    try {
      Utils.deleteEmptyDirectoryRecursive(newMoviePath);
    }
    catch (IOException e) {
      LOGGER.warn("Could not delete empty subfolders of '{}' - '{}'", newMoviePath, e.getMessage());
    }
  }

  private static MediaEntityFilenameHistory.FilenameHistory createFilenameHistory(String newMoviePath, Path oldFilePath, Path newFilePath) {
    String oldFilename = Paths.get(newMoviePath).relativize(oldFilePath).toString(); // already changed in the generate filename logic
    String newFilename = Paths.get(newMoviePath).relativize(newFilePath).toString();
    return new MediaEntityFilenameHistory.FilenameHistory(oldFilename, newFilename);
  }

  private static MediaEntityFilenameHistory.FilenameHistory findFilenameHistoryForMediaFile(Movie movie, MediaFile mediaFile) {
    if (movie.getRenameHistory() == null) {
      return null;
    }

    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(movie.getPath());
    String moviePathStr = movie.getPath();

    for (MediaEntityFilenameHistory.FilenameHistory filenameHistory : movie.getRenameHistory().getFilenameHistory()) {
      String relativeFilename;
      if (isWebDav) {
        // For WebDAV, use string manipulation to get relative path
        String filePathStr = WebDavDataSourceHelper.normalizeWebDavPath(mediaFile.getFileAsPath().toString());
        if (filePathStr.startsWith(moviePathStr)) {
          relativeFilename = filePathStr.substring(moviePathStr.length());
          if (relativeFilename.startsWith("/")) {
            relativeFilename = relativeFilename.substring(1);
          }
        }
        else {
          relativeFilename = filePathStr;
        }
      }
      else {
        Path moviePath = movie.getPathNIO();
        relativeFilename = moviePath.relativize(mediaFile.getFileAsPath()).toString();
      }

      if (filenameHistory.newFilename().equals(relativeFilename)) {
        return filenameHistory;
      }
    }

    return null;
  }

  private static boolean renameMovieFolder(Movie movie, String newPathname) {
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(movie.getPath());

    // For WebDAV, use string comparison instead of Path operations
    if (isWebDav) {
      String srcPath = movie.getPath();
      if (!srcPath.equals(newPathname)) {
        // WebDAV folder renaming not yet supported
        LOGGER.warn("WebDAV folder renaming is not yet supported. Keeping original path: {}", srcPath);
        return true; // Return true to continue with file renaming
      }
      return true;
    }

    Path srcDir = movie.getPathNIO();
    Path destDir = Paths.get(newPathname);
    if (!srcDir.toAbsolutePath().toString().equals(destDir.toAbsolutePath().toString())) {
      boolean newDestIsMultiMovieDir = false;
      // re-evaluate multiMovieDir based on renamer settings
      // folder MUST BE UNIQUE, we need at least a T/E-Y combo or IMDBid
      // so if renaming just to a fixed pattern (eg "$S"), movie will downgrade to a MMD
      if (!isFolderPatternUnique(MovieModuleManager.getInstance().getSettings().getRenamerPathname())) {
        newDestIsMultiMovieDir = true;
      }
      else {
        // check if the target folder already exists (and is not empty)
        // check if the user wants this behaviour
        try {
          if (Files.exists(destDir) && !Utils.isFolderEmpty(destDir)
              && MovieModuleManager.getInstance().getSettings().isAllowMultipleMoviesInSameDir()) {
            // destination folder exists and is not empty - assume there is another movie -> MMD = true
            newDestIsMultiMovieDir = true;
            MessageManager.getInstance()
                .pushMessage(new Message(MessageLevel.INFO, srcDir, "message.renamer.mergetommd", new String[] { movie.getTitle() }));
          }
        }
        catch (Exception e) {
          LOGGER.warn("Could not check if dir '{}' exists/is empty - '{}'", destDir, e.getMessage());
        }
      }
      LOGGER.debug("movie willBeMulti?: {}", newDestIsMultiMovieDir);

      // ######################################################################
      // ## 1) old = separate movie dir, and new too -> move folder
      // ######################################################################
      if (!movie.isMultiMovieDir() && !newDestIsMultiMovieDir) {
        boolean ok;
        try {
          ok = Utils.moveDirectorySafe(srcDir, destDir);
          if (ok) {
            movie.setMultiMovieDir(false);
            movie.updateMediaFilePath(srcDir, destDir);
            movie.setPath(newPathname);
            movie.saveToDb(); // since we moved already, save it
          }
        }
        catch (Exception e) {
          LOGGER.error("Error renaming movie folder '{}' to '{}' - '{}' ", srcDir, destDir, e.getMessage());
          MessageManager.getInstance()
              .pushMessage(new Message(MessageLevel.ERROR, srcDir, "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
          return false;
        }
        if (!ok) {
          MessageManager.getInstance()
              .pushMessage(new Message(MessageLevel.ERROR, srcDir, "message.renamer.failedrename", new String[] { movie.getTitle() }));
          LOGGER.error("Could not move movie '{}' to destination '{}' - NOT renaming folder", movie.getTitle(), destDir);
          return false;
        }
      }
      else if (movie.isMultiMovieDir() && !newDestIsMultiMovieDir) {
        // ######################################################################
        // ## 2) MMD movie -> normal movie (upgrade)
        // ######################################################################
        LOGGER.trace("Upgrading movie into it's own dir :) - {}", newPathname);
        if (!Files.exists(destDir)) {
          try {
            Files.createDirectories(destDir);
          }
          catch (Exception e) {
            LOGGER.error("Could not create destination '{}' - NOT renaming folder ('upgrade' movie) - '{}'", destDir, e.getMessage());
            // well, better not to rename
            return false;
          }
        }
        else {
          LOGGER.error("Directory already exists! '{}' - NOT renaming folder ('upgrade' movie)", destDir);
          // well, better not to rename
          return false;
        }
        movie.setMultiMovieDir(false);
      }
      else {
        // ######################################################################
        // ## Can be
        // ## 3) MMD movie -> MMD movie (but foldername possible changed)
        // ## 4) normal movie -> MMD movie (downgrade)
        // ## either way - check & create dest folder
        // ######################################################################
        LOGGER.trace("New movie path is a MMD :( - {}", newPathname);
        if (!Files.exists(destDir)) { // if existent, all is good -> MMD
          try {
            Files.createDirectories(destDir);
          }
          catch (Exception e) {
            LOGGER.error("Could not create destination '{}' - NOT renaming folder ('MMD' movie) - '{}'", destDir, e.getMessage());
            // well, better not to rename
            return false;
          }
        }
        movie.setMultiMovieDir(true);
      }
    } // src == dest

    return true;
  }

  public static String generateNewVideoBasename(Movie movie) {
    String newVideoBasename = "";
    if (!isFilePatternValid()) {
      // Template empty or not even title set, so we are NOT renaming any files
      // we keep the same name on renaming ;)
      newVideoBasename = movie.getVideoBasenameWithoutStacking();
      LOGGER.warn("File pattern '{}' is not valid - NOT renaming files!", MovieModuleManager.getInstance().getSettings().getRenamerFilename());
    }
    else {
      // since we rename, generate the new basename
      String oldVideoBasename = Utils.cleanStackingMarkers(movie.getMainVideoFile().getBasename());
      MediaFile ftr = generateFilename(movie, movie.getMediaFiles(MediaFileType.VIDEO).get(0), newVideoBasename, oldVideoBasename).get(0);
      newVideoBasename = FilenameUtils.getBaseName(ftr.getFilenameWithoutStacking());
    }
    LOGGER.debug("Our new basename for renaming: {}", newVideoBasename);
    return newVideoBasename;
  }

  /**
   * generates renamed filename(s) per MF
   *
   * @param movie
   *          the movie (for datasource, path)
   * @param mf
   *          the MF
   * @param newVideoFileName
   *          the basename of the renamed videoFileName (saved earlier)
   * @return list of renamed filename
   */
  public static List<MediaFile> generateFilename(Movie movie, MediaFile mf, String newVideoFileName) {
    return generateFilename(movie, mf, newVideoFileName, "");
  }

  /**
   * generates renamed filename(s) per MF
   *
   * @param movie
   *          the movie (for datasource, path)
   * @param mf
   *          the MF
   * @param newVideoFileName
   *          the basename of the renamed videoFileName (saved earlier)
   * @param oldVideoFileName
   *          the basename of the ORIGINAL videoFileName (saved earlier)
   * @return list of renamed filename
   */
  public static List<MediaFile> generateFilename(Movie movie, MediaFile mf, String newVideoFileName, String oldVideoFileName) {
    // return list of all generated MFs
    List<MediaFile> newFiles = new ArrayList<>();
    boolean newDestIsMultiMovieDir = movie.isMultiMovieDir();
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(movie.getPath());
    String newPathname = "";

    String pattern = MovieModuleManager.getInstance().getSettings().getRenamerPathname();
    // keep MMD setting unless renamer pattern is not empty
    if (!pattern.isEmpty()) {
      // re-evaluate multiMovieDir based on renamer settings
      // folder MUST BE UNIQUE, so we need at least a T/E-Y combo or IMDBid
      // If renaming just to a fixed pattern (eg "$S"), movie will downgrade to a MMD
      newDestIsMultiMovieDir = !MovieRenamer.isFolderPatternUnique(pattern);
      newPathname = MovieRenamer.createDestinationForFoldername(pattern, movie);
    }
    else {
      // keep same dir
      if (isWebDav) {
        // Use WebDavPath for clean path extraction
        try {
          LOGGER.debug("Rename Debug - movie.getPath(): {}", movie.getPath());
          LOGGER.debug("Rename Debug - movie.getDataSource(): {}", movie.getDataSource());

          WebDavPath moviePath = new WebDavPath(movie.getPath());
          WebDavPath datasourcePath = new WebDavPath(movie.getDataSource());

          LOGGER.debug("Rename Debug - moviePath.remotePath: {}", moviePath.getRemotePath());
          LOGGER.debug("Rename Debug - datasourcePath.remotePath: {}", datasourcePath.getRemotePath());

          newPathname = moviePath.getRelativePath(datasourcePath);

          LOGGER.debug("Rename Debug - calculated newPathname: {}", newPathname);

          if (newPathname == null) {
            // If relative path calculation failed, use the folder name
            newPathname = moviePath.getFileName();
            LOGGER.debug("Could not calculate relative path, using folder name: {}", newPathname);
          }
        }
        catch (Exception e) {
          LOGGER.warn("Error processing WebDAV paths: {}", e.getMessage());
          // Fallback to empty pathname
          newPathname = "";
        }
      }
      else {
        // we are moving; searching for the relativity of the old name
        newPathname = Utils.relPath(WebDavDataSourceHelper.getWebDavPath(movie.getDataSource()), movie.getPathNIO());
      }
    }

    Path newMovieDir;
    WebDavPath newMovieDirWebDavPath = null; // Save the WebDavPath object for later use

    if (isWebDav) {
      // Use WebDavPath for clean path construction
      try {
        WebDavPath datasourcePath = new WebDavPath(movie.getDataSource());
        String safeNewPathname = newPathname != null ? newPathname : "";

        LOGGER.debug("Rename Debug - safeNewPathname before check: '{}'", safeNewPathname);

        if (safeNewPathname.isEmpty()) {
          // If newPathname is empty, use the current movie folder name
          WebDavPath moviePath = new WebDavPath(movie.getPath());
          safeNewPathname = moviePath.getFileName();
          LOGGER.debug("Rename Debug - safeNewPathname was empty, now: '{}'", safeNewPathname);
        }

        newMovieDirWebDavPath = datasourcePath.resolve(safeNewPathname);
        newMovieDir = newMovieDirWebDavPath.toPath();

        LOGGER.debug("Rename Debug - newMovieDirWebDavPath: {}", newMovieDirWebDavPath.toString());
        LOGGER.debug("Rename Debug - newMovieDir: {}", newMovieDir);
      }
      catch (Exception e) {
        LOGGER.warn("Error constructing WebDAV path: {}", e.getMessage());
        // Fallback to current path
        newMovieDir = movie.getPathNIO();
      }
    }
    else {
      newMovieDir = movie.getPathNIO();
      try {
        String separator = movie.getDataSource().endsWith("/") ? "" : "/";
        newMovieDir = WebDavDataSourceHelper.getWebDavPath(movie.getDataSource() + separator + newPathname);
      }
      catch (Exception e) {
        LOGGER.warn("New movie folder name '{}' is not allowed - '{}'", newPathname, e.getMessage());
      }
    }

    // For WebDAV, use the string representation from WebDavPath to avoid Path.toString() format issues
    final String newMovieDirStr = (isWebDav && newMovieDirWebDavPath != null) ? newMovieDirWebDavPath.toString() : newMovieDir.toString();

    String newFilename = newVideoFileName;
    if (StringUtils.isBlank(newFilename)) {
      // empty only when first generating basename, so generation here is OK
      newFilename = createDestinationForFilename(MovieModuleManager.getInstance().getSettings().getRenamerFilename(), movie);
    }
    // when renaming with $originalFilename, we get already the extension added!
    if (newFilename.endsWith("." + mf.getExtension())) {
      newFilename = FilenameUtils.getBaseName(newFilename);
    }

    // happens, when renaming pattern returns nothing (empty field like originalTitle)
    // just return same file
    if (StringUtils.isBlank(newFilename)) {
      newFiles.add(mf);
      return newFiles;
    }

    // extra clone, just for easy adding the "default" ones ;)
    MediaFile defaultMF = new MediaFile(mf);

    // Check if this specific file is a WebDAV file, not just if the movie is WebDAV
    // (e.g., poster images might be local cache files even if the movie is on WebDAV)
    String filePathStr = WebDavDataSourceHelper.normalizeWebDavPath(mf.getFileAsPath().toString());
    boolean isFileWebDav = WebDavDataSourceHelper.isWebDavPath(filePathStr);

    if (isWebDav && isFileWebDav) {
      // Both movie and file are WebDAV - use WebDavPath for clean path replacement
      try {
        WebDavPath oldPath = new WebDavPath(movie.getPath());
        WebDavPath filePath = new WebDavPath(filePathStr);

        // Use the saved WebDavPath object instead of recreating from newMovieDir.toString()
        // This avoids path format issues with Paths.get().toString()
        WebDavPath newMovieDirPath = newMovieDirWebDavPath != null ? newMovieDirWebDavPath : new WebDavPath(movie.getDataSource());

        // Calculate relative path from old movie dir to file
        String relativePart = filePath.getRelativePath(oldPath);

        if (relativePart != null) {
          // Resolve relative path against new movie dir
          WebDavPath newFilePath = newMovieDirPath.resolve(relativePart);
          defaultMF.setFile(newFilePath.toPath());
        }
        else {
          LOGGER.warn("Could not calculate relative path for WebDAV file: {}", filePathStr);
        }
      }
      catch (Exception e) {
        LOGGER.warn("Error replacing WebDAV file path: {}", e.getMessage());
      }
    }
    else {
      // File is local (or movie is local) - use standard path replacement
      defaultMF.replacePathForRenamedFolder(movie.getPathNIO(), newMovieDir);
    }

    Path relativePathOfMediafile;
    if (isWebDav && isFileWebDav) {
      // Both movie and file are WebDAV - use WebDavPath for clean relative path calculation
      try {
        WebDavPath moviePath = new WebDavPath(movie.getPath());
        WebDavPath filePath = new WebDavPath(filePathStr);

        String relativePath = filePath.getRelativePath(moviePath);
        if (relativePath != null) {
          relativePathOfMediafile = Paths.get(relativePath);
        }
        else {
          relativePathOfMediafile = mf.getFileAsPath();
        }
      }
      catch (Exception e) {
        LOGGER.warn("Error calculating relative path for WebDAV file: {}", e.getMessage());
        relativePathOfMediafile = mf.getFileAsPath();
      }
    }
    else {
      // file is local
      if (isWebDav) {
        // movie is WebDAV but file is local (e.g. artwork in cache)
        // we cannot relativize a local absolute path against a WebDAV path
        // just use the filename, assuming it will be put into the movie folder
        relativePathOfMediafile = Paths.get(mf.getFilename());
      }
      else {
        // File is local and Movie is local - use standard relative path calculation
        relativePathOfMediafile = movie.getPathNIO().relativize(mf.getFileAsPath());
      }
    }

    if (!isFilePatternValid() && !movie.isDisc()) {
      // not renaming files, but IF we have a folder pattern, we need to move around! (but NOT disc movies!)
      newFiles.add(defaultMF);
      return newFiles;
    }

    switch (mf.getType()) {
      case VIDEO:
        MediaFile vid = new MediaFile(mf);
        if (movie.isDisc() || mf.isDiscFile()) {
          // just replace new path and return file (do not change names!)
          if (isWebDav) {
            // For WebDAV, manually replace path with proper URL normalization
            String oldPath = movie.getPath();
            String newPath = newMovieDirStr;
            String filePath = WebDavDataSourceHelper.normalizeWebDavPath(mf.getFileAsPath().toString());

            // Normalize all paths for consistent comparison
            String normalizedOldPath = oldPath.replaceFirst("webdav:/", "webdav://");
            String normalizedNewPath = newPath.replaceFirst("webdav:/", "webdav://");
            String normalizedFilePath = filePath.replaceFirst("webdav:/", "webdav://");

            // Ensure old path ends with slash
            if (!normalizedOldPath.endsWith("/")) {
              normalizedOldPath = normalizedOldPath + "/";
            }

            // Try with normalized paths for comparison
            if (normalizedFilePath.startsWith(normalizedOldPath)) {
              String relativePart = normalizedFilePath.substring(normalizedOldPath.length());
              String newFilePath = normalizedNewPath.endsWith("/") ? normalizedNewPath + relativePart : normalizedNewPath + "/" + relativePart;
              vid.setFile(Paths.get(newFilePath));
            }
            // Fallback to original comparison if normalization fails
            else if (filePath.startsWith(oldPath)) {
              String relativePart = filePath.substring(oldPath.length());
              String newFilePath = newPath.endsWith("/") ? newPath + relativePart : newPath + "/" + relativePart;
              vid.setFile(Paths.get(newFilePath));
            }
          }
          else {
            vid.replacePathForRenamedFolder(movie.getPathNIO(), newMovieDir);
          }
        }
        else {
          newFilename += getStackingString(mf);
          newFilename += "." + mf.getExtension();

          // Check if the file is in a subdirectory relative to the movie folder
          String oldPath = movie.getPath();
          String filePath = WebDavDataSourceHelper.normalizeWebDavPath(mf.getFileAsPath().toString());

          // Normalize URLs for comparison - handle single vs double slashes in protocol
          String normalizedOldPath = oldPath.replaceFirst("webdav:/", "webdav://");
          String normalizedFilePath = filePath.replaceFirst("webdav:/", "webdav://");

          // Ensure old path ends with slash for correct subdirectory matching
          if (!normalizedOldPath.endsWith("/")) {
            normalizedOldPath = normalizedOldPath + "/";
          }

          if (normalizedFilePath.startsWith(normalizedOldPath)) {
            // Extract the subdirectory path from the original file path
            String relativePart = normalizedFilePath.substring(normalizedOldPath.length());
            if (relativePart.startsWith("/")) {
              relativePart = relativePart.substring(1);
            }

            // Check if there's a subdirectory (contains path separator)
            int lastSlashIndex = relativePart.lastIndexOf('/');
            if (lastSlashIndex >= 0) {
              // File is in a subdirectory, preserve the subdirectory structure
              String subdirectory = relativePart.substring(0, lastSlashIndex + 1);
              if (isWebDav) {
                String newPath = newMovieDirStr;
                String newFilePath = newPath.endsWith("/") ? newPath + subdirectory + newFilename : newPath + "/" + subdirectory + newFilename;
                vid.setFile(Paths.get(newFilePath));
              }
              else {
                Path subdirPath = Paths.get(subdirectory);
                vid.setFile(newMovieDir.resolve(subdirPath).resolve(newFilename));
              }
            }
            else {
              // File is in the root movie directory, use the standard approach
              if (isWebDav) {
                // For WebDAV, use string concatenation
                String newPath = newMovieDirStr;
                String newFilePath = newPath.endsWith("/") ? newPath + newFilename : newPath + "/" + newFilename;
                vid.setFile(Paths.get(newFilePath));
              }
              else {
                vid.setFile(newMovieDir.resolve(newFilename));
              }
            }
          }
          else {
            // Fallback: try to extract filename from original path and append to new directory
            // This handles cases where paths might not match exactly but we still want to preserve subdirectories
            String originalFilename = mf.getFilename();
            String fullFilePath = WebDavDataSourceHelper.normalizeWebDavPath(mf.getFileAsPath().toString());

            // Find the position of the original filename in the full path
            int filenameIndex = fullFilePath.lastIndexOf(originalFilename);
            if (filenameIndex > 0) {
              // Extract the path part before the filename (subdirectory structure)
              String subdirectoryPath = fullFilePath.substring(0, filenameIndex);

              // Try to find common path part with movie path
              String moviePath = movie.getPath();

              // Normalize both paths for comparison
              String normalizedMoviePath = isWebDav ? moviePath.replaceFirst("webdav:/", "webdav://") : moviePath;
              String normalizedSubdirectoryPath = isWebDav ? subdirectoryPath.replaceFirst("webdav:/", "webdav://") : subdirectoryPath;

              if (normalizedSubdirectoryPath.startsWith(normalizedMoviePath)) {
                // Extract just the subdirectory part relative to the movie directory
                String relativeSubdir = normalizedSubdirectoryPath.substring(normalizedMoviePath.length());
                if (isWebDav) {
                  String newPath = newMovieDirStr;
                  String newFilePath = newPath.endsWith("/") ? newPath + relativeSubdir + newFilename : newPath + "/" + relativeSubdir + newFilename;
                  vid.setFile(Paths.get(newFilePath));
                }
                else {
                  Path subdirPath = Paths.get(relativeSubdir);
                  vid.setFile(newMovieDir.resolve(subdirPath).resolve(newFilename));
                }
              }
              else {
                // If all else fails, at least keep the original filename
                if (isWebDav) {
                  String newPath = newMovieDirStr;
                  String newFilePath = newPath.endsWith("/") ? newPath + newFilename : newPath + "/" + newFilename;
                  vid.setFile(Paths.get(newFilePath));
                }
                else {
                  vid.setFile(newMovieDir.resolve(newFilename));
                }
              }
            }
            else {
              // Fallback if we can't extract subdirectory
              if (isWebDav) {
                String newPath = newMovieDirStr;
                String newFilePath = newPath.endsWith("/") ? newPath + newFilename : newPath + "/" + newFilename;
                vid.setFile(Paths.get(newFilePath));
              }
              else {
                vid.setFile(newMovieDir.resolve(newFilename));
              }
            }
          }
        }
        newFiles.add(vid);
        break;

      case TRAILER:
        // if the trailer is in a /trailer subfolder, just move it to the destination
        if (relativePathOfMediafile.getNameCount() > 1
            && MediaFileHelper.TRAILER_FOLDERS.contains(relativePathOfMediafile.subpath(0, 1).toString().toLowerCase(Locale.ROOT))) {
          // the trailer is in a /trailer(s) subfolder
          newFiles.add(defaultMF);
        }
        else {
          // not in a /trailer(s) subfolder
          List<MovieTrailerNaming> trailernames = new ArrayList<>();
          if (newDestIsMultiMovieDir) {
            // Fixate the name regardless of setting
            trailernames.add(MovieTrailerNaming.FILENAME_TRAILER);
          }
          else if (movie.isDisc()) {
            trailernames.add(MovieTrailerNaming.FILENAME_TRAILER);
          }
          else {
            trailernames.addAll(MovieModuleManager.getInstance().getSettings().getTrailerFilenames());
            if (trailernames.isEmpty()) {
              // we have a trailer, but no settings for it?! don't delete it, just rename it to the default
              trailernames.add(MovieTrailerNaming.FILENAME_TRAILER);
            }
          }

          // check if the trailer ends with a "stacking" marker
          String stackingMarker = "";
          Matcher matcher = TRAILER_STACKING_PATTERN.matcher(mf.getBasename());
          if (matcher.matches()) {
            stackingMarker = matcher.group(1);
          }

          // getTrailerFilename NEEDS extension - so add it here default, and overwrite it in isDisc()
          newFilename += ".avi";
          // DVD/BluRay folders can have trailers within!
          // check for discFolders and/or files
          Path outputFolder;
          if (movie.isDisc() && MovieModuleManager.getInstance().getSettings().isTrailerDiscFolderInside()) {
            MediaFile main = movie.getMainFile();
            if (MediaFileHelper.isDiscFolder(main.getFilename())) {
              Path mainFile = main.getFileAsPath();
              Path rel;
              if (isWebDav) {
                // For WebDAV, use string manipulation
                String moviePath = movie.getPath();
                String mainFilePath = mainFile.toString();
                if (mainFilePath.startsWith(moviePath)) {
                  String relativePath = mainFilePath.substring(moviePath.length());
                  if (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                  }
                  rel = Paths.get(relativePath);
                }
                else {
                  rel = mainFile;
                }
              }
              else {
                rel = movie.getPathNIO().relativize(mainFile);
              }

              if (isWebDav) {
                // For WebDAV, use string concatenation
                String newPath = newMovieDirStr;
                String relPath = rel.toString();
                String outputPath = newPath.endsWith("/") ? newPath + relPath : newPath + "/" + relPath;
                outputFolder = Paths.get(outputPath);
              }
              else {
                outputFolder = newMovieDir.resolve(rel);
              }
            }
            else {
              outputFolder = newMovieDir; // not a virtual "MF folder"? use default
            }
            // since we ARE in a disc structure, we have to name it accordingly.... (folder or not)
            newFilename = movie.findDiscMainFile(); // with ext
          }
          else {
            outputFolder = newMovieDir; // default
          }

          for (MovieTrailerNaming name : trailernames) {
            String newTrailerName = movie.getTrailerFilename(name, newFilename); // basename used, so add fake extension
            if (newTrailerName.isEmpty()) {
              continue;
            }
            MediaFile trail = new MediaFile(mf);
            String trailerFilename;
            if (StringUtils.isNotBlank(stackingMarker)) {
              trailerFilename = newTrailerName + "." + stackingMarker + "." + mf.getExtension();
            }
            else {
              trailerFilename = newTrailerName + "." + mf.getExtension();
            }

            if (isWebDav) {
              // For WebDAV, use string concatenation
              String outputPath = outputFolder.toString();
              String trailerPath = outputPath.endsWith("/") ? outputPath + trailerFilename : outputPath + "/" + trailerFilename;
              trail.setFile(Paths.get(trailerPath));
            }
            else {
              trail.setFile(outputFolder.resolve(trailerFilename));
            }
            newFiles.add(trail);
          }
        }
        break;

      case EXTRA:
      case VIDEO_EXTRA:
        // this extra is for an episode -> move it at least to the season folder and try to replace the episode tokens
        MediaFile extra = new MediaFile(mf);
        if (MediaFileHelper.isExtraInDedicatedFolder(mf, movie)) {
          // do nothing
          newFiles.add(defaultMF);
        }
        else {
          // try to detect the title of the extra file
          String extraTitle = mf.getBasename().replace(oldVideoFileName, "");
          String extraFilename = newFilename + extraTitle + "." + mf.getExtension();
          if (isWebDav) {
            // For WebDAV, use string concatenation
            String newPath = newMovieDirStr;
            String extraPath = newPath.endsWith("/") ? newPath + extraFilename : newPath + "/" + extraFilename;
            extra.setFile(Paths.get(extraPath));
          }
          else {
            extra.setFile(newMovieDir.resolve(extraFilename));
          }
          newFiles.add(extra);
        }
        break;

      case SAMPLE:
        MediaFile sample = new MediaFile(mf);
        newFilename += "-sample." + mf.getExtension();
        if (isWebDav) {
          // For WebDAV, use string concatenation
          String newPath = newMovieDirStr;
          String samplePath = newPath.endsWith("/") ? newPath + newFilename : newPath + "/" + newFilename;
          sample.setFile(Paths.get(samplePath));
        }
        else {
          sample.setFile(newMovieDir.resolve(newFilename));
        }
        newFiles.add(sample);
        break;

      case MEDIAINFO:
        MediaFile mi = new MediaFile(mf);
        if (movie.isDisc()) {
          // hmm.. dunno, keep at least 1:1
          if (isWebDav) {
            // For WebDAV, manually replace path
            String oldPath = movie.getPath();
            String newPath = newMovieDirStr;
            String filePath = WebDavDataSourceHelper.normalizeWebDavPath(mf.getFileAsPath().toString());
            if (filePath.startsWith(oldPath)) {
              String relativePart = filePath.substring(oldPath.length());
              String newFilePath = newPath.endsWith("/") ? newPath + relativePart : newPath + "/" + relativePart;
              mi.setFile(Paths.get(newFilePath));
            }
          }
          else {
            mi.replacePathForRenamedFolder(movie.getPathNIO(), newMovieDir);
          }
          newFiles.add(mi);
        }
        else {
          newFilename += getStackingString(mf);
          newFilename += "-mediainfo." + mf.getExtension();
          if (isWebDav) {
            // For WebDAV, use string concatenation
            String newPath = newMovieDirStr;
            String miPath = newPath.endsWith("/") ? newPath + newFilename : newPath + "/" + newFilename;
            mi.setFile(Paths.get(miPath));
          }
          else {
            mi.setFile(newMovieDir.resolve(newFilename));
          }
          newFiles.add(mi);
        }
        break;

      case DOUBLE_EXT:
      case VSMETA:
        MediaFile doubleExt = new MediaFile(mf);
        if (movie.isDisc()) {
          // keep 1:1
          if (isWebDav) {
            // For WebDAV, use string concatenation
            String newPath = newMovieDirStr;
            String filename = doubleExt.getFilename();
            String filePath = newPath.endsWith("/") ? newPath + filename : newPath + "/" + filename;
            doubleExt.setFile(Paths.get(filePath));
          }
          else {
            doubleExt.setFile(newMovieDir.resolve(doubleExt.getFilename()));
          }
        }
        else {
          newFilename += getStackingString(mf);
          // HACK: get video extension from "old" name, eg video.avi.vsmeta
          String videoExt = FilenameUtils.getExtension(FilenameUtils.getBaseName(mf.getFilename()));
          newFilename += "." + videoExt + "." + FilenameUtils.getExtension(mf.getFilename());
          if (isWebDav) {
            // For WebDAV, use string concatenation
            String newPath = newMovieDirStr;
            String filePath = newPath.endsWith("/") ? newPath + newFilename : newPath + "/" + newFilename;
            doubleExt.setFile(Paths.get(filePath));
          }
          else {
            doubleExt.setFile(newMovieDir.resolve(newFilename));
          }
        }
        newFiles.add(doubleExt);
        break;

      case SUBTITLE:
        List<MediaFileSubtitle> subtitles = mf.getSubtitles();
        newFilename += getStackingString(mf);
        String subtitleFilename = newFilename;
        if (subtitles != null && !subtitles.isEmpty()) {
          MediaFileSubtitle sub = mf.getSubtitles().get(0);
          if (sub != null) {
            if (!sub.getLanguage().isEmpty()) {
              String lang = LanguageStyle.getLanguageCodeForStyle(sub.getLanguage(),
                  MovieModuleManager.getInstance().getSettings().getSubtitleLanguageStyle());
              if (StringUtils.isBlank(lang)) {
                lang = sub.getLanguage();
              }
              subtitleFilename += "." + lang;
            }

            if (sub.isForced()) {
              subtitleFilename += ".forced";
            }
            if (sub.isSdh()) {
              subtitleFilename += ".sdh"; // double possible?! should be no prob...
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
          String subFilename = subtitleFilename + "." + mf.getExtension();
          if (isWebDav) {
            // For WebDAV, use string concatenation
            String newPath = newMovieDirStr;
            String subPath = newPath.endsWith("/") ? newPath + subFilename : newPath + "/" + subFilename;
            subtitle.setFile(Paths.get(subPath));
          }
          else {
            subtitle.setFile(newMovieDir.resolve(subFilename));
          }
          newFiles.add(subtitle);
        }
        break;

      case NFO:
        if (MovieModuleManager.getInstance().getSettings().getNfoFilenames().isEmpty()) {
          // we do not want NFO to be renamed? so they will be removed....
          break;
        }

        if (MovieConnectors.isValidNFO(mf.getFileAsPath())) {
          List<MovieNfoNaming> nfonames = new ArrayList<>();
          if (newDestIsMultiMovieDir) {
            // Fixate the name regardless of setting
            nfonames.add(MovieNfoNaming.FILENAME_NFO);
          }
          else if (movie.isDisc()) {
            nfonames.add(MovieNfoNaming.FILENAME_NFO);
          }
          else {
            nfonames = MovieModuleManager.getInstance().getSettings().getNfoFilenames();
          }
          for (MovieNfoNaming name : nfonames) {
            String newNfoName = movie.getNfoFilename(name, newFilename + ".avi"); // basename used, so add fake extension
            if (newNfoName.isEmpty()) {
              continue;
            }
            MediaFile nfo = new MediaFile(mf);
            if (isWebDav) {
              // For WebDAV, use string concatenation
              String newPath = newMovieDirStr;
              String nfoPath = newPath.endsWith("/") ? newPath + newNfoName : newPath + "/" + newNfoName;
              nfo.setFile(Paths.get(nfoPath));
            }
            else {
              nfo.setFile(newMovieDir.resolve(newNfoName));
            }
            newFiles.add(nfo);
          }
        }
        else {
          // not a TMM NFO
          if (!MovieModuleManager.getInstance().getSettings().isRenamerNfoCleanup()) {
            newFiles.add(new MediaFile(mf));
          }

        }
        break;

      case POSTER:
        // Determine destination folder based on settings for artwork
        Path artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
        for (MoviePosterNaming name : MovieArtworkHelper.getPosterNamesForMovie(movie)) {
          String newPosterName = name.getFilename(newFilename, getArtworkExtension(mf));
          if (StringUtils.isNotBlank(newPosterName)) {
            MediaFile pos = new MediaFile(mf);
            if (isWebDav) {
              // For WebDAV, use string concatenation
              String artworkPath = artworkDir.toString();
              String posterPath = artworkPath.endsWith("/") ? artworkPath + newPosterName : artworkPath + "/" + newPosterName;
              pos.setFile(Paths.get(posterPath));
            }
            else {
              pos.setFile(artworkDir.resolve(newPosterName));
            }
            newFiles.add(pos);
          }
        }
        break;

      case FANART:
        // Use artwork directory for fanart
        artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
        for (MovieFanartNaming name : MovieArtworkHelper.getFanartNamesForMovie(movie)) {
          String newFanartName = name.getFilename(newFilename, getArtworkExtension(mf));
          if (StringUtils.isNotBlank(newFanartName)) {
            MediaFile fan = new MediaFile(mf);
            if (isWebDav) {
              // For WebDAV, use string concatenation
              String artworkPath = artworkDir.toString();
              String fanartPath = artworkPath.endsWith("/") ? artworkPath + newFanartName : artworkPath + "/" + newFanartName;
              fan.setFile(Paths.get(fanartPath));
            }
            else {
              fan.setFile(artworkDir.resolve(newFanartName));
            }
            newFiles.add(fan);
          }
        }
        break;

      case BANNER:
        // Use artwork directory for banner
        artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
        for (MovieBannerNaming name : MovieArtworkHelper.getBannerNamesForMovie(movie)) {
          String newBannerName = name.getFilename(newFilename, getArtworkExtension(mf));
          if (StringUtils.isNotBlank(newBannerName)) {
            MediaFile banner = new MediaFile(mf);
            if (isWebDav) {
              // For WebDAV, use string concatenation
              String artworkPath = artworkDir.toString();
              String bannerPath = artworkPath.endsWith("/") ? artworkPath + newBannerName : artworkPath + "/" + newBannerName;
              banner.setFile(Paths.get(bannerPath));
            }
            else {
              banner.setFile(artworkDir.resolve(newBannerName));
            }
            newFiles.add(banner);
          }
        }
        break;

      case CLEARART:
        // Use artwork directory for clearart
        artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
        for (MovieClearartNaming name : MovieArtworkHelper.getClearartNamesForMovie(movie)) {
          String newClearartName = name.getFilename(newFilename, getArtworkExtension(mf));
          if (StringUtils.isNotBlank(newClearartName)) {
            MediaFile clearart = new MediaFile(mf);
            if (isWebDav) {
              String artworkPath = artworkDir.toString();
              String clearartPath = artworkPath.endsWith("/") ? artworkPath + newClearartName : artworkPath + "/" + newClearartName;
              clearart.setFile(Paths.get(clearartPath));
            }
            else {
              clearart.setFile(artworkDir.resolve(newClearartName));
            }
            newFiles.add(clearart);
          }
        }
        break;

      case DISC:
        // Use artwork directory for disc art
        artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
        for (MovieDiscartNaming name : MovieArtworkHelper.getDiscartNamesForMovie(movie)) {
          String newDiscartName = name.getFilename(newFilename, getArtworkExtension(mf));
          if (StringUtils.isNotBlank(newDiscartName)) {
            MediaFile discart = new MediaFile(mf);
            if (isWebDav) {
              String artworkPath = artworkDir.toString();
              String discartPath = artworkPath.endsWith("/") ? artworkPath + newDiscartName : artworkPath + "/" + newDiscartName;
              discart.setFile(Paths.get(discartPath));
            }
            else {
              discart.setFile(artworkDir.resolve(newDiscartName));
            }
            newFiles.add(discart);
          }
        }
        break;

      case CLEARLOGO:
      case LOGO:
        // Use artwork directory for logo
        artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
        for (MovieClearlogoNaming name : MovieArtworkHelper.getClearlogoNamesForMovie(movie)) {
          String newClearlogoName = name.getFilename(newFilename, getArtworkExtension(mf));
          if (StringUtils.isNotBlank(newClearlogoName)) {
            MediaFile clearlogo = new MediaFile(mf);
            if (isWebDav) {
              String artworkPath = artworkDir.toString();
              String clearlogoPath = artworkPath.endsWith("/") ? artworkPath + newClearlogoName : artworkPath + "/" + newClearlogoName;
              clearlogo.setFile(Paths.get(clearlogoPath));
            }
            else {
              clearlogo.setFile(artworkDir.resolve(newClearlogoName));
            }
            newFiles.add(clearlogo);
          }
        }
        break;

      case THUMB:
        // Use artwork directory for thumb
        artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
        for (MovieThumbNaming name : MovieArtworkHelper.getThumbNamesForMovie(movie)) {
          String newThumbName = name.getFilename(newFilename, getArtworkExtension(mf));
          if (StringUtils.isNotBlank(newThumbName)) {
            MediaFile thumb = new MediaFile(mf);
            if (isWebDav) {
              String artworkPath = artworkDir.toString();
              String thumbPath = artworkPath.endsWith("/") ? artworkPath + newThumbName : artworkPath + "/" + newThumbName;
              thumb.setFile(Paths.get(thumbPath));
            }
            else {
              thumb.setFile(artworkDir.resolve(newThumbName));
            }
            newFiles.add(thumb);
          }
        }
        break;

      case KEYART:
        // Use artwork directory for keyart
        artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
        for (MovieKeyartNaming name : MovieArtworkHelper.getKeyartNamesForMovie(movie)) {
          String newKeyartName = name.getFilename(newFilename, getArtworkExtension(mf));
          if (StringUtils.isNotBlank(newKeyartName)) {
            MediaFile key = new MediaFile(mf);
            if (isWebDav) {
              String artworkPath = artworkDir.toString();
              String keyartPath = artworkPath.endsWith("/") ? artworkPath + newKeyartName : artworkPath + "/" + newKeyartName;
              key.setFile(Paths.get(keyartPath));
            }
            else {
              key.setFile(artworkDir.resolve(newKeyartName));
            }
            newFiles.add(key);
          }
        }
        break;

      case EXTRAFANART:
        // get the index (is always the last digit before the extension)
        int index = MovieArtworkHelper.getIndexOfArtwork(mf.getFilename());
        if (index > 0) {
          // at the moment, we just support 1 naming scheme here! if we decide to enhance that, we will need to enhance the MovieExtraImageFetcherTask
          // too
          List<MovieExtraFanartNaming> extraFanartNamings = MovieArtworkHelper.getExtraFanartNamesForMovie(movie);
          if (!extraFanartNamings.isEmpty()) {
            MovieExtraFanartNaming fileNaming = extraFanartNamings.get(0);

            String newExtraFanartFilename = fileNaming.getFilename(newFilename, getArtworkExtension(mf));
            if (StringUtils.isNotBlank(newExtraFanartFilename)) {
              // split the filename again and attach the counter
              String basename = FilenameUtils.getBaseName(newExtraFanartFilename);
              newExtraFanartFilename = basename + index + "." + getArtworkExtension(mf);

              // create an empty extrafanarts folder if the right naming has been chosen
              // Use artwork directory for extra fanart
              artworkDir = getDestinationFolderForMovieArtwork(movie, newMovieDir);
              Path folder;
              if (fileNaming == MovieExtraFanartNaming.FOLDER_EXTRAFANART) {
                if (isWebDav) {
                  String artworkPath = artworkDir.toString();
                  String folderPath = artworkPath.endsWith("/") ? artworkPath + "extrafanart" : artworkPath + "/extrafanart";
                  folder = Paths.get(folderPath);
                  // For WebDAV, skip directory creation (handled by WebDAV operations)
                }
                else {
                  folder = artworkDir.resolve("extrafanart");
                  try {
                    if (!Files.exists(folder)) {
                      Files.createDirectories(folder);
                    }
                  }
                  catch (IOException e) {
                    LOGGER.error("Could not create extrafanarts folder '{}' - '{}'", folder, e.getMessage());
                  }
                }
              }
              else {
                folder = artworkDir;
              }

              MediaFile extrafanart = new MediaFile(mf);
              if (isWebDav) {
                String folderPath = folder.toString();
                String extrafanartPath = folderPath.endsWith("/") ? folderPath + newExtraFanartFilename : folderPath + "/" + newExtraFanartFilename;
                extrafanart.setFile(Paths.get(extrafanartPath));
              }
              else {
                extrafanart.setFile(folder.resolve(newExtraFanartFilename));
              }
              newFiles.add(extrafanart);
            }
          }
        }
        break;

      // *************
      // OK, from here we check only the settings
      // *************
      case EXTRATHUMB:
        // pass the file regardless of the settings (they're here so we just rename them)
        newFiles.add(defaultMF);
        break;

      // *************
      // here we add all others
      // *************
      default:
        newFiles.add(defaultMF);
        break;
    }

    return newFiles;
  }

  private static String getArtworkExtension(MediaFile mf) {
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
   * returns "delimiter + stackingString" for use in filename
   *
   * @param mf
   *          a mediaFile
   * @return eg ".CD1" dependent of settings
   */
  private static String getStackingString(MediaFile mf) {
    String delimiter = " ";
    if (MovieModuleManager.getInstance().getSettings().isRenamerFilenameSpaceSubstitution()) {
      delimiter = MovieModuleManager.getInstance().getSettings().getRenamerFilenameSpaceReplacement();
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
   * Creates the new filename according to template string
   *
   * @param template
   *          the template
   * @param movie
   *          the movie
   * @return the string
   */
  public static String createDestinationForFilename(String template, Movie movie) {
    return createDestination(template, movie, true);
  }

  /**
   * Creates the new filename according to template string
   *
   * @param template
   *          the template
   * @param movie
   *          the movie
   * @return the string
   */
  public static String createDestinationForFoldername(String template, Movie movie) {
    return createDestination(template, movie, false);
  }

  /**
   * gets the token value (${x}) from specified movie object with all renamer related replacements!
   *
   * @param movie
   *          our movie
   * @param token
   *          the ${x} token
   * @return value or empty string
   */
  public static String getTokenValue(Movie movie, String token) {
    try {
      Engine engine = createEngine();
      engine.setModelAdaptor(new TmmModelAdaptor());

      engine.setOutputAppender(new TmmOutputAppender() {
        @Override
        protected String replaceInvalidCharacters(String text) {
          if (isUnicodeReplacementEnabled()) {
            // Unicode replacement of forbidden characters
            return StrgUtils.replaceForbiddenFilesystemCharacters(text);
          }

          return MovieRenamer.replaceInvalidCharacters(text);
        }

        @Override
        protected boolean isUnicodeReplacementEnabled() {
          return MovieModuleManager.getInstance().getSettings().isUnicodeReplacement();
        }
      });

      Map<String, Object> root = new HashMap<>();
      root.put("movie", movie);

      // only offer movie set for movies with more than 1 movie or if setting is set
      if (movie.getMovieSet() != null
          && (movie.getMovieSet().getMovies().size() > 1 || MovieModuleManager.getInstance().getSettings().isRenamerCreateMoviesetForSingleMovie())) {
        root.put("movieSet", movie.getMovieSet());
      }

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
    engine.registerNamedRenderer(new MovieNamedFirstCharacterRenderer());
    engine.registerNamedRenderer(new MovieNamedIndexOfMovieSetRenderer());
    engine.registerNamedRenderer(new MovieNamedIndexOfMovieSetWithDummyRenderer());
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
   * Creates the new file/folder name according to template string
   *
   * @param template
   *          the template
   * @param movie
   *          the movie
   * @param forFilename
   *          replace for filename (=true)? or for a foldername (=false)<br>
   *          Former does replace ALL directory separators
   * @return the string
   */
  public static String createDestination(String template, Movie movie, boolean forFilename) {
    // Update file size information before parsing template only if enabled for movies
    if (Settings.getInstance().isMovieUpdateFileSizeOnRename()) {
      movie.updateFileSizeInformation();
    }

    String newDestination = getTokenValue(movie, template);

    // replace empty brackets
    newDestination = newDestination.replaceAll("\\([ ]?\\)", "");
    newDestination = newDestination.replaceAll("\\[[ ]?\\]", "");
    newDestination = newDestination.replaceAll("\\{[ ]?\\}", "");

    // if there are multiple file separators in a row - strip them out
    if (SystemUtils.IS_OS_WINDOWS) {
      if (!forFilename) {
        // trim whitespace around directory sep
        newDestination = newDestination.replaceAll("\\s+\\\\", "\\\\");
        newDestination = newDestination.replaceAll("\\\\\\s+", "\\\\");
        // remove separators in front of path separators
        newDestination = newDestination.replaceAll("[ \\.\\-_]+\\\\", "\\\\");
      }
      // we need to mask it in windows
      newDestination = newDestination.replaceAll("\\\\{2,}", "\\\\");
      newDestination = newDestination.replaceAll("^\\\\", "");
    }
    else {
      if (!forFilename) {
        // trim whitespace around directory sep
        newDestination = newDestination.replaceAll("\\s+/", "/");
        newDestination = newDestination.replaceAll("/\\s+", "/");
        // remove separators in front of path separators
        newDestination = newDestination.replaceAll("[ \\.\\-_]+/", "/");
      }
      newDestination = newDestination.replaceAll("/{2,}", "/");
      newDestination = newDestination.replaceAll("^/", "");
    }

    // replace ALL directory separators, if we generate this for filenames!
    if (forFilename) {
      newDestination = replacePathSeparators(newDestination);
    }

    // replace spaces with underscores if needed (filename only)
    if (forFilename && MovieModuleManager.getInstance().getSettings().isRenamerFilenameSpaceSubstitution()) {
      String replacement = MovieModuleManager.getInstance().getSettings().getRenamerFilenameSpaceReplacement();
      newDestination = newDestination.replace(" ", replacement);

      // also replace now multiple replacements with one to avoid strange looking results
      // example:
      // Abraham Lincoln - Vampire Hunter -> Abraham-Lincoln---Vampire-Hunter
      newDestination = newDestination.replaceAll(Pattern.quote(replacement) + "+", replacement);
    }
    else if (!forFilename && MovieModuleManager.getInstance().getSettings().isRenamerPathnameSpaceSubstitution()) {
      String replacement = MovieModuleManager.getInstance().getSettings().getRenamerPathnameSpaceReplacement();
      newDestination = newDestination.replace(" ", replacement);

      // also replace now multiple replacements with one to avoid strange looking results
      // example:
      // Abraham Lincoln - Vapire Hunter -> Abraham-Lincoln---Vampire-Hunter
      newDestination = newDestination.replaceAll(Pattern.quote(replacement) + "+", replacement);
    }

    // replace all leading/trailing separators (except the underscore which could be valid in the front)
    newDestination = newDestination.replaceAll("^[ \\.\\-]+", "");
    newDestination = newDestination.replaceAll("[ \\.\\-_]+$", "");

    // ASCII replacement
    if (MovieModuleManager.getInstance().getSettings().isAsciiReplacement()) {
      newDestination = StrgUtils.convertToAscii(newDestination, false);
    }

    // the illegal filesystem characters are handled by JMTE, but it looks like some users are stupid enough to add this to the pattern itself...
    newDestination = replaceInvalidCharacters(newDestination);

    // replace new lines
    newDestination = newDestination.replaceAll("\r?\n", " ");

    // replace multiple spaces with a single one
    newDestination = newDestination.replaceAll(" +", " ");

    if (SystemUtils.IS_OS_WINDOWS) {
      // remove illegal characters on Windows
      newDestination = newDestination.replace("\"", " ");
    }

    // replace three subsequent dots with the Unicode ellipsis character
    if (MovieModuleManager.getInstance().getSettings().isUnicodeReplacement()) {
      newDestination = newDestination.replace("...", "…");
    }

    return newDestination.strip();
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
  static boolean moveFile(Path oldFilename, Path newFilename) {
    try {
      // Check if paths are WebDAV paths
      String oldPathStr = oldFilename.toString();
      String newPathStr = newFilename.toString();

      if (WebDavDataSourceHelper.isWebDavPath(oldPathStr) && WebDavDataSourceHelper.isWebDavPath(newPathStr)) {
        // Both are WebDAV paths - use WebDAV operations
        LOGGER.debug("Moving WebDAV file from '{}' to '{}'", oldPathStr, newPathStr);
        return WebDavFileOperations.moveWebDavFile(oldPathStr, newPathStr);
      }
      else if (WebDavDataSourceHelper.isWebDavPath(oldPathStr) || WebDavDataSourceHelper.isWebDavPath(newPathStr)) {
        // One is WebDAV and one is local - not supported
        LOGGER.error("Cannot move files between WebDAV and local file system: {} -> {}", oldPathStr, newPathStr);
        return false;
      }

      // Both are local paths - use standard file operations
      // 如果源文件和目标文件路径完全相同，直接返回成功
      if (oldFilename.toAbsolutePath().equals(newFilename.toAbsolutePath())) {
        LOGGER.debug("Source and destination files are identical, skipping move");
        return true;
      }

      // create parent if needed
      if (!Files.exists(newFilename.getParent())) {
        Files.createDirectory(newFilename.getParent());
      }

      // 检查目标文件是否已存在
      int counter = 1;
      Path targetPath = newFilename;
      String extension = targetPath.toString().substring(targetPath.toString().lastIndexOf("."));
      String baseName = targetPath.toString().substring(0, targetPath.toString().lastIndexOf("."));

      // 如果目标文件已存在，添加数字后缀避免冲突
      while (Files.exists(targetPath)) {
        // 如果是同一目录下的相同文件，且名称只是因为格式化改变（不是真的冲突），则尝试使用原始文件名
        if (oldFilename.getParent().equals(targetPath.getParent()) && Files.exists(oldFilename)) {
          LOGGER.debug("Same directory rename detected, checking if we can use the original file");
          return true; // 同一目录下，认为重命名成功
        }
        LOGGER.info("File '{}' already exists, trying alternative name", targetPath);
        targetPath = Paths.get(baseName + " (" + counter + ")" + extension);
        counter++;
      }

      boolean ok = Utils.moveFileSafe(oldFilename, targetPath);
      if (ok) {
        return true;
      }
      else {
        LOGGER.warn("Could not move media file '{}' to '{}'", oldFilename, targetPath);
        return false; // rename failed
      }
    }
    catch (Exception e) {
      LOGGER.error("Error moving file '{}' to '{}' - '{}'", oldFilename, newFilename, e.getMessage());
      MessageManager.getInstance()
          .pushMessage(new Message(MessageLevel.ERROR, oldFilename, "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
      return false; // rename failed
    }
  }

  /**
   * moves a directory.
   *
   * @param oldName
   *          the old filename
   * @param newName
   *          the new filename
   * @return true, when we moved file
   */
  static boolean moveDirectory(Path oldName, Path newName) {
    try {
      // Check if paths are WebDAV paths
      String oldPathStr = oldName.toString();
      String newPathStr = newName.toString();

      if (WebDavDataSourceHelper.isWebDavPath(oldPathStr) && WebDavDataSourceHelper.isWebDavPath(newPathStr)) {
        // Both are WebDAV paths - use WebDAV operations
        LOGGER.debug("Moving WebDAV directory from '{}' to '{}'", oldPathStr, newPathStr);
        return WebDavFileOperations.moveWebDavFile(oldPathStr, newPathStr);
      }
      else if (WebDavDataSourceHelper.isWebDavPath(oldPathStr) || WebDavDataSourceHelper.isWebDavPath(newPathStr)) {
        // One is WebDAV and one is local - not supported
        LOGGER.error("Cannot move directories between WebDAV and local file system: {} -> {}", oldPathStr, newPathStr);
        return false;
      }

      // Both are local paths - use standard file operations
      // create parent if needed
      if (!Files.exists(newName.getParent())) {
        Files.createDirectory(newName.getParent());
      }

      // 检查目标目录是否已存在
      int counter = 1;
      Path targetPath = newName;

      // 如果目标目录已存在，添加数字后缀避免冲突
      while (Files.exists(targetPath)) {
        LOGGER.info("Directory '{}' already exists, trying alternative name", targetPath);
        targetPath = Paths.get(targetPath.toString() + " (" + counter + ")");
        counter++;
      }

      boolean ok = Utils.moveDirectorySafe(oldName, targetPath);
      if (ok) {
        return true;
      }
      else {
        LOGGER.error("Could not move folder '{}' to '{}'", oldName, targetPath);
        return false; // rename failed
      }
    }
    catch (Exception e) {
      LOGGER.error("Error moving folder '{}' to '{}' - '{}'", oldName, newName, e.getMessage());
      MessageManager.getInstance()
          .pushMessage(new Message(MessageLevel.ERROR, oldName, "message.renamer.failedrename", new String[] { ":", e.getLocalizedMessage() }));
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
  static boolean copyFile(Path oldFilename, Path newFilename) {
    // Check if paths are WebDAV paths
    String oldPathStr = oldFilename.toString();
    String newPathStr = newFilename.toString();

    if (WebDavDataSourceHelper.isWebDavPath(oldPathStr) && WebDavDataSourceHelper.isWebDavPath(newPathStr)) {
      // Both are WebDAV paths - use WebDAV operations
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
      LOGGER.debug("copy file {} to {}", oldFilename, newFilename);
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
   * Check if the folder rename pattern is unique<br>
   * Unique true, when having at least a $T/$E-$Y combo or $I imdbId<br>
   *
   * @param pattern
   *          the pattern to check the uniqueness for
   * @return true/false
   */
  public static boolean isFolderPatternUnique(String pattern) {
    return TITLE_PATTERN.matcher(pattern).find() && YEAR_ID_PATTERN.matcher(pattern).find();
  }

  /**
   * Check if the FILE rename pattern is valid<br>
   * What means, pattern has at least title set (${title}|${originalTitle}|${titleSortable})<br>
   * "empty" is considered as invalid - so not renaming files
   *
   * @return true/false
   */
  public static boolean isFilePatternValid() {
    return isFilePatternValid(MovieModuleManager.getInstance().getSettings().getRenamerFilename());
  }

  /**
   * Check if the FILE rename pattern is valid<br>
   * What means, that at least title (${title}|${originalTitle}|${titleSortable})<br>
   * or original filename has been set "empty" is considered as invalid - so not renaming files
   *
   * @return true/false
   */
  public static boolean isFilePatternValid(String pattern) {
    return TITLE_PATTERN.matcher(pattern).find() || ORIGINAL_FILENAME_PATTERN.matcher(pattern).find();
  }

  /**
   * replaces all invalid/illegal characters for filenames/foldernames with ""<br>
   * except the colon, which will be changed to a dash
   *
   * @param source
   *          string to clean
   * @return cleaned string
   */
  public static String replaceInvalidCharacters(String source) {
    String result = source;

    if ("-".equals(MovieModuleManager.getInstance().getSettings().getRenamerColonReplacement())) {
      result = result.replace(": ", " - "); // nicer
      result = result.replace(":", "-"); // nicer
    }
    else {
      result = result.replace(":", MovieModuleManager.getInstance().getSettings().getRenamerColonReplacement());
    }

    return result.replaceAll("([\":<>|?*])", "");
  }

  /**
   * replace all path separators in the given {@link String} with a space
   *
   * @param source
   *          the original {@link String}
   * @return the cleaned {@link String}
   */
  public static String replacePathSeparators(String source) {
    String result = source.replaceAll("\\/", " "); // NOSONAR
    return result.replaceAll("\\\\", " "); // NOSONAR
  }

  /**
   * Get the destination folder for movie artwork during rename based on settings
   *
   * @param movie
   *          the movie entity
   * @param originalMovieDir
   *          the original movie directory (for non-artwork files)
   * @return the destination folder path for artwork
   */
  private static Path getDestinationFolderForMovieArtwork(Movie movie, Path originalMovieDir) {
    boolean saveToCache = MovieModuleManager.getInstance().getSettings().isSaveArtworkToCache();

    if (saveToCache) {
      // Create a structured cache folder: cache/artwork/movies
      Path cacheArtworkDir = ImageCache.getCacheDir().resolve("artwork").resolve("movies");

      // Create entity-specific subfolder using title and year for uniqueness
      String folderName = movie.getTitle();
      if (movie.getYear() > 0) {
        folderName += " (" + movie.getYear() + ")";
      }
      // Sanitize folder name for filesystem compatibility
      folderName = folderName.replaceAll("[<>:\"/\\\\|?*]", "_");

      Path entityFolder = cacheArtworkDir.resolve(folderName);

      try {
        Files.createDirectories(entityFolder);
        LOGGER.info("Created cache artwork folder for movie rename '{}': {}", movie.getTitle(), entityFolder);
      }
      catch (Exception e) {
        LOGGER.warn("Could not create cache artwork folder '{}', falling back to video folder - '{}'", entityFolder, e.getMessage());
        return originalMovieDir;
      }

      LOGGER.info("Using cache artwork folder for movie rename '{}': {}", movie.getTitle(), entityFolder);
      return entityFolder;
    }
    else {
      // Default behavior: save to video folder
      LOGGER.debug("Using default video folder for movie rename '{}': {}", movie.getTitle(), originalMovieDir);
      return originalMovieDir;
    }
  }
}
