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
package org.tinymediamanager.ui.movies.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;

import org.tinymediamanager.core.AbstractModelObject;
import org.tinymediamanager.core.RenamerPreviewContainer;
import org.tinymediamanager.core.RenamerPreviewContainer.MediaFileTypeContainer;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieComparator;
import org.tinymediamanager.core.movie.MovieRenamerPreview;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.tasks.MovieRenameTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.TmmUILayoutStore;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.table.TmmTable;
import org.tinymediamanager.ui.components.table.TmmTableFormat;
import org.tinymediamanager.ui.components.table.TmmTableModel;
import org.tinymediamanager.ui.dialogs.TmmDialog;
import org.tinymediamanager.ui.renderer.MultilineTextareaCellRenderer;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.swing.DefaultEventSelectionModel;
import ca.odell.glazedlists.swing.GlazedListsSwing;
import net.miginfocom.swing.MigLayout;

/**
 * The class MovieRenamerPreviewDialog. generate a preview which movies have to be renamed.
 * 
 * @author Manuel Laggner
 */
public class MovieRenamerPreviewDialog extends TmmDialog {
  private final EventList<RenamerPreviewContainer> results;
  private final ResultSelectionModel               resultSelectionModel;
  private final EventList<MediaFileTypeContainer>  mediaFileEventList;

  /** UI components */
  private final TmmTable                           tableMovies;
  private final TmmTable                           tableMediaFiles;
  private final JLabel                             lblTitle;
  private final JLabel                             lblDatasource;
  private final JLabel                             lblFolderOld;
  private final JLabel                             lblFolderNew;
  private final JCheckBox                          cbFilter;

  private final MoviePreviewWorker                 worker;

  public MovieRenamerPreviewDialog(final List<Movie> selectedMovies) {
    super(TmmResourceBundle.getString("movie.renamerpreview"), "movieRenamerPreview");

    mediaFileEventList = GlazedLists.eventList(new ArrayList<>());

    results = GlazedListsSwing.swingThreadProxyList(GlazedLists.threadSafeList(new BasicEventList<>()));
    {
      JPanel panelContent = new JPanel();
      getContentPane().add(panelContent, BorderLayout.CENTER);
      panelContent.setLayout(new MigLayout("", "[950lp,grow]", "[600lp,grow]"));
      {
        JSplitPane splitPane = new JSplitPane();
        splitPane.setName(getName() + ".splitPane");
        TmmUILayoutStore.getInstance().install(splitPane);
        splitPane.setResizeWeight(0.3);
        panelContent.add(splitPane, "cell 0 0,grow");
        {
          TmmTableModel<RenamerPreviewContainer> tableModel = new TmmTableModel<>(GlazedListsSwing.swingThreadProxyList(results),
              new ResultTableFormat());
          tableMovies = new TmmTable(tableModel);

          DefaultEventSelectionModel<RenamerPreviewContainer> tableSelectionModel = new DefaultEventSelectionModel<>(results);
          resultSelectionModel = new ResultSelectionModel();
          tableSelectionModel.addListSelectionListener(resultSelectionModel);
          resultSelectionModel.selectedResults = tableSelectionModel.getSelected();
          tableMovies.setSelectionModel(tableSelectionModel);

          tableModel.addTableModelListener(arg0 -> {
            // select first movie if nothing is selected
            ListSelectionModel selectionModel = tableMovies.getSelectionModel();
            if (selectionModel.isSelectionEmpty() && tableModel.getRowCount() > 0) {
              selectionModel.setSelectionInterval(0, 0);
            }
            if (selectionModel.isSelectionEmpty() && tableModel.getRowCount() == 0) {
              resultSelectionModel.setSelectedResult(null);
            }
          });

          JScrollPane scrollPaneMovies = new JScrollPane();
          tableMovies.configureScrollPane(scrollPaneMovies);
          splitPane.setLeftComponent(scrollPaneMovies);
        }
        {
          JPanel panelDetails = new JPanel();
          splitPane.setRightComponent(panelDetails);
          panelDetails.setLayout(new MigLayout("", "[][][300lp,grow]", "[][][][][][][][grow]"));
          {
            lblTitle = new JLabel("");
            TmmFontHelper.changeFont(lblTitle, 1.33, Font.BOLD);
            panelDetails.add(lblTitle, "cell 0 0 3 1,growx");
          }
          {
            JLabel lblDatasourceT = new TmmLabel(TmmResourceBundle.getString("metatag.datasource"));
            panelDetails.add(lblDatasourceT, "cell 0 2");

            lblDatasource = new JLabel("");
            panelDetails.add(lblDatasource, "cell 2 2,growx,aligny center");
          }
          {
            JLabel lblFolderOldT = new TmmLabel(TmmResourceBundle.getString("renamer.oldfolder"));
            panelDetails.add(lblFolderOldT, "cell 0 4");

            lblFolderOld = new JLabel("");
            panelDetails.add(lblFolderOld, "cell 2 4,growx,aligny center");
          }
          {
            JLabel lblFolderNewT = new TmmLabel(TmmResourceBundle.getString("renamer.newfolder"));
            panelDetails.add(lblFolderNewT, "cell 0 5");

            lblFolderNew = new JLabel("");
            panelDetails.add(lblFolderNew, "cell 2 5,growx,aligny center");
          }
          {
            JPanel panelMediaFiles = new JPanel();
            panelDetails.add(panelMediaFiles, "cell 0 7 3 1,grow");
            panelMediaFiles.setLayout(new MigLayout("", "[grow][grow]", "[15px][grow]"));
            {
              tableMediaFiles = new TmmTable(
                  new TmmTableModel<>(GlazedListsSwing.swingThreadProxyList(mediaFileEventList), new RenamerTableFormat()));
              JScrollPane scrollPaneMediaFiles = new JScrollPane();
              tableMediaFiles.configureScrollPane(scrollPaneMediaFiles);
              panelMediaFiles.add(scrollPaneMediaFiles, "cell 0 1 2 1,grow");
            }
          }
        }
      }
    }
    {
      {
        cbFilter = new JCheckBox(TmmResourceBundle.getString("renamer.hideunchanged"));
        cbFilter.addActionListener(l -> resultSelectionModel.updateSelectedResult());

        JPanel bottomPanel = new JPanel(new MigLayout("", "[]", "[]"));
        bottomPanel.add(cbFilter, "cell 0 0");

        setBottomInformationPanel(bottomPanel);
      }
    }
    {
      JButton btnRename = new JButton(TmmResourceBundle.getString("Button.rename"));
      btnRename.setToolTipText(TmmResourceBundle.getString("movie.rename"));
      btnRename.addActionListener(arg0 -> {
        List<Movie> selectedMovies1 = new ArrayList<>();
        List<RenamerPreviewContainer> selectedResults = new ArrayList<>(resultSelectionModel.selectedResults);
        for (RenamerPreviewContainer result : selectedResults) {
          selectedMovies1.add((Movie) result.get());
        }

        // rename
        TmmThreadPool renameTask = new MovieRenameTask(selectedMovies1);
        TmmTaskManager.getInstance().addMainTask(renameTask);
        results.removeAll(selectedResults);
      });
      addButton(btnRename);

      JButton btnClose = new JButton(TmmResourceBundle.getString("Button.close"));
      btnClose.addActionListener(arg0 -> setVisible(false));
      addDefaultButton(btnClose);
    }

    // start calculation of the preview
    worker = new MoviePreviewWorker(selectedMovies);
    worker.execute();
  }

  @Override
  public void setVisible(boolean visible) {
    if (!visible) {
      // window closing
      if (worker != null && !worker.isDone()) {
        worker.cancel(true);
      }
    }

    super.setVisible(visible);
  }

  /**********************************************************************
   * helper classes
   *********************************************************************/
  private static class ResultTableFormat extends TmmTableFormat<RenamerPreviewContainer> {

    public ResultTableFormat() {
      /*
       * duped status
       */
      Column col = new Column("", "indicator", container -> container.renamerProblems ? IconManager.TABLE_ALERT : null, ImageIcon.class);
      col.setMinWidth(24);
      col.setMaxWidth(24);
      col.setColumnResizeable(false);
      col.setCellTooltip(container -> container.renamerProblems ? TmmResourceBundle.getString("renamer.problemfound") : null);
      addColumn(col);

      /*
       * movie title
       */
      col = new Column(TmmResourceBundle.getString("metatag.movie"), "title", container -> ((Movie) container.get()).getTitleSortable(),
          String.class);
      col.setCellTooltip(container -> ((Movie) container.get()).getTitleSortable());
      addColumn(col);
    }
  }

  private static class RenamerTableFormat extends TmmTableFormat<MediaFileTypeContainer> {
    public RenamerTableFormat() {
      /*
       * duped status
       */
      Column col = new Column("", "indicator", container -> container.duped ? IconManager.TABLE_ALERT : null, ImageIcon.class);
      col.setMinWidth(24);
      col.setMaxWidth(24);
      col.setColumnResizeable(false);
      col.setCellTooltip(container -> container.duped ? TmmResourceBundle.getString("renamer.duplicate") : null);
      addColumn(col);

      /*
       * old filename
       */
      col = new Column(TmmResourceBundle.getString("renamer.oldfiles"), "oldFilename", container -> String.join("\n", container.oldFiles),
          String.class);
      col.setCellRenderer(new MultilineTextareaCellRenderer());
      addColumn(col);

      /*
       * new filename
       */
      col = new Column(TmmResourceBundle.getString("renamer.newfiles"), "newFilename", container -> String.join("\n", container.newFiles),
          String.class);
      col.setCellRenderer(new MultilineTextareaCellRenderer());
      addColumn(col);
    }
  }

  private class MoviePreviewWorker extends SwingWorker<Void, Void> {
    private final List<Movie> moviesToProcess;

    private MoviePreviewWorker(List<Movie> movies) {
      this.moviesToProcess = new ArrayList<>(movies);
    }

    @Override
    protected Void doInBackground() {
      // sort movies
      moviesToProcess.sort(new MovieComparator());

      // do 2 independent movies have the same path after rename?
      // Usually we do not merge them, but a "downgrade to MMD" is possible, IF no files overlap
      // Since this is uncommon and not desired, show an alert for those (TBD if we want to improve that)
      Map<Path, RenamerPreviewContainer> dupeNewPath = new HashMap<>();

      // rename them
      for (Movie movie : moviesToProcess) {
        if (isCancelled()) {
          return null;
        }

        RenamerPreviewContainer container = new MovieRenamerPreview(movie).generatePreview();
        if (dupeNewPath.containsKey(container.newPath)) {
          // we have a dupe
          container.renamerProblems = true;
          RenamerPreviewContainer other = dupeNewPath.get(container.newPath);
          other.renamerProblems = true;
        }
        else {
          dupeNewPath.put(container.newPath, container);
        }

        if (container.isNeedsRename() || container.hasRenamerProblems()) {
          results.add(container);
        }
      }
      dupeNewPath.clear();

      SwingUtilities.invokeLater(() -> {
        if (results.isEmpty()) { // check has to be in here, since it needs some time to propagate
          JOptionPane.showMessageDialog(MovieRenamerPreviewDialog.this, TmmResourceBundle.getString("movie.renamerpreview.nothingtorename"));
          setVisible(false);
        }
      });

      return null;
    }
  }

  private class ResultSelectionModel extends AbstractModelObject implements ListSelectionListener {
    private final RenamerPreviewContainer emptyResult;

    private RenamerPreviewContainer       selectedResult;
    private List<RenamerPreviewContainer> selectedResults;

    ResultSelectionModel() {
      emptyResult = new RenamerPreviewContainer(new Movie());
    }

    void updateSelectedResult() {
      lblTitle.setText(((Movie) selectedResult.get()).getTitleSortable());
      lblDatasource.setText(selectedResult.get().getDataSource());

      // the empty result does not have any valid Path
      if (selectedResult != emptyResult) {
        lblFolderOld.setText(selectedResult.getOldPathRelative().toString());
        lblFolderNew.setText(selectedResult.getNewPathRelative().toString());
      }
      else {
        lblFolderOld.setText("");
        lblFolderNew.setText("");
      }

      try {
        mediaFileEventList.getReadWriteLock().writeLock().lock();
        mediaFileEventList.clear();

        // add em with duped status
        for (MediaFileTypeContainer container : selectedResult.getFiles()) {
          if (!container.duped && cbFilter.isSelected() && container.isUnchanged()) {
            continue;
          }
          mediaFileEventList.add(container);
        }
      }
      catch (Exception ignored) {
        // ignored.printStackTrace();
      }
      finally {
        mediaFileEventList.getReadWriteLock().writeLock().unlock();
      }

      // update row heights in GUI thread
      SwingUtilities.invokeLater(() -> adjustRowHeights(tableMediaFiles));
    }

    synchronized void setSelectedResult(RenamerPreviewContainer newValue) {
      if (newValue == null) {
        selectedResult = emptyResult;
      }
      else {
        selectedResult = newValue;
      }

      updateSelectedResult();
    }

    @Override
    public void valueChanged(ListSelectionEvent arg0) {
      if (arg0.getValueIsAdjusting()) {
        return;
      }

      // display first selected result
      if (!selectedResults.isEmpty() && selectedResult != selectedResults.get(0)) {
        setSelectedResult(selectedResults.get(0));
      }

      // display empty result
      if (selectedResults.isEmpty()) {
        setSelectedResult(emptyResult);
      }
    }
  }

  private void adjustRowHeights(JTable table) {
    for (int row = 0; row < table.getRowCount(); row++) {
      int maxHeight = table.getRowHeight();
      for (int column = 0; column < table.getColumnCount(); column++) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component comp = table.prepareRenderer(renderer, row, column);
        maxHeight = Math.max(maxHeight, comp.getPreferredSize().height);
      }
      table.setRowHeight(row, maxHeight);
    }
  }
}
