package org.tinymediamanager.scraper.imdb.entities;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.tinymediamanager.scraper.entities.MediaType;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ImdbTitleType {
  public String               id                   = "";
  public String               text                 = "";
  public boolean              canHaveEpisodes      = false;
  public boolean              isEpisode            = false;
  public boolean              isSeries             = false;

  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<>();

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  /**
   * maps internal groups to our mediaTypes - if it must be parsed as movie or tvshow with episodes
   * 
   * @return MediaType or NULL if we cannot identify it
   */
  public MediaType getMediaType() {
    // (slightly different than advanecSearch TitleTypes)
    switch (id) {
      case "movie":
      case "tvMovie":
      case "tvSpecial":
      case "documentary":
      case "short":
      case "tvShort":
      case "musicVideo":
      case "video":
        return MediaType.MOVIE;

      case "tvSeries":
      case "tvMiniSeries":
      case "podcastSeries":
        return MediaType.TV_SHOW;

      case "tvEpisode":
      case "podcastEpisode":
        return MediaType.TV_EPISODE;

      case "videoGame":
      default:
        break;
    }
    return null;
  }
}
