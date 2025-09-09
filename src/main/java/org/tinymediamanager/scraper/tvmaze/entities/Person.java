package org.tinymediamanager.scraper.tvmaze.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Person extends BaseJsonEntity {
  public int       id;
  public String    url;
  public String    name;
  public Country   country;
  public String    birthday;
  public String    deathday;
  public String    gender;
  public ImageUrls image;
}
