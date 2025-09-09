package org.tinymediamanager.scraper.tvmaze.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Network extends BaseJsonEntity {
  public int     id;
  public String  name;
  public Country country;
  public String  officialSite;
}
