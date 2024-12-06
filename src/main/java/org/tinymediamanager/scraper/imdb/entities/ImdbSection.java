package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbSection extends BaseJsonEntity {
  public int                   total     = 0;
  public String                endCursor = "";
  public String                rowLink   = "";
  public List<ImdbSectionItem> items     = new ArrayList<>();
}
