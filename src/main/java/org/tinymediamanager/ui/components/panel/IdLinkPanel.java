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

package org.tinymediamanager.ui.components.panel;

import java.awt.FlowLayout;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.components.label.LinkLabel;
import org.tinymediamanager.ui.components.label.TmmLabel;

/**
 * The class {@link IdLinkPanel} is used to provide a {@link JPanel} with an embedded {@link JLabel} for the ID source and a {@link LinkLabel} or
 * {@link JLabel} for the ID itself
 * 
 * @author Manuel Laggner
 */
public class IdLinkPanel extends JPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(IdLinkPanel.class);

  public IdLinkPanel(String key, MediaEntity mediaEntity) {
    super();
    setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    JLabel keyLabel = new TmmLabel(createTextForKey(key));
    keyLabel.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 5));

    String id = mediaEntity.getIdAsString(key);
    String url = createUrlForId(key, id, mediaEntity);

    JLabel idLabel;
    if (url.startsWith("http")) {
      LinkLabel linkLabel = new LinkLabel(id);
      linkLabel.setLink(url);
      linkLabel.addActionListener(arg0 -> {
        try {
          TmmUIHelper.browseUrl(url);
        }
        catch (Exception e) {
          LOGGER.error("browse to '{}' - {}", url, e.getMessage());
          MessageManager.instance
              .pushMessage(new Message(Message.MessageLevel.ERROR, url, "message.erroropenurl", new String[] { ":", e.getLocalizedMessage() }));
        }
      });

      idLabel = linkLabel;
    }
    else {
      idLabel = new JLabel(id);
    }
    idLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

    add(keyLabel);
    add(idLabel);
  }

  private String createTextForKey(String key) {
    return switch (key.toLowerCase(Locale.ROOT)) {
      case "imdb" -> "IMDb:";
      case "tmdb" -> "TMDB:";
      case "wikidata" -> "Wikidata:";
      case "trakt" -> "Trakt.tv:";
      case "tmdbset" -> "TMDB " + TmmResourceBundle.getString("metatag.movieset") + ":";
      default -> key + ":";
    };
  }

  private String createUrlForId(String key, String id, MediaEntity mediaEntity) {
    if (StringUtils.isBlank(id)) {
      return "";
    }

    String url = "";

    // same url, regardless if movie or tv
    switch (key) {
      case MediaMetadata.IMDB:
        url = "https://www.imdb.com/title/" + id;
        break;

      case MediaMetadata.ANIDB:
        url = "https://anidb.net/anime/" + id;
        break;

      case "moviemeter":
        url = "https://www.moviemeter.nl/film/" + id;
        break;

      case "mpdbtv":
        url = "https://mpdb.tv/movie/en_us/" + id;
        break;

      case "ofdb":
        url = "https://ssl.ofdb.de/film/" + id + "," + mediaEntity.getTitle();
        break;

      case "omdbapi":
        url = "https://www.omdb.org/movie/" + id + "-" + mediaEntity.getTitle();
        break;

      case MediaMetadata.TVMAZE:
        url = "https://www.tvmaze.com/shows/" + id;
        break;

      case MediaMetadata.WIKIDATA:
        url = "https://www.wikidata.org/wiki/" + id;
        break;

      case "eidr":
        url = "https://ui.eidr.org/view/content?id=" + id;
        break;

      case MediaMetadata.TMDB_SET:
        url = "https://www.themoviedb.org/collection/" + id;
        break;

      default:
        break;
    }

    if (mediaEntity instanceof Movie) {
      switch (key) {
        case MediaMetadata.TRAKT_TV:
          url = "https://trakt.tv/search/trakt/" + id + "?id_type=movie";
          break;

        case MediaMetadata.TMDB:
          url = "https://www.themoviedb.org/movie/" + id;
          break;

        case MediaMetadata.TVDB:
          url = "https://thetvdb.com/dereferrer/movie/" + id;
          break;

        default:
          break;
      }
    }
    else if (mediaEntity instanceof TvShow) {
      switch (key) {
        case MediaMetadata.TRAKT_TV:
          url = "https://trakt.tv/search/trakt/" + id + "?id_type=show";
          break;

        case MediaMetadata.TMDB:
          url = "https://www.themoviedb.org/tv/" + id;
          break;

        case MediaMetadata.TVDB:
          url = "https://thetvdb.com/dereferrer/series/" + id;
          break;

        // do not use zap2it for now, because most IDs are broken
        // case "zap2it":
        // url = "https://tvschedule.zap2it.com/overview.html?programSeriesId=" + id;
        // break;

        default:
          break;
      }
    }
    else if (mediaEntity instanceof TvShowEpisode) {
      switch (key) {
        case MediaMetadata.TRAKT_TV:
          url = "https://trakt.tv/search/trakt/" + id + "?id_type=episode";
          break;

        case MediaMetadata.TMDB:
          url = "https://www.themoviedb.org/tv/" + id;
          break;

        case MediaMetadata.TVDB:
          url = "https://thetvdb.com/dereferrer/episode/" + id;
          break;

        default:
          break;
      }
    }

    return url;
  }
}
