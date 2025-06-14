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
package org.tinymediamanager.ui.movies.actions;

import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.tasks.MediaEntityCopyTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.movies.MovieUIModule;

/**
 * Action to copy selected movies to a user-specified directory.
 * <p>
 * This action allows users to select one or more movies and copy all associated files to a chosen folder. It uses {@link MediaEntityCopyTask} to
 * perform the copy operation in the background.
 * 
 * @author Myron Boyle
 */
public class MovieCopyToAction extends TmmAction {

  /**
   * Constructs a new MovieCopyToAction.
   */
  public MovieCopyToAction() {
    putValue(NAME, TmmResourceBundle.getString("movie.copyto"));
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("movie.copyto.desc"));
    putValue(SMALL_ICON, IconManager.COPY);
    putValue(LARGE_ICON_KEY, IconManager.COPY);
  }

  /**
   * Processes the copy action when triggered.
   * <p>
   * Prompts the user to select a target directory and starts a background task to copy the selected movies.
   *
   * @param e
   *          the action event
   */
  @Override
  protected void processAction(ActionEvent e) {
    List<Movie> selectedMovies = MovieUIModule.getInstance().getSelectionModel().getSelectedMovies(true);

    if (selectedMovies.isEmpty()) {
      JOptionPane.showMessageDialog(MainWindow.getInstance(), TmmResourceBundle.getString("tmm.nothingselected"));
      return;
    }

    SwingUtilities.invokeLater(() -> {
      Path folder = TmmUIHelper.selectDirectory(TmmResourceBundle.getString("movie.copyto"), null);
      if (folder != null) {
        MediaEntityCopyTask task = new MediaEntityCopyTask(selectedMovies, folder);
        TmmTaskManager.getInstance().addUnnamedTask(task);
      }
    });
  }
}
