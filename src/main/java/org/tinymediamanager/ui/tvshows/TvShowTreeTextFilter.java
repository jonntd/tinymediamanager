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

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.tree.TreeNode;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.jmte.JmteUtils;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowRenamer;
import org.tinymediamanager.core.tvshow.TvShowSettings;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.scraper.util.StrgUtils;
import org.tinymediamanager.ui.components.tree.TmmTreeNode;
import org.tinymediamanager.ui.components.tree.TmmTreeTextFilter;

import com.floreysoft.jmte.Engine;

/**
 * the class {@link TvShowTreeTextFilter} is used to filter the TV shows by text input
 * 
 * @author Manuel Laggner
 */
public class TvShowTreeTextFilter<E extends TmmTreeNode> extends TmmTreeTextFilter<E> {
  private final TvShowSettings settings = TvShowModuleManager.getInstance().getSettings();
  private static final Engine  ENGINE   = TvShowRenamer.createEngine();

  @Override
  protected String prepareFilterText() {
    return StrgUtils.normalizeString(getText());
  }

  @Override
  public boolean accept(E node) {
    if (StringUtils.isBlank(filterText)) {
      return true;
    }

    if (node instanceof TvShowTreeDataProvider.AbstractTvShowTreeNode) {
      TvShowTreeDataProvider.AbstractTvShowTreeNode treeNode = (TvShowTreeDataProvider.AbstractTvShowTreeNode) node;
      // System.out.println(" ".repeat(3 - treeNode.getDepth()) + treeNode.getTitle());

      // filter on the node
      Matcher matcher;
      if (settings.getNode()) {
        matcher = filterPattern.matcher(StrgUtils.normalizeString(treeNode.toString()));
        if (matcher.find()) {
          return true;
        }
      }

      // filter on the title
      if (settings.getTitle()) {
        matcher = filterPattern.matcher(StrgUtils.normalizeString(treeNode.getTitle()));
        if (matcher.find()) {
          return true;
        }
      }

      // filter on the original title
      if (settings.getOriginalTitle()) {
        matcher = filterPattern.matcher(StrgUtils.normalizeString(treeNode.getOriginalTitle()));
        if (matcher.find()) {
          return true;
        }
      }

      // filter on english title
      if (settings.getEnglishTitle()) {
        matcher = filterPattern.matcher(StrgUtils.normalizeString(treeNode.getEnglishTitle()));
        if (matcher.find()) {
          return true;
        }
      }

      // second: parse all children too
      // if a child matches, the Show/Season must be shown, WITH ALL CHILDREN
      // Contrary to EP matches, where ONLY the matched EP is visible
      for (Enumeration<? extends TreeNode> e = node.children(); e.hasMoreElements();) {
        if (accept((E) e.nextElement())) {
          return true;
        }
      }

      // third: check the parent(s)
      if (checkParent(node.getDataProvider().getParent(treeNode), filterPattern)) {
        return true;
      }

      // last: heavy check
      if (matchByShortFilter(treeNode)) {
        return true;
      }

      return false;
    }

    // no AbstractTvShowTreeNode? super call accept from super
    return super.accept(node);
  }

  @Override
  protected boolean checkParent(TmmTreeNode node, Pattern pattern) {
    if (node == null) {
      return false;
    }

    if (node instanceof TvShowTreeDataProvider.AbstractTvShowTreeNode treeNode) {
      // first: filter on the node
      Matcher matcher = pattern.matcher(StrgUtils.normalizeString(treeNode.toString()));
      if (matcher.find()) {
        return true;
      }

      // second: filter on the original title
      matcher = pattern.matcher(StrgUtils.normalizeString(treeNode.getTitle()));
      if (matcher.find()) {
        return true;
      }

      // third: filter on the original title
      matcher = pattern.matcher(StrgUtils.normalizeString(treeNode.getOriginalTitle()));
      if (matcher.find()) {
        return true;
      }

      if (checkParent(node.getDataProvider().getParent(node), pattern)) {
        return true;
      }

      // last: heavy check
      if (matchByShortFilter(treeNode)) {
        return true;
      }

      return false;
    }

    return super.checkParent(node, pattern);
  }

  private boolean matchByShortFilter(TvShowTreeDataProvider.AbstractTvShowTreeNode treeNode) {
    // match by field:value (eg search by ids:160, actors:mel gibson))
    if (filterText.matches("\\w+:\\w[\\w\\s]+")) {
      // System.out.println(" ".repeat(3 - treeNode.getDepth()) + treeNode.getTitle());

      String[] kv = filterText.split(":");
      Object userObject = treeNode.getUserObject();
      try {
        PropertyDescriptor pd = null;
        if (userObject instanceof TvShow) {
          pd = new PropertyDescriptor(kv[0], TvShow.class);
        }
        else if (userObject instanceof TvShowSeason) {
          pd = new PropertyDescriptor(kv[0], TvShowSeason.class);
        }
        else if (userObject instanceof TvShowEpisode) {
          pd = new PropertyDescriptor(kv[0], TvShowEpisode.class);
        }
        Method getter = pd.getReadMethod();
        Object f = getter.invoke(userObject);

        String res = String.valueOf(f).toLowerCase(Locale.ROOT);
        if (res.contains(kv[1].toLowerCase(Locale.ROOT))) {
          // System.out.println(" ".repeat(3 - treeNode.getDepth()) + "Found via getter: " + kv[1] + " in " + res);
          return true;
        }
      }
      catch (Exception e) {
        // Fallback: try field via JMTE
        if (TvShowRenamer.getTokenMap().containsKey(kv[0])) {
          Map<String, Object> root = new HashMap<>();
          if (userObject instanceof TvShow) {
            root.put("tvShow", (TvShow) userObject);
          }
          else if (userObject instanceof TvShowSeason) {
            root.put("season", ((TvShowSeason) userObject));
          }
          else if (userObject instanceof TvShowEpisode) {
            root.put("episode", (TvShowEpisode) userObject);
          }
          String val = ENGINE.transform(JmteUtils.morphTemplate("${" + kv[0] + "}", TvShowRenamer.getTokenMap()), root);
          if (StringUtils.containsIgnoreCase(val, kv[1])) {
            // System.out.println(" ".repeat(3 - treeNode.getDepth()) + "Found via JMTE: " + kv[1] + " in " + val);
            return true;
          }
        }
      }
    }
    return false;
  }
}
