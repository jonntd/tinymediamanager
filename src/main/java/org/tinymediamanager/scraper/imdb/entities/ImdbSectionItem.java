package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbSectionItem extends BaseJsonEntity {
  public String             id          = "";
  public String             rowTitle    = "";
  public String             rowLink     = "";
  public List<ImdbTextType> listContent = new ArrayList<>();
}
