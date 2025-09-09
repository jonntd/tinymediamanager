package org.tinymediamanager.scraper.mdblist.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class MdbRating extends BaseJsonEntity {
  public String source;
  public float  value;
  public int    score;
  public int    votes;
  /**
   * id of source
   */
  public String url;

}
