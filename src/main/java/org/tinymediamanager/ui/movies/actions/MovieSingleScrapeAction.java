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
package org.tinymediamanager.ui.movies.actions;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieScraperMetadataConfig;
import org.tinymediamanager.core.movie.MovieSearchAndScrapeOptions;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.tasks.MovieScrapeTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.movies.MovieUIModule;
import org.tinymediamanager.ui.movies.dialogs.MovieChooserDialog;
import org.tinymediamanager.ui.movies.dialogs.MovieScrapeMetadataDialog;

/**
 * MovieSingleScrapeAction - does a single scrape for a movie including moviechooser popup
 * 
 * @author Manuel Laggner
 */
public class MovieSingleScrapeAction extends TmmAction {
  public MovieSingleScrapeAction() {
    putValue(NAME, TmmResourceBundle.getString("movie.scrape.selected"));
    putValue(SMALL_ICON, IconManager.SEARCH);
    putValue(LARGE_ICON_KEY, IconManager.SEARCH);
    putValue(SHORT_DESCRIPTION, TmmResourceBundle.getString("movie.scrape.selected"));
    putValue(ACCELERATOR_KEY,
        KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() + InputEvent.SHIFT_DOWN_MASK));
  }

  @Override
  protected void processAction(ActionEvent e) {
    List<Movie> selectedMovies = MovieUIModule.getInstance().getSelectionModel().getSelectedMovies();

    if (selectedMovies.isEmpty()) {
      return;
    }

    // 如果选择了多个电影，提供批量识别选项
    if (selectedMovies.size() > 1) {
      String[] options = {
        "批量识别 (推荐)",
        "逐个选择",
        "取消"
      };

      int choice = JOptionPane.showOptionDialog(
        MainWindow.getInstance(),
        "您选择了 " + selectedMovies.size() + " 个电影。\n\n" +
        "批量识别：使用 AI 自动识别所有电影标题，更快更准确\n" +
        "逐个选择：传统方式，手动选择每个电影的匹配结果",
        "选择刮削方式",
        JOptionPane.YES_NO_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0] // 默认选择批量识别
      );

      if (choice == 0) {
        // 使用批量识别
        useBatchRecognition(selectedMovies);
        return;
      } else if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
        // 取消
        return;
      }
      // choice == 1 继续使用传统方式
    }

    // 传统的逐个选择方式
    int selectedCount = selectedMovies.size();
    int index = 0;

    do {
      Movie movie = selectedMovies.get(index);
      MovieChooserDialog dialogMovieChooser = new MovieChooserDialog(movie, index, selectedCount);
      dialogMovieChooser.setVisible(true);

      if (!dialogMovieChooser.isContinueQueue()) {
        break;
      }

      if (dialogMovieChooser.isNavigateBack()) {
        index -= 1;
      }
      else {
        index += 1;
      }

    } while (index < selectedCount);
  }

  /**
   * 使用批量识别方式刮削电影
   */
  private void useBatchRecognition(List<Movie> selectedMovies) {
    MovieScrapeMetadataDialog dialog = new MovieScrapeMetadataDialog("批量识别刮削选定电影");
    dialog.setLocationRelativeTo(MainWindow.getInstance());
    dialog.setVisible(true);

    // get options from dialog
    MovieSearchAndScrapeOptions options = dialog.getMovieSearchAndScrapeOptions();
    List<MovieScraperMetadataConfig> config = dialog.getMovieScraperMetadataConfig();
    boolean overwrite = dialog.getOverwriteExistingItems();

    // do we want to scrape?
    if (dialog.shouldStartScrape()) {
      // scrape - 使用批量识别
      TmmThreadPool scrapeTask = new MovieScrapeTask(
          new MovieScrapeTask.MovieScrapeParams(selectedMovies, options, config).setDoSearch(true).setOverwriteExistingItems(overwrite));
      TmmTaskManager.getInstance().addMainTask(scrapeTask);
    }
  }
}
