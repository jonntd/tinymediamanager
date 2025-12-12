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

package org.tinymediamanager.ui.dialogs;

import java.awt.BorderLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.webdav.WebDavClient;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;
import org.tinymediamanager.core.webdav.WebDavFile;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TableColumnResizer;
import org.tinymediamanager.ui.components.table.TmmTable;
import org.tinymediamanager.ui.components.table.TmmTableFormat;
import org.tinymediamanager.ui.components.table.TmmTableModel;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.swing.GlazedListsSwing;
import net.miginfocom.swing.MigLayout;

/**
 * show a dialog with all unwanted files
 *
 * @author Wolfgang Janes
 */
public class CleanUpUnwantedFilesDialog extends TmmDialog {

  private static final Logger            LOGGER = LoggerFactory.getLogger(CleanUpUnwantedFilesDialog.class);

  private final EventList<FileContainer> results;
  private final TmmTable                 table;
  private final JButton                  btnClean;
  private final JProgressBar             progressBar;
  private final JLabel                   lblProgressAction;

  public CleanUpUnwantedFilesDialog(List<? extends MediaEntity> selectedEntities) {
    super(TmmResourceBundle.getString("cleanupfiles"), "cleanupEntities");

    results = GlazedListsSwing.swingThreadProxyList(GlazedLists.threadSafeList(new BasicEventList<>()));

    {
      table = new TmmTable(new TmmTableModel<>(results, new CleanUpTableFormat()));
      JScrollPane scrollPane = new JScrollPane();
      table.configureScrollPane(scrollPane);
      getContentPane().add(scrollPane, BorderLayout.CENTER);
    }

    {
      {
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new MigLayout("", "[][grow]", "[]"));

        progressBar = new JProgressBar();
        infoPanel.add(progressBar, "cell 0 0");

        lblProgressAction = new JLabel("");
        infoPanel.add(lblProgressAction, "cell 1 0");

        setBottomInformationPanel(infoPanel);
      }
      {
        btnClean = new JButton(TmmResourceBundle.getString("Button.deleteselected"));
        btnClean.setIcon(IconManager.DELETE_INV);
        btnClean.addActionListener(arg0 -> cleanFiles(table));
        addButton(btnClean);

        JButton btnClose = new JButton(TmmResourceBundle.getString("Button.close"));
        btnClose.setIcon(IconManager.APPLY_INV);
        btnClose.addActionListener(arg0 -> setVisible(false));
        addButton(btnClose);
      }
    }

    CleanUpWorker worker = new CleanUpWorker(selectedEntities);
    worker.execute();

  }

  private static class CleanUpTableFormat extends TmmTableFormat<FileContainer> {

    public CleanUpTableFormat() {
      /*
       * filename
       */
      Column col = new Column(TmmResourceBundle.getString("metatag.filename"), "filename", FileContainer::getFileName, String.class);
      col.setCellTooltip(FileContainer::getFileName);
      addColumn(col);

      /*
       * size
       */
      col = new Column(TmmResourceBundle.getString("metatag.size"), "size", FileContainer::getFilesizeInKilobytes, String.class);
      addColumn(col);

      /*
       * extension
       */
      col = new Column(TmmResourceBundle.getString("metatag.filetype"), "type", FileContainer::getExtension, String.class);
      addColumn(col);

    }
  }

  private static class FileContainer {
    MediaEntity entity;
    Path        file;
    long        filesize;

    // WebDAV specific fields
    boolean     isWebDav    = false;
    String      webDavPath;
    String      webDavSourceId;
    String      webDavRemotePath;
    boolean     isDirectory = false;

    String getFilesizeInKilobytes() {
      if (filesize > 0) {
        DecimalFormat df = new DecimalFormat("#0.00");
        return df.format(filesize / (1000.0)) + " kB";
      }
      return "";
    }

    String getExtension() {
      if (isWebDav) {
        return FilenameUtils.getExtension(webDavRemotePath);
      }
      return FilenameUtils.getExtension(file.getFileName().toString());
    }

    String getFileName() {
      if (isWebDav) {
        try {
          return URLDecoder.decode(webDavPath, StandardCharsets.UTF_8);
        }
        catch (Exception e) {
          LOGGER.warn("Could not decode path '{}'", webDavPath);
          return webDavPath;
        }
      }
      return file.toString();
    }
  }

  private class CleanUpWorker extends SwingWorker<Void, Void> {

    private final List<? extends MediaEntity> selectedEntities;

    private CleanUpWorker(List<? extends MediaEntity> entities) {
      this.selectedEntities = entities;
    }

    @Override
    protected Void doInBackground() {
      btnClean.setEnabled(false);
      startProgressBar();

      // Get Cleanup File Types from the settings
      List<String> regexPatterns = Settings.getInstance().getCleanupFileType();
      LOGGER.info("Start cleanup of unwanted file types: {}", regexPatterns);

      selectedEntities.sort(Comparator.comparing(MediaEntity::getTitle));

      HashSet<Path> fileList = new HashSet<>();
      HashSet<String> webDavFileList = new HashSet<>();

      for (MediaEntity entity : selectedEntities) {
        String entityPath = entity.getPath();
        LOGGER.info("Processing entity: title='{}', path='{}'", entity.getTitle(), entityPath);
        boolean isWebDav = entityPath != null && WebDavDataSourceHelper.isWebDavPath(entityPath);
        LOGGER.info("isWebDav check result: {}", isWebDav);

        // Check if this is a WebDAV path
        if (isWebDav) {
          LOGGER.info("Processing WebDAV path: {}", entityPath);
          processWebDavEntity(entity, entityPath, regexPatterns, webDavFileList);
        }
        else {
          // Local file system - use standard file walker
          for (Path file : Utils.getUnknownFilesByRegex(entity.getPathNIO(), regexPatterns)) {
            if (fileList.contains(file)) {
              continue;
            }

            FileContainer fileContainer = new FileContainer();
            fileContainer.entity = entity;
            fileContainer.file = file;
            fileContainer.isWebDav = false;

            if (!Files.isDirectory(file)) {
              try {
                BasicFileAttributes attrs = Files.readAttributes(fileContainer.file, BasicFileAttributes.class);
                fileContainer.filesize = attrs.size();
              }
              catch (Exception ignored) {
                // ignored
              }
            }

            results.add(fileContainer);
            fileList.add(file);
          }
        }
      }

      return null;
    }

    private void processWebDavEntity(MediaEntity entity, String entityPath, List<String> regexPatterns, HashSet<String> webDavFileList) {
      LOGGER.info("processWebDavEntity called for path: {}", entityPath);
      try {
        // Parse the WebDAV path to get source ID and remote path
        String[] parsed = WebDavDataSourceHelper.parseWebDavPath(entityPath);
        if (parsed == null) {
          LOGGER.warn("Could not parse WebDAV path: {}", entityPath);
          return;
        }

        String sourceId = parsed[0];
        String remotePath = parsed[1];
        LOGGER.info("Parsed WebDAV path: sourceId={}, remotePath={}", sourceId, remotePath);

        // Get WebDAV source
        var source = WebDavDataSourceHelper.getWebDavSource(sourceId);
        if (source == null) {
          LOGGER.warn("WebDAV source not found: {}", sourceId);
          return;
        }
        LOGGER.info("Found WebDAV source: {}", source.getName());

        // Create WebDAV client and list files
        WebDavClient client = new WebDavClient(source);
        try {
          client.connect();
          LOGGER.info("Connected to WebDAV source, listing files at: {}", remotePath);
          List<WebDavFile> files = client.list(remotePath);
          LOGGER.info("Found {} files in WebDAV directory", files.size());

          int matchCount = 0;
          for (WebDavFile webDavFile : files) {
            String fileName = webDavFile.getName();

            // Check if file matches any regex pattern
            for (String regex : regexPatterns) {
              try {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(fileName);
                if (matcher.find()) {
                  String fullPath = webDavFile.getPath();
                  if (webDavFileList.contains(fullPath)) {
                    continue;
                  }

                  LOGGER.info("File '{}' matched pattern '{}', adding to results", fileName, regex);
                  FileContainer fileContainer = new FileContainer();
                  fileContainer.entity = entity;
                  fileContainer.webDavPath = "webdav://" + sourceId + fullPath;
                  fileContainer.webDavSourceId = sourceId;
                  fileContainer.webDavRemotePath = fullPath;
                  fileContainer.filesize = webDavFile.getSize();
                  fileContainer.isWebDav = true;
                  fileContainer.isDirectory = webDavFile.isDirectory();

                  results.add(fileContainer);
                  webDavFileList.add(fullPath);
                  matchCount++;
                  break; // File matched, no need to check other patterns
                }
              }
              catch (Exception e) {
                LOGGER.warn("Invalid regex pattern '{}': {}", regex, e.getMessage());
              }
            }
          }
          LOGGER.info("Total matched files for path '{}': {}", remotePath, matchCount);
        }
        finally {
          client.disconnect();
        }
      }
      catch (Exception e) {
        LOGGER.error("Error processing WebDAV path '{}': {}", entityPath, e.getMessage(), e);
      }
    }

    @Override
    protected void done() {
      stopProgressBar();
      SwingUtilities.invokeLater(() -> {
        btnClean.setEnabled(true);
        TableColumnResizer.adjustColumnPreferredWidths(table);
        table.getParent().invalidate();
        results.sort(Comparator.comparing(FileContainer::getFileName));
      });
    }

    private void startProgressBar() {
      SwingUtilities.invokeLater(() -> {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        lblProgressAction.setText("movie.searchunwanted");
      });
    }

    private void stopProgressBar() {
      SwingUtilities.invokeLater(() -> {
        progressBar.setVisible(false);
        progressBar.setIndeterminate(false);
        lblProgressAction.setText("");
      });
    }
  }

  private void cleanFiles(JTable table) {
    // clean selected Files and remove them from the List
    int[] rows = table.getSelectedRows();
    List<FileContainer> fileList = new ArrayList<>();

    for (int row : rows) {
      FileContainer selectedFile = results.get(row);
      try {
        fileList.add(selectedFile);

        if (selectedFile.isWebDav) {
          // Handle WebDAV file deletion
          deleteWebDavFile(selectedFile);
        }
        else {
          // Handle local file deletion
          if (Files.isDirectory(selectedFile.file)) {
            LOGGER.debug("Deleting folder - {}", selectedFile.file);
            Utils.deleteDirectoryRecursive(selectedFile.file);
          }
          else {
            MediaFile mf = new MediaFile(selectedFile.file);
            if (mf.getType() == MediaFileType.VIDEO) {
              // prevent users from doing something stupid
              continue;
            }
            LOGGER.debug("Deleting file - {}", selectedFile.file);
            Utils.deleteFileWithBackup(selectedFile.file, selectedFile.entity.getDataSource());
            if (selectedFile.entity.getMediaFiles().contains(mf)) {
              selectedFile.entity.removeFromMediaFiles(mf);
              selectedFile.entity.saveToDb();
            }
          }
        }
      }
      catch (Exception e) {
        LOGGER.error("Could not delete '{}' - '{}'", selectedFile.getFileName(), e.getMessage());
      }
    }

    results.removeAll(fileList);
  }

  private void deleteWebDavFile(FileContainer fileContainer) {
    try {
      String[] parsed = WebDavDataSourceHelper.parseWebDavPath(fileContainer.webDavPath);
      if (parsed == null) {
        LOGGER.warn("Could not parse WebDAV path for deletion: {}", fileContainer.webDavPath);
        return;
      }

      String sourceId = parsed[0];
      String remotePath = parsed[1];

      var source = WebDavDataSourceHelper.getWebDavSource(sourceId);
      if (source == null) {
        LOGGER.warn("WebDAV source not found for deletion: {}", sourceId);
        return;
      }

      WebDavClient client = new WebDavClient(source);
      try {
        client.connect();

        if (fileContainer.isDirectory) {
          LOGGER.debug("Deleting WebDAV folder - {}", fileContainer.webDavPath);
        }
        else {
          LOGGER.debug("Deleting WebDAV file - {}", fileContainer.webDavPath);
        }

        boolean success = client.delete(remotePath);
        if (!success) {
          LOGGER.warn("Failed to delete WebDAV file: {}", fileContainer.webDavPath);
        }
      }
      finally {
        client.disconnect();
      }
    }
    catch (Exception e) {
      LOGGER.error("Error deleting WebDAV file '{}': {}", fileContainer.webDavPath, e.getMessage());
    }
  }
}
