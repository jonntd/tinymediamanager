/*
 * Copyright 2012 - 2024 Manuel Laggner
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

package org.tinymediamanager.scraper.thesportsdb;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.tinymediamanager.scraper.http.TmmHttpClient;
import org.tinymediamanager.scraper.thesportsdb.services.v1.List;
import org.tinymediamanager.scraper.thesportsdb.services.v1.Lookup;
import org.tinymediamanager.scraper.thesportsdb.services.v1.Schedule;
import org.tinymediamanager.scraper.thesportsdb.services.v1.Search;
import org.tinymediamanager.scraper.thesportsdb.services.v2.All;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Helper class for easy usage of the TSDB API using retrofit.
 */
class TheSportsDbController {
  public static final String     API_HOST          = "www.thesportsdb.com";
  public static final String     API_VERSION       = "v1";
  public static final String     PATH_API_KEY      = "api_key";
  private static final String    TSDB_DATE_PATTERN = "yyyy-MM-dd";

  private final String           apiUrl;
  private final SimpleDateFormat dateFormat;
  private String                 apiKey;

  private Retrofit               retrofit;

  TheSportsDbController(String apiKey) {
    this.apiKey = apiKey;
    if (apiKey.isEmpty()) {
      this.apiKey = "3";
    }

    this.apiUrl = "https://" + API_HOST + "/api/" + API_VERSION + "/";
    this.dateFormat = new SimpleDateFormat(TSDB_DATE_PATTERN, Locale.ENGLISH);
  }

  public String getApiKey() {
    return apiKey;
  }

  /**
   * with the old free key, we get sometimes more results, which leads in better caching. <br>
   * Unfortunately, the free key sometimes responds with garbage data (inputID does not match outputID) <br>
   * so then we could try the other key ;)
   */
  public void swapFreeKey() {
    this.apiKey = this.apiKey.equals("3") ? "123" : "3";
  }

  protected Retrofit getRetrofit() {
    if (retrofit == null) {
      retrofit = new Retrofit.Builder().baseUrl(apiUrl)
          .addConverterFactory(GsonConverterFactory.create(getGsonBuilder().create()))
          .client(okHttpClient())
          .build();
    }
    return retrofit;
  }

  protected synchronized OkHttpClient okHttpClient() {
    // use the tmm internal okhttp client
    OkHttpClient.Builder builder = TmmHttpClient.newBuilderWithForcedCache(1, TimeUnit.DAYS);
    builder.connectTimeout(30, TimeUnit.SECONDS);
    builder.writeTimeout(30, TimeUnit.SECONDS);
    builder.readTimeout(30, TimeUnit.SECONDS);

    builder.addInterceptor(chain -> {
      Request originalRequest = chain.request();
      HttpUrl.Builder urlBuilder = originalRequest.url().newBuilder();
      java.util.List<String> segments = originalRequest.url().pathSegments();
      for (int i = 0; i < segments.size(); i++) {
        if ("{api_key}".equalsIgnoreCase(segments.get(i))) {
          urlBuilder.setPathSegment(i, getApiKey());
        }
      }
      Request request = originalRequest.newBuilder().url(urlBuilder.build()).build();
      return chain.proceed(request);
    });
    return builder.build();
  }

  protected GsonBuilder getGsonBuilder() {
    GsonBuilder builder = new GsonBuilder();

    // class types
    builder.registerTypeAdapter(Integer.class, (JsonDeserializer<Integer>) (json, typeOfT, context) -> json.getAsInt());
    builder.registerTypeAdapter(Date.class, (JsonDeserializer<Date>) (json, typeOfT, context) -> {
      try {
        return dateFormat.parse(json.getAsString());
      }
      catch (ParseException e) {
        // return null instead of failing (like default parser would)
        return null;
      }
    });
    return builder;
  }

  // ***** V2 *****
  All AllServiceV2() {
    return getRetrofit().create(All.class);
  }

  // ***** V1 *****
  // FIXME: weird structure on HP, combine to single class?
  List listServiceV1() {
    return getRetrofit().create(List.class);
  }

  Lookup lookupServiceV1() {
    return getRetrofit().create(Lookup.class);
  }

  Schedule ScheduleServiceV1() {
    return getRetrofit().create(Schedule.class);
  }

  Search SearchServiceV1() {
    return getRetrofit().create(Search.class);
  }
}
