package org.tinymediamanager.scraper.thesportsdb.services.v1;

import org.tinymediamanager.scraper.thesportsdb.entities.Events;
import org.tinymediamanager.scraper.thesportsdb.entities.Team;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface Schedule {

  @GET("json/{api_key}/eventsday.php")
  Call<Team> lookupTeam(@Query("id") String teamId);

  /**
   * All events in specific league by season (Free tier limited to 100 events)
   */
  @GET("json/{api_key}/eventsseason.php")
  Call<Events> getEvents(@Query("id") String leagueId, @Query("s") String seasonName);
}
