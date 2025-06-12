package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbImage extends BaseJsonEntity {

  public String        id        = "";
  public String        url       = "";
  public Integer       height    = 0;
  public Integer       width     = 0;
  public Integer       maxHeight = 0;
  public Integer       maxWidth  = 0;
  public ImdbPlaintext caption   = null;

  public int getHeight() {
    return height == 0 ? maxHeight : height;
  }

  public int getWidth() {
    return width == 0 ? maxWidth : width;
  }
}
