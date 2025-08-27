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
package org.tinymediamanager.core.tasks;

import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.ImageCache;
import org.tinymediamanager.core.ImageUtils;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.util.ListUtils;

/**
 * The Class MediaEntityImageFetcherTask.
 * 
 * @author Manuel Laggner
 */
public class MediaEntityImageFetcherTask implements Runnable {
  private static final Logger    LOGGER    = LoggerFactory.getLogger(MediaEntityImageFetcherTask.class);

  private final MediaEntity      entity;
  private final String           url;
  private final MediaArtworkType type;
  private final List<String>     filenames = new ArrayList<>();

  public MediaEntityImageFetcherTask(MediaEntity entity, String url, MediaArtworkType type, List<String> filenames) {
    this.entity = entity;
    this.url = url;
    this.type = type;

    if (ListUtils.isNotEmpty(filenames)) {
      this.filenames.addAll(filenames);
    }
  }

  @Override
  public void run() {
    // check for destination file names
    if (filenames.isEmpty()) {
      return;
    }

    // check for supported artwork types
    switch (type) {
      case POSTER:
      case BACKGROUND:
      case BANNER:
      case THUMB:
      case CLEARART:
      case DISC:
      case LOGO:
      case CLEARLOGO:
      case CHARACTERART:
      case KEYART:
        break;

      default:
        return;
    }

    // remember old media files
    List<MediaFile> oldMediaFiles = entity.getMediaFiles(MediaFileType.getMediaFileType(type));
    List<MediaFile> newMediaFiles = new ArrayList<>();
    try {
      // try to download the file to the first one
      String firstFilename = filenames.get(0);
      LOGGER.debug("writing {} - {}", type, firstFilename);

      // Determine destination folder based on settings
      Path destinationFolder = getDestinationFolder(entity);
      Path destFile = ImageUtils.downloadImage(url, destinationFolder, firstFilename);

      // downloading worked (no exception) - so let's remove all old artworks (except the just downloaded one)
      entity.removeAllMediaFiles(MediaFileType.getMediaFileType(type));
      for (MediaFile mediaFile : oldMediaFiles) {
        ImageCache.invalidateCachedImage(mediaFile.getFile());
        if (!mediaFile.getFile().equals(destFile)) {
          Utils.deleteFileSafely(mediaFile.getFile());
        }
      }

      // and copy it to all other variants
      newMediaFiles.add(new MediaFile(destFile, MediaFileType.getMediaFileType(type)));

      for (String filename : filenames) {
        if (firstFilename.equals(filename)) {
          // already processed
          continue;
        }

        // don't write jpeg -> write jpg
        if (FilenameUtils.getExtension(filename).equalsIgnoreCase("JPEG")) {
          filename = FilenameUtils.getBaseName(filename) + ".jpg";
        }

        LOGGER.debug("writing {} - {}", type, filename);
        Path destFile2 = destinationFolder.resolve(filename);
        Utils.copyFileSafe(destFile, destFile2, true);

        newMediaFiles.add(new MediaFile(destFile2, MediaFileType.getMediaFileType(type)));
      }

      // last but not least - set all media files
      boolean first = true;
      for (MediaFile artwork : newMediaFiles) {
        // build up image cache before calling the events
        ImageCache.cacheImageSilently(artwork.getFile());

        if (first) {
          // the first one needs to be processed differently (mainly for UI eventing)
          entity.setArtwork(artwork.getFile(), MediaFileType.getMediaFileType(type));
          entity.callbackForWrittenArtwork(type);
          entity.saveToDb();
          first = false;
        }
        else {
          artwork.gatherMediaInformation();
          entity.addToMediaFiles(artwork);
        }
      }
    }
    catch (InterruptedException | InterruptedIOException e) {
      // do not swallow these Exceptions
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOGGER.error("Could not fetch image '{}' - '{}'", url, e.getMessage());
      MessageManager.getInstance()
          .pushMessage(
              new Message(MessageLevel.ERROR, "ArtworkDownload", "message.artwork.threadcrashed", new String[] { ":", e.getLocalizedMessage() }));
    }
  }

  /**
   * Get the destination folder for artwork based on entity type and settings
   *
   * @param entity the media entity
   * @return the destination folder path
   */
  private Path getDestinationFolder(MediaEntity entity) {
    boolean saveToCache = false;

    // Check settings based on entity type
    if (entity instanceof Movie) {
      saveToCache = MovieModuleManager.getInstance().getSettings().isSaveArtworkToCache();
      LOGGER.debug("Movie '{}' - saveArtworkToCache setting: {}", entity.getTitle(), saveToCache);
    } else if (entity instanceof TvShow) {
      saveToCache = TvShowModuleManager.getInstance().getSettings().isSaveArtworkToCache();
      LOGGER.debug("TV Show '{}' - saveArtworkToCache setting: {}", entity.getTitle(), saveToCache);
    }

    if (saveToCache) {
      // Create a structured cache folder: cache/artwork/movies or cache/artwork/tvshows
      String entityType = (entity instanceof Movie) ? "movies" : "tvshows";
      Path cacheArtworkDir = ImageCache.getCacheDir().resolve("artwork").resolve(entityType);

      // Create entity-specific subfolder using title and year for uniqueness
      String folderName = entity.getTitle();
      if (entity.getYear() > 0) {
        folderName += " (" + entity.getYear() + ")";
      }
      // Sanitize folder name for filesystem compatibility
      folderName = folderName.replaceAll("[<>:\"/\\\\|?*]", "_");

      Path entityFolder = cacheArtworkDir.resolve(folderName);

      try {
        Files.createDirectories(entityFolder);
        LOGGER.info("Created cache artwork folder for '{}': {}", entity.getTitle(), entityFolder);
      } catch (Exception e) {
        LOGGER.warn("Could not create cache artwork folder '{}', falling back to video folder - '{}'",
                   entityFolder, e.getMessage());
        return entity.getPathNIO();
      }

      LOGGER.info("Using cache artwork folder for '{}': {}", entity.getTitle(), entityFolder);
      return entityFolder;
    } else {
      // Default behavior: save to video folder
      LOGGER.debug("Using default video folder for '{}': {}", entity.getTitle(), entity.getPathNIO());
      return entity.getPathNIO();
    }
  }
}
