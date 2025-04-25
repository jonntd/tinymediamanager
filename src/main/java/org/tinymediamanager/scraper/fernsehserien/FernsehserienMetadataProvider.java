package org.tinymediamanager.scraper.fernsehserien;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.interfaces.IMediaProvider;

abstract class FernsehserienMetadataProvider implements IMediaProvider {

  protected static final ExecutorService EXECUTOR = new ThreadPoolExecutor(5, 10, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
  static final String                    ID       = "fernsehserien";
  static final String                    BASE_URL = "https://www.fernsehserien.de";
  private final MediaProviderInfo        providerInfo;

  FernsehserienMetadataProvider() {
    providerInfo = createMediaProviderInfo();
  }

  /**
   * get the sub id of this scraper (for dedicated storage)
   *
   * @return the sub id
   */
  protected abstract String getSubId();

  protected MediaProviderInfo createMediaProviderInfo() {
    return new MediaProviderInfo(ID, getSubId(), "fernsehserien.de", "<html><h3>fernsehserien.de</h3><br />languages: DE</html>",
        FernsehserienMetadataProvider.class.getResource("/org/tinymediamanager/scraper/fernsehseriende.png"));
  }

  @Override
  public MediaProviderInfo getProviderInfo() {
    return providerInfo;
  }

  @Override
  public boolean isActive() {
    return isFeatureEnabled() && isApiKeyAvailable(null);
  }

}
