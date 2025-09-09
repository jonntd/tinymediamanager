package org.tinymediamanager.scraper.mdblist.entities;

import java.util.Date;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class MdbReview extends BaseJsonEntity {
  public Date   updated_at;
  public String author;
  public int    rating;
  public int    provider_id;
  public String content;
}
