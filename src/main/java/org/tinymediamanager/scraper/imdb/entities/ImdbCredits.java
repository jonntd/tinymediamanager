package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbCredits extends BaseJsonEntity {

  public ImdbIdTextType category = null;
  public List<ImdbCrew> credits  = new ArrayList<>();
}
