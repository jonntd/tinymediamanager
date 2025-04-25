package org.tinymediamanager.scraper.thesportsdb;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.tinymediamanager.core.FeatureNotEnabledException;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.IMediaProvider;
import org.tinymediamanager.scraper.tmdb.entities.Configuration;

public abstract class TheSportsDbMetadataProvider implements IMediaProvider {
  private final MediaProviderInfo providerInfo;

  protected TheSportsDbController api;
  protected Configuration         configuration;
  protected String                artworkBaseUrl;

  TheSportsDbMetadataProvider() {
    providerInfo = createMediaProviderInfo();
  }

  /**
   * get the sub id of this scraper (for dedicated storage)
   *
   * @return the sub id
   */
  protected abstract String getSubId();

  protected MediaProviderInfo createMediaProviderInfo() {
    MediaProviderInfo info = new MediaProviderInfo(MediaMetadata.TSDB, getSubId(), "thesportsdb.com",
        "<html><h3>The Sports Database (TSDb)</h3><br />An open, crowd-sourced sports database of artwork and metadata<br /><br />Available languages: multiple</html>",
        TheSportsDbMetadataProvider.class.getResource("/org/tinymediamanager/scraper/thesportsdb.svg"), 50);

    info.getConfig().addText(MediaProviderInfo.API_KEY, "", true);
    info.getConfig().load();

    return info;
  }

  @Override
  public MediaProviderInfo getProviderInfo() {
    return providerInfo;
  }

  protected abstract Logger getLogger();

  protected synchronized void initAPI() throws ScrapeException {

    // check if the API should change from current key to another
    if (api != null) {
      String userApiKey = providerInfo.getUserApiKey();
      if (StringUtils.isNotBlank(userApiKey) && !userApiKey.equals(api.apiKey())) {
        // force re-initialization with new key
        api = null;
      }
      else if (StringUtils.isBlank(userApiKey) && !getApiKey().equals(api.apiKey())) {
        // force re-initialization with new key
        api = null;
      }
    }

    if (api == null) {
      if (!isActive()) {
        throw new ScrapeException(new FeatureNotEnabledException(this));
      }
      try {
        String userApiKey = providerInfo.getUserApiKey();
        api = new TheSportsDbController(StringUtils.isNotBlank(userApiKey) ? userApiKey : getApiKey());
      }
      catch (Exception e) {
        getLogger().error("could not initialize the API: {}", e.getMessage());
        // force re-initialization the next time this will be called
        api = null;
        throw new ScrapeException(e);
      }
    }
  }

  @Override
  public boolean isActive() {
    return isFeatureEnabled() && isApiKeyAvailable(providerInfo.getUserApiKey());
  }

}
