package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbCreditsCategorySection extends BaseJsonEntity {
  public List<ImdbCreditsCategoryPerson> items           = new ArrayList<>();
  public int                           listItemType    = 0;                // 2 = person list ?!
  public int                           total           = 0;
  public String                        endCursor       = "";               // base64
  public int                           splitIndex      = 0;
  public String                        subSectionTitle = "";               // cast
}
