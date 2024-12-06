package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;
import org.tinymediamanager.scraper.entities.MediaType;

public class ImdbTitleType extends BaseJsonEntity {
  public String  id              = "";
  public String  text            = "";
  public boolean canHaveEpisodes = false;
  public boolean isEpisode       = false;
  public boolean isSeries        = false;

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
