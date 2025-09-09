package org.tinymediamanager.scraper.tvmaze.entities;

import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class Embed extends BaseJsonEntity {
  public List<Cast>    cast;
  public List<Crew>    crew;
  public List<Cast>    guestcast;
  public List<Crew>    guestcrew;
  public List<Season>  seasons;
  public List<Image>   images;
  public List<Episode> episodes;
}
