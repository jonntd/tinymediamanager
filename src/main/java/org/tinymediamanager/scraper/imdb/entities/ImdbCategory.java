package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbCategory extends BaseJsonEntity {
  public String      id      = "";
  public String      name    = "";
  public ImdbSection section = null;
}
