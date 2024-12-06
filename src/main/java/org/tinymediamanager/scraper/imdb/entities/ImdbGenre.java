package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.core.entities.MediaGenres;
import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbGenre extends BaseJsonEntity {
  public String id;
  public String text;

  public MediaGenres toTmm() {
    return MediaGenres.getGenre(id);
  }
}
