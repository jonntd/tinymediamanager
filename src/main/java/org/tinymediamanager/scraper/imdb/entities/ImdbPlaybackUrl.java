package org.tinymediamanager.scraper.imdb.entities;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbPlaybackUrl extends BaseJsonEntity {
  public ImdbLocalizedString displayName     = null;
  public String              videoMimeType   = "";  // FIXME: check for valid renaming
  public String              videoDefinition = "";  // DEF_AUTO, DEF_480p, DEF_SD
  public String              url             = "";
}
