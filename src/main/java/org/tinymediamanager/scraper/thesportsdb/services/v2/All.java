package org.tinymediamanager.scraper.thesportsdb.services.v2;

import org.tinymediamanager.scraper.thesportsdb.entities.Countries;
import org.tinymediamanager.scraper.thesportsdb.entities.Leagues;
import org.tinymediamanager.scraper.thesportsdb.entities.Sports;

import retrofit2.Call;
import retrofit2.http.GET;

public interface All {

  @GET("json/all/countries")
  Call<Countries> getAllCountries();

  @GET("json/all/sports")
  Call<Sports> getAllSports();

  @GET("json/all/leagues")
  Call<Leagues> getAllLeagues();
}
