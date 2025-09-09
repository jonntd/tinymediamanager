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

import java.io.IOException;
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
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.filenaming.TvShowExtraFanartNaming;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;

/**
 * The class TvShowExtraImageFetcherTask. To fetch extrafanarts and extrathumbs
 * 
 * @author Manuel Laggner
 */
public class TvShowExtraImageFetcherTask implements Runnable {
  private static final Logger                 LOGGER = LoggerFactory.getLogger(TvShowExtraImageFetcherTask.class);

  private final TvShow                        tvShow;
  private final MediaFileType                 type;

  private final List<TvShowExtraFanartNaming> extraFanartNamings;

  public TvShowExtraImageFetcherTask(TvShow tvShow, MediaFileType type) {
    this.tvShow = tvShow;
    this.type = type;

    this.extraFanartNamings = new ArrayList<>(TvShowModuleManager.getInstance().getSettings().getExtraFanartFilenames());
  }

  @Override
  public void run() {
    // try/catch block in the root of the thread to log crashes
    try {
      boolean ok = downloadExtraFanart();

      // check if tmm has been shut down
      if (Thread.interrupted()) {
        return;
      }

      if (ok) {
        tvShow.callbackForWrittenArtwork(MediaArtworkType.ALL);
        tvShow.saveToDb();
      }
    }
    catch (Exception e) {
      LOGGER.error("Could not download extra artwork for TV show '{}' - '{}'", tvShow.getTitle(), e.getMessage());
      MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, tvShow, "message.extraimage.threadcrashed"));
    }
  }

  private boolean downloadExtraFanart() {
    List<String> fanartUrls = tvShow.getExtraFanartUrls();

    // do not create extrafanarts folder, if no extrafanarts are available
    if (fanartUrls.isEmpty()) {
      return false;
    }

    // if we do not have any valid extrafanart filename, stop there
    if (extraFanartNamings.isEmpty()) {
      return false;
    }

    // 1. clean all old extrafanarts
    for (MediaFile mediaFile : tvShow.getMediaFiles(MediaFileType.EXTRAFANART)) {
      Utils.deleteFileSafely(mediaFile.getFile());
      tvShow.removeFromMediaFiles(mediaFile);
    }

    // at the moment, we just support 1 naming scheme here! if we decide to enhance that, we will need to enhance the renamer too
    TvShowExtraFanartNaming fileNaming = extraFanartNamings.get(0);

    // create an empty extrafanarts folder if the right naming has been chosen
    Path folder;
    if (fileNaming == TvShowExtraFanartNaming.FOLDER_EXTRAFANART) {
      folder = tvShow.getPathNIO().resolve("extrafanart");
      try {
        if (!Files.exists(folder)) {
          Files.createDirectory(folder);
        }
      }
      catch (IOException e) {
        LOGGER.error("Could not create extrafanarts folder for TV show '{}' - '{}'", tvShow.getTitle(), e.getMessage());
        return false;
      }
    }
    else {
      folder = tvShow.getPathNIO();
    }

    // fetch and store images
    int i = 1;
    for (String urlAsString : fanartUrls) {
      try {
        String extension = Utils.getArtworkExtensionFromUrl(urlAsString);
        String filename = fileNaming.getFilename("", extension);

        // split the filename again and attach the counter
        String basename = FilenameUtils.getBaseName(filename);
        filename = basename + i + "." + extension;

        // Determine destination folder based on settings
        Path destinationFolder = getDestinationFolderForExtraImages(tvShow, folder);
        Path destFile = ImageUtils.downloadImage(urlAsString, destinationFolder, filename);

        MediaFile mf = new MediaFile(destFile, MediaFileType.EXTRAFANART);
        mf.gatherMediaInformation();
        tvShow.addToMediaFiles(mf);

        // build up image cache
        ImageCache.invalidateCachedImage(destFile);
        ImageCache.cacheImageSilently(destFile);

        i++;
      }
      catch (InterruptedException | InterruptedIOException e) {
        // do not swallow these Exceptions
        Thread.currentThread().interrupt();
      }
      catch (Exception e) {
        LOGGER.warn("Problem downloading extrafanart for TV show '{}' - '{}'", tvShow.getTitle(), e.getMessage());
      }
    }

    return true;
  }

  /**
   * Get the destination folder for extra images based on settings
   *
   * @param tvShow the TV show entity
   * @param originalFolder the original folder path
   * @return the destination folder path
   */
  private Path getDestinationFolderForExtraImages(TvShow tvShow, Path originalFolder) {
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

      // Preserve the subfolder structure (e.g., "extrafanart")
      if (!originalFolder.equals(tvShow.getPathNIO())) {
        entityFolder = entityFolder.resolve(originalFolder.getFileName());
      }

      try {
        Files.createDirectories(entityFolder);
        LOGGER.info("Created cache artwork folder for extra images '{}': {}", tvShow.getTitle(), entityFolder);
      } catch (Exception e) {
        LOGGER.warn("Could not create cache artwork folder '{}', falling back to video folder - '{}'",
                   entityFolder, e.getMessage());
        return originalFolder;
      }

      LOGGER.info("Using cache artwork folder for extra images '{}': {}", tvShow.getTitle(), entityFolder);
      return entityFolder;
    } else {
      // Default behavior: save to video folder
      LOGGER.debug("Using default video folder for extra images '{}': {}", tvShow.getTitle(), tvShow.getPathNIO());
      return originalFolder;
    }
  }
}
