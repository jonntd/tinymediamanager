package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbEpisodeListEpisode extends BaseJsonEntity {

  public String          id              = "";
  public String          episode         = "";
  public String          season          = "";
  public String          titleText       = "";
  public String          plot            = "";
  public double          aggregateRating = 0.0;
  public int             voteCount       = 0;
  public ImdbReleaseDate releaseDate     = null;
  public int             releaseYear     = 0;
  public ImdbImageString image           = null;
}
