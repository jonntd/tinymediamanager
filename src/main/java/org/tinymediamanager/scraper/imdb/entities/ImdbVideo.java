package org.tinymediamanager.scraper.imdb.entities;

import java.util.ArrayList;
import java.util.List;

import org.tinymediamanager.scraper.entities.BaseJsonEntity;

public class ImdbVideo extends BaseJsonEntity {
  public String                id           = "";
  public boolean               isMature     = false;
  public String                createdDate  = "";
  public ImdbImage             thumbnail    = null;
  public ImdbLocalizedString   description  = null;
  public ImdbLocalizedString   name         = null;
  public List<ImdbPlaybackUrl> playbackURLs = new ArrayList<>();
}
