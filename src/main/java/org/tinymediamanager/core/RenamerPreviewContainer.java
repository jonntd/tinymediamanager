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
package org.tinymediamanager.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;

/**
 * The class RenamerPreviewContainer. To hold all relevant data for the renamer preview
 * 
 * @author Myron Boyle
 */
public class RenamerPreviewContainer {
  final MediaEntity                  entity;
  final Path                         oldPath;
  final List<MediaFileTypeContainer> files;

  public Path                        newPath;
  public boolean                     renamerProblems = false;

  public RenamerPreviewContainer(MediaEntity entity) {
    this.entity = entity;
    this.files = new ArrayList<>();

    if (entity != null && !entity.getDataSource().isEmpty()) {
      // For WebDAV paths, use Paths.get(entity.getPath()) instead of getPathNIO()
      // to avoid the toAbsolutePath() issue
      if (WebDavDataSourceHelper.isWebDavPath(entity.getPath())) {
        // URL decode the path to display readable characters
        String pathToUse = entity.getPath();
        try {
          pathToUse = java.net.URLDecoder.decode(entity.getPath(), "UTF-8");
        }
        catch (Exception e) {
          // If decoding fails, use the original path
        }
        this.oldPath = Paths.get(pathToUse);
      }
      else {
        this.oldPath = entity.getPathNIO();
      }
    }
    else {
      this.oldPath = null;
    }
  }

  public MediaEntity get() {
    return entity;
  }

  public Path getOldPath() {
    return oldPath;
  }

  /**
   * Get the old path as a readable string (URL decoded if needed)
   */
  public String getOldPathReadable() {
    if (oldPath == null) {
      return "";
    }
    String pathStr = oldPath.toString();
    // URL decode the path to display readable characters
    try {
      return java.net.URLDecoder.decode(pathStr, "UTF-8");
    }
    catch (Exception e) {
      return pathStr;
    }
  }

  /**
   * Get the old path relative to the datasource
   */
  public Path getOldPathRelative() {
    // For WebDAV paths, we need to handle path operations differently
    if (WebDavDataSourceHelper.isWebDavPath(entity.getPath())) {
      // Extract the relative path by removing the datasource prefix
      String fullPath = entity.getPath();
      String datasource = entity.getDataSource();

      // URL decode both paths to ensure proper comparison
      try {
        fullPath = java.net.URLDecoder.decode(fullPath, "UTF-8");
        datasource = java.net.URLDecoder.decode(datasource, "UTF-8");
      }
      catch (Exception e) {
        // If decoding fails, use the original paths
      }

      // Remove datasource prefix to get relative path
      if (fullPath.startsWith(datasource)) {
        String relativePath = fullPath.substring(datasource.length());
        // Remove leading slash if present
        if (relativePath.startsWith("/")) {
          relativePath = relativePath.substring(1);
        }
        return Paths.get(relativePath);
      }
      // Fallback: if we can't extract relative path, return just the folder name
      return Paths.get(fullPath.substring(fullPath.lastIndexOf('/') + 1));
    }
    else {
      return Paths.get(entity.getDataSource()).relativize(entity.getPathNIO());
    }
  }

  public Path getNewPath() {
    return newPath;
  }

  /**
   * Get the new path as a readable string (URL decoded if needed)
   */
  public String getNewPathReadable() {
    if (newPath == null) {
      return "";
    }
    String pathStr = newPath.toString();
    // URL decode the path to display readable characters
    try {
      return java.net.URLDecoder.decode(pathStr, "UTF-8");
    }
    catch (Exception e) {
      return pathStr;
    }
  }

  /**
   * Get the new path relative to the datasource
   */
  public Path getNewPathRelative() {
    // For WebDAV paths, we need to handle path operations differently
    if (WebDavDataSourceHelper.isWebDavPath(entity.getPath())) {
      // Extract the relative path by removing the datasource prefix
      String fullPath = newPath.toString();
      String datasource = entity.getDataSource();

      // URL decode both paths to ensure proper comparison
      try {
        fullPath = java.net.URLDecoder.decode(fullPath, "UTF-8");
        datasource = java.net.URLDecoder.decode(datasource, "UTF-8");
      }
      catch (Exception e) {
        // If decoding fails, use the original paths
      }

      // Remove datasource prefix to get relative path
      if (fullPath.startsWith(datasource)) {
        String relativePath = fullPath.substring(datasource.length());
        // Remove leading slash if present
        if (relativePath.startsWith("/")) {
          relativePath = relativePath.substring(1);
        }
        return Paths.get(relativePath);
      }
      // Fallback: if we can't extract relative path, return just the folder name
      return Paths.get(fullPath.substring(fullPath.lastIndexOf('/') + 1));
    }
    else {
      return Paths.get(entity.getDataSource()).relativize(newPath);
    }
  }

  /**
   * Get the old path relative as a readable string (URL decoded if needed)
   */
  public String getOldPathRelativeReadable() {
    Path relativePath = getOldPathRelative();
    String pathStr = relativePath.toString();
    // URL decode the path to display readable characters
    try {
      return java.net.URLDecoder.decode(pathStr, "UTF-8");
    }
    catch (Exception e) {
      return pathStr;
    }
  }

  /**
   * Get the new path relative as a readable string (URL decoded if needed)
   */
  public String getNewPathRelativeReadable() {
    Path relativePath = getNewPathRelative();
    String pathStr = relativePath.toString();
    // URL decode the path to display readable characters
    try {
      return java.net.URLDecoder.decode(pathStr, "UTF-8");
    }
    catch (Exception e) {
      return pathStr;
    }
  }

  public boolean isNeedsRename() {
    // For WebDAV paths, compare using string representation after URL decoding
    if (WebDavDataSourceHelper.isWebDavPath(entity.getPath())) {
      try {
        String decodedOldPath = java.net.URLDecoder.decode(entity.getPath(), "UTF-8");
        String decodedNewPath = java.net.URLDecoder.decode(newPath.toString(), "UTF-8");
        if (!decodedOldPath.equals(decodedNewPath)) {
          return true;
        }
      }
      catch (Exception e) {
        // If decoding fails, fall back to original comparison
        if (!entity.getPath().equals(newPath.toString())) {
          return true;
        }
      }
    }
    else {
      if (!entity.getPathNIO().equals(newPath)) {
        return true;
      }
    }
    return files.stream().anyMatch(mftc -> !mftc.isUnchanged());
  }

  public boolean hasRenamerProblems() {
    return renamerProblems;
  }

  public List<MediaFileTypeContainer> getFiles() {
    return files;
  }

  public void addFile(MediaFileTypeContainer file) {
    files.add(file);
  }

  public static class MediaFileTypeContainer {
    public Set<String> oldFiles = new TreeSet<>();
    public Set<String> newFiles = new TreeSet<>();
    public boolean     duped    = false;

    public boolean isUnchanged() {
      return oldFiles.equals(newFiles);
    }
  }
}
