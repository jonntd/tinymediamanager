package org.tinymediamanager.scraper.thesportsdb.services.v1;

import org.tinymediamanager.scraper.thesportsdb.entities.Events;
import org.tinymediamanager.scraper.thesportsdb.entities.Team;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface Search {

  @GET("json/{api_key}/searchteams.php")
  Call<Team> searchTeamByName(@Query("t") String teamName);

  @GET("json/{api_key}/searchteams.php")
  Call<Team> searchTeamByCode(@Query("sname") String teamCode);

  @GET("json/{api_key}/searchfilename.php")
  Call<Events> searchEventByFilename(@Query("e") String fileName);

  @GET("json/{api_key}/searchevents.php")
  Call<Events> searchEventByName(@Query("e") String eventName);

  @GET("json/{api_key}/searchevents.php")
  Call<Events> searchEventByName(@Query("e") String eventName, @Query("s") String seasonName);
}
