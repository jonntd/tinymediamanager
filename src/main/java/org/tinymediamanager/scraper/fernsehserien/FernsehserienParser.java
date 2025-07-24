package org.tinymediamanager.scraper.fernsehserien;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.entities.MediaGenres;
import org.tinymediamanager.core.entities.Person;
import org.tinymediamanager.core.tvshow.TvShowEpisodeSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.TvShowSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.entities.MediaEpisodeGroup;
import org.tinymediamanager.scraper.entities.MediaEpisodeNumber;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.NothingFoundException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.http.InMemoryCachedUrl;
import org.tinymediamanager.scraper.http.OnDiskCachedUrl;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.interfaces.IMediaProvider;
import org.tinymediamanager.scraper.util.CacheMap;
import org.tinymediamanager.scraper.util.DateUtils;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.ParserUtils;
import org.tinymediamanager.scraper.util.StrgUtils;
import org.tinymediamanager.scraper.util.UrlUtil;

public class FernsehserienParser {
  private static final Logger                                LOGGER                 = LoggerFactory.getLogger(FernsehserienParser.class);
  private static final Pattern                               IMDB_ID_PATTERN        = Pattern.compile("imdb\\.com/title/(tt[0-9]{6,})");
  private static final Pattern                               TVDB_ID_PATTERN        = Pattern.compile("thetvdb\\.com/series/([0-9]+)");
  private static final Pattern                               TVDB_ID_PATTERN2       = Pattern.compile("thetvdb\\.com/.*?id=([0-9]+)");
  private static final Pattern                               TVMAZE_ID_PATTERN      = Pattern.compile("tvmaze\\.com/shows/([0-9]+)");
  private static final CacheMap<String, List<MediaMetadata>> EPISODE_LIST_CACHE_MAP = new CacheMap<>(60, 10);

  FernsehserienParser(IMediaProvider mediaProvider, ExecutorService executor) {
  }

  protected SortedSet<MediaSearchResult> search(MediaSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("search(): {}", options);
    SortedSet<MediaSearchResult> results = new TreeSet<>();

    String searchTerm = options.getSearchQuery();
    if (StringUtils.isEmpty(searchTerm)) {
      return results;
    }

    searchTerm = MetadataUtil.removeNonSearchCharacters(searchTerm);
    LOGGER.debug("========= BEGIN Fernsehserien Scraper Search for: {}", searchTerm);
    try {
      Document doc = null;
      Url url = new InMemoryCachedUrl(FernsehserienMetadataProvider.BASE_URL + "/suche/" + URLEncoder.encode(searchTerm, StandardCharsets.UTF_8));
      InputStream is = url.getInputStream();
      doc = Jsoup.parse(is, UrlUtil.UTF_8, "");
      doc.setBaseUri(FernsehserienMetadataProvider.BASE_URL);

      Element ul = doc.getElementsByClass("suchergebnisse").first(); // there should be one, else nothing found
      if (ul != null) {
        Elements li = ul.getElementsByTag("li"); // all results
        for (Element result : li) {

          // rule out invalid types
          switch (options.getMediaType()) {
            case MOVIE:
              if (!result.attr("class").contains("film")) {
                continue;
              }
              break;

            case TV_SHOW:
              if (!result.attr("class").contains("sendung")) {
                continue;
              }
              break;

            default:
          }

          MediaSearchResult sr = new MediaSearchResult(FernsehserienMetadataProvider.ID, options.getMediaType());

          Element a = result.getElementsByTag("a").first();
          if (a != null) {
            String title = a.attr("title");
            String id = a.attr("href").substring(1); // trim slash
            sr.setId(id); // no real ID beside the rel url :/
            sr.setTitle(title);
          }

          int year = 0;
          Elements dds = result.getElementsByTag("dd");
          for (Element el : dds) {
            String y = el.ownText();
            y = StrgUtils.substr(y, ".*(\\d{4}).*"); // first 4 nums as year
            year = MetadataUtil.parseInt(y, 0);
            if (year > 0) {
              break; // we found one
            }
          }
          sr.setYear(year);

          Element div = result.getElementsByTag("div").first();
          if (div != null) {
            String posterUrl = div.attr("data-src");
            sr.setPosterUrl(posterUrl);
          }

          sr.calculateScore(options);
          results.add(sr);
        }
      }
      else {
        // no searchresult? Maybe redirected directly to details page? (Search for "scrubs")
        Element ogurl = doc.getElementsByAttributeValue("property", "og:url").first();
        if (ogurl != null) {
          String id = ogurl.attr("content");
          id = id.replace(FernsehserienMetadataProvider.BASE_URL + "/", "");
          if (!id.startsWith("suche")) {
            options.setId(FernsehserienMetadataProvider.ID, id);
            MediaMetadata md = getMetadata(options);
            MediaSearchResult sr = md.toSearchResult(options.getMediaType());
            sr.setScore(1f);
            results.add(sr);
          }
        }
        else {
          throw new MissingIdException(FernsehserienMetadataProvider.ID);
        }
      }
    }
    catch (Exception e) {
      LOGGER.debug("tried to fetch search response", e);
      throw new ScrapeException(e);
    }
    LOGGER.debug("Found {} results.", results.size());

    return results;
  }

  // parses SEASON page, which is an aggregated single episode details page
  // else "/seriesname/folgen/someTitle-0000
  MediaMetadata getEpisodeMetadata(TvShowEpisodeSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("getMetadata(): {}", options);

    MediaMetadata md = new MediaMetadata(FernsehserienMetadataProvider.ID);
    md.setScrapeOptions(options);
    int seasonNr = options.getIdAsIntOrDefault(MediaMetadata.SEASON_NR, -1);
    int episodeNr = options.getIdAsIntOrDefault(MediaMetadata.EPISODE_NR, -1);
    String episodeId = options.getIdAsString(FernsehserienMetadataProvider.ID);
    if (!MediaIdUtil.isValidImdbId(episodeId)) {
      episodeId = "";
    }

    List<MediaMetadata> episodes = getEpisodeList(options.createTvShowSearchAndScrapeOptions());
    // first get the base episode metadata which can be gathered via getEpisodeList()
    // only if we get a S/E number
    MediaMetadata wantedEpisode = null;

    // search by ID
    if (StringUtils.isNotBlank(episodeId)) {
      for (MediaMetadata episode : episodes) {
        if (episodeId.equals(episode.getId(FernsehserienMetadataProvider.ID))) {
          wantedEpisode = episode;
          break;
        }
      }
    }

    // search by S/E
    if (wantedEpisode == null) {
      for (MediaMetadata episode : episodes) {
        MediaEpisodeNumber episodeNumber = episode.getEpisodeNumber(MediaEpisodeGroup.EpisodeGroupType.AIRED);

        if (episodeNumber != null && episodeNumber.season() == seasonNr && episodeNumber.episode() == episodeNr) {
          // search via season/episode number
          wantedEpisode = episode;
          break;
        }
      }
    }
    // we did not find the episode; return
    if (wantedEpisode == null && StringUtils.isBlank(episodeId)) {
      LOGGER.debug("episode not found");
      throw new NothingFoundException();
    }

    md.mergeFrom(wantedEpisode);
    md.removeId("fs-sid"); // our temp seasonId

    // parse now season page
    // we also could parse each episode page (includes actors)
    String SeasonID = wantedEpisode.getIdAsString("fs-sid");
    String showUrl = (String) options.getTvShowIds().get(FernsehserienMetadataProvider.ID);
    String wantedEpisodeId = wantedEpisode.getIdAsString(FernsehserienMetadataProvider.ID);

    Url url;
    try {
      // cache this on disk because that will be called multiple times
      url = new OnDiskCachedUrl(FernsehserienMetadataProvider.BASE_URL + "/" + showUrl + "/episodenguide/staffel-" + seasonNr + "/" + SeasonID, 1,
          TimeUnit.DAYS);
    }
    catch (Exception e) {
      LOGGER.debug("problem scraping: {}", e.getMessage());
      throw new ScrapeException(e);
    }

    Document doc;
    try (InputStream is = url.getInputStream()) {
      doc = Jsoup.parse(is, "UTF-8", "");
      if (doc != null) {

        for (Element episode : doc.getElementsByAttributeValue("itemprop", "episode")) {
          Element button = episode.getElementsByTag("button").first();
          if (button != null) {
            String entryId = button.attr("data-episode-id");
            if (entryId.equals(wantedEpisodeId)) {
              // parse episode

              // for image
              Element fig = null;
              // plot
              Element plotEl = episode.getElementsByAttributeValue("itemprop", "description").first(); // serien
              if (plotEl == null) {
                plotEl = episode.getElementsByClass("episode-output-inhalt").first(); // filme + EPs
              }
              if (plotEl != null) {
                fig = plotEl.getElementsByTag("figure").first(); // in plot, we have the HTML for image
                plotEl.select("figure").remove();
                plotEl.select("br").before("\n");
                plotEl.select("p").before("\n\n");
                plotEl.select("div").before("\n\n");
                md.setPlot(plotEl.wholeText().replace("  ", " ").strip());
              }

              // image parse
              if (fig != null) {
                Element img = fig.getElementsByTag("img").first();
                if (img != null) {
                  MediaArtwork art = new MediaArtwork(FernsehserienMetadataProvider.ID, MediaArtworkType.POSTER);
                  art.setOriginalUrl(img.attr("src"));
                  // TODO artwork image sizes?
                }
              }

              break; // on first match
            }
          }
        }

      } // end doc==null
    } // end inputStream
    catch (InterruptedException | InterruptedIOException e) {
      // do not swallow these Exceptions
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOGGER.debug("problem scraping: {}", e.getMessage());
      throw new ScrapeException(e);
    }

    return md;
  }

  // movies & show!!
  MediaMetadata getMetadata(MediaSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("getMetadata(): {}", options);
    MediaMetadata md = new MediaMetadata(FernsehserienMetadataProvider.ID);
    md.setScrapeOptions(options);
    md.addEpisodeGroup(MediaEpisodeGroup.DEFAULT_AIRED);

    String id = "";
    // id from searchResult
    if (options.getSearchResult() != null) {
      id = options.getSearchResult().getIdAsString(FernsehserienMetadataProvider.ID);
    }
    if (StringUtils.isBlank(id)) {
      id = options.getIdAsString(FernsehserienMetadataProvider.ID);
    }
    if (StringUtils.isBlank(id)) {
      LOGGER.debug("not possible to scrape from Fernsehserien.de - no id found");
      throw new MissingIdException(FernsehserienMetadataProvider.ID);
    }
    md.setId(FernsehserienMetadataProvider.ID, id);

    Url url;
    try {
      // cache this on disk because that may be called multiple times
      url = new OnDiskCachedUrl(FernsehserienMetadataProvider.BASE_URL + "/" + id, 1, TimeUnit.DAYS);
    }
    catch (Exception e) {
      LOGGER.debug("problem scraping: {}", e.getMessage());
      throw new ScrapeException(e);
    }

    Document doc;
    try (InputStream is = url.getInputStream()) {
      doc = Jsoup.parse(is, "UTF-8", "");
      if (doc != null) {
        // title + year
        Element title = doc.head().getElementsByAttributeValue("name", "title").first();
        String[] ty = null;
        if (title != null) {
          ty = ParserUtils.detectCleanTitleAndYear(title.attr("content"), null);
        }
        else {
          title = doc.getElementsByAttributeValue("property", "og:title").first();
        }
        if (title != null) {
          ty = ParserUtils.detectCleanTitleAndYear(title.attr("content"), null);
        }
        md.setTitle(ty[0]);
        if (!ty[1].isEmpty()) {
          md.setYear(MetadataUtil.parseInt(ty[1], 0));
        }

        // languages
        Element lang = doc.getElementsByAttributeValue("itemprop", "inLanguage").first();
        if (lang != null) {
          md.setOriginalLanguage(lang.attr("content"));
        }

        // countries
        Elements countries = doc.getElementsByAttributeValue("itemprop", "countryOfOrigin");
        for (Element country : countries) {
          md.addCountry(country.attr("title"));
        }

        // original title
        Element otitle = doc.getElementsByAttributeValue("itemprop", "alternateName").first();
        if (otitle != null) {
          md.setOriginalTitle(otitle.text());
        }

        // for image
        Element fig = null;
        // plot
        Element plotEl = doc.getElementsByAttributeValue("itemprop", "description").first(); // serien
        if (plotEl == null) {
          plotEl = doc.getElementsByClass("episode-output-inhalt").first(); // filme
        }
        if (plotEl != null) {
          fig = plotEl.getElementsByTag("figure").first(); // in plot, we have the HTML for image
          plotEl.select("figure").remove();
          plotEl.select("br").before("\n");
          plotEl.select("p").before("\n\n");
          plotEl.select("div").before("\n\n");
          md.setPlot(plotEl.wholeText().replace("  ", " ").strip());
        }

        // image parse
        if (fig != null) {
          Element img = fig.getElementsByTag("img").first();
          if (img != null) {
            MediaArtwork art = new MediaArtwork(FernsehserienMetadataProvider.ID, MediaArtworkType.POSTER);
            art.setOriginalUrl(img.attr("src"));
            // TODO artwork image sizes?
          }
        }

        // genres
        Element genres = doc.getElementsByClass("genrepillen").first();
        if (genres != null) {
          for (Element li : genres.getElementsByTag("li")) {
            md.addGenre(MediaGenres.getGenre(li.text()));
          }
        }

        // other IDs (eg https://www.fernsehserien.de/sherlock)
        Elements links = doc.getElementsByClass("sendung-spielfilm-person-links-optional");
        for (Element link : links) {
          String href = link.getElementsByTag("a").attr("href");
          Matcher matcher = IMDB_ID_PATTERN.matcher(href);
          if (matcher.find()) {
            if (matcher.group(1) != null) {
              md.setId("imdb", matcher.group(1));
            }
          }
          matcher = TVDB_ID_PATTERN.matcher(href);
          if (matcher.find()) {
            if (matcher.group(1) != null) {
              md.setId("tvdb", matcher.group(1));
            }
          }
          matcher = TVDB_ID_PATTERN2.matcher(href);
          if (matcher.find()) {
            if (matcher.group(1) != null) {
              md.setId("tvdb", matcher.group(1));
            }
          }
          matcher = TVMAZE_ID_PATTERN.matcher(href);
          if (matcher.find()) {
            if (matcher.group(1) != null) {
              md.setId("tvmaze", matcher.group(1));
            }
          }
        }

        // div highlighted metadata as list
        Map<String, Date> eaAngaben = new HashMap<>();
        for (Element release : doc.getElementsByTag("ea-angabe")) {
          String tit = release.getElementsByTag("ea-angabe-titel").text().strip();
          String dat = release.getElementsByTag("ea-angabe-datum").text().strip();
          Date d = null;
          try {
            // Di. 02.10.2001
            dat = dat.replaceFirst(".+ ", "");
            d = DateUtils.parseDate(dat);
          }
          catch (ParseException e) {
            // ignore
          }
          eaAngaben.put(tit, d);
        }
        // get date in our german order
        if (md.getReleaseDate() == null) {
          md.setReleaseDate(eaAngaben.get("Deutscher Kinostart"));
        }
        if (md.getReleaseDate() == null) {
          md.setReleaseDate(eaAngaben.get("Deutsche TV-Premiere"));
        }
        if (md.getReleaseDate() == null) {
          md.setReleaseDate(eaAngaben.get("Original-Kinostart"));
        }
        if (md.getReleaseDate() == null) {
          md.setReleaseDate(eaAngaben.get("Original-TV-Premiere"));
        }
        if (md.getReleaseDate() == null) {
          md.setReleaseDate(eaAngaben.get("Internationaler Kinostart"));
        }
        if (md.getReleaseDate() == null) {
          md.setReleaseDate(eaAngaben.get("Original-Streaming-Premiere"));
        }
        if (md.getYear() == 0 && md.getReleaseDate() != null) {
          Calendar calendar = Calendar.getInstance();
          calendar.setTime(md.getReleaseDate());
          md.setYear(calendar.get(Calendar.YEAR));
        }

        // parse episodenguide urls for later processing
        Element eg = doc.getElementsByAttributeValue("data-menu-item", "episodenguide").first();
        if (eg != null) {
          Elements as = eg.getElementsByAttributeValue("data-event-category", "serienmenu-episoden-staffel");
          for (Element a : as) {
            md.addExtraData(a.text().replace(" ", ""), a.attr("href")); // StaffelX
          }
        }

      } // end doc==null
    } // end inputStream
    catch (InterruptedException | InterruptedIOException e) {
      // do not swallow these Exceptions
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOGGER.debug("problem scraping: {}", e.getMessage());
      throw new ScrapeException(e);
    }

    // cast/crew page + additional
    parseCastCrewPage(md, options.getMediaType());

    return md;
  }

  private void parseCastCrewPage(MediaMetadata md, MediaType type) throws ScrapeException {
    String id = md.getIdAsString(FernsehserienMetadataProvider.ID);
    Url url;
    try {
      // cache this on disk because that may be called multiple times
      String postfix = "/cast-crew";
      if (type == MediaType.MOVIE) {
        postfix = ""; // cast is on same page
      }
      url = new OnDiskCachedUrl(FernsehserienMetadataProvider.BASE_URL + "/" + id + postfix, 1, TimeUnit.DAYS);
    }
    catch (Exception e) {
      LOGGER.debug("problem scraping: {}", e.getMessage());
      throw new ScrapeException(e);
    }

    Document doc;
    try (InputStream is = url.getInputStream()) {
      doc = Jsoup.parse(is, "UTF-8", "");
      if (doc != null) {

        for (Element cast : doc.getElementsByAttributeValue("itemprop", "actor")) {
          Person p = parsePersonWoType(cast);
          if (p != null) {
            p.setType(Person.Type.ACTOR);
            md.addCastMember(p);
          }
        }
        for (Element cast : doc.getElementsByAttributeValue("itemprop", "director")) {
          Person p = parsePersonWoType(cast);
          if (p != null) {
            p.setType(Person.Type.DIRECTOR);
            p.setRole("Regie");
            md.addCastMember(p);
          }
        }
        for (Element cast : doc.getElementsByAttributeValue("itemprop", "author")) {
          Person p = parsePersonWoType(cast);
          if (p != null) {
            p.setType(Person.Type.WRITER);
            p.setRole("Drehbuch");
            md.addCastMember(p);
          }
        }
        for (Element cast : doc.getElementsByAttributeValue("itemprop", "producer")) {
          Person p = parsePersonWoType(cast);
          if (p != null) {
            p.setType(Person.Type.PRODUCER);
            p.setRole("Produzent");
            md.addCastMember(p);
          }
        }

        for (Element company : doc.getElementsByAttributeValue("itemprop", "productionCompany")) {
          Element name = company.getElementsByAttributeValue("itemprop", "name").first();
          if (name != null) {
            md.addProductionCompany(name.text());
          }
        }

      } // end doc==null
    } // end inputStream
    catch (InterruptedException | InterruptedIOException e) {
      // do not swallow these Exceptions
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOGGER.debug("problem scraping: {}", e.getMessage());
      throw new ScrapeException(e);
    }
  }

  /**
   * parses the person HTML object. Since it can be recycled for ALL types, the type is NOT set
   * 
   * @param p
   * @return Person or NULL on (html) parsing error
   */
  private Person parsePersonWoType(Element p) {
    try {
      Person cm = new Person();
      cm.setName(p.getElementsByAttributeValue("itemprop", "name").first().text());
      Element img = p.getElementsByAttributeValue("itemprop", "image").first();
      if (img != null) {
        String imgUrl = img.text();
        cm.setThumbUrl(imgUrl);
      }
      Element profile = p.getElementsByAttributeValue("itemprop", "url").first();
      if (profile != null) {
        String href = profile.attr("href");
        cm.setProfileUrl(FernsehserienMetadataProvider.BASE_URL + href);
      }
      Element role = p.getElementsByTag("dd").first();
      if (role != null) {
        String r = role.ownText();
        r = r.replaceAll("\\(.*", "").trim();
        cm.setRole(r);
      }
      return cm;
    }
    catch (Exception e) {
      return null;
    }
  }

  // parses overview list with all episodes on single page :)
  protected List<MediaMetadata> getEpisodeList(TvShowSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("getEpisodeList(): {}", options);

    String id = options.getIdAsString(FernsehserienMetadataProvider.ID);
    if (StringUtils.isBlank(id)) {
      throw new MissingIdException(FernsehserienMetadataProvider.ID);
    }

    // look in the cache map if there is an entry
    List<MediaMetadata> episodes = EPISODE_LIST_CACHE_MAP.get(id + "_" + options.getLanguage().getLanguage());
    if (ListUtils.isNotEmpty(episodes)) {
      // cache hit!
      return episodes;
    }
    episodes = new ArrayList<>();

    // FS is nice, just having a dedicated page for whole show :)
    // just parse single page for basic EP title info...
    Url url;
    try {
      // cache this on disk because that may be called multiple times
      url = new OnDiskCachedUrl(FernsehserienMetadataProvider.BASE_URL + "/" + id + "/episodenguide", 1, TimeUnit.DAYS);
    }
    catch (Exception e) {
      LOGGER.debug("problem scraping: {}", e.getMessage());
      throw new ScrapeException(e);
    }

    Document doc;
    try (InputStream is = url.getInputStream()) {
      doc = Jsoup.parse(is, "UTF-8", "");
      if (doc != null) {

        // parse SeasonID, which need to be specified for all seasons.
        String seasonID = "";
        Element eg = doc.getElementsByAttributeValue("data-menu-item", "episodenguide").first();
        if (eg != null) {
          Element href = eg.getElementsByAttributeValue("data-event-category", "serienmenu-episoden-staffel").first();
          if (href != null) {
            seasonID = href.attr("href").substring(href.attr("href").lastIndexOf('/') + 1);
          }
        }

        for (Element season : doc.getElementsByAttributeValue("itemprop", "containsSeason")) {
          Elements span = season.getElementsByAttributeValue("itemprop", "seasonNumber");
          int s = -1;
          if (!span.isEmpty()) {
            s = MetadataUtil.parseInt(span.text(), -1);
          }
          else {
            // not found? maybe specials et all
            Element name = season.getElementsByAttributeValue("itemprop", "name").first();
            if (name != null) {
              String seasonName = name.attr("id");
              switch (seasonName) {
                case "Specials":
                  s = 0;
                  break;

                case "Spielfilm":
                case "Webisodes":
                default:
                  continue;
              }
            }
          }

          for (Element episode : season.getElementsByAttributeValue("itemprop", "episode")) {
            MediaMetadata ep = new MediaMetadata(FernsehserienMetadataProvider.ID);
            // ID
            Element button = episode.getElementsByTag("button").first();
            if (button != null) {
              ep.setId(FernsehserienMetadataProvider.ID, button.attr("data-episode-id"));
            }
            ep.setId("fs-sid", seasonID);

            Element epno = episode.getElementsByAttributeValue("itemprop", "episodeNumber").first();
            if (epno != null) {
              int e = MetadataUtil.parseInt(epno.attr("content"), -1);
              ep.setEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, s, e);
            }

            Element name = episode.getElementsByAttributeValue("itemprop", "name").first();
            if (name != null) {
              ep.setTitle(name.text());
            }

            // easier to parse table columns per index
            Elements columns = episode.getElementsByAttributeValue("role", "cell");
            Element otitle = columns.get(8);
            ep.setOriginalTitle(otitle.text());
            ep.setOriginalLanguage(otitle.attr("lang"));

            // local release date
            Element localRelease = columns.get(7);
            String localDate = localRelease.ownText(); // w/o alternative
            try {
              Date d = DateUtils.parseDate(localDate);
              ep.setReleaseDate(d);
            }
            catch (Exception e2) {
              // ignore
            }
            // original release date
            if (ep.getReleaseDate() == null) {
              Element origRelease = columns.get(9);
              String origDate = origRelease.text();
              try {
                Date od = DateUtils.parseDate(origDate);
                ep.setReleaseDate(od);
              }
              catch (Exception e2) {
                // ignore
              }
            }

            episodes.add(ep);
          } // end foreach episode
        } // end foreach season
      } // end doc==null
    } // end inputStream
    catch (InterruptedException | InterruptedIOException e) {
      // do not swallow these Exceptions
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOGGER.debug("problem scraping: {}", e.getMessage());
      throw new ScrapeException(e);
    }

    if (!episodes.isEmpty()) {
      EPISODE_LIST_CACHE_MAP.put(id + "_" + options.getLanguage().getLanguage(), episodes);
    }

    return episodes;
  }

  /*
   * Maps scraper Genres to internal TMM genres
   */
  protected MediaGenres getTmmGenre(String genre) {
    MediaGenres g = null;
    if (StringUtils.isBlank(genre)) {
      return null;
    }
    // TODO: split name by "/" and use separate
    // just activate perfect matches, let all other create a new genre...

    // @formatter:off
    switch (genre) {
      case "Abenteuer":                     g = MediaGenres.ADVENTURE;        break;
      case "Action":                        g = MediaGenres.ACTION;           break;
      case "Animation":                     g = MediaGenres.ANIMATION;        break;
      case "Anime":                         g = MediaGenres.ANIME;            break;
      case "Biographie":                    g = MediaGenres.ACTION;           break;
      case "Bumsbuster":                    g = MediaGenres.EROTIC;           break;
      case "Comedy":                        g = MediaGenres.COMEDY;           break;
      case "Corona-Special":                g = MediaGenres.DOCUMENTARY;      break;
      case "Doku-Drama":                    g = MediaGenres.DRAMA;            break;
      case "Doku-Soap":                     g = MediaGenres.SOAP;             break;
      case "Dokumentation":                 g = MediaGenres.DOCUMENTARY;      break;
      case "Drama":                         g = MediaGenres.DRAMA;            break;
      case "Eastern":                       g = MediaGenres.EASTERN;          break;
      case "Erotik":                        g = MediaGenres.EROTIC;           break;
      case "Familienfilm":                  g = MediaGenres.FAMILY;           break;
      case "Fantasy":                       g = MediaGenres.FANTASY;          break;
      case "Film noir":                     g = MediaGenres.FILM_NOIR;        break;
      case "Gangsterfilm":                  g = MediaGenres.CRIME;            break;
      case "Geschichte":                    g = MediaGenres.HISTORY;          break;
      case "Geschichtsdrama":               g = MediaGenres.HISTORY;          break;
      case "Gespräch &amp; Diskussion":     g = MediaGenres.TALK_SHOW;        break;
      case "Horror":                        g = MediaGenres.HORROR;           break;
      case "Italowestern":                  g = MediaGenres.WESTERN;          break;
      case "Kabarett":                      g = MediaGenres.COMEDY;           break;
      case "Katastrophenfilm":              g = MediaGenres.DISASTER;         break;
      case "Kinderfilm":                    g = MediaGenres.FAMILY;           break;
      case "Komödie":                       g = MediaGenres.COMEDY;           break;
      case "Kriegsfilm":                    g = MediaGenres.WAR;              break;
      case "Krimi":                         g = MediaGenres.CRIME;            break;
      case "Kurzfilm":                      g = MediaGenres.SHORT;            break;
      case "Liebesfilm":                    g = MediaGenres.ROMANCE;          break;
      case "Lifestyle &amp; Mode":          g = MediaGenres.REALITY_TV;       break;
      case "Literaturverfilmung":           g = MediaGenres.HISTORY;          break;
      case "Martial Arts":                  g = MediaGenres.ACTION;           break;
      case "Melodram":                      g = MediaGenres.DRAMA;            break;
      case "Musical":                       g = MediaGenres.MUSICAL;          break;
      case "Musik":                         g = MediaGenres.MUSIC;            break;
      case "Mystery":                       g = MediaGenres.MYSTERY;          break;
      case "Quiz":                          g = MediaGenres.GAME_SHOW;        break;
      case "Ranking-Show":                  g = MediaGenres.GAME_SHOW;        break;
      case "Ratgeber":                      g = MediaGenres.TALK_SHOW;        break;
      case "Reality":                       g = MediaGenres.REALITY_TV;       break;
      case "Road Movie":                    g = MediaGenres.ROAD_MOVIE;       break;
      case "Romantic Comedy":               g = MediaGenres.ROMANCE;          break;
      case "Satire":                        g = MediaGenres.COMEDY;           break;
      case "Science-Fiction":               g = MediaGenres.SCIENCE_FICTION;  break;
      case "Show":                          g = MediaGenres.TALK_SHOW;        break;
      case "Sketch-Comedy":                 g = MediaGenres.COMEDY;           break;
      case "Splatter":                      g = MediaGenres.HORROR;           break;
      case "Sport":                         g = MediaGenres.SPORT;            break;
      case "Stummfilm":                     g = MediaGenres.SILENT_MOVIE;     break;
      case "Talk":                          g = MediaGenres.TALK_SHOW;        break;
      case "Thriller":                      g = MediaGenres.THRILLER;         break;
      case "Tiere":                         g = MediaGenres.ANIMAL;           break;
      case "True Crime":                    g = MediaGenres.CRIME;            break;
      case "TV Movie":                      g = MediaGenres.TV_MOVIE;         break;
      case "Western":                       g = MediaGenres.WESTERN;          break;
      case "Zeichentrick":                  g = MediaGenres.ANIMATION;        break;
      // Serien
      case "Action &amp; Abenteuer":        g = MediaGenres.ACTION;           break;
      case "Animation &amp; Zeichentrick":  g = MediaGenres.ANIMATION;        break;
      case "Comedyserien":                  g = MediaGenres.COMEDY;           break;
      case "Dokumentationen &amp; Sport":   g = MediaGenres.DOCUMENTARY;      break;
      case "Dramen &amp; Soaps":            g = MediaGenres.DRAMA;            break;
      case "Familienserien":                g = MediaGenres.FAMILY;           break;
      case "Kinder &amp; Jugend":           g = MediaGenres.FAMILY;           break;
      case "Science-Fiction &amp; Fantasy": g = MediaGenres.SCIENCE_FICTION;  break;
      case "Shows, Talk &amp; Musik":       g = MediaGenres.TALK_SHOW;        break;
      // create a new one
      default:                              g = MediaGenres.getGenre(genre);
    }
    // @formatter:on

    return g;
  }
}
