/*
 * Copyright 2012 - 2025 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.scraper.mdblist;

import java.util.Date;

import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.http.TmmHttpClient;
import org.tinymediamanager.scraper.mdblist.entities.MdbMediaEntity;
import org.tinymediamanager.scraper.mdblist.services.MdbListService;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.internal.bind.DateTypeAdapter;

import okhttp3.HttpUrl;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * The class {@link MdbListController} is the controller for the MDBList metadata provider
 * 
 * @author Myron Boyle
 */
public class MdbListController {
  private static final String API_URL       = "https://api.mdblist.com";
  public static final String  PARAM_API_KEY = "apikey";
  private Retrofit            restAdapter;
  private String              apiKey        = "";

  MdbListController() {
  }

  private GsonBuilder getGsonBuilder() {
    GsonBuilder builder = new GsonBuilder().setLenient();
    // class types
    builder.registerTypeAdapter(Integer.class, (JsonDeserializer<Integer>) (json, typeOfT, context) -> {
      try {
        return json.getAsInt();
      }
      catch (NumberFormatException e) {
        return 0;
      }
    });
    builder.registerTypeAdapter(Date.class, new DateTypeAdapter());
    return builder;
  }

  protected Retrofit getRestAdapter() {
    if (restAdapter == null && !apiKey.isBlank()) {
      restAdapter = new Retrofit.Builder().baseUrl(API_URL)
          .addConverterFactory(GsonConverterFactory.create(getGsonBuilder().create()))
          .client(TmmHttpClient.newBuilder().addInterceptor(chain -> {
            Request request = chain.request();
            HttpUrl url = request.url().newBuilder().addQueryParameter(PARAM_API_KEY, apiKey).build();
            request = request.newBuilder().url(url).build();
            return chain.proceed(request);
          }).build())
          .build();
    }
    return restAdapter;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  private MdbListService getService() {
    return getRestAdapter().create(MdbListService.class);
  }

  private String getMediaTypeForQuery(MediaType mediaType) {
    String mt = "movie";
    if (mediaType == MediaType.TV_SHOW || mediaType == MediaType.TV_EPISODE) {
      mt = "show";
    }
    return mt;
  }

  /**
   * Gets the whole MDBList entity, with all ratings etc...
   * 
   * @param mediaProvider
   *          like imdb/tmdb/tvdb
   * @param mediaType
   *          movie or show
   * @param mediaId
   *          the scraper ID
   * @return
   */
  public Call<MdbMediaEntity> getMediaEntity(String mediaProvider, MediaType mediaType, String mediaId) {
    return getService().getMdbMediaEntity(mediaProvider, getMediaTypeForQuery(mediaType), mediaId);
  }
}
