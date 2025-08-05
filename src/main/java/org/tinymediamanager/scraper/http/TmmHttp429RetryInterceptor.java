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

package org.tinymediamanager.scraper.http;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * {@link Interceptor} to add the API key query parameter and if available session information. As it modifies the URL and may retry requests, ensure
 * this is added as an application interceptor (never a network interceptor), otherwise caching will be broken and requests will fail.
 */
public class TmmHttp429RetryInterceptor implements Interceptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(TmmHttp429RetryInterceptor.class);

  public Response intercept(Chain chain) throws IOException {
    return handleIntercept(chain);
  }

  public Response handleIntercept(Chain chain) throws IOException {
    Request request = chain.request();
    Response response = chain.proceed(request);

    if (!response.isSuccessful()) {
      // re-try if the server indicates we should
      String retryHeader = response.header("Retry-After");
      if (retryHeader != null) {
        try {
          int retry = Integer.parseInt(retryHeader);
          LOGGER.debug("Hold your horses! The server is asking us to wait {} seconds before retrying", retry);
          Thread.sleep((int) ((retry + 0.5) * 1000));

          // close body of unsuccessful response
          if (response.body() != null) {
            response.body().close();
          }
          // is fine because, unlike a network interceptor, an application interceptor can re-try requests
          return handleIntercept(chain);
        }
        catch (NumberFormatException | InterruptedException ignored) {
          LOGGER.warn("Invalid Retry-After header: {}", retryHeader);
        }
      }
    }

    return response;
  }
}
