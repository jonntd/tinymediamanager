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
    container.newPath = Paths.get(movie.getDataSource())
        .resolve(MovieRenamer.createDestinationForFoldername(MovieModuleManager.getInstance().getSettings().getRenamerPathname(), movie));
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
    for (MediaFileType type : MediaFileType.values()) {
      MediaFileTypeContainer c = new MediaFileTypeContainer();
      for (MediaFile typeMf : movie.getMediaFiles(type)) {
        c.oldFiles.add(container.getOldPath().relativize(typeMf.getFileAsPath()).toString());
        List<MediaFile> mfs = MovieRenamer.generateFilename(movie, new MediaFile(typeMf), newVideoBasename);
        for (MediaFile mf : mfs) {
          c.newFiles.add(container.getNewPath().relativize(mf.getFileAsPath()).toString());
        }
      }
      if (!c.oldFiles.isEmpty()) {
        container.addFile(c);
      }
    }
  }
}
