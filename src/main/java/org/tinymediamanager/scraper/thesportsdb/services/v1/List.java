package org.tinymediamanager.scraper.thesportsdb.services.v1;

import org.tinymediamanager.scraper.thesportsdb.entities.Countries;
import org.tinymediamanager.scraper.thesportsdb.entities.Event;
import org.tinymediamanager.scraper.thesportsdb.entities.Leagues;
import org.tinymediamanager.scraper.thesportsdb.entities.Seasons;
import org.tinymediamanager.scraper.thesportsdb.entities.Sports;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface List {

  @GET("json/{api_key}/all_countries.php")
  Call<Countries> getAllCountries();

  @GET("json/{api_key}/all_sports.php")
  Call<Sports> getAllSports();

  @GET("json/{api_key}/all_leagues.php")
  Call<Leagues> getAllLeagues();

  /**
   * List all Seasons in a League
   */
  @GET("json/{api_key}/search_all_seasons.php")
  Call<Seasons> getSeasons(@Query("id") String leagueId);

  /**
   * might return nothing
   */
  @GET("json/{api_key}/search_all_seasons.php?poster=1")
  Call<Seasons> getSeasonsWithPosters(@Query("id") String leagueId);

  /**
   * might return nothing
   */
  @GET("json/{api_key}/search_all_seasons.php?badges=1")
  Call<Seasons> getSeasonsWithBadges(@Query("id") String leagueId);

  @GET("json/{api_key}/searchevents.php")
  Call<Event> getEventByName(@Query("e") String eventName);
}
