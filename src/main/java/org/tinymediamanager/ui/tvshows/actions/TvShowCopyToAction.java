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
import java.nio.file.Path;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.tasks.MediaEntityCopyTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;

/**
 * Action to copy selected TV shows to a user-specified directory.
 * <p>
 * This action allows users to select one or more TV shows and copy all associated files to a chosen folder. It uses {@link MediaEntityCopyTask} to
 * perform the copy operation in the background.
 * 
 * @author Myron Boyle
 */
public class TvShowCopyToAction extends TmmAction {

  /**
   * Constructs a new TvShowCopyToAction.
   */
  public TvShowCopyToAction() {
    putValue(NAME, TmmResourceBundle.getString("tvshow.copyto"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("tvshow.copyto.desc"));
    putValue(SMALL_ICON, IconManager.COPY);
    putValue(LARGE_ICON_KEY, IconManager.COPY);
  }

  /**
   * Processes the copy action when triggered.
   * <p>
   * Prompts the user to select a target directory and starts a background task to copy the selected TV shows.
   *
   * @param e
   *          the action event
   */
  @Override
  protected void processAction(ActionEvent e) {
    List<TvShow> selectedTvShows = TvShowUIModule.getInstance().getSelectionModel().getSelectedTvShows(true);

    if (selectedTvShows.isEmpty()) {
      JOptionPane.showMessageDialog(MainWindow.getInstance(), TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    SwingUtilities.invokeLater(() -> {
      Path folder = TmmUIHelper.selectDirectory(TmmResourceBundle.getString("tvshow.copyto"), null);
      if (folder != null) {
        MediaEntityCopyTask task = new MediaEntityCopyTask(selectedTvShows, folder);
        TmmTaskManager.getInstance().addUnnamedTask(task);
      }
    });
  }
}
