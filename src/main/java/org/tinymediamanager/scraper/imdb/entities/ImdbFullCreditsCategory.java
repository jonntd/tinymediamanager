package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbFullCreditsCategory extends BaseJsonEntity {

  public ImdbIdTextType category     = null;             // id
  public ImdbIdTextType categoryText = null;             // text translated
  public List<ImdbCrew> credits      = new ArrayList<>();
}
