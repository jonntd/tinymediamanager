package org.tinymediamanager.scraper.mdblist.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class MdbSeason extends BaseJsonEntity {
  public int    tmdbid;
  public String name;
  // public Date air_date;
  public String air_date;     // cannot parse with default typeAdapter
  public int    episode_count;
  public int    season_number;
  public String tomatofresh;
  public String poster_path;
}