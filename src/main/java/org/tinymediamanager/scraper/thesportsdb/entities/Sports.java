package org.tinymediamanager.scraper.thesportsdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Sports extends BaseJsonEntity {
  public List<Sport> sports = new ArrayList<>();
}
