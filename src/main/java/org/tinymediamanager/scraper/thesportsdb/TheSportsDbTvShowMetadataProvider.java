package org.tinymediamanager.scraper.thesportsdb;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.entities.MediaTrailer;
import org.tinymediamanager.core.entities.Person;
import org.tinymediamanager.core.entities.Person.Type;
import org.tinymediamanager.core.tvshow.TvShowEpisodeSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.TvShowSearchAndScrapeOptions;
import org.tinymediamanager.scraper.ArtworkSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.entities.MediaEpisodeGroup;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.HttpException;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.NothingFoundException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.ITvShowArtworkProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowMetadataProvider;
import org.tinymediamanager.scraper.thesportsdb.entities.Event;
import org.tinymediamanager.scraper.thesportsdb.entities.Events;
import org.tinymediamanager.scraper.thesportsdb.entities.LeagueDetail;
import org.tinymediamanager.scraper.thesportsdb.entities.Season;
import org.tinymediamanager.scraper.thesportsdb.entities.Seasons;
import org.tinymediamanager.scraper.util.CacheMap;
import org.tinymediamanager.scraper.util.DateUtils;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MetadataUtil;

import retrofit2.Response;

public class TheSportsDbTvShowMetadataProvider extends TheSportsDbMetadataProvider implements ITvShowMetadataProvider, ITvShowArtworkProvider {

  private static final Logger                                LOGGER                 = LoggerFactory
      .getLogger(TheSportsDbTvShowMetadataProvider.class);
  private static final CacheMap<String, List<MediaMetadata>> EPISODE_LIST_CACHE_MAP = new CacheMap<>(60, 10);

  @Override
  protected Logger getLogger() {
    return LOGGER;
  }

  @Override
  protected String getSubId() {
    return "tvshow";
  }

  @Override
  public MediaMetadata getMetadata(@NotNull TvShowSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("getMetadata(): {}", options);

    // lazy initialization of the api
    initAPI();

    MediaMetadata md = new MediaMetadata(getId());
    md.setScrapeOptions(options);
    md.addEpisodeGroup(MediaEpisodeGroup.DEFAULT_AIRED);
    String language = options.getLanguage().getLanguage().toUpperCase(Locale.ROOT);

    // do we have an id from the options?
    String leagueId = options.getIdAsString(MediaMetadata.TSDB);
    if (leagueId == null || leagueId.isEmpty()) {
      LOGGER.debug("no league id available");
      throw new MissingIdException(MediaMetadata.TSDB);
    }

    LeagueDetail league = null;

    // get show information
    LOGGER.debug("========= BEGIN TheSportsDB Scraping");
    try {
      Response<LeagueDetail> httpResponse = api.lookupServiceV1().lookupLeague(leagueId).execute();
      if (!httpResponse.isSuccessful()) {
        throw new HttpException(httpResponse.code(), httpResponse.message());
      }
      league = httpResponse.body();
    }
    catch (IOException e) {
      LOGGER.trace("could not get Main TvShow information: {}", e.getMessage());
    }
    if (league == null) {
      throw new NothingFoundException();
    }

    // special case
    // Free api key "3" ALWAYS returns league 4396 (for demo purposes i guess)
    if (!leagueId.equals(league.idLeague)) {
      // not able to scrape a "show" does not allow to scrape "episodes"
      // so we FAKE a successful scrape here for free users :/
      if (api.apiKey().equals("3")) {
        md.setTitle(options.getSearchQuery()); // return 1:1
        md.setId(MediaMetadata.TSDB, leagueId);// return 1:1
        return md;
      }
      // with a payed key, THAT should not happen!
      throw new ScrapeException("API returned garbage - cannot proceed");
    }

    md.setId(MediaMetadata.TSDB, league.idLeague);
    md.setTitle(league.strLeague);

    try {
      md.setReleaseDate(DateUtils.parseDate(league.dateFirstEvent));
    }
    catch (Exception e) {
      LOGGER.trace("could not parse releasedate: {}", e.getMessage());
    }

    String plot = league.getDescriptionForLanguage(language);
    if (StringUtils.isBlank(plot)) {
      plot = league.getDescriptionForLanguage("EN"); // at least that one should be filled...
    }
    if (StringUtils.isNotBlank(plot)) {
      md.setPlot(plot);
    }

    // we won't add null ones ;)
    md.addMediaArt(imagesToMA(MediaArtworkType.POSTER, league.strPoster));
    md.addMediaArt(imagesToMA(MediaArtworkType.BACKGROUND, league.strFanart1));
    md.addMediaArt(imagesToMA(MediaArtworkType.BACKGROUND, league.strFanart2));
    md.addMediaArt(imagesToMA(MediaArtworkType.BACKGROUND, league.strFanart3));
    md.addMediaArt(imagesToMA(MediaArtworkType.BACKGROUND, league.strFanart4));
    md.addMediaArt(imagesToMA(MediaArtworkType.BANNER, league.strBanner));
    md.addMediaArt(imagesToMA(MediaArtworkType.THUMB, league.strBadge));
    md.addMediaArt(imagesToMA(MediaArtworkType.LOGO, league.strLogo));

    return md;
  }

  @Override
  public MediaMetadata getMetadata(@NotNull TvShowEpisodeSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("getMetadata() TvShowEpisode: {}", options);

    // lazy initialization of the api
    initAPI();

    String eventId = options.getIdAsString(MediaMetadata.TSDB);
    Date aired = null;
    if (options.getMetadata() != null && options.getMetadata().getReleaseDate() != null) {
      aired = options.getMetadata().getReleaseDate();
    }
    String query = options.getSearchQuery();
    if (query.isEmpty() && options.getMetadata() != null) {
      query = options.getMetadata().getTitle();
    }

    // get episode number and season number
    // MediaEpisodeGroup episodeGroup = options.getEpisodeGroup();
    // int seasonNr = -1;
    // int episodeNr = -1;

    List<MediaMetadata> events = getEpisodeList(options.createTvShowSearchAndScrapeOptions());
    // match here in 4 distinct loops, to not accidentally match by S/EE when we have the correct id/date later on
    if (!events.isEmpty()) {
      if (eventId != null) {
        for (MediaMetadata event : events) {
          if (event.getIdAsString(MediaMetadata.TSDB).equals(eventId)) {
            LOGGER.trace("found match via ID");
            return event;
          }
        }
      }
      if (aired != null) {
        // match by DATE is our best option - unfortunately, we have to find a good match,
        // since there are multiple events a day!
        Map<Float, MediaMetadata> found = new HashMap<>();
        for (MediaMetadata event : events) {
          if (aired.equals(event.getReleaseDate())) {
            // we recalculate our score
            float score = MetadataUtil.calculateScore(query, event.getTitle());
            found.put(score, event);
          }
        }
        if (found != null && found.size() > 0) {
          // get best match!!!
          List<Float> sortedKeys = new ArrayList<Float>(found.keySet());
          Collections.sort(sortedKeys);
          Collections.reverse(sortedKeys);
          LOGGER.trace("found match via releaseDate");
          return found.get(sortedKeys.get(0));
        }
      }
      if (options.getMetadata() != null && query.length() > 0) {
        for (MediaMetadata event : events) {
          if (query.equals(event.getTitle())) {
            LOGGER.trace("found match via title");
            return event;
          }
        }
      }
      // nah, no number matching here
      // for (MediaMetadata ep : eps) {
      // MediaEpisodeNumber num = ep.getEpisodeNumber(episodeGroup);
      // if (num.episode() == episodeNr && num.season() == seasonNr) {
      // LOGGER.trace("found match via S/EE numbers");
      // return ep;
      // }
      // }
      LOGGER.trace("could not match anything from episodeList.");
    }
    else {
      // scrape direct?!
      LOGGER.trace("getEpisodeList() returned nothing...?");
    }

    // not found? Maybe we couldn't scrape episodeList (b/c of missing showId) - get direct
    throw new NothingFoundException();
  }

  @Override
  public SortedSet<MediaSearchResult> search(TvShowSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("search(): {}", options);

    // lazy initialization of the api
    initAPI();

    SortedSet<MediaSearchResult> searchResults = new TreeSet<>();

    // do we already have a TSDB leagueId (=show)?
    String leagueId = options.getIdAsString(MediaMetadata.TSDB);

    // do we NOW have a LeagueID, to get the entry direct?
    if (leagueId != null && !leagueId.isEmpty()) {
      MediaMetadata md = getMetadata(options);
      MediaSearchResult msr = new MediaSearchResult(getId(), MediaType.TV_SHOW);
      msr.mergeFrom(md);
      msr.setScore(1f);
      searchResults.add(msr);
      return searchResults;
    }

    // no ID? return all leagues here...
    TheSportsDbHelper.SPORT_LEAGUES.forEach((k, v) -> {
      MediaSearchResult msr = new MediaSearchResult(getId(), MediaType.TV_SHOW);
      msr.setTitle(k); // strLeague or alternates
      msr.setId(MediaMetadata.TSDB, v.idLeague);
      msr.calculateScore(options);
      if (msr.getScore() >= 0.75f) {
        searchResults.add(msr);
      }
    });

    return searchResults;
  }

  @Override
  public List<MediaMetadata> getEpisodeList(TvShowSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("getEpisodeList: {}", options);
    initAPI();

    // do we have an id from the options?
    String leagueId = options.getIdAsString(MediaMetadata.TSDB);
    if (leagueId == null || leagueId.isEmpty()) {
      LOGGER.debug("no league id available");
      throw new MissingIdException(MediaMetadata.TSDB);
    }

    // look in the cache map if there is an entry
    List<MediaMetadata> episodes = EPISODE_LIST_CACHE_MAP.get(leagueId + "_" + options.getLanguage().getLanguage());
    if (ListUtils.isNotEmpty(episodes)) {
      // cache hit!
      return episodes;
    }

    List<Event> eventList = new ArrayList<>();
    List<Season> seasons = null;
    try {
      // MediaMetadata show = getMetadata(options);
      Response<Seasons> response = api.listServiceV1().getSeasons(leagueId).execute();
      if (!response.isSuccessful()) {
        throw new HttpException(response.code(), response.message());
      }
      seasons = response.body().seasons;
    }
    catch (Exception e) {
      LOGGER.trace("could not get Episode information: {}", e.getMessage());
    }
    if (seasons == null) {
      throw new NothingFoundException();
    }

    // loop over all seasons, to get all (100 when FREE api) events
    for (Season season : seasons) {
      try {
        Response<Events> response = api.ScheduleServiceV1().getEvents(leagueId, season.strSeason).execute();
        if (!response.isSuccessful()) {
          throw new HttpException(response.code(), response.message());
        }
        Events events = response.body();
        if (events.events.size() == 100 && api.apiKey().equals("3")) {
          LOGGER.trace("League {} / Season {} did return 100 events. There could be more with a paid API key...", leagueId, season.strSeason);
        }
        eventList.addAll(events.events);
      }
      catch (Exception e) {
        LOGGER.trace("could not get Episode information: {}", e.getMessage());
      }
    }

    List<MediaMetadata> returnList = new ArrayList<>();
    // get the correct information
    for (Event event : eventList) {
      MediaMetadata md = new MediaMetadata(getId());
      md.setScrapeOptions(options);

      md.setId(MediaMetadata.TSDB, event.idEvent);
      md.setTitle(event.strEvent);
      md.setPlot(event.strDescriptionEN);

      int season = 0;
      int ep = 0;

      try {
        Date d = DateUtils.parseDate(event.dateEvent);
        md.setReleaseDate(d);
        LocalDate ld = DateUtils.toLocalD(d);
        md.setYear(ld.getYear());
        season = ld.getYear(); // our seasons are year based!
      }
      catch (ParseException e) {
        // ignore
      }

      ep = MetadataUtil.parseInt(event.intRound, 0);
      md.setEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, season, ep);

      if (event.strHomeTeam != null && !event.strHomeTeam.isEmpty()) {
        Person p = new Person(Type.ACTOR);
        p.setName(event.strHomeTeam);
        p.setRole("Home Team"); // FIXME: translate
        p.setThumbUrl(event.strHomeTeamBadge);
        md.addCastMember(p);
      }
      if (event.strAwayTeam != null && !event.strAwayTeam.isEmpty()) {
        Person p = new Person(Type.GUEST); // uuh, guest, nice :)
        p.setName(event.strAwayTeam);
        p.setRole("Away Team"); // FIXME: translate
        p.setThumbUrl(event.strAwayTeamBadge);
        md.addCastMember(p);
      }

      md.addCountry(event.strVenue); // well
      md.addCountry(event.strCity); // well
      md.addCountry(event.strCountry);

      if (event.strVideo != null && !event.strVideo.isEmpty()) {
        MediaTrailer mt = new MediaTrailer();
        mt.setProvider(getProviderFromUrl(event.strVideo));
        mt.setUrl(event.strVideo);
        mt.setDate(md.getReleaseDate());
        mt.setScrapedBy(MediaMetadata.TSDB);
        mt.setName(DateUtils.toLocalD(md.getReleaseDate()).toString() + " - " + event.strEvent);
        md.addTrailer(mt);
      }

      // we won't add null ones ;)
      md.addMediaArt(imagesToMA(MediaArtworkType.POSTER, event.strPoster));
      md.addMediaArt(imagesToMA(MediaArtworkType.BACKGROUND, event.strFanart));
      md.addMediaArt(imagesToMA(MediaArtworkType.THUMB, event.strThumb));
      md.addMediaArt(imagesToMA(MediaArtworkType.BANNER, event.strBanner));

      returnList.add(md);
    }

    // cache for further fast access
    if (ListUtils.isNotEmpty(returnList)) {
      EPISODE_LIST_CACHE_MAP.put(leagueId + "_" + options.getLanguage().getLanguage(), returnList);
    }
    return returnList;
  }

  /**
   * Returns the "Source" for this trailer by parsing the URL.
   *
   * @param url
   *          the url
   * @return the provider from url
   */
  private static String getProviderFromUrl(String url) {
    // FIXME: consider that as default for MediaTrailer
    // Ignore double domains like .co.at?!
    String domain = "unknown";
    try {
      URI u = new URI(url);
      if (u.getHost().matches("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")) {
        domain = u.getHost(); // matches IPv4? return that
      }
      else {
        String[] parts = u.getHost().split("\\."); // split host only - second last should be domain name only
        domain = parts[parts.length - 2]; // parts is 0-based, but length is not, so -2 gives us the second last ;)
      }
    }
    catch (Exception e) {
      // ignore
    }

    // overrides
    if (url.contains("youtu.be")) {
      domain = "youtube";
    }
    return domain;
  }

  @Override
  public List<MediaArtwork> getArtwork(ArtworkSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("getArtwork(): {}", options);

    // Original - /images/media/league/fanart/xpwsrw1421853005.jpg
    // Medium 500px - /images/media/league/fanart/xpwsrw1421853005.jpg/medium
    // Small 250px - /images/media/league/fanart/xpwsrw1421853005.jpg/small
    // Tiny 50px - /images/media/league/badge/pdd43f1610891709.png/tiny

    // lazy initialization of the api
    initAPI();

    try {
      // FIXME: season artwork? where?
      if (options.getMediaType() != MediaType.TV_SHOW && options.getMediaType() != MediaType.TV_EPISODE) {
        return Collections.emptyList();
      }
      if (options.getMediaType() == MediaType.TV_EPISODE) {
        // episode artwork has to be scraped via the metadata scraper
        TvShowEpisodeSearchAndScrapeOptions episodeSearchAndScrapeOptions = new TvShowEpisodeSearchAndScrapeOptions();
        episodeSearchAndScrapeOptions.setDataFromOtherOptions(options);
        if (options.getIds().get(MediaMetadata.TVSHOW_IDS) instanceof Map) {
          Map<String, Object> tvShowIds = (Map<String, Object>) options.getIds().get(MediaMetadata.TVSHOW_IDS);
          episodeSearchAndScrapeOptions.setTvShowIds(tvShowIds);
        }
        MediaMetadata md = getMetadata(episodeSearchAndScrapeOptions);
        return md.getMediaArt(options.getArtworkType());
      }
      else {
        TvShowSearchAndScrapeOptions op = new TvShowSearchAndScrapeOptions();
        op.setDataFromOtherOptions(options);
        return getMetadata(op).getMediaArt(options.getArtworkType());
      }
    }
    catch (MissingIdException | NothingFoundException e) {
      // no valid ID given or nothing has been found - just do nothing
      return Collections.emptyList();
    }
    catch (ScrapeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new ScrapeException(e);
    }
  }

  private MediaArtwork imagesToMA(MediaArtworkType type, String imageUrl) {
    MediaArtwork ma = null;
    try {
      if (imageUrl != null && !imageUrl.isEmpty()) {
        ma = new MediaArtwork(MediaMetadata.TSDB, type);
        ma.setOriginalUrl(imageUrl);
        ma.setPreviewUrl(imageUrl + "/small");
        // Original - /images/media/league/fanart/xpwsrw1421853005.jpg
        // Medium 500px - /images/media/league/fanart/xpwsrw1421853005.jpg/medium
        // Small 250px - /images/media/league/fanart/xpwsrw1421853005.jpg/small
        // Tiny 50px - /images/media/league/badge/pdd43f1610891709.png/tiny

        // reuse poster sizes, since our enum is the same across all types ;)
        ma.addImageSize(500, 0, imageUrl + "/medium", MediaArtwork.PosterSizes.MEDIUM.ordinal());
        ma.addImageSize(250, 0, imageUrl + "/small", MediaArtwork.PosterSizes.SMALL.ordinal());
      }
    }
    catch (Exception e) {
      return null;
    }
    return ma;
  }

}
