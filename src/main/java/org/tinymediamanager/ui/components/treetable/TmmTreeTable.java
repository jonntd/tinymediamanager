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

import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.event.TreeModelEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.tree.AbstractLayoutCache;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.jetbrains.annotations.NotNull;
import org.tinymediamanager.core.AbstractSettings;
import org.tinymediamanager.ui.ITmmUIFilter;
import org.tinymediamanager.ui.components.table.TmmTable;
import org.tinymediamanager.ui.components.table.TmmTableColumnModel;
import org.tinymediamanager.ui.components.table.TmmTableFormat;
import org.tinymediamanager.ui.components.tree.ITmmTreeFilter;
import org.tinymediamanager.ui.components.tree.TmmTreeDataProvider;
import org.tinymediamanager.ui.components.tree.TmmTreeModel;
import org.tinymediamanager.ui.components.tree.TmmTreeNode;

/**
 * The {@code TmmTreeTable} class provides a combination of a tree and a table, allowing hierarchical data to be displayed and interacted with in a
 * tabular format. It supports filtering, sorting, and custom rendering of tree nodes within a table structure.
 *
 * @author Manuel Laggner
 */
public class TmmTreeTable extends TmmTable {

  protected final TmmTreeDataProvider<TmmTreeNode> dataProvider;
  protected final Set<ITmmTreeFilter<TmmTreeNode>> treeFilters;

  protected TmmTreeTableRenderDataProvider         renderDataProvider = null;
  protected int                                    selectedRow        = -1;
  protected Boolean                                cachedRootVisible  = true;
  protected ITmmTreeTableModel                     treeTableModel;
  protected PropertyChangeListener                 filterChangeListener;

  private int[]                                    lastEditPosition;

  /**
   * Constructs a new {@code TmmTreeTable} with the given data provider and table format.
   *
   * @param dataProvider
   *          the data provider for the tree nodes
   * @param tableFormat
   *          the table format for the tree table
   */
  public TmmTreeTable(TmmTreeDataProvider<TmmTreeNode> dataProvider, TmmTreeTableFormat<TmmTreeNode> tableFormat) {
    this.dataProvider = dataProvider;
    this.treeFilters = new CopyOnWriteArraySet<>();
    this.treeTableModel = new TmmTreeTableModel(new TmmTreeModelConnector<>(dataProvider), tableFormat);
    this.filterChangeListener = evt -> {
      updateFiltering();
      storeFilters();
    };
    setModel(treeTableModel);
    initTreeTable();
  }

  /**
   * Adds a column to the tree table.
   *
   * @param aColumn
   *          the column to add
   */
  @Override
  public void addColumn(TableColumn aColumn) {
    if (aColumn.getIdentifier() == null && getModel() instanceof TmmTreeTableModel) {
      aColumn.setHeaderRenderer(new SortableIconHeaderRenderer());

      TmmTreeTableModel tableModel = ((TmmTreeTableModel) getModel());
      tableModel.setUpColumn(aColumn);
    }
    super.addColumn(aColumn);
  }

  /**
   * Initializes the tree table, setting up selection and header properties.
   */
  protected void initTreeTable() {
    getSelectionModel().addListSelectionListener(e -> {
      if (getSelectedRowCount() == 1) {
        selectedRow = getSelectedRow();
      }
      else {
        selectedRow = -1;
      }
    });

    // setTableHeader(createTableHeader());
    getTableHeader().setReorderingAllowed(false);
    // getTableHeader().setOpaque(false);
    // setOpaque(false);
    // turn off grid painting as we'll handle this manually in order to paint grid lines over the entire viewport.
    setShowGrid(false);

    // install the keyadapter for navigation
    addKeyListener(new TmmTreeTableKeyAdapter(this));
  }

  /**
   * Returns the cell renderer for the specified cell.
   *
   * @param row
   *          the row index
   * @param column
   *          the column index
   * @return the TableCellRenderer for the cell
   */
  @Override
  public TableCellRenderer getCellRenderer(int row, int column) {
    int c = convertColumnIndexToModel(column);
    TableCellRenderer result;
    if (c == 0) {
      TableColumn tableColumn = getColumnModel().getColumn(column);
      TableCellRenderer renderer = tableColumn.getCellRenderer();
      if (renderer == null) {
        result = getDefaultRenderer(Object.class);
      }
      else {
        result = renderer;
      }
    }
    else {
      result = super.getCellRenderer(row, column);
    }
    return result;
  }

  /**
   * Get the RenderDataProvider which is providing text, icons and tooltips for items in the tree column. The default property for this value is null,
   * in which case standard JTable/JTree object -> icon/string conventions are used
   */
  public TmmTreeTableRenderDataProvider getRenderDataProvider() {
    return renderDataProvider;
  }

  /**
   * Set the RenderDataProvider which will provide text, icons and tooltips for items in the tree column. The default is null. If null, the data
   * displayed will be generated in the standard JTable/JTree way - calling <code>toString()</code> on objects in the tree model and using the look
   * and feel's default tree folder and tree leaf icons.
   */
  public void setRenderDataProvider(TmmTreeTableRenderDataProvider provider) {
    if (provider != renderDataProvider) {
      TmmTreeTableRenderDataProvider old = renderDataProvider;
      renderDataProvider = provider;
      firePropertyChange("renderDataProvider", old, provider);
    }
  }

  /**
   * Get the TreePathSupport object which manages path expansion for this Treetable
   */
  TmmTreeTableTreePathSupport getTreePathSupport() {
    TmmTreeTableModel mdl = getTreeTableModel();
    if (mdl != null) {
      return mdl.getTreePathSupport();
    }
    else {
      return null;
    }
  }

  /**
   * Returns the tree table model used by this table.
   *
   * @return the TmmTreeTableModel instance or null if not available
   */
  public TmmTreeTableModel getTreeTableModel() {
    TableModel mdl = getModel();
    if (mdl instanceof TmmTreeTableModel) {
      return (TmmTreeTableModel) getModel();
    }
    else {
      return null;
    }
  }

  /**
   * Sets the default hidden columns based on the table format.
   */
  @Override
  public void setDefaultHiddenColumns() {
    if (getColumnModel() instanceof TmmTableColumnModel && getModel() instanceof TmmTreeTableModel) {
      TmmTreeTableModel tableModel = (TmmTreeTableModel) getModel();
      TmmTableFormat<TmmTreeNode> tableFormat = tableModel.getTableModel().getTableFormat();

      List<String> hiddenColumns = new ArrayList<>();

      for (int i = 0; i < tableFormat.getColumnCount(); i++) {
        if (tableFormat.isColumnDefaultHidden(i)) {
          hiddenColumns.add(tableFormat.getColumnIdentifier(i));
        }
      }

      readHiddenColumns(hiddenColumns);
    }
  }

  /**
   * Returns whether the column configurator should be used for this table.
   *
   * @return true if the model is a TmmTreeTableModel, false otherwise
   */
  @Override
  protected boolean useColumnConfigurator() {
    return getModel() instanceof TmmTreeTableModel;
  }

  /**
   * Returns the tree node at the specified row.
   *
   * @param row
   *          the row index
   * @return the DefaultMutableTreeNode at the given row
   */
  public DefaultMutableTreeNode getTreeNode(int row) {
    return getTreeTableModel().getTreeNode(row);
  }

  /**
   * Expands the tree node at the specified row.
   *
   * @param row
   *          the row index
   */
  public void expandRow(int row) {
    expandPath(getRowPath(row));
  }

  /**
   * Collapses the tree node at the specified row.
   *
   * @param row
   *          the row index
   */
  public void collapseRow(int row) {
    collapsePath(getRowPath(row));
  }

  /**
   * Expands the tree node at the specified path.
   *
   * @param path
   *          the TreePath to expand
   */
  public void expandPath(TreePath path) {
    getTreePathSupport().expandPath(path);
  }

  /**
   * Checks if the tree node at the specified path is expanded.
   *
   * @param path
   *          the TreePath to check
   * @return true if expanded, false otherwise
   */
  public boolean isExpanded(TreePath path) {
    return getTreePathSupport().isExpanded(path);
  }

  /**
   * Checks if the tree node at the specified row is expanded.
   *
   * @param row
   *          the row index
   * @return true if expanded, false otherwise
   */
  public boolean isExpanded(int row) {
    return isExpanded(getRowPath(row));
  }

  /**
   * Checks if the tree node at the specified row is collapsed.
   *
   * @param row
   *          the row index
   * @return true if collapsed, false otherwise
   */
  public boolean isCollapsed(int row) {
    return !isExpanded(row);
  }

  /**
   * Checks if the tree node at the specified path is a leaf.
   *
   * @param path
   *          the TreePath to check
   * @return true if the node is a leaf, false otherwise
   */
  public boolean isLeaf(TreePath path) {
    return getTreePathSupport().isLeaf(path);
  }

  /**
   * Checks if the tree node at the specified row is a leaf.
   *
   * @param row
   *          the row index
   * @return true if the node is a leaf, false otherwise
   */
  public boolean isLeaf(int row) {
    return isLeaf(getRowPath(row));
  }

  /**
   * Checks if the tree node at the specified row is a branch (not a leaf).
   *
   * @param row
   *          the row index
   * @return true if the node is a branch, false otherwise
   */
  public boolean isBranch(int row) {
    return !isLeaf(row);
  }

  /**
   * Collapses the tree node at the specified path.
   *
   * @param path
   *          the TreePath to collapse
   */
  public void collapsePath(TreePath path) {
    getTreePathSupport().collapsePath(path);
  }

  /**
   * Returns the TreePath for the specified row.
   *
   * @param row
   *          the row index
   * @return the TreePath for the row
   */
  TreePath getRowPath(int row) {
    return treeTableModel.getLayout().getPathForRow(row);
  }

  /**
   * Returns the TreePaths for all selected rows.
   *
   * @return an array of selected TreePaths
   */
  TreePath[] getSelectedTreePaths() {
    return IntStream.of(getSelectedRows()).mapToObj(this::getRowPath).toArray(TreePath[]::new);
  }

  /**
   * Checks if the specified column index is the tree column.
   *
   * @param column
   *          the column index
   * @return true if it is the tree column, false otherwise
   */
  boolean isTreeColumnIndex(int column) {
    int columnIndex = convertColumnIndexToModel(column);
    return columnIndex == 0;
  }

  /**
   * Returns the layout cache used by this tree table.
   *
   * @return the AbstractLayoutCache instance
   */
  public final AbstractLayoutCache getLayoutCache() {
    TmmTreeTableModel model = getTreeTableModel();
    if (model != null) {
      return model.getLayout();
    }
    else {
      return null;
    }
  }

  /**
   * Sets whether the root node is visible.
   *
   * @param val
   *          true to make the root visible, false otherwise
   */
  public void setRootVisible(boolean val) {
    if (getTreeTableModel() == null) {
      cachedRootVisible = val;
    }
    if (val != isRootVisible()) {
      AbstractLayoutCache layoutCache = getLayoutCache();
      if (layoutCache != null) {
        layoutCache.setRootVisible(val);
        if (layoutCache.getRowCount() > 0) {
          TreePath rootPath = layoutCache.getPathForRow(0);
          if (null != rootPath)
            layoutCache.treeStructureChanged(new TreeModelEvent(this, rootPath));
        }
        firePropertyChange("rootVisible", !val, val); // NOI18N
      }
    }
  }

  /**
   * Checks if the root node is visible.
   *
   * @return true if the root is visible, false otherwise
   */
  public boolean isRootVisible() {
    if (getLayoutCache() == null) {
      return cachedRootVisible;
    }
    else {
      return getLayoutCache().isRootVisible();
    }
  }

  /**
   * Handles editing of a cell at the specified position. If it's the tree column (column 0), handles expansion/collapse of nodes and checkbox
   * interactions. Otherwise delegates to the standard table cell editing.
   *
   * @param row
   *          the row to be edited
   * @param column
   *          the column to be edited
   * @param e
   *          the event that triggered the edit
   * @return true if the cell was successfully edited, false otherwise
   */
  @Override
  public boolean editCellAt(int row, int column, EventObject e) {
    // If it was on column 0, it may be a request to expand a tree node - check for that first.
    boolean isTreeColumn = isTreeColumnIndex(column);
    if (isTreeColumn && e instanceof MouseEvent) {
      MouseEvent me = (MouseEvent) e;
      AbstractLayoutCache layoutCache = getLayoutCache();
      if (layoutCache != null) {
        TreePath path = layoutCache.getPathForRow(convertRowIndexToModel(row));
        if (path != null && !getTreeTableModel().isLeaf(path.getLastPathComponent())) {
          int handleWidth = TmmTreeTableCellRenderer.getExpansionHandleWidth();
          Insets ins = getInsets();
          int nd = path.getPathCount() - (isRootVisible() ? 1 : 2);
          if (nd < 0) {
            nd = 0;
          }
          int handleStart = ins.left + (nd * TmmTreeTableCellRenderer.getNestingWidth());
          int handleEnd = ins.left + handleStart + handleWidth;
          // Translate 'x' to position of column if non-0:
          int columnStart = getCellRect(row, column, false).x;
          handleStart += columnStart;
          handleEnd += columnStart;

          TableColumn tableColumn = getColumnModel().getColumn(column);
          if ((me.getX() > ins.left && me.getX() >= handleStart && me.getX() <= handleEnd)) {
            boolean expanded = layoutCache.isExpanded(path);
            if (!expanded) {
              getTreePathSupport().expandPath(path);

              Object ourObject = path.getLastPathComponent();
              int cCount = getTreeTableModel().getChildCount(ourObject);
              if (cCount > 0) {
                int lastRow = row;
                for (int i = 0; i < cCount; i++) {
                  Object child = getTreeTableModel().getChild(ourObject, i);
                  TreePath childPath = path.pathByAddingChild(child);
                  int childRow = layoutCache.getRowForPath(childPath);
                  childRow = convertRowIndexToView(childRow);
                  if (childRow > lastRow) {
                    lastRow = childRow;
                  }
                }
                int firstRow = row;
                Rectangle rectLast = getCellRect(lastRow, 0, true);
                Rectangle rectFirst = getCellRect(firstRow, 0, true);
                Rectangle rectFull = new Rectangle(rectFirst.x, rectFirst.y, rectLast.x + rectLast.width - rectFirst.x,
                    rectLast.y + rectLast.height - rectFirst.y);
                scrollRectToVisible(rectFull);
              }

            }
            else {
              getTreePathSupport().collapsePath(path);
            }
            return false;
          }
        }
        // It may be a request to check/uncheck a check-box
        if (checkAt(row, column, me)) {
          return false;
        }
      }
    }

    boolean res = false;
    if (!isTreeColumn || e instanceof MouseEvent && row >= 0 && isEditEvent(row, column, (MouseEvent) e)) {
      res = super.editCellAt(row, column, e);
    }
    if (res && isTreeColumn && row >= 0 && null != getEditorComponent()) {
      configureTreeCellEditor(getEditorComponent(), row, column);
    }
    if (e == null && !res && isTreeColumn) {
      // Handle SPACE
      checkAt(row, column, null);
    }
    return res;
  }

  /**
   * Determines if the mouse event should trigger a cell edit. Takes into account click count and modifiers to decide if editing should begin.
   *
   * @param row
   *          the row being clicked
   * @param column
   *          the column being clicked
   * @param me
   *          the mouse event
   * @return true if the event should trigger cell editing, false otherwise
   */
  private boolean isEditEvent(int row, int column, MouseEvent me) {
    if (me.getClickCount() > 1) {
      return true;
    }
    boolean noModifiers = me.getModifiersEx() == MouseEvent.BUTTON1_DOWN_MASK;
    if (lastEditPosition != null && selectedRow == row && noModifiers && lastEditPosition[0] == row && lastEditPosition[1] == column) {

      int handleWidth = TmmTreeTableCellRenderer.getExpansionHandleWidth();
      Insets ins = getInsets();
      AbstractLayoutCache layoutCache = getLayoutCache();
      if (layoutCache != null) {
        TreePath path = layoutCache.getPathForRow(convertRowIndexToModel(row));
        int nd = path.getPathCount() - (isRootVisible() ? 1 : 2);
        if (nd < 0) {
          nd = 0;
        }
        int handleStart = ins.left + (nd * TmmTreeTableCellRenderer.getNestingWidth());
        int handleEnd = ins.left + handleStart + handleWidth;
        // Translate 'x' to position of column if non-0:
        int columnStart = getCellRect(row, column, false).x;
        handleStart += columnStart;
        handleEnd += columnStart;
        if (me.getX() >= handleEnd) {
          lastEditPosition = null;
          return true;
        }
      }
    }
    lastEditPosition = new int[] { row, column };
    return false;
  }

  /**
   * Handles checkbox interactions in the tree column. Manages the state of checkboxes including their position and selection.
   *
   * @param row
   *          the row containing the checkbox
   * @param column
   *          the column containing the checkbox
   * @param me
   *          the mouse event that triggered the check, or null for keyboard events
   * @return true if the checkbox state was changed, false otherwise
   */
  protected final boolean checkAt(int row, int column, MouseEvent me) {
    TmmTreeTableRenderDataProvider render = getRenderDataProvider();
    TableCellRenderer tcr = getDefaultRenderer(Object.class);
    if (render instanceof TmmTreeTableCheckRenderDataProvider && tcr instanceof TmmTreeTableCellRenderer) {
      TmmTreeTableCheckRenderDataProvider crender = (TmmTreeTableCheckRenderDataProvider) render;
      TmmTreeTableCellRenderer ocr = (TmmTreeTableCellRenderer) tcr;
      Object value = getValueAt(row, column);
      if (value != null && crender.isCheckable(value) && crender.isCheckEnabled(value)) {
        boolean chBoxPosition = false;
        if (me == null) {
          chBoxPosition = true;
        }
        else {
          int handleWidth = TmmTreeTableCellRenderer.getExpansionHandleWidth();
          int chWidth = ocr.getTheCheckBoxWidth();
          Insets ins = getInsets();
          AbstractLayoutCache layoutCache = getLayoutCache();
          if (layoutCache != null) {
            TreePath path = layoutCache.getPathForRow(convertRowIndexToModel(row));
            int nd = path.getPathCount() - (isRootVisible() ? 1 : 2);
            if (nd < 0) {
              nd = 0;
            }
            int chStart = ins.left + (nd * TmmTreeTableCellRenderer.getNestingWidth()) + handleWidth;
            int chEnd = chStart + chWidth;

            chBoxPosition = (me.getX() > ins.left && me.getX() >= chStart && me.getX() <= chEnd);
          }
        }
        if (chBoxPosition) {
          Boolean selected = crender.isSelected(value);
          if (selected == null || Boolean.TRUE.equals(selected)) {
            crender.setSelected(value, Boolean.FALSE);
          }
          else {
            crender.setSelected(value, Boolean.TRUE);
          }
          Rectangle r = getCellRect(row, column, true);
          repaint(r.x, r.y, r.width, r.height);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Configures the tree cell editor component with appropriate border and visual properties.
   *
   * @param editor
   *          the editor component to configure
   * @param row
   *          the row being edited
   * @param column
   *          the column being edited
   */
  protected void configureTreeCellEditor(Component editor, int row, int column) {
    if (!(editor instanceof JComponent)) {
      return;
    }
    TreeCellEditorBorder b = new TreeCellEditorBorder();

    AbstractLayoutCache layoutCache = getLayoutCache();
    if (layoutCache != null) {
      TreePath path = layoutCache.getPathForRow(convertRowIndexToModel(row));
      Object o = getValueAt(row, column);
      TmmTreeTableRenderDataProvider rdp = getRenderDataProvider();
      TableCellRenderer tcr = getDefaultRenderer(Object.class);
      if (rdp instanceof TmmTreeTableCheckRenderDataProvider && tcr instanceof TmmTreeTableCellRenderer) {
        TmmTreeTableCheckRenderDataProvider crender = (TmmTreeTableCheckRenderDataProvider) rdp;
        TmmTreeTableCellRenderer ocr = (TmmTreeTableCellRenderer) tcr;
        Object value = getValueAt(row, column);
        if (value != null && crender.isCheckable(value) && crender.isCheckEnabled(value)) {
          b.checkWidth = ocr.getTheCheckBoxWidth();
          b.checkBox = ocr.setUpCheckBox(crender, value, ocr.createCheckBox());
        }
      }
      b.icon = rdp.getIcon(o);
      b.nestingDepth = Math.max(0, path.getPathCount() - (isRootVisible() ? 1 : 2));
      b.isLeaf = getTreeTableModel().isLeaf(o);
      b.isExpanded = layoutCache.isExpanded(path);

      ((JComponent) editor).setBorder(b);
    }
  }

  /**
   * Called when this component is added to a container. Recalculates the row height based on font metrics and expansion handle height.
   */
  @Override
  public void addNotify() {
    super.addNotify();
    calcRowHeight();
  }

  /**
   * Calculates and sets the appropriate row height for the tree table. Takes into account the font metrics and expansion handle height.
   */
  private void calcRowHeight() {
    // Users of themes can set an explicit row height, so check for it

    int rHeight = 20;
    // Derive a row height to accommodate the font and expand icon
    Font f = getFont();
    FontMetrics fm = getFontMetrics(f);
    int h = Math.max(fm.getHeight() + fm.getMaxDescent(), TmmTreeTableCellRenderer.getExpansionHandleHeight());
    rHeight = Math.max(rHeight, h) + 2;

    setRowHeight(rHeight);
  }

  /**
   * Returns all set tree nodes filter.
   *
   * @return a list of all set tree nodes filters
   */
  public List<ITmmTreeFilter<TmmTreeNode>> getFilters() {
    return new ArrayList<>(treeFilters);
  }

  /**
   * Removes any applied tree nodes filter.
   */
  public void clearFilter() {
    // remove our filter listener
    for (ITmmTreeFilter<TmmTreeNode> filter : treeFilters) {
      if (filter instanceof ITmmUIFilter) {
        ITmmUIFilter<?> tmmUIFilter = (ITmmUIFilter<?>) filter;

        tmmUIFilter.setFilterState(ITmmUIFilter.FilterState.INACTIVE);
        tmmUIFilter.clearFilter();
      }
      filter.removePropertyChangeListener(filterChangeListener);
    }

    updateFiltering();
    storeFilters();
  }

  /**
   * add a new filter to this tree
   *
   * @param newFilter
   *          the new filter to be added
   */
  public void addFilter(ITmmTreeFilter<TmmTreeNode> newFilter) {
    // add our filter listener
    newFilter.addPropertyChangeListener(ITmmTreeFilter.TREE_FILTER_CHANGED, filterChangeListener);
    treeFilters.add(newFilter);
  }

  /**
   * removes the given filter from this tree
   *
   * @param filter
   *          the filter to be removed
   */
  public void removeFilter(ITmmTreeFilter<TmmTreeNode> filter) {
    // remove our filter listener
    filter.removePropertyChangeListener(filterChangeListener);
    treeFilters.remove(filter);
  }

  /**
   * Updates nodes sorting and filtering for all loaded nodes.
   */
  void updateFiltering() {
    // re-evaluate active filters
    Set<ITmmTreeFilter<TmmTreeNode>> activeTreeFilters = new HashSet<>();

    for (ITmmTreeFilter<TmmTreeNode> filter : treeFilters) {
      if (filter.isActive()) {
        activeTreeFilters.add(filter);
      }
    }

    dataProvider.setTreeFilters(activeTreeFilters);

    // and update the UI
    final TreeModel model = treeTableModel.getTreeModel();
    if (model instanceof TmmTreeModel) {
      ((TmmTreeModel<?>) model).invalidateFilterCache();
      ((TmmTreeModel<?>) model).updateSortingAndFiltering();
    }

    firePropertyChange("filterChanged", null, treeFilters);
  }

  /**
   * to be overridden to provide storing of filters
   */
  public void storeFilters() {
    // to be overridden in implementations
  }

  public void setFilterValues(List<AbstractSettings.UIFilters> values) {
    if (values == null) {
      values = Collections.emptyList();
    }

    for (ITmmTreeFilter<?> filter : treeFilters) {
      if (filter instanceof ITmmUIFilter) {
        ITmmUIFilter<?> tmmUIFilter = (ITmmUIFilter<?>) filter;
        AbstractSettings.UIFilters uiFilters = values.stream().filter(uiFilter -> uiFilter.id.equals(filter.getId())).findFirst().orElse(null);

        if (uiFilters != null) {
          tmmUIFilter.setFilterState(uiFilters.state);
          tmmUIFilter.setFilterValue(uiFilters.filterValue);
          tmmUIFilter.setFilterOption(uiFilters.option);
        }
        else {
          tmmUIFilter.setFilterState(ITmmUIFilter.FilterState.INACTIVE);
        }
      }
    }

    updateFiltering();
  }

  /**
   * set whether all filters are active or not
   *
   * @param filtersActive
   *          true if all filters should be active; false otherwise
   */
  public void setFiltersActive(boolean filtersActive) {
    final TreeModel model = treeTableModel.getTreeModel();
    if (model instanceof TmmTreeModel) {
      ((TmmTreeModel<?>) model).getDataProvider().setFiltersActive(filtersActive);
    }

    updateFiltering();
    storeFilters();
  }

  /**
   * check whether all filters are active or not
   *
   * @return true if not all filters are deaktivates
   */
  public boolean isFiltersActive() {
    final TreeModel model = treeTableModel.getTreeModel();
    if (model instanceof TmmTreeModel) {
      return ((TmmTreeModel<?>) model).getDataProvider().isFiltersActive();
    }

    return true;
  }

  /**
   * provide table cell tooltips via our table model
   *
   * @param e
   *          the mouse event
   * @return the tooltip or null
   */
  public String getToolTipText(@NotNull MouseEvent e) {
    if (!(getModel() instanceof TmmTreeTableModel)) {
      return null;
    }

    Point p = e.getPoint();
    int rowIndex = rowAtPoint(p);
    int colIndex = columnAtPoint(p);
    int realColumnIndex = convertColumnIndexToModel(colIndex) - 1; // first column is the tree

    if (colIndex == 0) {
      // tree
      return super.getToolTipText(e);
    }
    else if (colIndex > 0) {
      // table
      TmmTreeTableModel treeTableModel = ((TmmTreeTableModel) getModel());
      ConnectorTableModel tableModel = treeTableModel.getTableModel();

      return tableModel.getTooltipAt(rowIndex, realColumnIndex);
    }

    return null;
  }

  /**
   * Inner class that implements a border for tree cell editors. Handles the painting of expansion handles, checkboxes, and icons with proper spacing.
   */
  private static class TreeCellEditorBorder implements Border {
    private final Insets insets      = new Insets(0, 0, 0, 0);
    private final int    iconTextGap = new JLabel().getIconTextGap();

    private boolean      isLeaf;
    private boolean      isExpanded;
    private Icon         icon;
    private int          nestingDepth;
    private int          checkWidth;
    private JCheckBox    checkBox;

    /**
     * Returns the border insets for the specified component.
     *
     * @param c
     *          the component to get insets for
     * @return the border insets
     */
    @Override
    public Insets getBorderInsets(Component c) {
      insets.left = (nestingDepth * TmmTreeTableCellRenderer.getNestingWidth()) + TmmTreeTableCellRenderer.getExpansionHandleWidth() + 1;
      insets.left += checkWidth + ((icon != null) ? icon.getIconWidth() + iconTextGap : 0);
      insets.top = 1;
      insets.right = 1;
      insets.bottom = 1;
      return insets;
    }

    /**
     * Returns whether this border is opaque.
     *
     * @return always returns false
     */
    @Override
    public boolean isBorderOpaque() {
      return false;
    }

    /**
     * Paints the border with the appropriate expansion handles, checkboxes, and icons.
     *
     * @param c
     *          the component whose border is being painted
     * @param g
     *          the graphics context
     * @param x
     *          the x coordinate of the painting origin
     * @param y
     *          the y coordinate of the painting origin
     * @param width
     *          the width of the border area
     * @param height
     *          the height of the border area
     */
    @Override
    public void paintBorder(Component c, java.awt.Graphics g, int x, int y, int width, int height) {
      int iconY;
      int iconX = nestingDepth * TmmTreeTableCellRenderer.getNestingWidth();
      if (!isLeaf) {
        Icon expIcon = isExpanded ? TmmTreeTableCellRenderer.getExpandedIcon() : TmmTreeTableCellRenderer.getCollapsedIcon();
        if (expIcon.getIconHeight() < height) {
          iconY = (height / 2) - (expIcon.getIconHeight() / 2);
        }
        else {
          iconY = 0;
        }
        expIcon.paintIcon(c, g, iconX, iconY);
      }
      iconX += TmmTreeTableCellRenderer.getExpansionHandleWidth() + 1;

      if (null != checkBox) {
        java.awt.Graphics chbg = g.create(iconX, y, checkWidth, height);
        checkBox.paint(chbg);
        chbg.dispose();
      }
      iconX += checkWidth;

      if (null != icon) {
        if (icon.getIconHeight() < height) {
          iconY = (height / 2) - (icon.getIconHeight() / 2);
        }
        else {
          iconY = 0;
        }
        icon.paintIcon(c, g, iconX, iconY);
      }
    }
  }

  /**
   * Checks if the tree table is currently adjusting its content.
   *
   * @return true if the tree table is adjusting, false otherwise
   */
  public boolean isAdjusting() {
    return ((TmmTreeModel) treeTableModel.getTreeModel()).isAdjusting();
  }

  /**
   * Sets the selected rows in the tree table. Handles adjusting state to ensure proper selection behavior.
   *
   * @param selectedRows
   *          array of row indices to select
   */
  private void setSelectedRows(int[] selectedRows) {
    ((TmmTreeModel) treeTableModel.getTreeModel()).setAdjusting(true);
    clearSelection();
    ((TmmTreeModel) treeTableModel.getTreeModel()).setAdjusting(false);
    for (int selectedRow : selectedRows) {
      getSelectionModel().addSelectionInterval(selectedRow, selectedRow);
    }
  }

  /**
   * Inner class that extends TmmTreeModel to provide tree model functionality specific to the tree table.
   *
   * @param <E>
   *          the type of tree node
   */
  private class TmmTreeModelConnector<E extends TmmTreeNode> extends TmmTreeModel<E> {

    /**
     * Create a new instance of the TmmTreeModel for the given TmmTree and data provider
     *
     * @param dataProvider
     *          the data provider to create the model for
     */
    public TmmTreeModelConnector(final TmmTreeDataProvider<E> dataProvider) {
      super(null, dataProvider);
    }

    /**
     * Updates the sorting and filtering of the tree table content. Handles caching of changes and adaptive timing of updates.
     */
    @Override
    public void updateSortingAndFiltering() {
      long now = System.currentTimeMillis();

      if (now > nextNodeStructureChanged) {
        // store selected nodes
        TreePath[] selectedPaths = getSelectedTreePaths();

        setAdjusting(true);

        // Updating root node children
        boolean structureChanged = performFilteringAndSortingRecursively(getRoot());
        if (structureChanged) {
          nodeStructureChanged();

          // Restoring tree state including all selections and expansions
          setAdjusting(false);

          setSelectedRows(treeTableModel.getLayout().getRowsForPaths(selectedPaths));
        }
        else {
          setAdjusting(false);
        }
        long end = System.currentTimeMillis();

        if ((end - now) < TIMER_DELAY) {
          // logic has been run within the delay time
          nextNodeStructureChanged = end + TIMER_DELAY;
        }
        else {
          // logic was slower than the interval - increase the interval adaptively
          nextNodeStructureChanged = end + (end - now) * 2;
        }
      }
      else {
        startUpdateSortAndFilterTimer();
      }
    }
  }

  /**
   * Gets the current sorting strategy used by the tree table.
   *
   * @return the current ITmmTreeTableSortingStrategy, or null if none is set
   */
  public ITmmTreeTableSortingStrategy getSortStrategy() {
    Comparator<?> comparator = ((TmmTreeModel<?>) getTreeTableModel().getTreeModel()).getDataProvider().getTreeComparator();
    if (comparator instanceof ITmmTreeTableSortingStrategy sortStrategy) {
      return sortStrategy;
    }
    return null;
  }

  /**
   * Sets the sorting strategy from an encoded string representation.
   *
   * @param stringEncoded
   *          the encoded sorting strategy configuration
   */
  public void setSortStrategy(String stringEncoded) {
    ITmmTreeTableSortingStrategy sortingStrategy = getSortStrategy();

    if (sortingStrategy == null) {
      return;
    }

    sortingStrategy.fromString(stringEncoded);

    updateFiltering();
    // make sure the header also gets redrawn (this may not happen if the structure does not change)
    getTableHeader().repaint();
  }

  /**
   * Inner class that handles keyboard events for the tree table. Manages expansion, collapse, and navigation of tree nodes.
   */
  private class TmmTreeTableKeyAdapter extends KeyAdapter {
    final TmmTreeTable treeTable;

    TmmTreeTableKeyAdapter(TmmTreeTable treeTable) {
      this.treeTable = treeTable;
    }

    /**
     * Gets the parent rows for the given child rows.
     *
     * @param childRows
     *          array of child row indices
     * @return array of parent row indices
     */
    private int[] getParentRows(int[] childRows) {
      return IntStream.of(childRows).map(leafRow -> {
        @Nonnull
        TreePath leafPath = getRowPath(leafRow);
        @Nullable
        TreePath parentPath = leafPath.getParentPath();
        @Nonnull
        TreePath pathToReturn = parentPath != null ? parentPath : leafPath;
        int parentRow = treeTableModel.getLayout().getRowForPath(pathToReturn);
        return parentRow > -1 ? parentRow : leafRow;
      }).toArray();
    }

    /**
     * Toggles the expansion state of the specified rows.
     *
     * @param rows
     *          array of row indices to toggle
     * @param expand
     *          true to expand, false to collapse
     */
    private void toggleRows(int[] rows, boolean expand) {
      int[] collapsedAndLeafRows = IntStream.of(rows).filter(treeTable::isCollapsed).toArray();

      if (!expand && collapsedAndLeafRows.length == rows.length) {
        int[] parentRows = getParentRows(rows);
        setSelectedRows(parentRows);
        return;
      }

      // Expand/collapse rows in reverse order to ensure that row indexes don't change.
      int[] reversedBranchRows = new int[rows.length];
      for (int i = 0; i < rows.length; i++) {
        reversedBranchRows[rows.length - i - 1] = rows[i];
      }

      // Save the list of currently-selected row paths so that we can restore the user's selection after toggling.
      TreePath[] selectedPaths = getSelectedTreePaths();

      for (int row : reversedBranchRows) {
        if (expand) {
          treeTable.expandRow(row);
        }
        else {
          treeTable.collapseRow(row);
        }
      }

      setSelectedRows(treeTableModel.getLayout().getRowsForPaths(selectedPaths));
    }

    /**
     * Handles key press events for navigation and expansion/collapse.
     *
     * @param e
     *          the key event
     */
    @Override
    public void keyPressed(KeyEvent e) {
      int[] selectedRows = treeTable.getSelectedRows();
      if (selectedRows.length == 0) {
        return;
      }

      try {
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          toggleRows(selectedRows, true);
        }
        else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
          toggleRows(selectedRows, false);
        }
        else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          int[] branchRows = IntStream.of(selectedRows).filter(treeTable::isBranch).toArray();
          if (branchRows.length > 0) {
            boolean areAnyBranchesCollapsed = IntStream.of(branchRows).anyMatch(treeTable::isCollapsed);
            toggleRows(selectedRows, areAnyBranchesCollapsed);
          }
        }
      }
      catch (Exception ex) {
        // just not crash the UI!
      }
    }
  }
}
