package org.tinymediamanager.scraper.mdblist;

import java.io.IOException;
import java.util.Date;

import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.http.TmmHttpClient;
import org.tinymediamanager.scraper.mdblist.entities.MdbMediaEntity;
import org.tinymediamanager.scraper.mdblist.services.MdbListService;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.internal.bind.DateTypeAdapter;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MdbListController {
  private static final String API_URL       = "https://api.mdblist.com";
  public static final String  PARAM_API_KEY = "apikey";
  private Retrofit            restAdapter;
  private String              apiKey        = "";

  public MdbListController() {
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
          .client(TmmHttpClient.newBuilder().addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
              Request request = chain.request();
              HttpUrl url = request.url().newBuilder().addQueryParameter(PARAM_API_KEY, apiKey).build();
              request = request.newBuilder().url(url).build();
              return chain.proceed(request);
            }
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
