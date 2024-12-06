package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ImdbSearchResult {

  public String              id                    = "";
  public String              titleNameText         = "";
  public String              titleReleaseText      = "";
  public String              titleTypeText         = "";
  public ImdbImageString     titlePosterImageModel = null;
  public List<String>        topCredits            = new ArrayList<>();
  public ImdbTitleType       imageType             = null;
  public String              seriesId              = "";
  public String              seriesNameText        = "";
  public String              seriesReleaseText     = "";
  public String              seriesTypeText        = "";
  public String              seriesSeasonText      = "";
  public String              seriesEpisodeText     = "";
  @JsonIgnore
  public Map<String, Object> additionalProperties  = new HashMap<>();

  public String getId() {
    return id;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}
