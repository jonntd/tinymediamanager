package org.tinymediamanager.scraper.thesportsdb.services.v1;

import org.tinymediamanager.scraper.thesportsdb.entities.Leagues;
import org.tinymediamanager.scraper.thesportsdb.entities.Lineups;
import org.tinymediamanager.scraper.thesportsdb.entities.Team;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface Lookup {

  // free API returns WRONG liga - nice ^^
  @GET("json/{api_key}/lookupleague.php")
  Call<Leagues> lookupLeague(@Query("id") String leagueId);

  @GET("json/{api_key}/lookupteam.php")
  Call<Team> lookupTeam(@Query("id") String teamId);

  @GET("json/{api_key}/lookuplineup.php")
  Call<Lineups> lookupLineupForEvent(@Query("id") String eventId);
}
