package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbChartTitleEdge extends BaseJsonEntity {

  public int    currentRank = 0;
  public ImdbId node        = null;
}
