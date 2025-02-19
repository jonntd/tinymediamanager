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
package org.tinymediamanager.scraper.opensubtitles_com.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DownloadResponse {
  @JsonProperty("link")
  public String link;

  @JsonProperty("fname")
  public String filename;

  @JsonProperty("requests")
  public int    requestsConsumed;

  @Deprecated
  @JsonProperty("allowed")
  public int    requestsAllowed;

  @JsonProperty("remaining")
  public int    requestsRemaining;

  @JsonProperty("message")
  public String message;

  @JsonProperty("reset_time")
  public String resetTime;

  @JsonProperty("reset_time_utc")
  public String resetTimeUtc;

  // EXAMPLE
  // {
  // "link":
  // "https://www.opensubtitles.com/download/A184A5EA6302F2CAxxxxxxxxx7C603156926FC6C74AA1D14AABEA6E20/subfile/casstle.rook.s01e03.webrip.x264-tbs.ettv.-eng.ro.srt",
  // "file_name": "casstle.rook.s01e03.webrip.x264-tbs.ettv.-eng.ro.srt",
  // "requests": 3,
  // "remaining": 97,
  // "message": "Your quota will be renewed in 07 hours and 30 minutes (2022-04-08 13:03:16 UTC) ",
  // "reset_time": "07 hours and 30 minutes",
  // "reset_time_utc": "2022-04-08T13:03:16.000Z"
  // }
}
