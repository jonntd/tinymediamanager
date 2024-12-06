package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbRatingSummary extends BaseJsonEntity {
  public double aggregateRating = 0.0;
  public int    voteCount       = 0;
}
