package org.tinymediamanager.scraper.thesportsdb.services.v1;

import org.tinymediamanager.scraper.thesportsdb.entities.LeagueDetail;
import org.tinymediamanager.scraper.thesportsdb.entities.Team;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface Lookup {

  // free API returns WRONG liga - nice ^^
  @GET("json/{api_key}/lookupleague.php")
  Call<LeagueDetail> lookupLeague(@Query("id") String leagueId);

  @GET("json/{api_key}/lookupteam.php")
  Call<Team> lookupTeam(@Query("id") String teamId);

}
