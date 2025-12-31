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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.webdav.WebDavClient;
import org.tinymediamanager.core.webdav.WebDavDataSourceHelper;
import org.tinymediamanager.core.webdav.WebDavFile;
import org.tinymediamanager.core.webdav.WebDavSource;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.components.label.LinkLabel;

import net.miginfocom.swing.MigLayout;

/**
 * A dialog for browsing WebDAV directories and displaying files. Used when clicking on a WebDAV path in the movie information panel.
 * 
 * @author jonntd
 */
public class WebDavFileBrowserDialog extends TmmDialog {
    private static final Logger        LOGGER      = LoggerFactory.getLogger(WebDavFileBrowserDialog.class);

    private final WebDavSource         source;
    private final String               initialPath;
    private final JTree                tree;
    private final DefaultTreeModel     treeModel;
    private final JTable               table;
    private final WebDavFileTableModel tableModel;
    private final JLabel               lblStatus;
    private final JLabel               lblPath;
    private final JPanel               pathPanel;
    private final JButton              btnRefresh;
    private final JButton              btnNewFolder;
    private final JButton              btnParent;

    private volatile WebDavClient      client;
    private String                     currentPath = "/";

    /**
     * Create the dialog for a given WebDAV path string
     * 
     * @param webDavPath
     *            the full WebDAV path (webdav://source-id/remote/path)
     */
    public WebDavFileBrowserDialog(String webDavPath) {
        super(MainWindow.getInstance(), TmmResourceBundle.getString("webdav.browser.title"), "webDavFileBrowser");

        // Parse the WebDAV path
        String[] parsed = WebDavDataSourceHelper.parseWebDavPath(webDavPath);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid WebDAV path: " + webDavPath);
        }

        String sourceId = parsed[0];
        this.initialPath = parsed[1];
        this.currentPath = this.initialPath;

        LOGGER.info("=== WebDavFileBrowserDialog Constructor ===");
        LOGGER.info("Input webDavPath: {}", webDavPath);
        LOGGER.info("Parsed sourceId: {}", sourceId);
        LOGGER.info("Parsed initialPath: {}", this.initialPath);
        LOGGER.info("Current path set to: {}", this.currentPath);

        // Get the WebDAV source
        this.source = WebDavDataSourceHelper.getWebDavSource(sourceId);
        if (this.source == null) {
            throw new IllegalArgumentException("WebDAV source not found: " + sourceId);
        }

        // Build the UI
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new MigLayout("", "[300lp][700lp,grow]", "[][][][500lp,grow][]"));
        getContentPane().add(contentPanel, BorderLayout.CENTER);

        // Title with source name
        JLabel lblTitle = new JLabel(TmmResourceBundle.getString("webdav.browser.title") + ": " + source.getDisplayName());
        contentPanel.add(lblTitle, "cell 0 0, span 2");

        // Toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        btnRefresh = new JButton(TmmResourceBundle.getString("webdav.browser.refresh"), IconManager.REFRESH);
        btnRefresh.addActionListener(e -> refreshCurrentDirectory());
        toolBar.add(btnRefresh);

        btnNewFolder = new JButton(TmmResourceBundle.getString("webdav.browser.newfolder"), IconManager.ADD);
        btnNewFolder.addActionListener(e -> createNewFolder());
        toolBar.add(btnNewFolder);

        btnParent = new JButton(TmmResourceBundle.getString("webdav.browser.parent"), IconManager.BACK_INV);
        btnParent.addActionListener(e -> navigateToParent());
        btnParent.setEnabled(false);
        toolBar.add(btnParent);

        contentPanel.add(toolBar, "cell 0 1, span 2");

        // Current path display with breadcrumb navigation
        pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel lblPathPrefix = new JLabel(TmmResourceBundle.getString("webdav.browser.currentpath") + " ");
        pathPanel.add(lblPathPrefix);

        lblPath = new JLabel(currentPath);
        pathPanel.add(lblPath);

        contentPanel.add(pathPanel, "cell 0 2, span 2");

        // Left panel: Directory Tree
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new WebDavTreeNode("/", "/", true));
        rootNode.add(new DefaultMutableTreeNode(TmmResourceBundle.getString("webdav.browser.loading")));
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // Tree selection listener - update table when directory is selected
        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node != null && node.getUserObject() instanceof WebDavTreeNode webDavNode) {
                LOGGER.info("Tree selection: name='{}', path='{}', isDirectory={}", webDavNode.getName(), webDavNode.getPath(),
                        webDavNode.isDirectory());
                if (webDavNode.isDirectory()) {
                    navigateToDirectory(webDavNode.getPath());
                }
            }
        });

        // Lazy loading on tree expand
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (node.getUserObject() instanceof WebDavTreeNode webDavNode && !webDavNode.isLoaded()) {
                    loadTreeChildren(node, webDavNode.getPath());
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
                // nothing to do
            }
        });

        JScrollPane treeScrollPane = new JScrollPane(tree);
        // contentPanel.add(treeScrollPane, "cell 0 3, grow");

        // Right panel: File Table
        tableModel = new WebDavFileTableModel();
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);

        // Enable drag and drop
        table.setDragEnabled(true);
        table.setDropMode(DropMode.ON);
        table.setTransferHandler(new WebDavFileTransferHandler());

        // Set column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(50); // Icon
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(300); // Name
        table.getColumnModel().getColumn(2).setPreferredWidth(100); // Size
        table.getColumnModel().getColumn(3).setPreferredWidth(150); // Modified

        // Double-click to enter directory
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        int modelRow = table.convertRowIndexToModel(row);
                        WebDavFile file = tableModel.getFileAt(modelRow);
                        if (file != null && file.isDirectory()) {
                            navigateToDirectory(file.getPath());
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        if (!table.isRowSelected(row)) {
                            table.setRowSelectionInterval(row, row);
                        }
                        showContextMenu(e);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        // contentPanel.add(scrollPane, "cell 1 3, grow");

        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, scrollPane);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerLocation(250);
        contentPanel.add(splitPane, "cell 0 3, span 2, grow");

        // Status
        lblStatus = new JLabel("");
        contentPanel.add(lblStatus, "cell 0 4, span 2");

        // Button panel
        JPanel buttonPanel = new JPanel();
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        JButton btnClose = new JButton(TmmResourceBundle.getString("Button.close"));
        btnClose.addActionListener(e -> {
            if (client != null) {
                client.disconnect();
            }
            setVisible(false);
            dispose();
        });
        buttonPanel.add(btnClose);

        // Set dialog size
        // Set dialog size
        setMinimumSize(new Dimension(900, 600));
        setPreferredSize(new Dimension(1100, 800));
        pack();
        // Force size after pack just in case
        setSize(new Dimension(1100, 800));
        setLocationRelativeTo(MainWindow.getInstance());
        LOGGER.info("!!! WebDavFileBrowserDialog RESIZED - 1100x800 !!!");

        // Initialize client and load initial directory
        initializeAndLoadDirectory(currentPath);
    }

    private void showContextMenu(MouseEvent e) {
        // 获取所有选中的文件
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }

        // 转换为模型索引并获取文件列表
        List<WebDavFile> selectedFiles = new ArrayList<>();
        for (int row : selectedRows) {
            int modelRow = table.convertRowIndexToModel(row);
            WebDavFile file = tableModel.getFileAt(modelRow);
            if (file != null) {
                selectedFiles.add(file);
            }
        }

        if (selectedFiles.isEmpty()) {
            return;
        }

        JPopupMenu popup = new JPopupMenu();

        // Refresh
        JMenuItem refreshItem = new JMenuItem(TmmResourceBundle.getString("webdav.browser.refresh"), IconManager.REFRESH);
        refreshItem.addActionListener(evt -> refreshCurrentDirectory());
        popup.add(refreshItem);

        popup.addSeparator();

        // Rename (只有选中单个文件时才启用)
        JMenuItem renameItem = new JMenuItem(TmmResourceBundle.getString("Button.rename"), IconManager.EDIT);
        if (selectedFiles.size() == 1) {
            renameItem.addActionListener(evt -> renameFile(selectedFiles.get(0)));
        }
        else {
            renameItem.setEnabled(false);
        }
        popup.add(renameItem);

        // Delete (支持多选)
        String deleteLabel = selectedFiles.size() > 1 ? TmmResourceBundle.getString("webdav.browser.delete") + " (" + selectedFiles.size() + ")"
                : TmmResourceBundle.getString("webdav.browser.delete");
        JMenuItem deleteItem = new JMenuItem(deleteLabel, IconManager.DELETE);
        deleteItem.addActionListener(evt -> deleteFiles(selectedFiles));
        popup.add(deleteItem);

        popup.show(e.getComponent(), e.getX(), e.getY());
    }

    private void initializeAndLoadDirectory(String path) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        lblStatus.setText(TmmResourceBundle.getString("webdav.browser.loading"));
        setButtonsEnabled(false);

        SwingWorker<List<WebDavFile>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<WebDavFile> doInBackground() throws Exception {
                if (client == null) {
                    client = new WebDavClient(source);
                    client.connect();
                }
                return client.list(path);
            }

            @Override
            protected void done() {
                try {
                    List<WebDavFile> files = get();

                    // Update table
                    tableModel.setFiles(files);

                    // Update tree on first load (when we just connected and root is still showing "Loading...")
                    DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeModel.getRoot();
                    if (rootNode.getChildCount() == 1
                            && rootNode.getChildAt(0).toString().equals(TmmResourceBundle.getString("webdav.browser.loading"))) {

                        // Load root directory to populate tree
                        SwingWorker<List<WebDavFile>, Void> treeWorker = new SwingWorker<>() {
                            @Override
                            protected List<WebDavFile> doInBackground() throws Exception {
                                return client.list("/");
                            }

                            @Override
                            protected void done() {
                                try {
                                    List<WebDavFile> rootFiles = get();
                                    rootNode.removeAllChildren();

                                    for (WebDavFile file : rootFiles) {
                                        if (file.isDirectory()) {
                                            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(
                                                    new WebDavTreeNode(file.getName(), file.getPath(), true));
                                            childNode.add(new DefaultMutableTreeNode(TmmResourceBundle.getString("webdav.browser.loading")));
                                            rootNode.add(childNode);
                                        }
                                    }

                                    if (rootNode.getUserObject() instanceof WebDavTreeNode webDavNode) {
                                        webDavNode.setLoaded(true);
                                    }

                                    treeModel.reload();

                                    // Expand and select current path in tree
                                    expandAndSelectPath(path);
                                }
                                catch (Exception e) {
                                    LOGGER.error("Failed to load root directory for tree: {}", e.getMessage());
                                }
                            }
                        };
                        treeWorker.execute();
                    }

                    currentPath = path;
                    updatePathDisplay(currentPath);
                    lblStatus.setText(TmmResourceBundle.getString("webdav.connection.success") + " - " + files.size() + " items");
                    btnParent.setEnabled(!currentPath.equals("/"));
                }
                catch (Exception e) {
                    LOGGER.error("Failed to load WebDAV directory '{}': {}", path, e.getMessage());
                    lblStatus.setText(TmmResourceBundle.getString("webdav.connection.failed") + ": " + e.getMessage());
                    if (client != null) {
                        client.disconnect();
                        client = null;
                    }
                }
                finally {
                    setCursor(Cursor.getDefaultCursor());
                    setButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void refreshCurrentDirectory() {
        initializeAndLoadDirectory(currentPath);
    }

    private void navigateToDirectory(String path) {
        initializeAndLoadDirectory(path);
    }

    private void navigateToParent() {
        if (currentPath.equals("/")) {
            return;
        }

        // Calculate parent path
        String parentPath = currentPath;
        if (parentPath.endsWith("/")) {
            parentPath = parentPath.substring(0, parentPath.length() - 1);
        }
        int lastSlash = parentPath.lastIndexOf('/');
        if (lastSlash > 0) {
            parentPath = parentPath.substring(0, lastSlash);
        }
        else {
            parentPath = "/";
        }

        navigateToDirectory(parentPath);
    }

    /**
     * Update the path display with clickable breadcrumb navigation
     */
    private void updatePathDisplay(String path) {
        pathPanel.removeAll();

        // Add prefix label
        JLabel lblPathPrefix = new JLabel(TmmResourceBundle.getString("webdav.browser.currentpath") + " ");
        pathPanel.add(lblPathPrefix);

        // Decode URL-encoded path
        String decodedPath = path;
        try {
            decodedPath = java.net.URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            LOGGER.warn("Failed to decode path: {}", path);
        }

        // Split path into parts
        String[] parts = decodedPath.split("/");
        StringBuilder currentPathBuilder = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }

            // Add separator
            if (currentPathBuilder.length() > 0) {
                JLabel separator = new JLabel(" / ");
                pathPanel.add(separator);
            }

            // Build the path up to this point
            currentPathBuilder.append("/").append(parts[i]);
            final String targetPath = currentPathBuilder.toString();

            // Create clickable link for this path segment
            LinkLabel linkLabel = new LinkLabel(parts[i]);
            linkLabel.addActionListener(e -> navigateToDirectory(targetPath));
            pathPanel.add(linkLabel);
        }

        // If path is root, just show "/"
        if (decodedPath.equals("/")) {
            LinkLabel rootLabel = new LinkLabel("/");
            rootLabel.addActionListener(e -> navigateToDirectory("/"));
            pathPanel.add(rootLabel);
        }

        pathPanel.revalidate();
        pathPanel.repaint();
    }

    /**
     * Expand and select the given path in the tree
     */
    private void expandAndSelectPath(String targetPath) {
        if (targetPath == null || targetPath.equals("/")) {
            return;
        }

        LOGGER.info("Expanding and selecting path in tree: {}", targetPath);

        // Decode URL-encoded path
        String decodedPath = targetPath;
        try {
            decodedPath = java.net.URLDecoder.decode(targetPath, java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            LOGGER.warn("Failed to decode path for tree selection: {}", targetPath);
        }

        // Split path into parts
        String[] parts = decodedPath.split("/");
        DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode) treeModel.getRoot();
        TreePath treePath = new TreePath(currentNode);

        // Build the full path as we go
        StringBuilder currentFullPath = new StringBuilder();

        // Navigate through the tree to find and expand the path
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            // Add this part to the current path
            currentFullPath.append("/").append(part);
            String pathToLoad = currentFullPath.toString();

            // Find child node with matching name
            boolean found = false;
            DefaultMutableTreeNode matchingNode = null;

            for (int i = 0; i < currentNode.getChildCount(); i++) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) currentNode.getChildAt(i);
                Object userObject = childNode.getUserObject();

                if (userObject instanceof WebDavTreeNode treeNode) {
                    if (treeNode.getName().equals(part)) {
                        matchingNode = childNode;
                        found = true;
                        break;
                    }
                }
            }

            if (found && matchingNode != null) {
                currentNode = matchingNode;
                treePath = treePath.pathByAddingChild(currentNode);

                // Load children FIRST if not loaded yet, BEFORE expanding
                WebDavTreeNode treeNode = (WebDavTreeNode) currentNode.getUserObject();
                if (!treeNode.isLoaded()) {
                    LOGGER.info("Loading children for node: {}", treeNode.getName());
                    loadTreeNodeSync(currentNode, pathToLoad);
                }

                // Now expand the path (children are already loaded)
                tree.expandPath(treePath);
            }
            else {
                // Path not found in tree, stop here
                LOGGER.warn("Path part not found in tree: {}", part);
                break;
            }
        }

        // Select the final node
        tree.setSelectionPath(treePath);
        tree.scrollPathToVisible(treePath);
        LOGGER.info("Tree path selected: {}", treePath);
    }

    /**
     * Load children for a tree node (synchronous)
     */
    private void loadTreeNodeSync(DefaultMutableTreeNode node, String path) {
        try {
            LOGGER.info("Synchronously loading children for path: {}", path);
            List<WebDavFile> files = client.list(path);
            node.removeAllChildren();

            for (WebDavFile file : files) {
                if (file.isDirectory()) {
                    DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new WebDavTreeNode(file.getName(), file.getPath(), true));
                    childNode.add(new DefaultMutableTreeNode(TmmResourceBundle.getString("webdav.browser.loading")));
                    node.add(childNode);
                }
            }

            if (node.getUserObject() instanceof WebDavTreeNode treeNode) {
                treeNode.setLoaded(true);
            }

            treeModel.reload(node);
            LOGGER.info("Loaded {} children for path: {}", node.getChildCount(), path);
        }
        catch (Exception e) {
            LOGGER.error("Failed to load tree node synchronously: {}", e.getMessage());
        }
    }

    /**
     * Load children for a tree node
     */
    private void loadTreeNode(DefaultMutableTreeNode node, String path) {
        SwingWorker<List<WebDavFile>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<WebDavFile> doInBackground() throws Exception {
                return client.list(path);
            }

            @Override
            protected void done() {
                try {
                    List<WebDavFile> files = get();
                    node.removeAllChildren();

                    for (WebDavFile file : files) {
                        if (file.isDirectory()) {
                            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new WebDavTreeNode(file.getName(), file.getPath(), true));
                            childNode.add(new DefaultMutableTreeNode(TmmResourceBundle.getString("webdav.browser.loading")));
                            node.add(childNode);
                        }
                    }

                    if (node.getUserObject() instanceof WebDavTreeNode treeNode) {
                        treeNode.setLoaded(true);
                    }

                    treeModel.reload(node);
                }
                catch (Exception e) {
                    LOGGER.error("Failed to load tree node: {}", e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void createNewFolder() {
        String folderName = JOptionPane.showInputDialog(this, TmmResourceBundle.getString("webdav.browser.newfolder.name"),
                TmmResourceBundle.getString("webdav.browser.newfolder"), JOptionPane.PLAIN_MESSAGE);

        if (folderName == null || folderName.trim().isEmpty()) {
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setButtonsEnabled(false);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                String newFolderPath = currentPath.endsWith("/") ? currentPath + folderName : currentPath + "/" + folderName;
                return client.createDirectory(newFolderPath);
            }

            @Override
            protected void done() {
                try {
                    Boolean success = get();
                    if (success) {
                        refreshCurrentDirectory();
                    }
                    else {
                        JOptionPane.showMessageDialog(WebDavFileBrowserDialog.this, TmmResourceBundle.getString("webdav.browser.error.create"),
                                TmmResourceBundle.getString("webdav.browser.newfolder"), JOptionPane.ERROR_MESSAGE);
                    }
                }
                catch (Exception e) {
                    LOGGER.error("Failed to create folder: {}", e.getMessage());
                    JOptionPane.showMessageDialog(WebDavFileBrowserDialog.this,
                            TmmResourceBundle.getString("webdav.browser.error.create") + ": " + e.getMessage(),
                            TmmResourceBundle.getString("webdav.browser.newfolder"), JOptionPane.ERROR_MESSAGE);
                }
                finally {
                    setCursor(Cursor.getDefaultCursor());
                    setButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void renameFile(WebDavFile file) {
        String newName = JOptionPane.showInputDialog(this, TmmResourceBundle.getString("webdav.browser.rename.name"), file.getName());

        if (newName == null || newName.trim().isEmpty() || newName.equals(file.getName())) {
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setButtonsEnabled(false);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                String parentPath = currentPath.endsWith("/") ? currentPath : currentPath + "/";
                String newPath = parentPath + newName;
                return client.move(file.getPath(), newPath);
            }

            @Override
            protected void done() {
                try {
                    Boolean success = get();
                    if (success) {
                        refreshCurrentDirectory();
                    }
                    else {
                        JOptionPane.showMessageDialog(WebDavFileBrowserDialog.this, TmmResourceBundle.getString("webdav.browser.error.rename"),
                                TmmResourceBundle.getString("Button.rename"), JOptionPane.ERROR_MESSAGE);
                    }
                }
                catch (Exception e) {
                    LOGGER.error("Failed to rename file: {}", e.getMessage());
                    JOptionPane.showMessageDialog(WebDavFileBrowserDialog.this,
                            TmmResourceBundle.getString("webdav.browser.error.rename") + ": " + e.getMessage(),
                            TmmResourceBundle.getString("Button.rename"), JOptionPane.ERROR_MESSAGE);
                }
                finally {
                    setCursor(Cursor.getDefaultCursor());
                    setButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * 删除多个文件（支持多选）
     */
    private void deleteFiles(List<WebDavFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }

        // 构建确认消息
        String message;
        if (files.size() == 1) {
            message = TmmResourceBundle.getString("webdav.browser.delete.confirm") + "\n" + files.get(0).getName();
        }
        else {
            StringBuilder sb = new StringBuilder();
            sb.append(TmmResourceBundle.getString("webdav.browser.delete.confirm"));
            sb.append("\n\n");
            int showCount = Math.min(files.size(), 5); // 最多显示5个文件名
            for (int i = 0; i < showCount; i++) {
                sb.append("• ").append(files.get(i).getName()).append("\n");
            }
            if (files.size() > 5) {
                sb.append("... 还有 ").append(files.size() - 5).append(" 个文件");
            }
            message = sb.toString();
        }

        int result = JOptionPane.showConfirmDialog(this, message, TmmResourceBundle.getString("webdav.browser.delete") + " (" + files.size() + ")",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setButtonsEnabled(false);

        SwingWorker<int[], Void> worker = new SwingWorker<>() {
            @Override
            protected int[] doInBackground() throws Exception {
                int successCount = 0;
                int failCount = 0;

                for (WebDavFile file : files) {
                    try {
                        if (client.delete(file.getPath())) {
                            successCount++;
                            LOGGER.info("成功删除: {}", file.getPath());
                        }
                        else {
                            failCount++;
                            LOGGER.warn("删除失败: {}", file.getPath());
                        }
                    }
                    catch (Exception e) {
                        failCount++;
                        LOGGER.error("删除出错 {}: {}", file.getPath(), e.getMessage());
                    }
                }

                return new int[] { successCount, failCount };
            }

            @Override
            protected void done() {
                try {
                    int[] counts = get();
                    int successCount = counts[0];
                    int failCount = counts[1];

                    refreshCurrentDirectory();

                    if (failCount > 0) {
                        String msg = String.format("删除完成：成功 %d，失败 %d", successCount, failCount);
                        JOptionPane.showMessageDialog(WebDavFileBrowserDialog.this, msg, TmmResourceBundle.getString("webdav.browser.delete"),
                                failCount == files.size() ? JOptionPane.ERROR_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    }
                }
                catch (Exception e) {
                    LOGGER.error("批量删除失败: {}", e.getMessage());
                    JOptionPane.showMessageDialog(WebDavFileBrowserDialog.this,
                            TmmResourceBundle.getString("webdav.browser.error.delete") + ": " + e.getMessage(),
                            TmmResourceBundle.getString("webdav.browser.delete"), JOptionPane.ERROR_MESSAGE);
                }
                finally {
                    setCursor(Cursor.getDefaultCursor());
                    setButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void setButtonsEnabled(boolean enabled) {
        btnRefresh.setEnabled(enabled);
        btnNewFolder.setEnabled(enabled);
        btnParent.setEnabled(enabled && !currentPath.equals("/"));
    }

    /**
     * Open a WebDAV file browser dialog for the given path
     * 
     * @param webDavPath
     *            the WebDAV path to browse
     */
    public static void openWebDavBrowser(String webDavPath) {
        try {
            WebDavFileBrowserDialog dialog = new WebDavFileBrowserDialog(webDavPath);
            dialog.setVisible(true);
        }
        catch (Exception e) {
            LOGGER.error("Failed to open WebDAV browser for path '{}': {}", webDavPath, e.getMessage());
        }
    }

    /**
     * Table model for WebDAV files
     */
    private static class WebDavFileTableModel extends AbstractTableModel {
        private final List<WebDavFile> files       = new ArrayList<>();
        private final String[]         columnNames = { "",                                                                           // Icon
                TmmResourceBundle.getString("webdav.browser.column.name"), TmmResourceBundle.getString("webdav.browser.column.size"),
                TmmResourceBundle.getString("webdav.browser.column.modified") };
        private final SimpleDateFormat dateFormat  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        public void setFiles(List<WebDavFile> files) {
            this.files.clear();
            this.files.addAll(files);
            fireTableDataChanged();
        }

        public WebDavFile getFileAt(int row) {
            if (row >= 0 && row < files.size()) {
                return files.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return files.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return javax.swing.ImageIcon.class;
            }
            return String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            WebDavFile file = files.get(rowIndex);

            switch (columnIndex) {
                case 0: // Icon
                    return file.isDirectory() ? IconManager.FOLDER_OPEN : IconManager.MOVIE;

                case 1: // Name
                    return file.getName();

                case 2: // Size
                    return file.isDirectory() ? "" : Utils.formatFileSizeForDisplay(file.getSize());

                case 3: // Modified
                    return dateFormat.format(file.getModified());

                default:
                    return "";
            }
        }
    }

    /**
     * Transfer handler for drag and drop support
     */
    private class WebDavFileTransferHandler extends javax.swing.TransferHandler {
        private static final DataFlavor WEBDAV_FILE_FLAVOR = new DataFlavor(WebDavFile.class, "WebDAV File");

        @Override
        public int getSourceActions(javax.swing.JComponent c) {
            return DnDConstants.ACTION_MOVE;
        }

        @Override
        protected Transferable createTransferable(javax.swing.JComponent c) {
            if (c instanceof JTable) {
                JTable table = (JTable) c;
                int row = table.getSelectedRow();
                if (row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    WebDavFile file = tableModel.getFileAt(modelRow);
                    if (file != null) {
                        return new WebDavFileTransferable(file);
                    }
                }
            }
            return null;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) {
                return false;
            }

            if (!support.isDataFlavorSupported(WEBDAV_FILE_FLAVOR)) {
                return false;
            }

            // Only allow drop on directories
            JTable.DropLocation dropLocation = (JTable.DropLocation) support.getDropLocation();
            int row = dropLocation.getRow();
            if (row >= 0) {
                int modelRow = table.convertRowIndexToModel(row);
                WebDavFile targetFile = tableModel.getFileAt(modelRow);
                return targetFile != null && targetFile.isDirectory();
            }

            return false;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            try {
                WebDavFile sourceFile = (WebDavFile) support.getTransferable().getTransferData(WEBDAV_FILE_FLAVOR);
                JTable.DropLocation dropLocation = (JTable.DropLocation) support.getDropLocation();
                int row = dropLocation.getRow();
                int modelRow = table.convertRowIndexToModel(row);
                WebDavFile targetDir = tableModel.getFileAt(modelRow);

                if (sourceFile == null || targetDir == null || !targetDir.isDirectory()) {
                    return false;
                }

                // Perform the move operation
                moveFileToDirectory(sourceFile, targetDir);
                return true;
            }
            catch (UnsupportedFlavorException | IOException e) {
                LOGGER.error("Failed to import drag data: {}", e.getMessage());
                return false;
            }
        }

        @Override
        protected void exportDone(javax.swing.JComponent source, Transferable data, int action) {
            // Refresh after drag operation completes
            if (action == DnDConstants.ACTION_MOVE) {
                refreshCurrentDirectory();
            }
        }

        /**
         * Transferable wrapper for WebDavFile
         */
        private class WebDavFileTransferable implements Transferable {
            private final WebDavFile file;

            public WebDavFileTransferable(WebDavFile file) {
                this.file = file;
            }

            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] { WEBDAV_FILE_FLAVOR };
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return WEBDAV_FILE_FLAVOR.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return file;
            }
        }
    }

    /**
     * Move a file to a target directory
     */
    private void moveFileToDirectory(WebDavFile sourceFile, WebDavFile targetDir) {
        // Validate: cannot move to itself
        if (sourceFile.getPath().equals(targetDir.getPath())) {
            JOptionPane.showMessageDialog(this, TmmResourceBundle.getString("webdav.browser.move.error.self"),
                    TmmResourceBundle.getString("webdav.browser.move"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validate: cannot move a folder into its own subdirectory
        if (targetDir.getPath().startsWith(sourceFile.getPath() + "/")) {
            JOptionPane.showMessageDialog(this, TmmResourceBundle.getString("webdav.browser.move.error.subdir"),
                    TmmResourceBundle.getString("webdav.browser.move"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Calculate target path
        final String targetPath;
        {
            String temp = targetDir.getPath();
            if (!temp.endsWith("/")) {
                temp += "/";
            }
            temp += sourceFile.getName();
            targetPath = temp;
        }

        // Check if source and target are the same
        if (sourceFile.getPath().equals(targetPath)) {
            LOGGER.debug("Source and target are the same, skipping move");
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setButtonsEnabled(false);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return client.move(sourceFile.getPath(), targetPath);
            }

            @Override
            protected void done() {
                try {
                    Boolean success = get();
                    if (success) {
                        refreshCurrentDirectory();
                    }
                    else {
                        JOptionPane.showMessageDialog(WebDavFileBrowserDialog.this, TmmResourceBundle.getString("webdav.browser.error.rename"),
                                TmmResourceBundle.getString("webdav.browser.move"), JOptionPane.ERROR_MESSAGE);
                    }
                }
                catch (Exception e) {
                    LOGGER.error("Failed to move file: {}", e.getMessage());
                    JOptionPane.showMessageDialog(WebDavFileBrowserDialog.this,
                            TmmResourceBundle.getString("webdav.browser.error.rename") + ": " + e.getMessage(),
                            TmmResourceBundle.getString("webdav.browser.move"), JOptionPane.ERROR_MESSAGE);
                }
                finally {
                    setCursor(Cursor.getDefaultCursor());
                    setButtonsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * Load children for a tree node
     */
    private void loadTreeChildren(DefaultMutableTreeNode parentNode, String path) {
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

                    // Only add directories to the tree
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

    /**
     * Inner class to represent a WebDAV tree node
     */
    private static class WebDavTreeNode {
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
