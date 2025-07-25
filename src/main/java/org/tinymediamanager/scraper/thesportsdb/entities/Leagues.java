package org.tinymediamanager.scraper.thesportsdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Leagues extends BaseJsonEntity {
  public List<LeagueDetail> leagues = new ArrayList<>();
}
