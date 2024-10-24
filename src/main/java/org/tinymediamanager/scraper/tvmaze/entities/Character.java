package org.tinymediamanager.scraper.tvmaze.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Character extends BaseJsonEntity {
  public int       id;
  public String    url;
  public String    name;
  public ImageUrls image;
}
