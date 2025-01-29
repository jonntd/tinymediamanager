package org.tinymediamanager.scraper.tmdb.services;

import org.tinymediamanager.scraper.tmdb.entities.Certifications;

import retrofit2.Call;
import retrofit2.http.GET;

public interface CertificationService {

  /**
   * Get an up to date list of the officially supported movie certifications on TMDB.
   */
  @GET("certification/movie/list")
  Call<Certifications> getMovieCertification();

  /**
   * Get an up to date list of the officially supported tv certifications on TMDB.
   */
  @GET("certification/tv/list")
  Call<Certifications> getTvShowCertification();

}
