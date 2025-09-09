/*
 * Copyright 2012 - 2025 Manuel Laggner
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
package org.tinymediamanager.core.tvshow.tasks;

import static org.tinymediamanager.core.tvshow.TvShowArtworkHelper.sortArtworkUrls;
import static org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType.THUMB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.ScraperMetadataConfig;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.tvshow.TvShowEpisodeScraperMetadataConfig;
import org.tinymediamanager.core.tvshow.TvShowEpisodeSearchAndScrapeOptions;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowRenamer;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.ArtworkSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScraper;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.MissingIdException;
import org.tinymediamanager.scraper.exceptions.NothingFoundException;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.ITvShowArtworkProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowMetadataProvider;
import org.tinymediamanager.scraper.rating.RatingProvider;
import org.tinymediamanager.scraper.util.ListUtils;
import org.tinymediamanager.scraper.util.MediaIdUtil;

/**
 * The Class TvShowEpisodeScrapeTask.
 * 
 * @author Manuel Laggner
 */
public class TvShowEpisodeScrapeTask extends TmmTask {

  private static final Logger                            LOGGER   = LoggerFactory.getLogger(TvShowEpisodeScrapeTask.class);

  private final List<TvShowEpisode>                      episodes = new ArrayList<>();
  private final List<TvShowEpisodeScraperMetadataConfig> config   = new ArrayList<>();
  private final TvShowEpisodeSearchAndScrapeOptions      scrapeOptions;
  private final boolean                                  overwrite;

  /**
   * Instantiates a new tv show episode scrape task.
   * 
   * @param episodes
   *          the episodes to scrape
   * @param options
   *          the scraper options to use
   */
  public TvShowEpisodeScrapeTask(List<TvShowEpisode> episodes, TvShowEpisodeSearchAndScrapeOptions options,
      List<TvShowEpisodeScraperMetadataConfig> config, boolean overwrite) {
    super(TmmResourceBundle.getString("tvshow.scraping"), episodes.size(), TaskType.BACKGROUND_TASK);
    this.episodes.addAll(episodes);
    this.config.addAll(config);
    this.scrapeOptions = options;
    this.overwrite = overwrite;
  }

  @Override
  public void doInBackground() {
    setWorkUnits(episodes.size());

    MediaScraper mediaScraper = scrapeOptions.getMetadataScraper();

    if (!mediaScraper.isEnabled()) {
      return;
    }

    int count = 0;
    for (TvShowEpisode episode : episodes) {
      publishState(count++);

      // only scrape if at least one ID is available
      if (episode.getTvShow().getIds().isEmpty()) {
        LOGGER.warn("Cannot scrape episode (no ID available): '{}' - '{}'", episode.getTvShow().getTitle(), episode.getTitle());
        continue;
      }

      TvShowEpisodeSearchAndScrapeOptions options = new TvShowEpisodeSearchAndScrapeOptions(scrapeOptions);

      MediaMetadata md = new MediaMetadata(mediaScraper.getMediaProvider().getProviderInfo().getId());
      md.setScrapeOptions(options);
      md.setReleaseDate(episode.getFirstAired()); // airedDate matching
      md.setTitle(episode.getTitle()); // for title matching
      options.setMetadata(md);
      options.setIds(episode.getIds());
      options.setEpisodeGroup(episode.getEpisodeGroup());

      // have a look if the wanted episode order is available
      if (episode.getSeason() > -1 && episode.getEpisode() > -1) {
        // found -> pass it to the scraper
        options.setId(MediaMetadata.SEASON_NR, episode.getSeason());
        options.setId(MediaMetadata.EPISODE_NR, episode.getEpisode());
      }
      else {
        // not found. Fall back to the default one
        options.setId(MediaMetadata.SEASON_NR, episode.getAiredSeason());
        options.setId(MediaMetadata.EPISODE_NR, episode.getAiredEpisode());
      }

      options.setTvShowIds(episode.getTvShow().getIds());

      try {
        LOGGER.info("Scraping episode '{}' (S{} E{}) with '{}'", episode.getTitle(), episode.getSeason(), episode.getEpisode(),
            mediaScraper.getMediaProvider().getProviderInfo().getId());

        LOGGER.debug("=====================================================");
        LOGGER.debug("Scrape episode metadata with scraper: {}", mediaScraper.getMediaProvider().getProviderInfo().getId());
        LOGGER.debug(options.toString());
        LOGGER.debug("=====================================================");
        MediaMetadata metadata = ((ITvShowMetadataProvider) mediaScraper.getMediaProvider()).getMetadata(options);

        // also inject other ids
        metadata.setId(MediaMetadata.TVSHOW_IDS, options.getTvShowIds());
        MediaIdUtil.injectMissingIds(metadata.getIds(), MediaType.TV_EPISODE);

        // also fill other ratings if ratings are requested
        if (TvShowModuleManager.getInstance().getSettings().isFetchAllRatings() && config.contains(TvShowEpisodeScraperMetadataConfig.RATING)) {
          for (MediaRating rating : ListUtils.nullSafe(RatingProvider.getRatings(metadata.getIds(),
              TvShowModuleManager.getInstance().getSettings().getFetchRatingSources(), MediaType.TV_EPISODE))) {
            if (!metadata.getRatings().contains(rating)) {
              metadata.addRating(rating);
            }
          }
        }

        metadata.removeId(MediaMetadata.TVSHOW_IDS);

        if (StringUtils.isNotBlank(metadata.getTitle())) {
          episode.setMetadata(metadata, config, overwrite);
          episode.setLastScraperId(scrapeOptions.getMetadataScraper().getId());
          episode.setLastScrapeLanguage(scrapeOptions.getLanguage().name());
          episode.saveToDb();
        }

        if (cancel) {
          return;
        }

        // scrape artwork if wanted
        if (ScraperMetadataConfig.containsAnyArtwork(config)) {
          List<MediaArtwork> artworks = getArtwork(episode, metadata, options);

          // also add the thumb url from the metadata provider to the end (in case, the artwork provider does not fetch anything)
          artworks.addAll(metadata.getMediaArt(MediaArtwork.MediaArtworkType.THUMB));

          // thumb
          if (config.contains(TvShowEpisodeScraperMetadataConfig.THUMB)
              && (overwrite || StringUtils.isBlank(episode.getArtworkFilename(MediaFileType.THUMB)))) {
            int preferredSizeOrder = TvShowModuleManager.getInstance().getSettings().getImageThumbSize().getOrder();

            // sort artwork due to our preferences
            List<MediaArtwork.ImageSizeAndUrl> sortedThumbs = sortArtworkUrls(artworks, THUMB, preferredSizeOrder);

            // assign and download the poster
            if (!sortedThumbs.isEmpty()) {
              MediaArtwork.ImageSizeAndUrl foundThumb = sortedThumbs.get(0);
              episode.setArtworkUrl(foundThumb.getUrl(), MediaFileType.THUMB);
              episode.downloadArtwork(MediaFileType.THUMB, false);
              episode.saveToDb();
              episode.writeNFO();
            }
          }
        }

        if (cancel) {
          return;
        }
      }
      catch (MissingIdException e) {
        LOGGER.warn("Missing IDs for scraping TV show '{}', episode '{}' with '{}'", episode.getTvShow().getTitle(), episode.getTitle(),
            mediaScraper.getId());
        MessageManager.getInstance().pushMessage(new Message(Message.MessageLevel.ERROR, episode, "scraper.error.missingid"));
      }
      catch (NothingFoundException e) {
        LOGGER.info("Nothing found for scraping TV show '{}', episode '{}' with '{}'", episode.getTvShow().getTitle(), episode.getTitle(),
            mediaScraper.getId());
      }
      catch (ScrapeException e) {
        LOGGER.error("Could not scrape TV show '{}', episode '{}' with '{}' - '{}'", episode.getTvShow().getTitle(), episode.getTitle(),
            mediaScraper.getId(), e.getMessage());
        MessageManager.getInstance()
            .pushMessage(new Message(Message.MessageLevel.ERROR, episode, "message.scrape.metadataepisodefailed",
                new String[] { ":", e.getLocalizedMessage() }));
      }
      catch (Exception e) {
        LOGGER.error("Unforeseen error in episode scrape for '{}' - '{}'", episode.getTvShow().getTitle(), episode.getTitle(), e);
      }
    }

    if (cancel) {
      return;
    }

    // all episodes scraped now - dedicated rename all if wanted
    if (TvShowModuleManager.getInstance().getSettings().isRenameAfterScrape()) {
      for (TvShowEpisode episode : episodes) {
        TvShowRenamer.renameEpisode(episode);
      }
    }

    // and scrape actor images
    if (ScraperMetadataConfig.containsAnyCast(config) && TvShowModuleManager.getInstance().getSettings().isWriteActorImages()) {
      for (TvShowEpisode episode : episodes) {
        episode.writeActorImages(overwrite);
      }
    }
  }

  /**
   * Gets the artwork.
   *
   * @param episode
   *          the {@link TvShowEpisode} to get the artwork for
   * @param metadata
   *          already scraped {@link MediaMetadata}
   * @param scrapeOptions
   *          the {@link TvShowEpisodeSearchAndScrapeOptions} to use for atwork scraping
   * @return the artwork
   */
  private List<MediaArtwork> getArtwork(TvShowEpisode episode, MediaMetadata metadata, TvShowEpisodeSearchAndScrapeOptions scrapeOptions) {
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    List<MediaArtwork> artwork = new ArrayList<>();

    ArtworkSearchAndScrapeOptions options = new ArtworkSearchAndScrapeOptions(MediaType.TV_EPISODE);
    options.setDataFromOtherOptions(scrapeOptions);
    options.setArtworkType(MediaArtwork.MediaArtworkType.ALL);
    options.setMetadata(metadata);
    options.addIds(episode.getIds());
    options.setId(MediaMetadata.TVSHOW_IDS, episode.getTvShow().getIds());
    options.setId("mediaFile", episode.getMainFile());
    options.setThumbSize(TvShowModuleManager.getInstance().getSettings().getImageThumbSize());
    options.setEpisodeGroup(episode.getEpisodeGroup());

    // get episode images - either from all sources (if configured) or from the SAME scraper as the metadata has been fetched
    // speedup things by parallelizing
    scrapeOptions.getArtworkScrapers().parallelStream().forEach(artworkScraper -> {
      if (TvShowModuleManager.getInstance().getSettings().isImageEpisodeScrapeAllSources()
          || artworkScraper.getId().equals(scrapeOptions.getMetadataScraper().getId())) {
        ITvShowArtworkProvider artworkProvider = (ITvShowArtworkProvider) artworkScraper.getMediaProvider();
        try {
          lock.writeLock().lock();
          artwork.addAll(artworkProvider.getArtwork(options));
        }
        catch (MissingIdException ignored) {
          LOGGER.warn("Missing IDs for getting artwork for TV show '{}', episode '{}' with '{}'", episode.getTvShow().getTitle(), episode.getTitle(),
              artworkScraper.getId());
        }
        catch (ScrapeException e) {
          LOGGER.error("Could not scrape artwork for TV show '{}', episode '{}' with '{}' - '{}'", episode.getTvShow().getTitle(), episode.getTitle(),
              artworkProvider.getId(), e.getMessage());
          MessageManager.getInstance()
              .pushMessage(new Message(Message.MessageLevel.ERROR, episode, "message.scrape.tvshowartworkfailed",
                  new String[] { ":", e.getLocalizedMessage() }));
        }
        catch (Exception e) {
          LOGGER.error("Unforeseen error in movie artwork scrape for TV show '{}', episode '{}' with '{}'", episode.getTvShow().getTitle(),
              episode.getTitle(), artworkProvider.getId(), e);
        }
        finally {
          lock.writeLock().unlock();
        }
      }
    });

    // sort by size descending
    artwork.sort((o1, o2) -> {
      if (o1.getBiggestArtwork() == null || o2.getBiggestArtwork() == null) {
        return 0;
      }
      return Integer.compare(o2.getBiggestArtwork().getWidth(), o1.getBiggestArtwork().getWidth());
    });

    return artwork;
  }
}
