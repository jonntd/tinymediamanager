package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbShowEpisodes extends BaseJsonEntity {
  public EpisodeConnection    episodes              = null;
  public List<EpisodesSeason> seasons               = new ArrayList<EpisodesSeason>();
  public List<EpisodesYear>   years                 = new ArrayList<EpisodesYear>();
  public EpisodeConnection    totalEpisodes         = null;
  public EpisodeConnection    unknownSeasonEpisodes = null;

  public List<Integer> getSeasons() {
    List<Integer> ret = new ArrayList<>();
    for (EpisodesSeason ep : seasons) {
      ret.add(ep.number);
    }
    Collections.sort(ret);
    return ret;
  }

  public List<Integer> getYears() {
    List<Integer> ret = new ArrayList<>();
    for (EpisodesYear ep : years) {
      ret.add(ep.year);
    }
    Collections.sort(ret);
    return ret;
  }

  public static class EpisodeConnection extends BaseJsonEntity {
    public int total = 0;
  }

  public static class EpisodesSeason extends BaseJsonEntity {
    public int number = 0;
  }

  public static class EpisodesYear extends BaseJsonEntity {
    public int year = 0;
  }
}
