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

import static org.tinymediamanager.core.bus.EventBus.TOPIC_TV_SHOWS;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.tree.TreeNode;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.bus.Event;
import org.tinymediamanager.core.bus.EventBus;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.tvshow.TvShowList;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.ui.components.tree.TmmTreeDataProvider;
import org.tinymediamanager.ui.components.tree.TmmTreeNode;
import org.tinymediamanager.ui.components.treetable.TmmTreeTableFormat;

/**
 * The class TvShowTreeDataProvider is used for providing and managing the data for the TV show tree
 * 
 * @author Manuel Laggner
 */
public class TvShowTreeDataProvider extends TmmTreeDataProvider<TmmTreeNode> {
  private final TmmTreeNode root       = new TmmTreeNode(new Object(), this);

  private final TvShowList  tvShowList = TvShowModuleManager.getInstance().getTvShowList();

  public TvShowTreeDataProvider(TmmTreeTableFormat<TmmTreeNode> tableFormat) {

    EventBus.registerListener(TOPIC_TV_SHOWS, event -> {
      if (event.sender() instanceof TvShow tvShow) {
        processTvShow(tvShow, event.eventType());
      }
      else if (event.sender() instanceof TvShowSeason season) {
        processSeason(season, event.eventType());
      }
      else if (event.sender() instanceof TvShowEpisode episode) {
        processEpisode(episode, event.eventType());
      }
    });

    setTreeComparator(new TvShowTreeNodeComparator(tableFormat));

    TvShowModuleManager.getInstance().getSettings().addPropertyChangeListener(evt -> {
      switch (evt.getPropertyName()) {
        case "displayMissingEpisodes", "displayMissingSpecials", "displayMissingNotAired" -> updateDummyEpisodes();
      }
    });
  }

  private void processTvShow(TvShow tvShow, String eventType) {
    // check if existing
    TmmTreeNode tvShowNode = getNodeFromCache(tvShow);
    if (tvShowNode != null) {
      if (Event.TYPE_REMOVE.equals(eventType)) {
        // TV show deleted
        removeTvShow(tvShow);
      }
      else {
        // TV show updated
        nodeChanged(tvShow);
        updateDummyEpisodes();
      }
    }
    else {
      // TV show added
      addTvShow(tvShow);
    }
  }

  private void processSeason(TvShowSeason season, String eventType) {
    updateSeason(season);
  }

  private void processEpisode(TvShowEpisode episode, String eventType) {
    if (Event.TYPE_REMOVE.equals(eventType)) {
      removeTvShowEpisode(episode);
    }
    else {
      updateEpisode(episode);
    }
    updateDummyEpisodesForTvShow(episode.getTvShow());
  }

  private void updateSeason(TvShowSeason season) {
    TmmTreeNode seasonNode = getNodeFromCache(season);

    if (seasonNode == null) {
      seasonNode = addTvShowSeason(season);
    }

    if (seasonNode != null) {
      updateSeason(seasonNode);
    }
  }

  private void updateSeason(TmmTreeNode seasonNode) {
    if (seasonNode.getUserObject() instanceof TvShowSeason season) {
      if (seasonNode.getChildCount() == 0) {
        TmmTreeNode cachedNode = removeNodeFromCache(season);
        if (cachedNode == null) {
          return;
        }

        firePropertyChange(NODE_REMOVED, null, cachedNode);
      }
      else {
        List<TvShowEpisode> episodesForDisplay = season.getEpisodesForDisplay();

        // remove episodes which are no more to display
        for (Enumeration<TreeNode> e = seasonNode.children(); e.hasMoreElements();) {
          TreeNode child = e.nextElement();
          if (child instanceof TmmTreeNode tmmTreeNode && tmmTreeNode.getUserObject() instanceof TvShowEpisode episode) {
            if (!episodesForDisplay.contains(episode)) {
              removeTvShowEpisode(episode);
            }
          }
        }

        // add missing ones
        for (TvShowEpisode episode : episodesForDisplay) {
          addTvShowEpisode(episode);
        }
      }
    }
  }

  /**
   * update the episode node on demand (when something dramatically has changed)
   */
  private void updateEpisode(TvShowEpisode episode) {
    // the episode node
    TmmTreeNode episodeNode = getNodeFromCache(episode);

    if (episodeNode == null) {
      // episode not yet on the UI - readd
      removeTvShowEpisode(episode);
      addTvShowEpisode(episode);
    }
    else {
      TmmTreeNode seasonNodeUi = (TmmTreeNode) episodeNode.getParent();
      TmmTreeNode seasonNodeBean = getNodeFromCache(episode.getTvShowSeason());

      if (seasonNodeUi == null || seasonNodeUi != seasonNodeBean) {
        // season on the UI does not match the evaluated season - readd
        removeTvShowEpisode(episode);
        addTvShowEpisode(episode);
      }
      else {
        // episode is on UI, season is the same - reorder
        firePropertyChange(NODE_CHANGED, null, episodeNode);
      }
    }
  }

  /**
   * add the dummy episodes to the tree is the setting has been activated
   */
  private void updateDummyEpisodes() {
    for (TvShow tvShow : tvShowList.getTvShows()) {
      updateDummyEpisodesForTvShow(tvShow);
    }
  }

  /**
   * update dummy episodes after changing S/E of existing episodes
   */
  private void updateDummyEpisodesForTvShow(TvShow tvShow) {
    List<TvShowEpisode> dummyEpisodes = tvShow.getDummyEpisodes();
    List<TvShowEpisode> episodesForDisplay = tvShow.getEpisodesForDisplay();

    // iterate over all episodes for display and re-add/remove dummy episodes which needs an update
    for (TvShowEpisode episode : dummyEpisodes) {
      if (episodesForDisplay.contains(episode) && getNodeFromCache(episode) == null) {
        // should be here, but isn't -> re-add
        addTvShowEpisode(episode);
      }
      else if (!episodesForDisplay.contains(episode) && getNodeFromCache(episode) != null) {
        // is here but shouldn't -> remove
        removeTvShowEpisode(episode);
      }
    }
  }

  /**
   * trigger a node changed event for all other events
   * 
   * @param source
   *          the source {@link Object} to trigger the change
   */
  private void nodeChanged(Object source) {
    nodeChanged(getNodeFromCache(source));
  }

  /**
   * trigger a node changed event for all other events
   * 
   * @param node
   *          the {@link TmmTreeNode} to trigger the change
   */
  private void nodeChanged(TmmTreeNode node) {
    if (node != null) {
      firePropertyChange(NODE_CHANGED, null, node);
    }
  }

  @Override
  public TmmTreeNode getRoot() {
    return root;
  }

  @Override
  public TmmTreeNode getParent(TmmTreeNode child) {
    if (child.getUserObject() instanceof TvShow) {
      return root;
    }
    else if (child.getUserObject() instanceof TvShowSeason season) {
      TmmTreeNode node = getNodeFromCache(season.getTvShow());
      // parent TV show not yet added? add it
      if (node == null) {
        node = addTvShow(season.getTvShow());
      }
      return node;
    }
    else if (child.getUserObject() instanceof TvShowEpisode episode) {
      TmmTreeNode node = getNodeFromCache(episode.getTvShowSeason());
      if (node == null) {
        node = addTvShowSeason(episode.getTvShowSeason());
      }
      // also check if the TV show has already been added
      if (getNodeFromCache(episode.getTvShow()) == null) {
        addTvShow(episode.getTvShow());
      }
      return node;
    }
    return null;
  }

  @Override
  public List<TmmTreeNode> getChildren(TmmTreeNode parent) {
    if (parent == root) {
      List<TmmTreeNode> nodes = new ArrayList<>();
      for (TvShow tvShow : new ArrayList<>(tvShowList.getTvShows())) {
        nodes.add(getOrCreateNode(tvShow));
      }
      return nodes;
    }
    else if (parent.getUserObject() instanceof TvShow tvShow) {
      List<TmmTreeNode> nodes = new ArrayList<>();
      for (TvShowSeason season : tvShow.getSeasons()) {
        if (!season.getEpisodesForDisplay().isEmpty()) {
          nodes.add(getOrCreateNode(season));
        }
      }
      return nodes;
    }
    else if (parent.getUserObject() instanceof TvShowSeason season) {
      List<TmmTreeNode> nodes = new ArrayList<>();
      for (TvShowEpisode episode : season.getEpisodesForDisplay()) {
        nodes.add(getOrCreateNode(episode));
      }
      return nodes;
    }
    return null;
  }

  @Override
  public boolean isLeaf(TmmTreeNode node) {
    return node.getUserObject() instanceof TvShowEpisode;
  }

  private TmmTreeNode addTvShow(TvShow tvShow) {
    // check if this tv show has already been added
    TmmTreeNode cachedNode = getNodeFromCache(tvShow);
    if (cachedNode != null) {
      return cachedNode;
    }

    // add a new node
    TmmTreeNode node = new TvShowTreeNode(tvShow, this);
    putNodeToCache(tvShow, node);
    firePropertyChange(NODE_INSERTED, null, node);

    return node;
  }

  private void removeTvShow(TvShow tvShow) {
    TmmTreeNode cachedNode = removeNodeFromCache(tvShow);
    if (cachedNode == null) {
      return;
    }

    // remove all children from the map (the nodes will be removed by the treemodel)
    for (TvShowSeason season : tvShow.getSeasons()) {
      removeNodeFromCache(season);
    }
    for (TvShowEpisode episode : tvShow.getEpisodesForDisplay()) {
      removeNodeFromCache(episode);
    }

    firePropertyChange(NODE_REMOVED, null, cachedNode);
  }

  private TmmTreeNode getOrCreateNode(MediaEntity entity) {
    TmmTreeNode cachedNode = getNodeFromCache(entity);
    if (cachedNode != null) {
      return cachedNode;
    }

    if (entity instanceof TvShow tvShow) {
      TmmTreeNode node = new TvShowTreeNode(tvShow, this);
      putNodeToCache(tvShow, node);
      return node;
    }
    else if (entity instanceof TvShowSeason season) {
      TmmTreeNode node = new TvShowSeasonTreeNode(season, this);
      putNodeToCache(season, node);
      return node;
    }
    else if (entity instanceof TvShowEpisode episode) {
      TmmTreeNode node = new TvShowEpisodeTreeNode(episode, this);
      putNodeToCache(episode, node);
      return node;
    }
    else {
      throw new IllegalArgumentException();
    }
  }

  private TmmTreeNode addTvShowSeason(TvShowSeason season) {
    // check if this season has already been added
    TmmTreeNode cachedNode = getNodeFromCache(season);
    if (cachedNode != null) {
      return cachedNode;
    }

    // add a new node (only if there is at least one EP inside)
    if (!season.getEpisodesForDisplay().isEmpty()) {
      TmmTreeNode node = new TvShowSeasonTreeNode(season, this);
      putNodeToCache(season, node);
      firePropertyChange(NODE_INSERTED, null, node);

      return node;
    }

    return null;
  }

  private void addTvShowEpisode(TvShowEpisode episode) {
    // check if this episode has already been added
    TmmTreeNode cachedNode = getNodeFromCache(episode);
    if (cachedNode != null) {
      return;
    }

    // check if the season is already in the UI
    TmmTreeNode seasonNode = getNodeFromCache(episode.getTvShowSeason());
    if (seasonNode == null) {
      addTvShowSeason(episode.getTvShowSeason());
    }

    // add a new node
    TmmTreeNode node = new TvShowEpisodeTreeNode(episode, this);
    putNodeToCache(episode, node);
    firePropertyChange(NODE_INSERTED, null, node);
  }

  private void removeTvShowEpisode(TvShowEpisode episode) {
    TmmTreeNode cachedNode = removeNodeFromCache(episode);
    if (cachedNode != null) {
      TmmTreeNode seasonNode = (TmmTreeNode) cachedNode.getParent();

      firePropertyChange(NODE_REMOVED, null, cachedNode);

      // and remove the season node too
      if (seasonNode != null && seasonNode.getUserObject() instanceof TvShowSeason season && season.getEpisodesForDisplay().isEmpty()) {
        removeTvShowSeason(season);
      }
    }
  }

  private void removeTvShowSeason(TvShowSeason season) {
    TmmTreeNode cachedNode = removeNodeFromCache(season);
    if (cachedNode == null) {
      return;
    }

    firePropertyChange(NODE_REMOVED, null, cachedNode);
  }

  /*
   * helper classes
   */

  abstract static class AbstractTvShowTreeNode extends TmmTreeNode {
    AbstractTvShowTreeNode(Object userObject, TmmTreeDataProvider<TmmTreeNode> dataProvider) {
      super(userObject, dataProvider);
    }

    abstract String getTitle();

    abstract String getOriginalTitle();

    abstract String getEnglishTitle();
  }

  public static class TvShowTreeNode extends AbstractTvShowTreeNode {
    /**
     * Instantiates a new tv show tree node.
     * 
     * @param userObject
     *          the user object
     */
    TvShowTreeNode(Object userObject, TmmTreeDataProvider<TmmTreeNode> dataProvider) {
      super(userObject, dataProvider);
    }

    /**
     * provides the right name of the node for display.
     * 
     * @return the string
     */
    @Override
    public String toString() {
      // return TV show name
      if (getUserObject() instanceof TvShow tvShow) {
        return tvShow.getTitleSortable();
      }

      // fallback: call super
      return super.toString();
    }

    @Override
    public String getTitle() {
      if (getUserObject() instanceof TvShow tvShow) {
        return tvShow.getTitle();
      }

      return toString();
    }

    @Override
    public String getOriginalTitle() {
      if (getUserObject() instanceof TvShow tvShow) {
        return tvShow.getOriginalTitle();
      }

      return toString();
    }

    @Override
    String getEnglishTitle() {
      if (getUserObject() instanceof TvShow tvShow) {
        return tvShow.getEnglishTitle();
      }

      return toString();
    }
  }

  public static class TvShowSeasonTreeNode extends AbstractTvShowTreeNode {
    /**
     * Instantiates a new tv show season tree node.
     * 
     * @param userObject
     *          the user object
     */
    public TvShowSeasonTreeNode(Object userObject, TmmTreeDataProvider<TmmTreeNode> dataProvider) {
      super(userObject, dataProvider);
    }

    /**
     * provides the right name of the node for display
     */
    @Override
    public String toString() {
      // return season name
      if (getUserObject() instanceof TvShowSeason season) {
        if (season.getSeason() == -1) {
          return TmmResourceBundle.getString("tvshow.uncategorized");
        }

        String title = "";

        if (season.getSeason() == 0) {
          title = TmmResourceBundle.getString("metatag.specials");
        }
        else {
          title = TmmResourceBundle.getString("metatag.season") + " " + season.getSeason();
        }
        if (StringUtils.isNotBlank(season.getTitle()) && !title.strip().equalsIgnoreCase(season.getTitle().strip())) {
          title += " - " + season.getTitle();
        }

        return title;
      }

      // fallback: call super
      return super.toString();
    }

    @Override
    public String getTitle() {
      return toString();
    }

    @Override
    public String getOriginalTitle() {
      return toString();
    }

    @Override
    String getEnglishTitle() {
      return toString();
    }
  }

  public static class TvShowEpisodeTreeNode extends AbstractTvShowTreeNode {
    /**
     * Instantiates a new tv show episode tree node.
     * 
     * @param userObject
     *          the user object
     */
    public TvShowEpisodeTreeNode(Object userObject, TmmTreeDataProvider<TmmTreeNode> dataProvider) {
      super(userObject, dataProvider);
    }

    /**
     * provides the right name of the node for display.
     * 
     * @return the string
     */
    @Override
    public String toString() {
      // return episode name and number
      if (getUserObject() instanceof TvShowEpisode episode) {
        if (episode.getEpisode() >= 0) {
          return episode.getEpisode() + ". " + episode.getTitle();
        }
        else {
          return episode.getTitleSortable();
        }
      }

      // fallback: call super
      return super.toString();
    }

    @Override
    public String getTitle() {
      if (getUserObject() instanceof TvShowEpisode episode) {
        return episode.getTitle();
      }

      return toString();
    }

    @Override
    public String getOriginalTitle() {
      if (getUserObject() instanceof TvShowEpisode episode) {
        return episode.getOriginalTitle();
      }

      return toString();
    }

    @Override
    String getEnglishTitle() {
      if (getUserObject() instanceof TvShowEpisode episode) {
        return episode.getEnglishTitle();
      }

      return toString();
    }
  }
}
