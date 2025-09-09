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
package org.tinymediamanager.ui.components.treetable;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeModelListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.VariableHeightLayoutCache;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.ui.components.table.TmmTableFormat;

/**
 * Default table model implementation for the TmmTreeTable component.
 * <p>
 * This model connects a {@link TreeModel} with a tabular representation, allowing tree-structured data to be displayed and edited in a table format.
 * It manages the mapping between tree nodes and table rows, supports event broadcasting for both table and tree changes, and provides utility methods
 * for column setup and tooltip handling.
 * </p>
 * <p>
 * The model delegates most table-related operations to an internal {@link ConnectorTableModel} and tree-related operations to the provided
 * {@link TreeModel}. It also maintains a layout cache and path support for efficient row-path mapping and expansion state management.
 * </p>
 *
 * @author Manuel Laggner
 */
public class TmmTreeTableModel implements ITmmTreeTableModel {
  private final TreeModel                    treeModel;
  private final ConnectorTableModel          tableModel;
  private final AbstractLayoutCache          layout;
  private final TmmTreeTableEventBroadcaster eventBroadcaster;
  private final TmmTreeTableTreePathSupport  treePathSupport;

  public TmmTreeTableModel(TreeModel treeModel, TmmTreeTableFormat tableFormat) {
    this.treeModel = treeModel;
    this.tableModel = new ConnectorTableModel(tableFormat, this);

    this.layout = new VariableHeightLayoutCache();
    this.layout.setModel(this);
    this.layout.setRootVisible(true);
    this.treePathSupport = new TmmTreeTableTreePathSupport(layout);
    this.eventBroadcaster = new TmmTreeTableEventBroadcaster(this);
    this.treePathSupport.addTreeExpansionListener(eventBroadcaster);
    this.treePathSupport.addTreeWillExpandListener(eventBroadcaster);

    this.treeModel.addTreeModelListener(eventBroadcaster);
  }

  /**
   * Returns the number of rows in the tree table.
   *
   * @return the row count
   */
  @Override
  public int getRowCount() {
    return layout.getRowCount();
  }

  /**
   * Returns the number of columns in the tree table (including the tree column).
   *
   * @return the column count
   */
  @Override
  public int getColumnCount() {
    return tableModel.getColumnCount() + 1;
  }

  /**
   * Returns the name of the specified column.
   *
   * @param columnIndex
   *          the index of the column
   * @return the column name
   */
  @Override
  public String getColumnName(int columnIndex) {
    return tableModel.getColumnName(columnIndex - 1);
  }

  /**
   * Returns the class of the specified column.
   *
   * @param columnIndex
   *          the index of the column
   * @return the column class
   */
  @Override
  public Class<?> getColumnClass(int columnIndex) {
    if (columnIndex == 0) {
      return Object.class;
    }
    else {
      return tableModel.getColumnClass(columnIndex - 1);
    }
  }

  /**
   * Checks if the specified cell is editable.
   *
   * @param rowIndex
   *          the row index
   * @param columnIndex
   *          the column index
   * @return true if the cell is editable, false otherwise
   */
  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    if (columnIndex == 0) {
      return false;
    }
    else {
      return tableModel.isCellEditable(rowIndex, columnIndex - 1);
    }
  }

  /**
   * Returns the value at the specified cell.
   *
   * @param rowIndex
   *          the row index
   * @param columnIndex
   *          the column index
   * @return the value at the cell
   */
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Object result;
    if (columnIndex == 0) {
      TreePath path = layout.getPathForRow(rowIndex);
      if (path != null) {
        result = path.getLastPathComponent();
      }
      else {
        result = null;
      }
    }
    else {
      result = (tableModel.getValueAt(rowIndex, columnIndex - 1));
    }
    return result;
  }

  /**
   * Returns the tree node at the specified row index.
   *
   * @param rowIndex
   *          the row index
   * @return the tree node, or null if not found
   */
  public DefaultMutableTreeNode getTreeNode(int rowIndex) {
    TreePath path = layout.getPathForRow(rowIndex);
    if (path != null) {
      return (DefaultMutableTreeNode) path.getLastPathComponent();
    }
    return null;
  }

  /**
   * Sets the value at the specified cell.
   *
   * @param aValue
   *          the new value
   * @param rowIndex
   *          the row index
   * @param columnIndex
   *          the column index
   */
  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (columnIndex != 0) {
      tableModel.setValueAt(aValue, rowIndex, columnIndex - 1);
    }
    else {
      setTreeValueAt(aValue, rowIndex);
    }
  }

  /**
   * Adds a TableModelListener to the model.
   *
   * @param l
   *          the listener to add
   */
  @Override
  public void addTableModelListener(TableModelListener l) {
    eventBroadcaster.addTableModelListener(l);
  }

  /**
   * Removes a TableModelListener from the model.
   *
   * @param l
   *          the listener to remove
   */
  @Override
  public void removeTableModelListener(TableModelListener l) {
    eventBroadcaster.removeTableModelListener(l);
  }

  /**
   * Returns the root of the tree model.
   *
   * @return the root object
   */
  @Override
  public Object getRoot() {
    return treeModel.getRoot();
  }

  /**
   * Returns the child of a parent at the specified index.
   *
   * @param parent
   *          the parent node
   * @param index
   *          the index of the child
   * @return the child object
   */
  @Override
  public Object getChild(Object parent, int index) {
    return treeModel.getChild(parent, index);
  }

  /**
   * Returns the number of children for the specified parent.
   *
   * @param parent
   *          the parent node
   * @return the number of children
   */
  @Override
  public int getChildCount(Object parent) {
    return treeModel.getChildCount(parent);
  }

  /**
   * Checks if the specified node is a leaf.
   *
   * @param node
   *          the node to check
   * @return true if the node is a leaf, false otherwise
   */
  @Override
  public boolean isLeaf(Object node) {
    return null != node && treeModel.isLeaf(node);
  }

  /**
   * Notifies that the value for the specified path has changed.
   *
   * @param path
   *          the tree path
   * @param newValue
   *          the new value
   */
  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    // if the model is correctly implemented, this will trigger a change event
    treeModel.valueForPathChanged(path, newValue);
  }

  /**
   * Returns the index of the specified child in the parent.
   *
   * @param parent
   *          the parent node
   * @param child
   *          the child node
   * @return the index of the child
   */
  @Override
  public int getIndexOfChild(Object parent, Object child) {
    return treeModel.getIndexOfChild(parent, child);
  }

  /**
   * Adds a TreeModelListener to the model.
   *
   * @param l
   *          the listener to add
   */
  @Override
  public void addTreeModelListener(TreeModelListener l) {
    eventBroadcaster.addTreeModelListener(l);
  }

  /**
   * Removes a TreeModelListener from the model.
   *
   * @param l
   *          the listener to remove
   */
  @Override
  public void removeTreeModelListener(TreeModelListener l) {
    eventBroadcaster.removeTreeModelListener(l);
  }

  /**
   * Sets the value for the tree at the specified row index. Can be overridden by subclasses.
   *
   * @param aValue
   *          the new value
   * @param rowIndex
   *          the row index
   */
  protected void setTreeValueAt(Object aValue, int rowIndex) {
    // do nothing here
  }

  /**
   * Returns the TreePathSupport instance for this model.
   *
   * @return the TreePathSupport
   */
  @Override
  public final TmmTreeTableTreePathSupport getTreePathSupport() {
    return treePathSupport;
  }

  /**
   * Returns the layout cache for this model.
   *
   * @return the layout cache
   */
  @Override
  public final AbstractLayoutCache getLayout() {
    return layout;
  }

  /**
   * Returns the underlying tree model.
   *
   * @return the tree model
   */
  @Override
  public TreeModel getTreeModel() {
    return treeModel;
  }

  /**
   * Returns the underlying table model.
   *
   * @return the table model
   */
  @Override
  public ConnectorTableModel getTableModel() {
    return tableModel;
  }

  /**
   * Set up the column according to the table format.
   *
   * @param column
   *          the column to be set up
   */
  public void setUpColumn(TableColumn column) {
    int columnIndex = column.getModelIndex() - 1;
    if (columnIndex < 0) {
      return;
    }

    TmmTableFormat tmmTableFormat = tableModel.getTableFormat();
    column.setIdentifier(tmmTableFormat.getColumnIdentifier(columnIndex));

    TableCellRenderer tableCellRenderer = tmmTableFormat.getCellRenderer(columnIndex);
    if (tableCellRenderer != null) {
      column.setCellRenderer(tableCellRenderer);
    }

    ImageIcon headerIcon = tmmTableFormat.getHeaderIcon(columnIndex);
    if (headerIcon != null) {
      column.setHeaderValue(headerIcon);
    }

    if (column.getHeaderRenderer() instanceof JComponent headerRenderer) {
      headerRenderer.setToolTipText(getHeaderTooltip(columnIndex + 1)); // because inside the method an explicit -1 is already done as above
    }

    column.setResizable(tmmTableFormat.getColumnResizeable(columnIndex));
    column.setMinWidth(tmmTableFormat.getMinWidth(columnIndex));
  }

  /**
   * Returns the tooltip for the specified column header.
   *
   * @param column
   *          the column index
   * @return the tooltip text
   */
  public String getHeaderTooltip(int column) {
    int columnIndex = column - 1;
    if (columnIndex < 0) {
      return null;
    }

    var tmmTableFormat = tableModel.getTableFormat();

    String tooltip;
    try {
      tooltip = tmmTableFormat.getHeaderTooltip(columnIndex);
    }
    catch (Exception e) {
      tooltip = null;
    }

    if (StringUtils.isBlank(tooltip)) {
      tooltip = tmmTableFormat.getColumnName(columnIndex);
    }

    return tooltip;
  }
}
