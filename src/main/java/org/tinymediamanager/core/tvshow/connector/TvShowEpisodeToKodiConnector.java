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

package org.tinymediamanager.core.tvshow.connector;

import java.util.List;
import java.util.Locale;

import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.MediaMetadata;
import org.w3c.dom.Element;

/**
 * the class {@link TvShowEpisodeToKodiConnector} is used to write a most recent Kodi compatible NFO file
 *
 * @author Manuel Laggner
 */
public class TvShowEpisodeToKodiConnector extends TvShowEpisodeGenericXmlConnector {

  public TvShowEpisodeToKodiConnector(List<TvShowEpisode> episodes) {
    super(episodes);
  }

  /**
   * write the new rating style<br />
   * <ratings> <rating name="default" max="10" default="true"> <value>5.800000</value> <votes>2100</votes> </rating> <rating name="imdb">
   * <value>8.9</value> <votes>12345</votes> </rating> </ratings>
   */
  @Override
  protected void addRating(TvShowEpisode episode, TvShowEpisodeNfoParser.Episode parser) {
    Element ratings = document.createElement("ratings");

    for (MediaRating r : episode.getRatings().values()) {
      // skip user ratings here
      if (MediaRating.USER.equals(r.getId())) {
        continue;
      }

      Element rating = document.createElement("rating");
      // Kodi needs themoviedb instead of tmdb
      if (MediaMetadata.TMDB.equals(r.getId())) {
        rating.setAttribute("name", "themoviedb");
      }
      else {
        rating.setAttribute("name", r.getId());
      }
      rating.setAttribute("max", String.valueOf(r.getMaxValue()));

      MediaRating mainMediaRating = episode.getRating();
      rating.setAttribute("default", r == mainMediaRating ? "true" : "false");

      Element value = document.createElement("value");
      value.setTextContent(String.format(Locale.US, "%.1f", r.getRating()));
      rating.appendChild(value);

      Element votes = document.createElement("votes");
      votes.setTextContent(Integer.toString(r.getVotes()));
      rating.appendChild(votes);

      ratings.appendChild(rating);
    }

    root.appendChild(ratings);
  }

  /**
   * votes are now in the ratings tag
   */
  @Override
  protected void addVotes(TvShowEpisode episode, TvShowEpisodeNfoParser.Episode parser) {
  }

  @Override
  protected void addOwnTags(TvShowEpisode episode, TvShowEpisodeNfoParser.Episode parser) {
    addEpbookmark(episode, parser);
    addCode(episode, parser);
  }

  /**
   * add the <epbookmark>xxx</epbookmark>
   */
  protected void addEpbookmark(TvShowEpisode episode, TvShowEpisodeNfoParser.Episode parser) {
    Element epbookmark = document.createElement("epbookmark");
    if (parser != null) {
      epbookmark.setTextContent(parser.epbookmark);
    }
    root.appendChild(epbookmark);
  }

  /**
   * add the <code>xxx</code>
   */
  protected void addCode(TvShowEpisode episode, TvShowEpisodeNfoParser.Episode parser) {
    Element code = document.createElement("code");
    if (parser != null) {
      code.setTextContent(parser.code);
    }
    root.appendChild(code);
  }
}
