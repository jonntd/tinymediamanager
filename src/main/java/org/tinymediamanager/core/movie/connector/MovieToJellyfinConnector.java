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
package org.tinymediamanager.core.movie.connector;

import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.scraper.MediaMetadata;
import org.w3c.dom.Element;

/**
 * the class {@link MovieToJellyfinConnector} is used to write a most recent Jellyfin compatible NFO file
 *
 * @author Manuel Laggner
 */
public class MovieToJellyfinConnector extends MovieToKodiConnector {

  public MovieToJellyfinConnector(Movie movie) {
    super(movie);
  }

  @Override
  protected void addOwnTags() {
    // TmdbSetId is only recognized as collectionnumber, see https://jellyfin.org/docs/general/server/metadata/nfo
    String setId = movie.getIdAsString(MediaMetadata.TMDB_SET);
    if (!setId.isEmpty()) {
      Element id = document.createElement("collectionnumber");
      id.setTextContent(setId);
      root.appendChild(id);
    }
  }
}
