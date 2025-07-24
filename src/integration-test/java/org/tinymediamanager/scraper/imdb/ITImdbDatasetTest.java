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

package org.tinymediamanager.scraper.imdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.tinymediamanager.core.BasicITest;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviders;

public class ITImdbDatasetTest extends BasicITest {

  @Before
  public void setup() throws Exception {
    super.setup();
    MediaProviders.loadMediaProviders();
  }

  @Test
  public void episodes() {
    ImdbDatasetUtils u = new ImdbDatasetUtils();
    List<MediaMetadata> eps = u.getEpisodesByShowId("tt0434733"); // Sandmaennchen 9000+ EPs
    System.out.println(eps.size());
    assertThat(eps.size()).isGreaterThan(9000);
  }
}
