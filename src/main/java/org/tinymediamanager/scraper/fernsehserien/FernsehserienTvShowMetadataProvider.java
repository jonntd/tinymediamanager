/*
 * Copyright 2012 - 2023 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.scraper.fernsehserien;

import java.util.List;
import java.util.SortedSet;

import org.tinymediamanager.core.FeatureNotEnabledException;
import org.tinymediamanager.core.tvshow.TvShowEpisodeSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.TvShowSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.ITvShowMetadataProvider;

/**
 * the class {@link FernsehserienTvShowMetadataProvider} provides metadata for TV shows
 *
 * @author Manuel Laggner
 */
public class FernsehserienTvShowMetadataProvider extends FernsehserienMetadataProvider implements ITvShowMetadataProvider {

  @Override
  protected MediaProviderInfo createMediaProviderInfo() {
    MediaProviderInfo providerInfo = super.createMediaProviderInfo();

    return providerInfo;
  }

  @Override
  protected String getSubId() {
    return "tvshow";
  }

  @Override
  public MediaMetadata getMetadata(TvShowSearchAndScrapeOptions options) throws ScrapeException {
    if (!isActive()) {
      throw new ScrapeException(new FeatureNotEnabledException(this));
    }

    return (new FernsehserienParser(this, EXECUTOR)).getMetadata(options);
  }

  @Override
  public MediaMetadata getMetadata(TvShowEpisodeSearchAndScrapeOptions options) throws ScrapeException {
    if (!isActive()) {
      throw new ScrapeException(new FeatureNotEnabledException(this));
    }

    return (new FernsehserienParser(this, EXECUTOR)).getEpisodeMetadata(options);
  }

  @Override
  public SortedSet<MediaSearchResult> search(TvShowSearchAndScrapeOptions options) throws ScrapeException {

    if (!isActive()) {
      throw new ScrapeException(new FeatureNotEnabledException(this));
    }

    return (new FernsehserienParser(this, EXECUTOR)).search(options);
  }

  @Override
  public List<MediaMetadata> getEpisodeList(TvShowSearchAndScrapeOptions options) throws ScrapeException {

    if (!isActive()) {
      throw new ScrapeException(new FeatureNotEnabledException(this));
    }

    return new FernsehserienParser(this, EXECUTOR).getEpisodeList(options);
  }

}
