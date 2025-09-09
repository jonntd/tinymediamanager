package org.tinymediamanager.scraper.mdblist.entities;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class MdbMediaEntity extends BaseJsonEntity {
  public String              title;
  public int                 year;
  public Date                released;
  public Date                released_digital;
  public String              description;
  public int                 runtime;
  public int                 score;
  public int                 score_average;
  public Map<String, Object> ids = new ConcurrentHashMap<>(0);
  public String              type;
  public List<MdbRating>     ratings;
  public List<MdbIdTitle>    genres;
  public List<MdbIdName>     streams;
  public List<MdbIdName>     watch_providers;
  public List<MdbIdName>     production_companies;
  public String              language;
  public String              spspoken_language;
  public String              country;
  public String              certification;
  public Boolean             commonsense;
  public int                 age_rating;
  public String              status;
  public String              trailer;
  public String              poster;
  public String              backdrop;
  public List<MdbIdName>     keywords;
  public List<MdbSeason>     seasons;
}
