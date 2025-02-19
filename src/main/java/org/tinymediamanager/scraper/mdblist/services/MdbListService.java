package org.tinymediamanager.scraper.mdblist.services;

import org.tinymediamanager.scraper.mdblist.entities.MdbMediaEntity;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface MdbListService {

  @GET("{mediaProvider}/{mediaType}/{mediaId}")
  Call<MdbMediaEntity> getMdbMediaEntity(@Path("mediaProvider") String mediaProvider, @Path("mediaType") String mediaType,
      @Path("mediaId") String mediaId);

  @GET("{mediaProvider}/{mediaType}/{mediaId}")
  Call<MdbMediaEntity> getMdbMediaEntity(@Path("mediaProvider") String mediaProvider, @Path("mediaType") String mediaType,
      @Path("mediaId") String mediaId, @Query("append_to_response") String appendToResponse);
}
