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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.NfoUtils;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.entities.MediaTrailer;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.scraper.MediaMetadata;
import org.w3c.dom.Element;

/**
 * the class {@link MovieToKodiConnector} is used to write a most recent Kodi compatible NFO file
 *
 * @author Manuel Laggner
 */
public class MovieToKodiConnector extends MovieGenericXmlConnector {
  protected static final Logger  LOGGER              = LoggerFactory.getLogger(MovieToKodiConnector.class);
  protected static final Pattern HD_TRAILERS_PATTERN = Pattern
      .compile("https?://.*(apple.com|yahoo-redir|yahoo.com|youtube.com|moviefone.com|ign.com|hd-trailers.net|aol.com).*");

  public MovieToKodiConnector(Movie movie) {
    super(movie);
  }

  @Override
  protected void addOwnTags() {
    addEpbookmark();
    addTop250();
    addStatusAndCode();
  }

  /**
   * the new set style<br />
   * <set><name>xxx</name><overview>xxx</overview></set>
   */
  @Override
  protected void addSet() {
    Element set = document.createElement("set");

    if (movie.getMovieSet() != null) {
      Element name = document.createElement("name");
      name.setTextContent(movie.getMovieSet().getTitle());
      set.appendChild(name);

      Element overview = document.createElement("overview");
      overview.setTextContent(movie.getMovieSet().getPlot());
      set.appendChild(overview);
    }
    root.appendChild(set);
  }

  /**
   * the new thumb style<br />
   *
   * <thumb aspect="poster">xxx</thumb> <br />
   * <thumb aspect="banner">xxx</thumb> <br />
   * <thumb aspect="clearart">xxx</thumb> <br />
   * <thumb aspect="clearlogo">xxx</thumb> <br />
   * <thumb aspect="discart">xxx</thumb> <br />
   * <thumb aspect="landscape">xxx</thumb> <br />
   * <thumb aspect="keyart">xxx</thumb> //not yet supported by kodi <br />
   * <thumb aspect="logo">xxx</thumb> //not yet supported by kodi
   *
   * we will write all supported artwork types here
   */
  @Override
  protected void addThumb() {
    if (settings.isNfoWriteArtworkUrls()) {
      addThumb(MediaFileType.POSTER, "poster");
      addThumb(MediaFileType.BANNER, "banner");
      addThumb(MediaFileType.CLEARART, "clearart");
      addThumb(MediaFileType.CLEARLOGO, "clearlogo");
      addThumb(MediaFileType.DISC, "discart");
      addThumb(MediaFileType.THUMB, "landscape");
      addThumb(MediaFileType.KEYART, "keyart");
      addThumb(MediaFileType.LOGO, "logo");
    }
  }

  protected void addThumb(MediaFileType type, String aspect) {
    Element thumb = document.createElement("thumb");

    String artworkUrl = movie.getArtworkUrl(type);
    if (StringUtils.isNotBlank(artworkUrl)) {
      thumb.setAttribute("aspect", aspect);
      thumb.setTextContent(artworkUrl);
      root.appendChild(thumb);
    }
  }

  /**
   * the new fanart style<br />
   * <fanart><thumb>xxx</thumb></fanart>
   */
  @Override
  protected void addFanart() {
    if (settings.isNfoWriteArtworkUrls()) {
      Element fanart = document.createElement("fanart");

      Set<String> fanartUrls = new LinkedHashSet<>();

      // main fanart
      String fanartUrl = movie.getArtworkUrl(MediaFileType.FANART);
      if (StringUtils.isNotBlank(fanartUrl)) {
        fanartUrls.add(fanartUrl);
      }

      // extrafanart
      fanartUrls.addAll(movie.getExtraFanarts());

      for (String url : fanartUrls) {
        Element thumb = document.createElement("thumb");
        thumb.setTextContent(url);
        fanart.appendChild(thumb);
      }

      if (!fanartUrls.isEmpty()) {
        root.appendChild(fanart);
      }
    }
  }

  @Override
  protected void addTrailer() {
    if (settings.isNfoWriteTrailer()) {
      Element trailer = document.createElement("trailer");

      for (MediaTrailer mediaTrailer : new ArrayList<>(movie.getTrailer())) {
        if (mediaTrailer.getInNfo()) {
          if (mediaTrailer.getUrl().startsWith("http")) {
            trailer.setTextContent(prepareTrailerForKodi(mediaTrailer));
          }
          else {
            trailer.setTextContent(mediaTrailer.getUrl());
          }
          break;
        }
      }
      root.appendChild(trailer);
    }
  }

  protected String prepareTrailerForKodi(MediaTrailer trailer) {
    // youtube trailer are stored in a special notation: plugin://plugin.video.youtube/?action=play_video&videoid=<ID>
    // parse out the ID from the url and store it in the right notation
    Matcher matcher = Utils.YOUTUBE_PATTERN.matcher(trailer.getUrl());
    if (matcher.matches()) {
      return "plugin://plugin.video.youtube/play/?video_id=" + matcher.group(5);
    }

    // other urls are handled by the hd-trailers.net plugin
    matcher = HD_TRAILERS_PATTERN.matcher(trailer.getUrl());
    if (matcher.matches()) {
      try {
        return "plugin://plugin.video.hdtrailers_net/video/" + matcher.group(1) + "/" + URLEncoder.encode(trailer.getUrl(), "UTF-8");
      }
      catch (Exception e) {
        LOGGER.debug("failed to escape {} - {}", trailer.getUrl(), e.getMessage());
      }
    }
    // everything else is stored directly
    return trailer.getUrl();
  }

  /**
   * write the new rating style<br />
   * <ratings> <rating name="default" max="10" default="true"> <value>5.800000</value> <votes>2100</votes> </rating> <rating name="imdb">
   * <value>8.9</value> <votes>12345</votes> </rating> </ratings>
   */
  @Override
  protected void addRating() {
    Element ratings = document.createElement("ratings");

    MediaRating mainMediaRating = movie.getRating();

    for (MediaRating r : movie.getRatings().values()) {
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
  protected void addVotes() {
    // do nothing, votes are now in the ratings tag
  }

  /**
   * add the <epbookmark>xxx</epbookmark> just before the <year>xxx</year>
   */
  protected void addEpbookmark() {
    Element epbookmark = document.createElement("epbookmark");

    Element year = NfoUtils.getSingleElementByTag(document, "year");
    if (parser != null) {
      epbookmark.setTextContent(parser.epbookmark);
    }
    root.insertBefore(epbookmark, year);
  }

  /**
   * add the <top250>xxx</top250> just before the <set>xxx</set>
   */
  protected void addTop250() {
    Element top250 = document.createElement("top250");
    top250.setTextContent(Integer.toString(movie.getTop250()));
    Element set = NfoUtils.getSingleElementByTag(document, "set");
    root.insertBefore(top250, set);
  }

  /**
   * add the <status>xxx</status> and <code>xxx</code> just before the <premiered>xxx</premiered>
   */
  protected void addStatusAndCode() {
    Element status = document.createElement("status");
    Element code = document.createElement("code");

    Element premiered = NfoUtils.getSingleElementByTag(document, "premiered");
    if (parser != null) {
      status.setTextContent(parser.status);
      code.setTextContent(parser.code);
    }
    root.insertBefore(status, premiered);
    root.insertBefore(code, premiered);
  }
}
