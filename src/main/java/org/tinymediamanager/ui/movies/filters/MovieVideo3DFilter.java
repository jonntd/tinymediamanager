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
package org.tinymediamanager.ui.movies.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JLabel;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.mediainfo.MediaInfo3D;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.ui.components.label.TmmLabel;

/**
 * this class is used for a video - 3D movie filter
 * 
 * @author Manuel Laggner
 */
public class MovieVideo3DFilter extends AbstractCheckComboBoxMovieUIFilter<MediaInfo3D> {

  public MovieVideo3DFilter() {
    super();
    checkComboBox.enableFilter((s, s2) -> s.toString().toLowerCase(Locale.ROOT).startsWith(s2.toLowerCase(Locale.ROOT)));
    buildAndInstallEditionArray();
  }

  @Override
  public String getId() {
    return "movieVideo3D";
  }

  private void buildAndInstallEditionArray() {
    List<MediaInfo3D> editions = new ArrayList<>();

    for (MediaInfo3D ddd : MediaInfo3D.values()) {
      if (StringUtils.isNotBlank(ddd.toString())) {
        editions.add(ddd);
      }
    }

    setValues(editions);
  }

  @Override
  protected String parseTypeToString(MediaInfo3D type) throws Exception {
    return type.name();
  }

  @Override
  public boolean accept(Movie movie) {
    List<MediaInfo3D> selectedItems = checkComboBox.getSelectedItems();
    MediaInfo3D ddd = MediaInfo3D.get3DFrom(movie.getVideo3DFormat2());
    return selectedItems.contains(ddd);
  }

  @Override
  protected JLabel createLabel() {
    return new TmmLabel(TmmResourceBundle.getString("metatag.3d"));
  }

  @Override
  protected MediaInfo3D parseStringToType(String string) throws Exception {
    return MediaInfo3D.get3DFrom(string);
  }
}
