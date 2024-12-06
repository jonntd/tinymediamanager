package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbImageString extends BaseJsonEntity {

  public String  id        = "";
  public String  url       = "";
  public Integer height    = 0;
  public Integer width     = 0;
  public Integer maxHeight = 0;
  public Integer maxWidth  = 0;
  public String  caption   = "";

  public int getHeight() {
    return height == 0 ? maxHeight : height;
  }

  public int getWidth() {
    return width == 0 ? maxWidth : width;
  }
}
