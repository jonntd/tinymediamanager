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

import org.tinymediamanager.core.movie.entities.MovieSet;
import org.w3c.dom.Element;

/**
 * the class {@link MovieSetToKodiConnector} is used to write a most recent Kodi compatible NFO file
 *
 * @author Manuel Laggner
 */
public class MovieSetToKodiConnector extends MovieSetGenericXmlConnector {
  public MovieSetToKodiConnector(MovieSet movieSet) {
    super(movieSet);
  }

  /**
   * add the plot in the form <overview>xxx</overview>
   */
  @Override
  protected void addPlot() {
    Element plot = document.createElement("overview");
    plot.setTextContent(movieSet.getPlot());
    root.appendChild(plot);
  }

  @Override
  protected void addOwnTags() {
    // nothing to add yet
  }
}
