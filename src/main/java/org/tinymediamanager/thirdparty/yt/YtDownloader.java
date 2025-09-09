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
package org.tinymediamanager.thirdparty.yt;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.license.TmmFeature;
import org.tinymediamanager.scraper.exceptions.HttpException;
import org.tinymediamanager.scraper.http.TmmHttpClient;
import org.tinymediamanager.scraper.http.Url;

import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.AbstractDownloader;
import com.github.kiulian.downloader.downloader.request.RequestWebpage;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.downloader.response.ResponseImpl;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * The {@link YtDownloader} class is used to pass the YT download operations through our HTTP client. It extends {@link YoutubeDownloader} and
 * implements {@link TmmFeature} to provide YT download functionality with tinyMediaManager's HTTP client implementation.
 *
 * @author Manuel Laggner
 */
public class YtDownloader extends YoutubeDownloader implements TmmFeature {
  private static YtDownloader instance;

  /**
   * Initializes the YtDownloader singleton instance. Creates the instance eagerly to avoid overhead in subsequent calls.
   */
  public static void init() {
    // create a singleton instance eagerly to avoid overhead
    instance = new YtDownloader();
  }

  /**
   * Gets the singleton instance of YtDownloader. If no instance exists, creates a new one.
   *
   * @return the YtDownloader singleton instance
   */
  public static YtDownloader getInstance() {
    if (instance == null) {
      init();
    }
    return instance;
  }

  /**
   * Private constructor to ensure singleton pattern. Initializes the parent class and sets up the custom downloader implementation.
   */
  private YtDownloader() {
    super();
    setDownloader(new DownloaderImpl());
  }

  /**
   * Extracts the YouTube video ID from a given URL.
   *
   * @param url
   *          the YouTube URL to extract the ID from
   * @return the extracted YouTube video ID or null if invalid
   */
  public String extractYoutubeId(String url) {
    if (StringUtils.isBlank(url)) {
      return "";
    }

    Matcher matcher = Utils.YOUTUBE_PATTERN.matcher(url);

    if (matcher.matches()) {
      return matcher.group(5);
    }

    return "";
  }

  /**
   * Internal implementation of the YouTube downloader that uses tinyMediaManager's HTTP client. Handles both GET and POST requests for downloading
   * webpage content.
   */
  class DownloaderImpl extends AbstractDownloader {
    /**
     * Downloads a webpage using either GET or POST method based on the request configuration.
     *
     * @param requestWebpage
     *          the request configuration containing URL, headers, and other parameters
     * @return a Response containing the downloaded webpage content or error information
     */
    @Override
    public Response<String> downloadWebpage(RequestWebpage requestWebpage) {
      if ("POST".equals(requestWebpage.getMethod())) {
        return post(requestWebpage);
      }
      else {
        return get(requestWebpage);
      }
    }

    /**
     * Performs a GET request to download webpage content. Handles URL parameters, headers, and retry logic.
     *
     * @param requestWebpage
     *          the request configuration
     * @return a Response containing the downloaded content or error information
     */
    private Response<String> get(RequestWebpage requestWebpage) {
      try {
        Url url = new Url(requestWebpage.getDownloadUrl().replace("{API_KEY}", getApiKey()));

        if (requestWebpage.getHeaders() != null) {
          for (Map.Entry<String, String> entry : requestWebpage.getHeaders().entrySet()) {
            url.addHeader(entry.getKey(), entry.getValue());
          }
        }

        String body;
        if (requestWebpage.getMaxRetries() != null) {
          body = new String(url.getBytesWithRetry(requestWebpage.getMaxRetries()), StandardCharsets.UTF_8);
        }
        else {
          body = new String(url.getBytes(), StandardCharsets.UTF_8);
        }
        return ResponseImpl.from(body);
      }
      catch (Exception e) {
        return ResponseImpl.error(e);
      }
    }

    /**
     * Performs a POST request to download webpage content. Handles request body, headers, and response validation.
     *
     * @param requestWebpage
     *          the request configuration
     * @return a Response containing the downloaded content or error information
     */
    private Response<String> post(RequestWebpage requestWebpage) {
      Call call = null;
      okhttp3.Response response = null;

      try {
        RequestBody body = RequestBody.create(requestWebpage.getBody(), MediaType.parse("application/json"));
        Request.Builder builder = new Request.Builder().url(requestWebpage.getDownloadUrl().replace("{API_KEY}", getApiKey())).post(body);

        if (requestWebpage.getHeaders() != null) {
          for (Map.Entry<String, String> entry : requestWebpage.getHeaders().entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
          }
        }

        Request request = builder.build();

        call = TmmHttpClient.getHttpClient().newCall(request);
        response = call.execute();
        int responseCode = response.code();
        String responseMessage = response.message();

        // log any "connection problems"
        if (responseCode < 200 || responseCode >= 400) {
          throw new HttpException(requestWebpage.getBody(), responseCode, responseMessage);
        }

        return ResponseImpl.from(response.body().string());
      }
      catch (Exception e) {
        if (call != null) {
          call.cancel();
        }
        if (response != null) {
          response.close();
        }

        return ResponseImpl.error(e);
      }
    }
  }
}
