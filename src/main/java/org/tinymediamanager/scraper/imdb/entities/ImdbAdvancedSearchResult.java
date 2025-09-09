package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbAdvancedSearchResult extends BaseJsonEntity {

  public String            certificate       = "";               // eg 12 or TV-14
  public List<String>      genres            = new ArrayList<>();
  public String            originalTitleText = "";
  public int               metascore         = 0;
  public String            plot              = "";
  public ImdbImageString   primaryImage      = null;
  public ImdbRatingSummary ratingSummary     = null;
  public int               releaseYear       = 0;
  /** in minutes */
  public int               runtime           = 0;
  public String            titleId           = "";
  public String            titleText         = "";
  public ImdbTitleType     titleType         = null;
  public List<String>      topCast           = new ArrayList<>();
  public String            trailerId         = "";
}
