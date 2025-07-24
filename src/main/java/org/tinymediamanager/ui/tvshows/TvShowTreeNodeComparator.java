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

package org.tinymediamanager.ui.tvshows;

import java.util.Comparator;

import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.ui.components.table.TmmTableFormat;
import org.tinymediamanager.ui.components.tree.TmmTreeNode;
import org.tinymediamanager.ui.components.treetable.AbstractTmmTreeTableNodeComparator;
import org.tinymediamanager.ui.components.treetable.TmmTreeTableFormat;

class TvShowTreeNodeComparator extends AbstractTmmTreeTableNodeComparator {
  private final Comparator                      stringComparator;
  private final TmmTreeTableFormat<TmmTreeNode> tableFormat;

  private Comparator                            sortComparator;

  TvShowTreeNodeComparator(TmmTreeTableFormat<TmmTreeNode> tableFormat) {
    this.tableFormat = tableFormat;
    stringComparator = new TmmTableFormat.StringComparator();

    // initialize the comparator with comparing the title ascending
    sortColumn = 0;
    sortDirection = SortDirection.ASCENDING;
    sortComparator = getSortComparator();
  }

  @Override
  public int compare(TmmTreeNode o1, TmmTreeNode o2) {
    Object userObject1 = o1.getUserObject();
    Object userObject2 = o2.getUserObject();

    if (userObject1 instanceof TvShow && userObject2 instanceof TvShow) {
      int compairingResult = sortComparator.compare(getColumnValue(o1, sortColumn), getColumnValue(o2, sortColumn));
      if (compairingResult == 0 && sortColumn != 0) {
        compairingResult = stringComparator.compare(getColumnValue(o1, 0), getColumnValue(o2, 0));
      }
      else {
        if (sortDirection == SortDirection.DESCENDING) {
          compairingResult = compairingResult * -1;
        }
      }
      return compairingResult;
    }

    if (userObject1 instanceof TvShowSeason && userObject2 instanceof TvShowSeason) {
      TvShowSeason tvShowSeason1 = (TvShowSeason) userObject1;
      TvShowSeason tvShowSeason2 = (TvShowSeason) userObject2;
      return tvShowSeason1.getSeason() - tvShowSeason2.getSeason();
    }

    if (userObject1 instanceof TvShowEpisode && userObject2 instanceof TvShowEpisode) {
      TvShowEpisode tvShowEpisode1 = (TvShowEpisode) userObject1;
      TvShowEpisode tvShowEpisode2 = (TvShowEpisode) userObject2;
      return tvShowEpisode1.getEpisode() - tvShowEpisode2.getEpisode();
    }

    return o1.toString().compareToIgnoreCase(o2.toString());
  }

  @Override
  public void columnClicked(int column, boolean shift, boolean control) {
    if (sortColumn == column) {
      if (sortDirection == SortDirection.ASCENDING) {
        sortDirection = SortDirection.DESCENDING;
      }
      else {
        sortDirection = SortDirection.ASCENDING;
      }
    }
    else {
      sortDirection = SortDirection.ASCENDING;
    }
    sortColumn = column;

    sortComparator = getSortComparator();
  }

  @Override
  public void fromString(String stringEncoded) {
    super.fromString(stringEncoded);
    sortComparator = getSortComparator();
  }

  private Comparator getSortComparator() {
    if (sortColumn == 0) {
      // sort on the node/title
      return stringComparator;
    }
    else {
      return tableFormat.getColumnComparator(sortColumn - 1);
    }
  }

  private Object getColumnValue(TmmTreeNode treeNode, int i) {
    if (i == 0) {
      return ((TvShow) treeNode.getUserObject()).getTitleSortable();
    }
    return tableFormat.getColumnValue(treeNode, i - 1);
  }

  public SortDirection getSortDirection(int sortColumn) {
    if (sortColumn == this.sortColumn) {
      return sortDirection;
    }

    return null;
  }
}
