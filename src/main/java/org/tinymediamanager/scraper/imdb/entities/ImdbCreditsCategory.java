package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbCreditsCategory extends BaseJsonEntity {
  public String                     id      = "";
  public String                     name    = "";
  public ImdbCreditsCategorySection section = null;
  public Object                     pagination;
  public Object                     description;
}
