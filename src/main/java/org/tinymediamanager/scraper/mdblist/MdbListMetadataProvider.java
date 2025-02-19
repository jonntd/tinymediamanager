package org.tinymediamanager.scraper.mdblist;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.FeatureNotEnabledException;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.IMediaProvider;
import org.tinymediamanager.scraper.interfaces.IRatingProvider;
import org.tinymediamanager.scraper.mdblist.entities.MdbMediaEntity;
import org.tinymediamanager.scraper.mdblist.entities.MdbRating;

import retrofit2.Response;

public class MdbListMetadataProvider implements IMediaProvider, IRatingProvider {
  public static final String        ID     = "mdblist";
  private static final Logger       LOGGER = LoggerFactory.getLogger(MdbListMetadataProvider.class);

  protected final MediaProviderInfo providerInfo;
  protected MdbListController       controller;

  public MdbListMetadataProvider() {
    providerInfo = createMediaProviderInfo();
  }

  protected MediaProviderInfo createMediaProviderInfo() {
    return new MediaProviderInfo(ID, "", "mdblist.com",
        "MDBLIst - The Ultimate Dynamic List Manager<br><br>mdblist.com is your go-to tool for creating dynamic, auto-updating movie and show lists tailored to your preferences. Seamlessly integrated with Stremio, it combines the power of multiple rating platforms like IMDb, TMDb, Letterboxd, Rotten Tomatoes, Metacritic, MyAnimeList, and RogerEbert. Whether you’re building a watchlist, tracking ratings, or syncing your library progress, mdblist.com makes it effortless.",
        MdbListMetadataProvider.class.getResource("/org/tinymediamanager/scraper/mdblist.png"));
  }

  @Override
  public boolean isActive() {
    return isFeatureEnabled();
  }

  protected synchronized void initAPI() throws ScrapeException {
    if (controller == null) {
      if (!isActive()) {
        throw new ScrapeException(new FeatureNotEnabledException(this));
      }
      try {
        controller = new MdbListController();
        String apiKey = Settings.getInstance().getMdbListApiKey();
        if (apiKey.isBlank()) {
          throw new ScrapeException("apiKey missing - skipping");
        }
        controller.setApiKey(apiKey);
      }
      catch (Exception e) {
        LOGGER.error("could not initialize the API: {}", e.getMessage());
        // force re-initialization the next time this will be called
        controller = null;
        throw new ScrapeException(e);
      }
    }
  }

  @Override
  public MediaProviderInfo getProviderInfo() {
    return providerInfo;
  }

  @Override
  public List<MediaRating> getRatings(Map<String, Object> ids, MediaType mediaType) throws ScrapeException {
    initAPI();
    List<MediaRating> mediaRatingList = new ArrayList<>();

    // MDBList does only has show (and movie) ratings, so skip for episodes
    if (mediaType == MediaType.TV_EPISODE) {
      return mediaRatingList;
    }

    Response<MdbMediaEntity> response = null;
    for (Map.Entry<String, Object> entry : ids.entrySet()) {
      // Fetch the ratings with the first found ID
      try {
        response = controller.getMediaEntity(entry.getKey().toString(), mediaType, entry.getValue().toString()).execute();
        if (!response.isSuccessful()) {
          String message = "";
          try {
            message = response.errorBody().string();
          }
          catch (IOException e) {
            // ignore
          }
          LOGGER.warn("request was not successful: HTTP/{} - {}", response.code(), message);
        }
        else {
          break;
        }
      }
      catch (Exception e) {
        LOGGER.error("request was not successful:{}", e);
      }
    }

    List<MdbRating> ratings = response.body().ratings;
    // Loop over result to get all Ratings and add them to list of media ratings
    for (MdbRating rating : ratings) {
      if (rating.source == null || (rating.value == 0f && !rating.source.equals(MediaMetadata.ROGER_EBERT))) {
        continue;
      }
      MediaRating mediaRating = new MediaRating(rating.source, rating.value, rating.votes);
      switch (rating.source) {
        case MediaMetadata.LETTERBOXD: {
          mediaRating.setMaxValue(5);
          break;
        }

        case MediaMetadata.ROGER_EBERT: {
          mediaRating.setMaxValue(4); // yes, 4
          break;
        }
      }
      mediaRatingList.add(mediaRating);
    }

    return mediaRatingList;
  }
}
