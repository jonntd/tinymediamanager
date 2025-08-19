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
package org.tinymediamanager.scraper.yifysubtitles;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.FeatureNotEnabledException;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.SubtitleSearchAndScrapeOptions;
import org.tinymediamanager.scraper.SubtitleSearchResult;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.http.OnDiskCachedUrl;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.interfaces.IMovieSubtitleProvider;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.scraper.util.MetadataUtil;

/**
 * Provider for YifySubtitles.ch subtitles.
 * <p>
 * This class implements the {@link IMovieSubtitleProvider} interface to fetch and parse subtitles from YifySubtitles.ch.
 * 
 * @author Myron Boyle
 */
public class YifySubtitlesProvider implements IMovieSubtitleProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(YifySubtitlesProvider.class);
  private static final String ID     = "yify";

  @Override
  public MediaProviderInfo getProviderInfo() {
    return new MediaProviderInfo(ID, "movie_subtitle", "YifySubtitles.ch",
        "<html><h3>YifySubtitles.ch</h3><br />A subtitle scraper for YifySubtitles.ch</html>",
        YifySubtitlesProvider.class.getResource("/org/tinymediamanager/scraper/yifysubtitles-logo-small.png"));
  }

  @Override
  public boolean isActive() {
    return isFeatureEnabled();
  }

  /**
   * Initializes the API and checks if the feature is enabled.
   *
   * @throws ScrapeException
   *           if the feature is not enabled
   */
  private synchronized void initAPI() throws ScrapeException {
    if (!isActive()) {
      throw new ScrapeException(new FeatureNotEnabledException(this));
    }
  }

  @Override
  public List<SubtitleSearchResult> search(SubtitleSearchAndScrapeOptions options) throws ScrapeException, MissingIdException {
    initAPI();
    String imdbId = options.getImdbId();

    if (!MediaIdUtil.isValidImdbId(imdbId)) {
      throw new MissingIdException(MediaMetadata.IMDB);
    }

    List<SubtitleSearchResult> results = new ArrayList<>();

    results = parseDetailPage(imdbId, options.getLanguage());

    Collections.sort(results);
    Collections.reverse(results);

    return results;
  }

  /**
   * Parses the detail page for the given IMDb ID and language.
   *
   * @param imdbId
   *          the IMDb ID of the movie
   * @param language
   *          the desired subtitle language
   * @return a list of {@link SubtitleSearchResult} objects
   * @throws ScrapeException
   *           if an error occurs during parsing
   */
  private List<SubtitleSearchResult> parseDetailPage(String imdbId, MediaLanguages language) throws ScrapeException {
    List<SubtitleSearchResult> results = new ArrayList<>();
    Document doc = null;
    Url url;

    try {
      url = new OnDiskCachedUrl(getApiKey() + "/movie-imdb/" + imdbId, 7, TimeUnit.DAYS);
    }
    catch (Exception e) {
      LOGGER.debug("tried to fetch subtitle detail page for imdb {} - {}", imdbId, e.getMessage());
      throw new ScrapeException(e);
    }
    try (InputStream is = url.getInputStream()) {
      doc = Jsoup.parse(is, "UTF-8", "");
    }
    catch (InterruptedException | InterruptedIOException e) {
      // do not swallow these Exceptions
      Thread.currentThread().interrupt();
      return results; // return empty list
    }
    catch (Exception e) {
      LOGGER.debug("tried to parse subtitle detail page for imdb {} - {}", imdbId, e.getMessage());
      throw new ScrapeException(e);
    }

    // yay - HTML parsing :)
    Element table = doc.getElementsByClass("table other-subs").first();
    if (table != null) {
      for (Element tr : ListUtils.nullSafe(table.getElementsByTag("tr"))) {
        SubtitleSearchResult sr = new SubtitleSearchResult(ID);
        boolean langMatched = false;

        for (Element td : tr.getElementsByTag("td")) {
          switch (td.className()) {
            case "rating-cell": {
              int rating = MetadataUtil.parseInt(td.text(), 0);
              if (rating > 0) {
                sr.setRating((float) rating);
              }
              break;
            }

            case "flag-cell": {
              Element subLang = td.getElementsByClass("sub-lang").first();
              if (subLang != null) {
                String langu = subLang.text();
                MediaLanguages stLangu = MediaLanguages.get(langu);
                if (stLangu == language) {
                  langMatched = true;
                }
              }
              break;
            }

            case "uploader-cell":
            case "other-cell": {
              break;
            }

            default: {
              // if it has an A tag, it is our main
              Element a = td.getElementsByTag("a").first();
              if (a != null) {
                String downloadUrl = a.attr("href").replace("/subtitles/", getApiKey() + "/subtitle/") + ".zip";

                sr.setUrl(() -> downloadUrl);
                sr.setReleaseName(a.ownText().split(" ", 2)[0]); // multiple releases possible
              }
            }
          } // end switch
        } // end for td

        if (langMatched) {
          results.add(sr);
        }
      }
    }

    return results;
  }
}
