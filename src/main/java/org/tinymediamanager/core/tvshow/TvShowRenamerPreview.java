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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.RenamerPreviewContainer;
import org.tinymediamanager.core.RenamerPreviewContainer.MediaFileTypeContainer;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;

/**
 * the class {@link TvShowRenamerPreview} is used to create a renamer preview for TV shows
 * 
 * @author Manuel Laggner
 */
public class TvShowRenamerPreview {

  private final TvShow                  tvShow;
  private final TvShow                  clone;
  private final RenamerPreviewContainer container;

  public TvShowRenamerPreview(TvShow tvShow) {
    this.tvShow = tvShow;
    this.clone = new TvShow();
    this.clone.merge(tvShow);
    this.clone.setDataSource(tvShow.getDataSource());
    this.container = new RenamerPreviewContainer(tvShow);
  }

  public RenamerPreviewContainer generatePreview() {
    // generate the new path
    String newFolderName = TvShowRenamer.getTvShowFoldername(TvShowModuleManager.getInstance().getSettings().getRenamerTvShowFoldername(), tvShow);

    // For WebDAV paths, use Paths.get() directly without resolve
    // The getTvShowFoldername already returns the full path
    container.newPath = Paths.get(newFolderName);
    this.clone.setPath(container.newPath.toString());

    // process TV show media files
    processTvShow();

    // process season media files
    processSeasons();

    // generate all episode filenames
    processEpisodes();

    // check for dupes on all new MFs
    Map<String, MediaFileTypeContainer> duplicates = new HashMap<>();
    for (MediaFileTypeContainer files : container.getFiles()) {
      for (String rel : files.newFiles) {
        if (duplicates.containsKey(rel)) {
          // we have a dupe
          files.duped = true;
          MediaFileTypeContainer other = duplicates.get(rel);
          other.duped = true;
          // also set on container/show level
          container.renamerProblems = true;
        }
        else {
          duplicates.put(rel, files);
        }
      }
    }
    duplicates.clear();

    return container;
  }

  private void processTvShow() {
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(tvShow.getPath());

    for (MediaFileType type : MediaFileType.values()) {
      MediaFileTypeContainer c = new MediaFileTypeContainer();
      for (MediaFile typeMf : tvShow.getMediaFiles(type)) {
        // For WebDAV, use string manipulation instead of Path.relativize()
        if (isWebDav) {
          String oldPathStr = container.getOldPath().toString();
          String filePathStr = typeMf.getFileAsPath().toString();
          String relativePath = getRelativePath(oldPathStr, filePathStr);
          c.oldFiles.add(relativePath);
        }
        else {
          c.oldFiles.add(container.getOldPath().relativize(typeMf.getFileAsPath()).toString());
        }

        List<MediaFile> mfs = TvShowRenamer.generateFilename(clone, new MediaFile(typeMf));
        for (MediaFile mf : mfs) {
          if (isWebDav) {
            String newPathStr = container.getNewPath().toString();
            String filePathStr = mf.getFileAsPath().toString();
            String relativePath = getRelativePath(newPathStr, filePathStr);
            c.newFiles.add(relativePath);
          }
          else {
            c.newFiles.add(container.getNewPath().relativize(mf.getFileAsPath()).toString());
          }
        }
      }
      if (!c.oldFiles.isEmpty()) {
        container.addFile(c);
      }
    }
  }

  private void processSeasons() {
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(tvShow.getPath());

    for (TvShowSeason season : tvShow.getSeasons()) {
      for (MediaFileType type : MediaFileType.values()) {
        MediaFileTypeContainer c = new MediaFileTypeContainer();
        for (MediaFile typeMf : season.getMediaFiles(type)) {
          // For WebDAV, use string manipulation instead of Path.relativize()
          if (isWebDav) {
            String oldPathStr = container.getOldPath().toString();
            String filePathStr = typeMf.getFileAsPath().toString();
            String relativePath = getRelativePath(oldPathStr, filePathStr);
            c.oldFiles.add(relativePath);
          }
          else {
            c.oldFiles.add(container.getOldPath().relativize(typeMf.getFileAsPath()).toString());
          }

          List<MediaFile> mfs = TvShowRenamer.generateSeasonFilenames(clone, season, new MediaFile(typeMf));
          for (MediaFile mf : mfs) {
            if (isWebDav) {
              String newPathStr = container.getNewPath().toString();
              String filePathStr = mf.getFileAsPath().toString();
              String relativePath = getRelativePath(newPathStr, filePathStr);
              c.newFiles.add(relativePath);
            }
            else {
              c.newFiles.add(container.getNewPath().relativize(mf.getFileAsPath()).toString());
            }
          }
        }
        if (!c.oldFiles.isEmpty()) {
          container.addFile(c);
        }
      }
    }
  }

  private void processEpisodes() {
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(tvShow.getPath());
    List<TvShowEpisode> episodes = new ArrayList<>(tvShow.getEpisodes());
    Collections.sort(episodes);

    // cache here all processed video MFs, to find those from a multi-episode
    // different episode, same file
    List<Path> multiCache = new ArrayList<>();

    for (TvShowEpisode episode : episodes) {
      Path main = episode.getMainFile().getFileAsPath();
      if (multiCache.contains(main)) {
        // System.out.println("ignoring multi episode " + main);
        continue;
      }
      else {
        multiCache.add(main);
      }

      // BASENAME
      String oldVideoBasename = episode.getVideoBasenameWithoutStacking();

      // let all episode MF be in ONE container - looks nicer, and we usually only have a few...
      // for (MediaFileType type : MediaFileType.values()) {
      MediaFileTypeContainer c = new MediaFileTypeContainer();
      for (MediaFile typeMf : episode.getMediaFiles()) {
        // For WebDAV, use string manipulation instead of Path.relativize()
        if (isWebDav) {
          String oldPathStr = container.getOldPath().toString();
          String filePathStr = typeMf.getFileAsPath().toString();
          String relativePath = getRelativePath(oldPathStr, filePathStr);
          c.oldFiles.add(relativePath);
        }
        else {
          c.oldFiles.add(container.getOldPath().relativize(typeMf.getFileAsPath()).toString());
        }

        List<MediaFile> mfs = TvShowRenamer.generateEpisodeFilenames(clone, new MediaFile(typeMf), oldVideoBasename);
        for (MediaFile mf : mfs) {
          if (isWebDav) {
            String newPathStr = container.getNewPath().toString();
            String filePathStr = mf.getFileAsPath().toString();
            String relativePath = getRelativePath(newPathStr, filePathStr);
            c.newFiles.add(relativePath);
          }
          else {
            c.newFiles.add(container.getNewPath().relativize(mf.getFileAsPath()).toString());
          }
        }
      }
      if (!c.oldFiles.isEmpty()) {
        container.addFile(c);
      }
      // }
    }
  }

  /**
   * Helper method to get relative path for WebDAV paths
   * Returns the path relative to the TV show directory, not the full path
   */
  private String getRelativePath(String basePath, String fullPath) {
    String relativePath = "";
    
    // URL decode both paths to ensure proper comparison
    try {
      basePath = java.net.URLDecoder.decode(basePath, "UTF-8");
      fullPath = java.net.URLDecoder.decode(fullPath, "UTF-8");
    } 
    catch (Exception e) {
      // If decoding fails, use the original paths
    }
    
    // First, ensure both paths end with a separator for proper comparison
    String normalizedBasePath = basePath;
    if (!normalizedBasePath.endsWith("/")) {
      normalizedBasePath = normalizedBasePath + "/";
    }
    
    // Check if fullPath starts with the normalized base path
    if (fullPath.startsWith(normalizedBasePath)) {
      // Extract relative path - this is the file within the TV show directory
      relativePath = fullPath.substring(normalizedBasePath.length());
    }
    // Check if fullPath starts with the original base path (without trailing slash)
    else if (fullPath.startsWith(basePath)) {
      relativePath = fullPath.substring(basePath.length());
      // Remove leading slash if present
      if (relativePath.startsWith("/")) {
        relativePath = relativePath.substring(1);
      }
    }
    // If no direct match, try to extract just the filename
    else {
      // Extract just the filename part
      int lastSlashIndex = fullPath.lastIndexOf('/');
      if (lastSlashIndex >= 0) {
        relativePath = fullPath.substring(lastSlashIndex + 1);
      }
      else {
        relativePath = fullPath;
      }
    }
    
    return relativePath;
  }
}
