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
package org.tinymediamanager.ui.panels;

import java.awt.Cursor;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.webdav.WebDavClient;
import org.tinymediamanager.core.webdav.WebDavFile;
import org.tinymediamanager.core.webdav.WebDavSource;

import net.miginfocom.swing.MigLayout;

/**
 * The class {@link WebDavBrowserPanel} is used to browse WebDAV directories
 * 
 * @author Manuel Laggner
 */
public class WebDavBrowserPanel extends AbstractModalInputPanel {
  private static final Logger    LOGGER = LoggerFactory.getLogger(WebDavBrowserPanel.class);

  private final WebDavSource     source;
  private final JTree            tree;
  private final DefaultTreeModel treeModel;
  private final JLabel           lblStatus;

  private String                 selectedPath;
  private volatile WebDavClient  client;                                                    // volatile for thread safety

  public WebDavBrowserPanel(WebDavSource source) {
    super();
    this.source = source;

    // Increased size for better usability
    setLayout(new MigLayout("", "[850lp,grow]", "[][600lp,grow][]"));
    LOGGER.info("!!! WebDavBrowserPanel INITIALIZED with INCREASED SIZE !!!");

    // Title
    JLabel lblTitle = new JLabel(TmmResourceBundle.getString("webdav.browser.title") + ": " + source.getDisplayName());
    add(lblTitle, "cell 0 0");

    // Tree
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new WebDavTreeNode("/", "/", true));
    rootNode.add(new DefaultMutableTreeNode(TmmResourceBundle.getString("webdav.browser.loading")));
    treeModel = new DefaultTreeModel(rootNode);
    tree = new JTree(treeModel);
    tree.setRootVisible(true);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    // Lazy loading on expand
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
        if (node.getUserObject() instanceof WebDavTreeNode webDavNode && !webDavNode.isLoaded()) {
          loadChildren(node, webDavNode.getPath());
        }
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
        // nothing to do
      }
    });

    JScrollPane scrollPane = new JScrollPane(tree);
    add(scrollPane, "cell 0 1, grow");

    // Status
    lblStatus = new JLabel("");
    add(lblStatus, "cell 0 2");

    // Initialize client and load root
    initializeAndLoadRoot();
  }

  private void initializeAndLoadRoot() {
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    lblStatus.setText(TmmResourceBundle.getString("webdav.browser.loading"));

    SwingWorker<List<WebDavFile>, Void> worker = new SwingWorker<>() {
      @Override
      protected List<WebDavFile> doInBackground() throws Exception {
        client = new WebDavClient(source);
        client.connect();
        return client.list("/");
      }

      @Override
      protected void done() {
        try {
          List<WebDavFile> files = get();
          DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();
          rootNode.removeAllChildren();

          for (WebDavFile file : files) {
            if (file.isDirectory()) {
              DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new WebDavTreeNode(file.getName(), file.getPath(), true));
              childNode.add(new DefaultMutableTreeNode(TmmResourceBundle.getString("webdav.browser.loading")));
              rootNode.add(childNode);
            }
          }

          // Mark root as loaded
          if (rootNode.getUserObject() instanceof WebDavTreeNode webDavNode) {
            webDavNode.setLoaded(true);
          }

          treeModel.reload();
          lblStatus.setText("");
        }
        catch (Exception e) {
          LOGGER.error("Failed to load WebDAV root: {}", e.getMessage());
          lblStatus.setText(TmmResourceBundle.getString("webdav.connection.failed"));
          // Disconnect client on error to prevent resource leak
          if (client != null) {
            client.disconnect();
            client = null;
          }
        }
        finally {
          setCursor(Cursor.getDefaultCursor());
        }
      }
    };
    worker.execute();
  }

  private void loadChildren(DefaultMutableTreeNode parentNode, String path) {
    // Mark as loaded immediately to prevent duplicate requests
    if (parentNode.getUserObject() instanceof WebDavTreeNode webDavNode) {
      webDavNode.setLoaded(true);
    }

    SwingWorker<List<WebDavFile>, Void> worker = new SwingWorker<>() {
      @Override
      protected List<WebDavFile> doInBackground() throws Exception {
        return client.list(path);
      }

      @Override
      protected void done() {
        try {
          List<WebDavFile> files = get();
          parentNode.removeAllChildren();

          for (WebDavFile file : files) {
            if (file.isDirectory()) {
              DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new WebDavTreeNode(file.getName(), file.getPath(), true));
              childNode.add(new DefaultMutableTreeNode(TmmResourceBundle.getString("webdav.browser.loading")));
              parentNode.add(childNode);
            }
          }

          treeModel.reload(parentNode);
        }
        catch (Exception e) {
          LOGGER.error("Failed to load WebDAV directory '{}': {}", path, e.getMessage());
          // If loading failed, mark as not loaded so user can retry
          if (parentNode.getUserObject() instanceof WebDavTreeNode webDavNode) {
            webDavNode.setLoaded(false);
          }
        }
      }
    };
    worker.execute();
  }

  public String getSelectedPath() {
    return selectedPath;
  }

  @Override
  protected void onClose() {
    TreePath selectionPath = tree.getSelectionPath();
    if (selectionPath != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
      if (node.getUserObject() instanceof WebDavTreeNode webDavNode) {
        selectedPath = webDavNode.getPath();
      }
    }

    // Cleanup
    if (client != null) {
      client.disconnect();
    }

    setVisible(false);
  }

  @Override
  protected void onCancel() {
    selectedPath = null;
    if (client != null) {
      client.disconnect();
    }
    super.onCancel();
  }

  /**
   * Inner class to represent a WebDAV tree node
   */
  public static class WebDavTreeNode {
    private final String  name;
    private final String  path;
    private final boolean directory;
    private boolean       loaded = false;

    public WebDavTreeNode(String name, String path, boolean directory) {
      this.name = name;
      this.path = path;
      this.directory = directory;
    }

    public String getName() {
      return name;
    }

    public String getPath() {
      return path;
    }

    public boolean isDirectory() {
      return directory;
    }

    public boolean isLoaded() {
      return loaded;
    }

    public void setLoaded(boolean loaded) {
      this.loaded = loaded;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
