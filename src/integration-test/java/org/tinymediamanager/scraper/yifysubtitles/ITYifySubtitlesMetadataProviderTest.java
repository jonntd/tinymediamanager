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

package org.tinymediamanager.scraper.yifysubtitles;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.tinymediamanager.core.BasicITest;
import org.tinymediamanager.scraper.SubtitleSearchAndScrapeOptions;
import org.tinymediamanager.scraper.SubtitleSearchResult;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.entities.MediaType;

public class ITYifySubtitlesMetadataProviderTest extends BasicITest {

  private static final YifySubtitlesProvider MP = new YifySubtitlesProvider();

  @BeforeClass
  public static void setupClass() throws Exception {
  }

  @Test
  public void testLookupByImdbId() throws Exception {
    String imdbId = "tt0103064"; // Terminator 2: Judgment Day
    SubtitleSearchAndScrapeOptions options = new SubtitleSearchAndScrapeOptions(MediaType.SUBTITLE);
    options.setLanguage(MediaLanguages.en);
    options.setImdbId(imdbId);
    List<SubtitleSearchResult> results = MP.search(options);
    for (SubtitleSearchResult result : results) {
      System.out.println(result);
    }
  }
}
