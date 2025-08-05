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

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * {@link Interceptor} to add the API key query parameter and if available session information. As it modifies the URL and may retry requests, ensure
 * this is added as an application interceptor (never a network interceptor), otherwise caching will be broken and requests will fail.
 */
public class TheSportsDbInterceptor implements Interceptor {

  private final TheSportsDbController tsdbController;

  public TheSportsDbInterceptor(TheSportsDbController tsdbController) {
    this.tsdbController = tsdbController;
  }

  @Override
  public Response intercept(@Nonnull Chain chain) throws IOException {
    return handleIntercept(chain, tsdbController);
  }

  /**
   * If the host matches {@link TheSportsDbController#API_HOST} adds a query parameter with the API key.
   */
  public static Response handleIntercept(Chain chain, TheSportsDbController tsdbController) throws IOException {
    Request request = chain.request();

    if (!TheSportsDbController.API_HOST.equals(request.url().host())) {
      // do not intercept requests for other hosts
      // this allows the interceptor to be used on a shared okhttp client
      return chain.proceed(request);
    }

    // replace the API key in path
    HttpUrl.Builder urlBuilder = request.url().newBuilder();
    List<String> enc = urlBuilder.getEncodedPathSegments$okhttp();
    if (enc.contains("%7Bapi_key%7D")) {
      urlBuilder.setEncodedPathSegment(3, tsdbController.getApiKey()); // V1 is on 3rd column
    }

    Request.Builder builder = request.newBuilder();
    builder.url(urlBuilder.build());
    Response response = chain.proceed(builder.build());
    return response;
  }
}
