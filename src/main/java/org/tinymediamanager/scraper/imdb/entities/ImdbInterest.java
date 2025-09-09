package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

// https://www.imdb.com/de/interest/all/
public class ImdbInterest extends BaseJsonEntity {
  public String       id = "";
  public ImdbTextType primaryText;
  public ImdbImage    primaryImage;
}
