package org.tinymediamanager.scraper.thesportsdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Countries extends BaseJsonEntity {
  public List<Country> countries = new ArrayList<>();
}
