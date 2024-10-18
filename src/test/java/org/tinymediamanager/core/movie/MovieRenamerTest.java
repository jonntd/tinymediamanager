package org.tinymediamanager.core.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.Test;
import org.tinymediamanager.core.TmmModuleManager;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaFileAudioStream;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.filenaming.MoviePosterNaming;
import org.tinymediamanager.core.movie.tasks.MovieUpdateDatasourceTask;

public class MovieRenamerTest extends BasicMovieTest {

  private void initTest() throws Exception {
    // setup
    TmmModuleManager.getInstance().startUp();
    MovieModuleManager.getInstance().startUp();

    // just a copy; we might have another movie test which uses these files
    copyResourceFolderToWorkFolder("testmovies_rename");
    Path datasource = getWorkFolder().resolve("testmovies_rename").toAbsolutePath();
    MovieModuleManager.getInstance().getSettings().addMovieDataSources(datasource.toString());

    // read all movies via UDS - all needed infos are embedded in NFO
    MovieUpdateDatasourceTask task = new MovieUpdateDatasourceTask();
    task.run();

    MovieList movieList = MovieList.getInstance();
    assertThat(movieList.getMovies().size()).isEqualTo(8);

    MovieSettingsDefaults.setDefaultSettingsForKodi();
  }

  private void finishTest() throws Exception {
    MovieModuleManager.getInstance().shutDown();
    TmmModuleManager.getInstance().shutDown();
  }

  @Test
  public void testRenameSingleMovieFolder() throws Exception {
    try {
      initTest();

      MovieModuleManager.getInstance()
          .getSettings()
          .setRenamerPathname("${titleSortable;first}/${titleSortable} ${- ,edition,} (${year}) [${videoFormat}]");
      MovieModuleManager.getInstance()
          .getSettings()
          .setRenamerFilename("${title} ${- ,edition,} (${year}) [${videoFormat}] [${videoCodec}] [${audioCodec}]");

      UdsMiRenamerExample example = new UdsMiRenamerExample("Single", "Aladdin", 1992, "singlefile.avi", "1080p", "h264", "A/Aladdin (1992) [1080p]");
      example.oldFiles = new String[] { "singlefile.avi", "fanart.png", "movie.nfo", "singlefile-poster.png", "trailer.mp4", "extras/cut-scenes.mkv",
          "trailer/trailer2.mp4" };
      String movieBasename = "Aladdin (1992) [1080p] [h264] [DTS]";
      example.newFiles = new String[] { movieBasename + ".avi", movieBasename + "-fanart.png", movieBasename + ".nfo", movieBasename + "-poster.png",
          movieBasename + "-trailer.mp4", "extras/cut-scenes.mkv", "trailer/trailer2.mp4" };
      checkExample(example);
    }
    finally {
      finishTest();
    }
  }

  @Test
  public void testRenameSingleMovieFolderMultipleArtwork() throws Exception {
    try {
      initTest();

      MovieModuleManager.getInstance()
          .getSettings()
          .setRenamerPathname("${titleSortable;first}/${titleSortable} ${- ,edition,} (${year}) [${videoFormat}]");
      MovieModuleManager.getInstance()
          .getSettings()
          .setRenamerFilename("${title} ${- ,edition,} (${year}) [${videoFormat}] [${videoCodec}] [${audioCodec}]");

      MovieModuleManager.getInstance().getSettings().addPosterFilename(MoviePosterNaming.FOLDER);

      UdsMiRenamerExample example = new UdsMiRenamerExample("Single", "Aladdin", 1992, "singlefile.avi", "1080p", "h264", "A/Aladdin (1992) [1080p]");
      example.oldFiles = new String[] { "singlefile.avi", "fanart.png", "movie.nfo", "singlefile-poster.png", "trailer.mp4", "extras/cut-scenes.mkv",
          "trailer/trailer2.mp4" };
      String movieBasename = "Aladdin (1992) [1080p] [h264] [DTS]";
      example.newFiles = new String[] { movieBasename + ".avi", movieBasename + "-fanart.png", movieBasename + ".nfo", movieBasename + "-poster.png",
          "folder.png", movieBasename + "-trailer.mp4", "extras/cut-scenes.mkv", "trailer/trailer2.mp4" };
      checkExample(example);
    }
    finally {
      finishTest();
    }
  }

  @Test
  public void testRenameBdmv() throws Exception {
    try {
      initTest();

      MovieModuleManager.getInstance()
          .getSettings()
          .setRenamerPathname("${titleSortable;first}/${titleSortable} ${- ,edition,} (${year}) [${videoFormat}]");
      MovieModuleManager.getInstance()
          .getSettings()
          .setRenamerFilename("${title} ${- ,edition,} (${year}) [${videoFormat}] [${videoCodec}] [${audioCodec}]");

      UdsMiRenamerExample example = new UdsMiRenamerExample("BluRay", "Brave", 2012, "BDMV", "2160p", "h265", "B/Brave (2012) [2160p]");
      example.oldFiles = new String[] { "BDMV", "BDMV/index.nfo", "fanart.png", "poster.png", "trailer.mp4", "extras/cut-scenes.mkv",
          "trailer/trailer2.mp4" };
      example.newFiles = new String[] { "BDMV", "BDMV/index.nfo", "fanart.png", "poster.png", "BDMV/index-trailer.mp4", "extras/cut-scenes.mkv",
          "trailer/trailer2.mp4" };
      checkExample(example);
    }
    finally {
      finishTest();
    }
  }

  @Test
  public void testRenameVideoTS() throws Exception {
    try {
      initTest();

      MovieModuleManager.getInstance()
          .getSettings()
          .setRenamerPathname("${titleSortable;first}/${titleSortable} ${- ,edition,} (${year}) [${videoFormat}]");
      MovieModuleManager.getInstance()
          .getSettings()
          .setRenamerFilename("${title} ${- ,edition,} (${year}) [${videoFormat}] [${videoCodec}] [${audioCodec}]");

      UdsMiRenamerExample example = new UdsMiRenamerExample("DVDfolder", "Cars", 2006, "VIDEO_TS", "576p", "MPEG-2", "C/Cars (2006) [576p]");
      example.oldFiles = new String[] { "VIDEO_TS", "movie.nfo", "fanart.png", "poster.png", "trailer.mp4", "extras/cut-scenes.mkv",
          "trailer/trailer2.mp4" };
      example.newFiles = new String[] { "VIDEO_TS", "VIDEO_TS/VIDEO_TS.nfo", "fanart.png", "poster.png", "VIDEO_TS/VIDEO_TS-trailer.mp4",
          "extras/cut-scenes.mkv", "trailer/trailer2.mp4" };
      checkExample(example);
    }
    finally {
      finishTest();
    }
  }

  private void checkExample(UdsMiRenamerExample example) {
    MovieList movieList = MovieList.getInstance();
    Path datasource = Paths.get(MovieModuleManager.getInstance().getSettings().getMovieDataSource().get(0));

    Movie movie = movieList.getMovieByPath(datasource.resolve(example.oldFolder));
    assertThat(movie.getTitle()).isEqualTo(example.title);
    assertThat(movie.getYear()).isEqualTo(example.year);

    // test UDS
    Path moviePath = datasource.resolve(example.oldFolder);
    checkFiles(moviePath, example.oldFiles);

    // test MI
    MediaFile mainVideoFile = movie.getMainVideoFile();
    assertThat(mainVideoFile.getFile()).isEqualTo(moviePath.resolve(example.mainVideoFilename));
    assertThat(mainVideoFile.getVideoFormat()).isEqualTo(example.videoFormat);
    assertThat(mainVideoFile.getVideoCodec()).isEqualTo(example.videoCodec);

    // rename the movie
    MovieRenamer.renameMovie(movie);
    moviePath = datasource.resolve(example.newFolder);
    assertThat(movie.getPathNIO()).isEqualTo(moviePath);
    checkFiles(moviePath, example.newFiles);

    // undo rename
    MovieRenamer.undoRename(movie);

    assertThat(moviePath).doesNotExist();
    moviePath = datasource.resolve(example.oldFolder);
    assertThat(movie.getPathNIO()).isEqualTo(moviePath);
    checkFiles(moviePath, example.oldFiles);
  }

  private void checkFiles(Path moviePath, String... filenames) {
    for (String filename : filenames) {
      Path filePath = moviePath.resolve(filename);
      assertThat(filePath).exists();
    }
  }

  @Test
  public void testSpecialCases() {
    assertEqual("jb - the bla", MovieRenamer.replaceInvalidCharacters("jb: the bla"));
    assertEqual("jb  - the bla", MovieRenamer.replaceInvalidCharacters("jb : the bla"));
    assertEqual("2-22", MovieRenamer.replaceInvalidCharacters("2:22"));
    assertEqual("2 -22", MovieRenamer.replaceInvalidCharacters("2 :22"));

    // we do not strip path separators here
    assertEqual("weird \\\\-/ movie", MovieRenamer.replaceInvalidCharacters("weird \"\\\\:<>|/?* movie"));
  }

  @Test
  public void testRenamerToken() throws Exception {
    // MediaInfoUtils.loadMediaInfo(); // no MI on buildserver
    copyResourceFolderToWorkFolder("samples");

    Movie m = new Movie();
    m.setTitle("The Dish");
    m.setYear(2000);
    MediaFile mf = new MediaFile(getWorkFolder().resolve("samples").resolve("thx_scarface-DWEU.vob"));

    // mf.gatherMediaInformation();
    mf.setVideoCodec("MPEG");
    mf.setVideoHeight(480);
    mf.setVideoWidth(720);
    ArrayList<MediaFileAudioStream> audl = new ArrayList<>();
    MediaFileAudioStream aud = new MediaFileAudioStream();
    aud.setAudioChannels(6);
    aud.setCodec("AC3");
    aud.setLanguage("en");
    audl.add(aud);
    mf.setAudioStreams(audl);

    aud = new MediaFileAudioStream();
    aud.setAudioChannels(2);
    aud.setCodec("MP3");
    aud.setLanguage("de");
    audl.add(aud);
    mf.setAudioStreams(audl);

    m.addToMediaFiles(mf);

    assertEqual("The Dish (2000) MPEG-480p AC3-6ch",
        MovieRenamer.createDestinationForFilename("${title} (${year}) ${videoCodec}-${videoFormat} ${audioCodec}-${audioChannels}", m));
    assertEqual("The Dish (2000)", MovieRenamer.createDestinationForFoldername("${title} (${year})", m));
    assertEqual("_The Dish (2000)", MovieRenamer.createDestinationForFoldername("${_,title,} (${year})", m));
    assertEqual("The Dish (2000)", MovieRenamer.createDestinationForFoldername(".${title} (${year})", m));
    assertEqual("The Dish (2000)", MovieRenamer.createDestinationForFoldername("-${title} (${year})-", m));
    assertEqual("2000-2009", MovieRenamer.createDestinationForFoldername("${decadeLong}", m));
    assertEqual("2000s", MovieRenamer.createDestinationForFoldername("${decadeShort}", m));
  }

  private static class UdsMiRenamerExample {
    // old locations (UDS & undo)
    String   oldFolder;
    String[] oldFiles;

    String   title;
    int      year;

    // MI values
    String   mainVideoFilename;
    String   videoFormat;
    String   videoCodec;

    // new locations (renamer)
    String   newFolder;
    String[] newFiles;

    public UdsMiRenamerExample(String oldFolder, String title, int year, String mainVideoFilename, String videoFormat, String videoCodec,
        String newFolder) {
      this.oldFolder = oldFolder;
      this.title = title;
      this.year = year;
      this.mainVideoFilename = mainVideoFilename;
      this.videoFormat = videoFormat;
      this.videoCodec = videoCodec;
      this.newFolder = newFolder;
    }
  }
}
