package org.tinymediamanager.scraper.thesportsdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Lineups extends BaseJsonEntity {
  public List<Lineup> lineup = new ArrayList<>();
}
