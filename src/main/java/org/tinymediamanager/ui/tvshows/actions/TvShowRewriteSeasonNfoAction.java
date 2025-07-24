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
package org.tinymediamanager.ui.tvshows.actions;

import java.awt.event.ActionEvent;
import java.util.Set;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskHandle.TaskType;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * The class {@link TvShowRewriteSeasonNfoAction}. To rewrite the NFOs of the selected seasons
 * 
 * @author Manuel Laggner
 */
public class TvShowRewriteSeasonNfoAction extends TmmAction {
  public TvShowRewriteSeasonNfoAction() {
    putValue(NAME, TmmResourceBundle.getString("tvshow.rewriteseasonnfo"));
  }

  @Override
  protected void processAction(ActionEvent e) {
    Set<TvShowSeason> selectedSeasons = TvShowUIModule.getInstance().getSelectionModel().getSelectedObjects().getSeasonsRecursive();

    if (selectedSeasons.isEmpty()) {
      return;
    }

    // rewrite selected NFOs
    TmmTaskManager.getInstance()
        .addUnnamedTask(new TmmTask(TmmResourceBundle.getString("tvshow.rewriteseasonnfo"), selectedSeasons.size(), TaskType.BACKGROUND_TASK) {
          @Override
          protected void doInBackground() {
            int i = 0;
            for (TvShowSeason season : selectedSeasons) {
              // the season
              season.writeNfo();
              season.saveToDb();
              publishState(++i);

              if (cancel) {
                break;
              }
            }
          }
        });
  }
}
