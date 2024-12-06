package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbSearchResult extends BaseJsonEntity {

  public String          id                    = "";
  public String          titleNameText         = "";
  public String          titleReleaseText      = "";
  public String          titleTypeText         = "";
  public ImdbImageString titlePosterImageModel = null;
  public List<String>    topCredits            = new ArrayList<>();
  public ImdbTitleType   imageType             = null;
  public String          seriesId              = "";
  public String          seriesNameText        = "";
  public String          seriesReleaseText     = "";
  public String          seriesTypeText        = "";
  public String          seriesSeasonText      = "";
  public String          seriesEpisodeText     = "";
}
