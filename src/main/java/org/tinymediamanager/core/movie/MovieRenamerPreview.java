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

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.RenamerPreviewContainer;
import org.tinymediamanager.core.RenamerPreviewContainer.MediaFileTypeContainer;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;

/**
 * The class {@link MovieRenamerPreview}. To create a preview of the movie renamer (dry run)
 * 
 * @author Manuel Laggner / Myron Boyle
 */
public class MovieRenamerPreview {

  private final Movie                   movie;
  private final Movie                   clone;    // unused for movies, as the generate makes its own clone
  private final RenamerPreviewContainer container;

  public MovieRenamerPreview(Movie movie) {
    this.movie = movie;
    this.clone = new Movie();
    this.clone.merge(movie);
    this.clone.setDataSource(movie.getDataSource());
    this.container = new RenamerPreviewContainer(movie);
  }

  public RenamerPreviewContainer generatePreview() {

    // generate the new path
    String newFolderName = MovieRenamer.createDestinationForFoldername(MovieModuleManager.getInstance().getSettings().getRenamerPathname(), movie);

    // For WebDAV paths, use string concatenation instead of Path.resolve()
    if (WebDavDataSourceHelper.isWebDavPath(movie.getPath())) {
      String datasource = movie.getDataSource();
      // Ensure proper path separator
      if (!datasource.endsWith("/") && !newFolderName.startsWith("/")) {
        container.newPath = Paths.get(datasource + "/" + newFolderName);
      }
      else {
        container.newPath = Paths.get(datasource + newFolderName);
      }
    }
    else {
      container.newPath = Paths.get(movie.getDataSource()).resolve(newFolderName);
    }

    this.clone.setPath(container.newPath.toString());

    // process movie media files
    processMovie();

    // check for dupes on all new MFs
    Map<String, MediaFileTypeContainer> duplicates = new HashMap<>();
    for (MediaFileTypeContainer files : container.getFiles()) {
      for (String rel : files.newFiles) {
        if (duplicates.containsKey(rel)) {
          // we have a dupe
          files.duped = true;
          MediaFileTypeContainer other = duplicates.get(rel);
          other.duped = true;
          // also set on container/movie level
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

  private void processMovie() {
    String newVideoBasename = MovieRenamer.generateNewVideoBasename(movie);
    boolean isWebDav = WebDavDataSourceHelper.isWebDavPath(movie.getPath());

    for (MediaFileType type : MediaFileType.values()) {
      MediaFileTypeContainer c = new MediaFileTypeContainer();
      for (MediaFile typeMf : movie.getMediaFiles(type)) {
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

        List<MediaFile> mfs = MovieRenamer.generateFilename(movie, new MediaFile(typeMf), newVideoBasename);
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

  /**
   * Helper method to get relative path for WebDAV paths
   * Returns the path relative to the movie directory, not the full path
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
      // Extract relative path - this is the file within the movie directory
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
