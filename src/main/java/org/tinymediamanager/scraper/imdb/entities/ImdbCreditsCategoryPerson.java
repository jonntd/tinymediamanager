package org.tinymediamanager.scraper.imdb.entities;

import java.util.Collections;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbCreditsCategoryPerson extends BaseJsonEntity {
  public String         id         = "";
  public String         rowTitle   = "";
  public boolean        isSeries   = false;
  public boolean        isCast     = false;
  public Object         refTagSuffix;                        // can be either STRING "cr" or JSON "{t="type",n=1}"
  public String         attributes = "";                     // like (voice), (Synchronisation) or similar
  public List<String>   characters = Collections.emptyList();
  public ImdbImageProps imageProps;
}
