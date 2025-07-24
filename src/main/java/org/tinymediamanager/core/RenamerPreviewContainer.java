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
      this.oldPath = entity.getPathNIO();
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

  public Path getOldPathRelative() {
    return Paths.get(entity.getDataSource()).relativize(entity.getPathNIO());
  }

  public Path getNewPath() {
    return newPath;
  }

  public Path getNewPathRelative() {
    return Paths.get(entity.getDataSource()).relativize(newPath);
  }

  public boolean isNeedsRename() {
    if (!entity.getPathNIO().equals(newPath)) {
      return true;
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
