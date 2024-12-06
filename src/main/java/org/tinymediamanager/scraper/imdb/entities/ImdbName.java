package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbName extends BaseJsonEntity {
  public String       id           = "";
  public ImdbNameText nameText     = null;
  public ImdbImage    primaryImage = null;

}
