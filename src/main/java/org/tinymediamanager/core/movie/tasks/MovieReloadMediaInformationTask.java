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
package org.tinymediamanager.core.movie.tasks;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.tasks.MediaFileInformationFetcherTask;
import org.tinymediamanager.core.threading.TmmThreadPool;

/**
 * The Class MovieReloadMediaInformationTask, to explicit reload mediainformation.
 * 
 * @author Manuel Laggner
 */
public class MovieReloadMediaInformationTask extends TmmThreadPool {
  private static final Logger LOGGER = LoggerFactory.getLogger(MovieReloadMediaInformationTask.class);

  private final List<Movie>   moviesToReload;

  public MovieReloadMediaInformationTask(List<Movie> movies) {
    super(TmmResourceBundle.getString("movie.updatemediainfo"));
    moviesToReload = new ArrayList<>(movies);
    initThreadPool(1, "reloadMI");
  }

  @Override
  protected void doInBackground() {
    try {
      // check if we should fetch video information
      if (!Settings.getInstance().isFetchVideoInfoOnUpdate()) {
        LOGGER.info("Skipping movie media information reload - fetchVideoInfoOnUpdate is disabled");
        return;
      }

      LOGGER.info("Getting MediaInfo for '{}' movies", moviesToReload.size());
      // update MediaInfo
      start();
      for (Movie m : moviesToReload) {
        if (cancel) {
          break;
        }
        for (MediaFile mf : m.getMediaFiles()) {
          submitTask(new MediaFileInformationFetcherTask(mf, m, true) {
            @Override
            public void callbackForGatheredMediainformation() {
              super.callbackForGatheredMediainformation();
              // rewrite NFO to include mediainfo data
              m.writeNFO();
            }
          });
        }
      }

      waitForCompletionOrCancel();

      LOGGER.info("Finished getting MediaInfo - took {} ms", getRuntime());
    }
    catch (Exception e) {
      LOGGER.error("Could not get MediaInfo - '{}'", e.getMessage());
      MessageManager.getInstance().pushMessage(new Message(MessageLevel.ERROR, "MediaInfo", "message.mediainfo.threadcrashed"));
    }
  }

  @Override
  public void callback(Object obj) {
    publishState((String) obj, progressDone);
  }
}
