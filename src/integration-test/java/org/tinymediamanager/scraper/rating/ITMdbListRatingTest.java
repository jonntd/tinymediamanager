package org.tinymediamanager.scraper.rating;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.entities.MediaType;

public class ITMdbListRatingTest {

  @Test
  public void testGetRatings() throws Exception {

    Map<String, Object> ids = new HashMap<>();

    ids.put(MediaMetadata.TMDB, 8475);

    MdbListRating ratings = new MdbListRating();
    List<MediaRating> mediaRatings = ratings.getRatings(MediaType.TV_SHOW, ids); 

    assertThat(mediaRatings).isNotEmpty();
  }
}
