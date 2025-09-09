package org.tinymediamanager.scraper.thesportsdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Seasons extends BaseJsonEntity {
  public List<Season> seasons = new ArrayList<>();
}
