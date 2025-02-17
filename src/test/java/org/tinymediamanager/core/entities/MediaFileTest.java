package org.tinymediamanager.core.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;
import org.tinymediamanager.core.BasicTest;
import org.tinymediamanager.core.MediaFileHelper;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.Movie;

public class MediaFileTest extends BasicTest {

  private String datasource;

  @Override
  public void setup() throws Exception {
    super.setup();

    datasource = getWorkFolder().resolve("datasource").toAbsolutePath().toString();
    MovieModuleManager.getInstance().getSettings().addMovieDataSources(datasource);
  }

  @Test
  public void mediaFileTypeNoTrailer() {
    Path filename = getWorkFolder().resolve("South Park - S00E00 - The New Terrance and Phillip Movie Trailer.avi");
    MediaFileType mft = MediaFileHelper.parseMediaFileType(filename);
    assertEqual(MediaFileType.VIDEO, mft);
  }

  @Test
  public void decade() {
    Movie m = new Movie();
    m.setYear(1985);
    assertThat(m.getDecadeLong()).isEqualTo("1980-1989");
    assertThat(m.getDecadeShort()).isEqualTo("1980s");

    m.setYear(201);
    assertThat(m.getDecadeLong()).isEqualTo("200-209");
    assertThat(m.getDecadeShort()).isEqualTo("200s");
  }

  @Test
  public void testMediaFileTypeDetectionVideo() {
    checkMediaFileType("This.is.Trailer.park.Boys.mkv", MediaFileType.VIDEO);
    checkMediaFileType("BDMV", MediaFileType.VIDEO);
    checkMediaFileType("VIDEO_TS", MediaFileType.VIDEO);
    checkMediaFileType("HVDVD_TS", MediaFileType.VIDEO);
  }

  @Test
  public void testMediaFileTypeDetectionExtras() {
    checkMediaFileType("So.Dark.the.Night.1946.720p.BluRay.x264-x0r.EXTRAS.mkv", MediaFileType.EXTRA);

    checkMediaFileType("movie.extra.mkv", MediaFileType.EXTRA);
    checkMediaFileType("movie-extra.mp4", MediaFileType.EXTRA);
    checkMediaFileType("movie_extra.avi", MediaFileType.EXTRA);
    checkMediaFileType("movie.extra1.mkv", MediaFileType.EXTRA);
    checkMediaFileType("movie-extra2.mp4", MediaFileType.EXTRA);
    checkMediaFileType("movie_extra3.avi", MediaFileType.EXTRA);

    checkMediaFileType("This.is.Trailer.park.Boys.extras.mkv", MediaFileType.EXTRA);
    checkMediaFileType("This.is.Trailer.park.Boys-extras.mp4", MediaFileType.EXTRA);
    checkMediaFileType("This.is.Trailer.park.Boys_extras.avi", MediaFileType.EXTRA);
    checkMediaFileType("This.is.Trailer.park.Boys.extras1.mkv", MediaFileType.EXTRA);
    checkMediaFileType("This.is.Trailer.park.Boys-extras2.mp4", MediaFileType.EXTRA);
    checkMediaFileType("This.is.Trailer.park.Boys_extras3.avi", MediaFileType.EXTRA);
    checkMediaFileType("movie-extras-blabla.mkv", MediaFileType.EXTRA);

    checkMediaFileType("extras/scene2.mkv", MediaFileType.EXTRA);
    checkMediaFileType("extras/scene2.mkv", MediaFileType.EXTRA);

    checkMediaFileType("cool movie-behindthescenes.mkv", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-behindthescenes2.mp4", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-deleted.mkv", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-deleted2.mp4", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-featurette.mkv", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-featurette3.mp4", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-interview.mkv", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-interview4.mp4", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-scene.mkv", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-scene5.mp4", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-short.mkv", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-short6.mp4", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-other.mkv", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-other7.mp4", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-bloopers.mkv", MediaFileType.EXTRA);
    checkMediaFileType("cool movie-bloopers8.mp4", MediaFileType.EXTRA);

    checkMediaFileType("behind the scenes/vid1.mkv", MediaFileType.EXTRA);
    checkMediaFileType("behindthescenes/vid1.mp4", MediaFileType.EXTRA);
    checkMediaFileType("deleted scenes/vid1of1.avi", MediaFileType.EXTRA);
    checkMediaFileType("deletedscenes/fail.mp4", MediaFileType.EXTRA);
    checkMediaFileType("deleted/trailer.mkv", MediaFileType.EXTRA);
    checkMediaFileType("featurette/scene.avi", MediaFileType.EXTRA);
    checkMediaFileType("featurettes/scene2.mp4", MediaFileType.EXTRA);
    checkMediaFileType("interview/Andrew.mkv", MediaFileType.EXTRA);
    checkMediaFileType("interviews/cast.mkv", MediaFileType.EXTRA);
    checkMediaFileType("scene/1.mkv", MediaFileType.EXTRA);
    checkMediaFileType("scenes/1 of 25.mkv", MediaFileType.EXTRA);
    checkMediaFileType("short/cloudy.mp4", MediaFileType.EXTRA);
    checkMediaFileType("shorts/various.avi", MediaFileType.EXTRA);
    checkMediaFileType("other/credits.avi", MediaFileType.EXTRA);
    checkMediaFileType("others/credits2.avi", MediaFileType.EXTRA);
    checkMediaFileType("bloopers/hot.avi", MediaFileType.EXTRA);
  }

  @Test
  public void testMediaFileTypeNfo() {
    checkMediaFileType("movie.nfo", MediaFileType.NFO);
    checkMediaFileType("This.is.Trailer.park.Boys.nfo", MediaFileType.NFO);
    checkMediaFileType("some-other-file.nfo", MediaFileType.NFO);
  }

  @Test
  public void testMediaFileTypeVsmeta() {
    checkMediaFileType("movie.vsmeta", MediaFileType.VSMETA);
    checkMediaFileType("This.is.Trailer.park.Boys.vsmeta", MediaFileType.VSMETA);
    checkMediaFileType("some-other-file.vsmeta", MediaFileType.VSMETA);
  }

  @Test
  public void testMediaFileTypeTheme() {
    checkMediaFileType("movie-theme.mp3", MediaFileType.THEME);
    checkMediaFileType("movie.theme1.aac", MediaFileType.THEME);
    checkMediaFileType("movie_theme2.mpa", MediaFileType.THEME);
    checkMediaFileType("movie-soundtrack.mp3", MediaFileType.THEME);
    checkMediaFileType("movie.soundtrack1.aac", MediaFileType.THEME);
    checkMediaFileType("movie_soundtrack2.ogg", MediaFileType.THEME);

    checkMediaFileType("theme.mpa", MediaFileType.THEME);
    checkMediaFileType("theme2.mp3", MediaFileType.THEME);
    checkMediaFileType("soundtrack.ac3", MediaFileType.THEME);
    checkMediaFileType("soundtrack2.ac3", MediaFileType.THEME);

    checkMediaFileType("This.is.Trailer.park.Boys.theme.mp3", MediaFileType.THEME);
    checkMediaFileType("This.is.Trailer.park.Boys.SoundTrack.wav", MediaFileType.THEME);
    checkMediaFileType("SoundTrack.ogg", MediaFileType.THEME);
    checkMediaFileType("theme.aac", MediaFileType.THEME);
  }

  @Test
  public void testMediaFileTypeAudio() {
    checkMediaFileType("This.is.Trailer.park.Boys.ger.mp3", MediaFileType.AUDIO);
    checkMediaFileType("This.is.Trailer.park.Boys-eng.aac", MediaFileType.AUDIO);
    checkMediaFileType("movie.ger.mpa", MediaFileType.AUDIO);
    checkMediaFileType("movie-eng.ac3", MediaFileType.AUDIO);
  }

  @Test
  public void testMediaFileTypeTrailer() {
    checkMediaFileType("So.Dark.the.Night.1946.720p.BluRay.x264-x0r[Trailer-Theatrical-Trailer].mkv", MediaFileType.TRAILER);
    checkMediaFileType("cool movie-trailer.mkv", MediaFileType.TRAILER);
    checkMediaFileType("film-trailer.1.mov", MediaFileType.TRAILER);
    checkMediaFileType("film-trailer1.mov", MediaFileType.TRAILER);
    checkMediaFileType("film-trailer-1.mov", MediaFileType.TRAILER);
    checkMediaFileType("movie-trailer.mp4", MediaFileType.TRAILER);
    checkMediaFileType("trailer.mkv", MediaFileType.TRAILER);

    checkMediaFileType("trailer/full.mov", MediaFileType.TRAILER);
    checkMediaFileType("trailers/first.mov", MediaFileType.TRAILER);
    checkMediaFileType("trailers/uncut.mov", MediaFileType.TRAILER);
  }

  @Test
  public void testMediaFileTypeSample() {
    checkMediaFileType("So.Dark.the.Night.1946.720p.BluRay.x264-x0r[Sample].mkv", MediaFileType.SAMPLE);
    checkMediaFileType("movie-sample.mkv", MediaFileType.SAMPLE);
    checkMediaFileType("movie_sample.mkv", MediaFileType.SAMPLE);
    checkMediaFileType("movie.sample.mkv", MediaFileType.SAMPLE);
    checkMediaFileType("video[sample].mkv", MediaFileType.SAMPLE);
    checkMediaFileType("clip(sample).mkv", MediaFileType.SAMPLE);
    checkMediaFileType("clip(sample).mkv", MediaFileType.SAMPLE);
    checkMediaFileType("sample/clip1.mkv", MediaFileType.SAMPLE);
    checkMediaFileType("sample/clip2.mkv", MediaFileType.SAMPLE);
    checkMediaFileType("sample/trailer2.mkv", MediaFileType.SAMPLE);
  }

  @Test
  public void testMediaFileTypeMediaInfoXml() {
    checkMediaFileType("movie-mediainfo.xml", MediaFileType.MEDIAINFO);
    checkMediaFileType("movie.mediainfo.xml", MediaFileType.MEDIAINFO);
    checkMediaFileType("This.is.Trailer.park.Boys-mediainfo.xml", MediaFileType.MEDIAINFO);
    checkMediaFileType("This.is.Trailer.park.Boys.mediainfo.xml", MediaFileType.MEDIAINFO);
  }

  @Test
  public void testMediaFileTypeDetectionPoster() {
    checkMediaFileType("poster.jpg", MediaFileType.POSTER);
    checkMediaFileType("PoStEr.PNG", MediaFileType.POSTER);
    checkMediaFileType("movie.jpg", MediaFileType.POSTER);
    checkMediaFileType("folder.tbn", MediaFileType.POSTER);
    checkMediaFileType("Folder.GIF", MediaFileType.POSTER);
    checkMediaFileType("cover.jpg", MediaFileType.POSTER);
    checkMediaFileType("COVER.jpeg", MediaFileType.POSTER);
    checkMediaFileType("movie-poster.png", MediaFileType.POSTER);
    checkMediaFileType("my-movie-poster.jpg", MediaFileType.POSTER);
    checkMediaFileType("Film-Title-Poster.jpeg", MediaFileType.POSTER);
    checkMediaFileType("coolmovie-cover.webp", MediaFileType.POSTER);
    checkMediaFileType("example-cover.bmp", MediaFileType.POSTER);
    checkMediaFileType("12345-cover.tbn", MediaFileType.POSTER);
  }

  @Test
  public void testMediaFileTypeDetectionFanart() {
    checkMediaFileType("fanart.jpg", MediaFileType.FANART);
    checkMediaFileType("FANART.PNG", MediaFileType.FANART);
    checkMediaFileType("movie-fanart.tbn", MediaFileType.FANART);
    checkMediaFileType("my-movie.fanart.jpg", MediaFileType.FANART);
    checkMediaFileType("Film-Title-Fanart.webp", MediaFileType.FANART);

    checkMediaFileType("backdrop.jpg", MediaFileType.FANART);
    checkMediaFileType("BACKDROP.PNG", MediaFileType.FANART);
    checkMediaFileType("movie-backdrop.png", MediaFileType.FANART);
    checkMediaFileType("my-movie.backdrop.gif", MediaFileType.FANART);
    checkMediaFileType("Film-Title-Backdrop.jpeg", MediaFileType.FANART);
  }

  @Test
  public void testMediaFileTypeDetectionExtraFanart() {
    checkMediaFileType("movie-fanart1.jpg", MediaFileType.EXTRAFANART);
    checkMediaFileType("my_movie-fanart2.png", MediaFileType.EXTRAFANART);
    checkMediaFileType("FilmTitle-fanart123.gif", MediaFileType.EXTRAFANART);
    checkMediaFileType("SeriesName-backdrop10.bmp", MediaFileType.EXTRAFANART);
    checkMediaFileType("example-fanart99.webp", MediaFileType.EXTRAFANART);
    checkMediaFileType("fanart5.tbn", MediaFileType.EXTRAFANART);
    checkMediaFileType("BACKDROP1.JPG", MediaFileType.EXTRAFANART);
    checkMediaFileType("movie.backdrop3.jpg", MediaFileType.EXTRAFANART);
    checkMediaFileType("film.fanart001.png", MediaFileType.EXTRAFANART);

    checkMediaFileType("extrafanart/fanart.jpg", MediaFileType.EXTRAFANART);
    checkMediaFileType("extrafanart/FANART.PNG", MediaFileType.EXTRAFANART);
    checkMediaFileType("extrafanart/movie-fanart.tbn", MediaFileType.EXTRAFANART);
    checkMediaFileType("extrafanart/my-movie.fanart.jpg", MediaFileType.EXTRAFANART);
    checkMediaFileType("extrafanart/Film-Title-Fanart.webp", MediaFileType.EXTRAFANART);

    checkMediaFileType("extrafanart/backdrop.jpg", MediaFileType.EXTRAFANART);
    checkMediaFileType("extrafanart/BACKDROP.PNG", MediaFileType.EXTRAFANART);
    checkMediaFileType("extrafanart/movie-backdrop.png", MediaFileType.EXTRAFANART);
    checkMediaFileType("extrafanart/my-movie.backdrop.gif", MediaFileType.EXTRAFANART);
    checkMediaFileType("extrafanart/Film-Title-Backdrop.jpeg", MediaFileType.EXTRAFANART);
  }

  @Test
  public void testMediaFileTypeDetectionBanner() {
    checkMediaFileType("banner.jpg", MediaFileType.BANNER);
    checkMediaFileType("BANNER.PNG", MediaFileType.BANNER);
    checkMediaFileType("BaNnEr.GiF", MediaFileType.BANNER);
    checkMediaFileType("movie-banner.tbn", MediaFileType.BANNER);
    checkMediaFileType("Film-Title-Banner.webp", MediaFileType.BANNER);
  }

  @Test
  public void testMediaFileTypeDetectionThumb() {
    checkMediaFileType("thumb.jpg", MediaFileType.THUMB);
    checkMediaFileType("THUMB.PNG", MediaFileType.THUMB);
    checkMediaFileType("ThUmB.GiF", MediaFileType.THUMB);
    checkMediaFileType("movie-thumb.tbn", MediaFileType.THUMB);
    checkMediaFileType("Film-Title-Thumb.webp", MediaFileType.THUMB);

    checkMediaFileType("landscape.jpg", MediaFileType.THUMB);
    checkMediaFileType("LANDSCAPE.PNG", MediaFileType.THUMB);
    checkMediaFileType("movie-landscape.gif", MediaFileType.THUMB);
    checkMediaFileType("Film-Title-Landscape.jpeg", MediaFileType.THUMB);
  }

  @Test
  public void testMediaFileTypeDetectionClearlogo() {
    checkMediaFileType("clearlogo.jpg", MediaFileType.CLEARLOGO);
    checkMediaFileType("CLEARLOGO.PNG", MediaFileType.CLEARLOGO);
    checkMediaFileType("Clearlogo.GiF", MediaFileType.CLEARLOGO);
    checkMediaFileType("movie-clearlogo.tbn", MediaFileType.CLEARLOGO);
    checkMediaFileType("Film-Title-Clearlogo.webp", MediaFileType.CLEARLOGO);

    // since logo is legacy, we return clearlogo
    checkMediaFileType("logo.jpg", MediaFileType.CLEARLOGO);
    checkMediaFileType("LOGO.PNG", MediaFileType.CLEARLOGO);
    checkMediaFileType("Logo.GiF", MediaFileType.CLEARLOGO);
    checkMediaFileType("movie-logo.tbn", MediaFileType.CLEARLOGO);
    checkMediaFileType("Film-Title-Logo.webp", MediaFileType.CLEARLOGO);
  }

  @Test
  public void testMediaFileTypeDetectionCharacterart() {
    checkMediaFileType("characterart.jpg", MediaFileType.CHARACTERART);
    checkMediaFileType("CHARACTERART.PNG", MediaFileType.CHARACTERART);
    checkMediaFileType("Characterart.GiF", MediaFileType.CHARACTERART);
    checkMediaFileType("movie-characterart.tbn", MediaFileType.CHARACTERART);
    checkMediaFileType("Film-Title-Characterart.webp", MediaFileType.CHARACTERART);
  }

  @Test
  public void testMediaFileTypeDetectionDiscart() {
    checkMediaFileType("discart.jpg", MediaFileType.DISC);
    checkMediaFileType("DISCART.PNG", MediaFileType.DISC);
    checkMediaFileType("movie-discart.tbn", MediaFileType.DISC);
    checkMediaFileType("Film-Title-Discart.webp", MediaFileType.DISC);

    checkMediaFileType("disc.jpg", MediaFileType.DISC);
    checkMediaFileType("DISC.PNG", MediaFileType.DISC);
    checkMediaFileType("movie-disc.png", MediaFileType.DISC);
    checkMediaFileType("Film-Title-Disc.jpeg", MediaFileType.DISC);
  }

  @Test
  public void testMediaFileTypeDetectionClearart() {
    checkMediaFileType("clearart.jpg", MediaFileType.CLEARART);
    checkMediaFileType("CLEARART.PNG", MediaFileType.CLEARART);
    checkMediaFileType("Clearart.GiF", MediaFileType.CLEARART);
    checkMediaFileType("movie-clearart.tbn", MediaFileType.CLEARART);
    checkMediaFileType("Film-Title-Clearart.webp", MediaFileType.CLEARART);
  }

  @Test
  public void testMediaFileTypeDetectionKeyart() {
    checkMediaFileType("keyart.jpg", MediaFileType.KEYART);
    checkMediaFileType("KEYART.PNG", MediaFileType.KEYART);
    checkMediaFileType("Keyart.GiF", MediaFileType.KEYART);
    checkMediaFileType("movie-keyart.tbn", MediaFileType.KEYART);
    checkMediaFileType("Film-Title-Keyart.webp", MediaFileType.KEYART);
  }

  @Test
  public void testMediaFileTypeDetectionSeasonPoster() {
    checkMediaFileType("season01.png", MediaFileType.SEASON_POSTER);
    checkMediaFileType("SEASON1.JPG", MediaFileType.SEASON_POSTER);
    checkMediaFileType("season1945.jpeg", MediaFileType.SEASON_POSTER);
    checkMediaFileType("season12-poster.tbn", MediaFileType.SEASON_POSTER);
    checkMediaFileType("sEaSoN-specials.GIF", MediaFileType.SEASON_POSTER);
    checkMediaFileType("season-specials-poster.webp", MediaFileType.SEASON_POSTER);
    checkMediaFileType("season-all.png", MediaFileType.SEASON_POSTER);
    checkMediaFileType("season-all-poster.gif", MediaFileType.SEASON_POSTER);

    checkMediaFileType("Season 1/season01.png", MediaFileType.SEASON_POSTER);
    checkMediaFileType("S01/SEASON1.JPG", MediaFileType.SEASON_POSTER);
    checkMediaFileType("S1945/season1945.jpeg", MediaFileType.SEASON_POSTER);
    checkMediaFileType("Season 12/season12-poster.tbn", MediaFileType.SEASON_POSTER);
    checkMediaFileType("Specials/sEaSoN-specials.GIF", MediaFileType.SEASON_POSTER);
    checkMediaFileType("Season 0/season-specials-poster.webp", MediaFileType.SEASON_POSTER);
    checkMediaFileType("S00/season0-poster.webp", MediaFileType.SEASON_POSTER);
  }

  @Test
  public void testMediaFileTypeDetectionSeasonFanart() {
    checkMediaFileType("season01-fanart.png", MediaFileType.SEASON_FANART);
    checkMediaFileType("SEASON1-FANART.JPG", MediaFileType.SEASON_FANART);
    checkMediaFileType("season1945-fanart.jpeg", MediaFileType.SEASON_FANART);
    checkMediaFileType("season12-fanart.tbn", MediaFileType.SEASON_FANART);
    checkMediaFileType("season-specials-fanart.webp", MediaFileType.SEASON_FANART);
    checkMediaFileType("season-all-fanart.gif", MediaFileType.SEASON_FANART);

    checkMediaFileType("Season 1/season01-fanart.png", MediaFileType.SEASON_FANART);
    checkMediaFileType("S01/SEASON1-FANART.JPG", MediaFileType.SEASON_FANART);
    checkMediaFileType("S1945/season1945-fanart.jpeg", MediaFileType.SEASON_FANART);
    checkMediaFileType("Season 12/season12-fanart.tbn", MediaFileType.SEASON_FANART);
    checkMediaFileType("Specials/sEaSoN-specials-fanart.GIF", MediaFileType.SEASON_FANART);
    checkMediaFileType("Season 0/season0-fanart.webp", MediaFileType.SEASON_FANART);
  }

  @Test
  public void testMediaFileTypeDetectionSeasonBanner() {
    checkMediaFileType("season01-banner.png", MediaFileType.SEASON_BANNER);
    checkMediaFileType("SEASON1-BANNER.JPG", MediaFileType.SEASON_BANNER);
    checkMediaFileType("season1945-banner.jpeg", MediaFileType.SEASON_BANNER);
    checkMediaFileType("season12-banner.tbn", MediaFileType.SEASON_BANNER);
    checkMediaFileType("season-specials-banner.webp", MediaFileType.SEASON_BANNER);
    checkMediaFileType("season-all-banner.gif", MediaFileType.SEASON_BANNER);

    checkMediaFileType("Season 1/season01-banner.png", MediaFileType.SEASON_BANNER);
    checkMediaFileType("S01/SEASON1-BANNER.JPG", MediaFileType.SEASON_BANNER);
    checkMediaFileType("S1945/season1945-banner.jpeg", MediaFileType.SEASON_BANNER);
    checkMediaFileType("Season 12/season12-banner.tbn", MediaFileType.SEASON_BANNER);
    checkMediaFileType("Specials/sEaSoN-specials-banner.GIF", MediaFileType.SEASON_BANNER);
    checkMediaFileType("Season 0/season0-banner.webp", MediaFileType.SEASON_BANNER);
  }

  @Test
  public void testMediaFileTypeDetectionSeasonThumb() {
    checkMediaFileType("season01-thumb.png", MediaFileType.SEASON_THUMB);
    checkMediaFileType("SEASON1-THUMB.JPG", MediaFileType.SEASON_THUMB);
    checkMediaFileType("season1945-thumb.jpeg", MediaFileType.SEASON_THUMB);
    checkMediaFileType("season12-thumb.tbn", MediaFileType.SEASON_THUMB);
    checkMediaFileType("season-specials-thumb.webp", MediaFileType.SEASON_THUMB);
    checkMediaFileType("season-all-thumb.gif", MediaFileType.SEASON_THUMB);

    checkMediaFileType("Season 1/season01-thumb.png", MediaFileType.SEASON_THUMB);
    checkMediaFileType("S01/SEASON1-THUMB.JPG", MediaFileType.SEASON_THUMB);
    checkMediaFileType("S1945/season1945-thumb.jpeg", MediaFileType.SEASON_THUMB);
    checkMediaFileType("Season 12/season12-thumb.tbn", MediaFileType.SEASON_THUMB);
    checkMediaFileType("Specials/sEaSoN-specials-thumb.GIF", MediaFileType.SEASON_THUMB);
    checkMediaFileType("Season 0/season0-thumb.webp", MediaFileType.SEASON_THUMB);

    checkMediaFileType("season01-landscape.png", MediaFileType.SEASON_THUMB);
    checkMediaFileType("SEASON1-LANDSCAPE.JPG", MediaFileType.SEASON_THUMB);
    checkMediaFileType("season1945-landscape.jpeg", MediaFileType.SEASON_THUMB);
    checkMediaFileType("season12-landscape.tbn", MediaFileType.SEASON_THUMB);
    checkMediaFileType("season-specials-landscape.webp", MediaFileType.SEASON_THUMB);
    checkMediaFileType("season-all-landscape.gif", MediaFileType.SEASON_THUMB);

    checkMediaFileType("Season 1/season01-landscape.png", MediaFileType.SEASON_THUMB);
    checkMediaFileType("S01/SEASON1-LANDSCAPE.JPG", MediaFileType.SEASON_THUMB);
    checkMediaFileType("S1945/season1945-landscape.jpeg", MediaFileType.SEASON_THUMB);
    checkMediaFileType("Season 12/season12-landscape.tbn", MediaFileType.SEASON_THUMB);
    checkMediaFileType("Specials/sEaSoN-specials-landscape.GIF", MediaFileType.SEASON_THUMB);
    checkMediaFileType("Season 0/season0-landscape.webp", MediaFileType.SEASON_THUMB);
  }

  private void checkMediaFileType(String filename, MediaFileType mediaFileType) {
    assertThat(new MediaFile(Paths.get(datasource, "movie", filename)).getType()).isEqualTo(mediaFileType);
  }
}
