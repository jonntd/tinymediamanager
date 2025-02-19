package org.tinymediamanager.scraper.rating;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.mdblist.MdbListMetadataProvider;

public class ITMdbListRatingTest {

  @Test
  public void testMovieRatings() throws Exception {
    MdbListMetadataProvider mp = new MdbListMetadataProvider();
    Map<String, Object> ids = new HashMap<>();
    ids.put(MediaMetadata.IMDB, "tt6718170");
    List<MediaRating> mediaRatings = mp.getRatings(ids, MediaType.MOVIE); // Super Mario Bros Movie
    assertThat(mediaRatings).isNotEmpty();
  }

  @Test
  public void testShowRatings() throws Exception {
    MdbListMetadataProvider mp = new MdbListMetadataProvider();
    Map<String, Object> ids = new HashMap<>();
    ids.put(MediaMetadata.TMDB, 8475);
    List<MediaRating> mediaRatings = mp.getRatings(ids, MediaType.TV_SHOW);
    assertThat(mediaRatings).isNotEmpty();

    // since "episode" is not a valid type on their side, it would fallback to "movie"... and scrape something else ;)
    ids.put(MediaMetadata.TMDB, 396535);
    mediaRatings = mp.getRatings(ids, MediaType.TV_EPISODE);
    assertThat(mediaRatings).isEmpty();
  }
}
