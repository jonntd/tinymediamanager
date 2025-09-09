package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbName extends BaseJsonEntity {
  public String       id           = "";
  public ImdbTextType nameText     = null;
  public ImdbImage    primaryImage = null;

}
