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
package org.tinymediamanager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.entities.Person;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.entities.MediaEpisodeGroup;
import org.tinymediamanager.scraper.entities.MediaEpisodeNumber;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.StrgUtils;
import org.tinymediamanager.ui.TmmUILayoutStore;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The class UpdateTasks. To perform needed update tasks
 *
 * @author Manuel Laggner / Myron Boyle
 */
public abstract class UpgradeTasks {
  private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeTasks.class);

  private static String       oldVersion;

  protected Set<MediaEntity>  entitiesToSave;

  protected UpgradeTasks() {
    entitiesToSave = new HashSet<>();
  }

  /**
   * perform all DB related upgrades
   */
  public abstract void performDbUpgrades();

  /**
   * register the {@link MediaEntity} for being saved
   * 
   * @param mediaEntity
   *          the {@link MediaEntity} to be saved
   */
  protected void registerForSaving(MediaEntity mediaEntity) {
    entitiesToSave.add(mediaEntity);
  }

  /**
   * Save all registered {@link MediaEntity}
   */
  protected abstract void saveAll();

  public static void setOldVersion() {
    oldVersion = Settings.getInstance().getVersion();
  }

  public static String getOldVersion() {
    return oldVersion;
  }

  public static boolean isNewVersion() {
    return StrgUtils.compareVersion(oldVersion, ReleaseInfo.getVersion()) == 0;
  }

  public static void performUpgradeTasksBeforeDatabaseLoading() {
    String v = "" + oldVersion;
    if (StringUtils.isBlank(v)) {
      v = "5.0"; // set version for other updates
    }

    // ****************************************************
    // PLEASE MAKE THIS TO RUN MULTIPLE TIMES WITHOUT ERROR
    // NEEDED FOR NIGHTLY SNAPSHOTS ET ALL
    // SVN BUILD IS ALSO CONSIDERED AS LOWER !!!
    // ****************************************************

    if (StrgUtils.compareVersion(v, "5.0") < 0) {
      LOGGER.info("Performing upgrade tasks to version 5.0");
      // migrate wrong launcher-extra.yml
      Path wrongExtra = Paths.get(Globals.DATA_FOLDER, LauncherExtraConfig.LAUNCHER_EXTRA_YML);
      if (Files.exists(wrongExtra)) {
        Path correctExtra = Paths.get(Globals.CONTENT_FOLDER, LauncherExtraConfig.LAUNCHER_EXTRA_YML);
        try {
          Files.move(wrongExtra, correctExtra, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
          LOGGER.warn("Could not move launcher-extra.yml from '{}' to '{}'", wrongExtra, correctExtra);
        }
      }
    }

    if (StrgUtils.compareVersion(v, "5.1.1") < 0) {
      // remove old addons from the native folder (only Windows & Linux)
      if (!SystemUtils.IS_OS_MAC) {
        Utils.deleteDirectorySafely(Paths.get(TmmOsUtils.getNativeFolderName(), "addons"));
      }
    }
  }

  /**
   * Fix some known rating problems min/max values
   * 
   * @param me
   *          the {@link MediaEntity}
   * @return true, if something detected (call save)
   */
  protected static boolean fixRatings(MediaEntity me) {
    boolean changed = false;
    for (MediaRating rat : me.getRatings().values()) {
      if (rat.getMaxValue() == 10 && rat.getRating() > 10f) {
        rat.setMaxValue(100);
        changed = true;
      }
      if (rat.getMaxValue() == 10 && rat.getId().equals(MediaMetadata.LETTERBOXD)) {
        rat.setMaxValue(5);
        changed = true;
      }
      if (rat.getMaxValue() == 10 && rat.getId().equals(MediaMetadata.ROGER_EBERT)) {
        rat.setMaxValue(4); // yes, 4
        changed = true;
      }
    }

    // remove -1/0 values
    Iterator<String> it = me.getRatings().keySet().iterator();
    while (it.hasNext()) {
      MediaRating rat = me.getRatings().get(it.next());
      if (rat.getRating() < 0) {
        LOGGER.trace("Remove invalid rating: [{}] from {}", rat, me.getTitle());
        it.remove();
        changed = true;
      }
      else if (rat.getRating() == 0 && !rat.getId().equals(MediaMetadata.ROGER_EBERT)) {
        // rogerebert DOES rate 0/4 star remove all others!
        LOGGER.trace("Remove invalid rating: [{}] from {}", rat, me.getTitle());
        it.remove();
        changed = true;
      }
    }

    return changed;
  }

  /**
   * removed HDR10, when also having HDR10+
   * 
   * @param me
   *          the {@link MediaEntity}
   * @return
   */
  protected static boolean fixHDR(MediaEntity me) {
    boolean changed = false;
    for (MediaFile mf : me.getMediaFiles()) {
      if (mf.isHDR()) {
        List<String> hdrs = new ArrayList<String>(Arrays.asList(mf.getHdrFormat().split(", "))); // modifyable
        if (hdrs.contains("HDR10+") && hdrs.contains("HDR10")) {
          hdrs.remove("HDR10");
          mf.setHdrFormat(String.join(", ", hdrs));
          changed = true;
        }
      }
    }
    return changed;
  }

  /**
   * migrate old ID keys to the current ones
   * 
   * @param mediaEntity
   *          the {@link MediaEntity} to migrate the IDs for
   * @return true if something has been changed / false otherwise
   */
  protected boolean migrateIds(MediaEntity mediaEntity) {
    boolean changed = false;

    // imdbId -> imdb
    changed |= migrateId("imdbId", MediaMetadata.IMDB, mediaEntity);

    // tmdbId -> tmdb
    changed |= migrateId("tmdbId", MediaMetadata.TMDB, mediaEntity);

    // traktId -> trakt
    changed |= migrateId("traktId", MediaMetadata.TRAKT_TV, mediaEntity);

    return changed;
  }

  private boolean migrateId(String oldKey, String newKey, MediaEntity mediaEntity) {
    Map<String, Object> ids = mediaEntity.getIds();

    Object oldId = ids.get(oldKey);
    if (oldId == null) {
      // old ID not available -> nothing to do
      return false;
    }

    Object newId = ids.get(newKey);
    if (newId == null) {
      // no new ID, but an old one -> migrate
      mediaEntity.setId(newKey, oldId);
    }

    // remove the old one
    mediaEntity.removeId(oldKey);

    // we changed the map (removed old and _maybe_ added new)
    return true;
  }

  public static void upgradeEpisodeNumbers(TvShowEpisode episode) {
    // create season and EGs, if we read it in "old" style
    if (!episode.additionalProperties.isEmpty() && episode.getEpisodeNumbers().isEmpty()) {
      // V4 style
      int s = MetadataUtil.parseInt(episode.additionalProperties.get("season"), -2);
      int e = MetadataUtil.parseInt(episode.additionalProperties.get("episode"), -2);
      if (s > -2 && e > -2) {
        // also record -1/-1 episodes
        episode.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_AIRED, s, e));
      }

      s = MetadataUtil.parseInt(episode.additionalProperties.get("dvdSeason"), -1);
      e = MetadataUtil.parseInt(episode.additionalProperties.get("dvdEpisode"), -1);
      if (s > -1 && e > -1) {
        episode.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DVD, s, e));
      }

      s = MetadataUtil.parseInt(episode.additionalProperties.get("displaySeason"), -1);
      e = MetadataUtil.parseInt(episode.additionalProperties.get("displayEpisode"), -1);
      if (s > -1 && e > -1) {
        episode.setEpisode(new MediaEpisodeNumber(MediaEpisodeGroup.DEFAULT_DISPLAY, s, e));
      }
    }
  }

  /**
   * Upgrade old style crew members (which are unmarshalled as a key/value {@link Map}
   * 
   * @param values
   *          the {@link Map} containing all properties of the old crew members
   * @return a {@link List} of all migrated crew members
   */
  public static List<Person> upgradeCrew(List<?> values) {
    ObjectMapper mapper = new ObjectMapper();
    List<Person> crew = new ArrayList<>();

    for (Object entry : values) {
      if (entry instanceof Map<?, ?> map) {
        try {
          Person person = mapper.convertValue(map, Person.class);

          // set the role name if empty
          if (person != null && StringUtils.isBlank(person.getRole())
              && (person.getType() == Person.Type.PRODUCER || person.getType() == Person.Type.DIRECTOR || person.getType() == Person.Type.WRITER)) {
            String roleName = TmmResourceBundle.getString("Person." + person.getType().name());
            if (!"???".equals(roleName)) {
              person.setRole(roleName);
            }
          }

          if (!crew.contains(person)) {
            crew.add(person);
          }
        }
        catch (Exception e) {
          LOGGER.debug("Could not upgrade crew member - '{}'", e.getMessage());
        }
      }
    }

    return crew;
  }

  /**
   * copy over data/settings from v4
   * 
   * @param path
   *          the path to the v4 data folder
   */
  public static void copyV4Data(Path path) {
    // close tmm internals
    TinyMediaManager.shutdown();

    // remove shutdown hook
    TmmUILayoutStore.getInstance().setSkipSaving(true);

    // data
    File[] files = path.toFile().listFiles();
    if (files != null) {
      for (File file : files) {
        try {
          Utils.copyFileSafe(file.toPath(), Paths.get(Globals.DATA_FOLDER, file.getName()), true);
        }
        catch (Exception e) {
          LOGGER.warn("could not copy file '{}' from v4 - '{}'", file.getName(), e.getMessage());
        }
      }
    }

    // try /cache too
    Path cache = path.getParent().resolve("cache");
    if (cache.toFile().exists() && cache.toFile().isDirectory()) {
      files = cache.toFile().listFiles();
      if (files != null) {
        for (File file : files) {
          try {
            if (file.isFile()) {
              Utils.copyFileSafe(file.toPath(), Paths.get(Globals.CACHE_FOLDER, file.getName()), true);
            }
            else if (file.isDirectory()) {
              Utils.copyDirectoryRecursive(file.toPath(), Paths.get(Globals.CACHE_FOLDER, file.getName()));
            }
          }
          catch (Exception e) {
            LOGGER.warn("could not copy file '{}' from v4 - '{}'", file.getName(), e.getMessage());
          }
        }
      }
    }

    // and copy over the launcher-extra.yml
    Path launcherExtra = path.getParent().resolve("launcher-extra.yml");
    if (launcherExtra.toFile().exists() && launcherExtra.toFile().isFile()) {
      try {
        Utils.copyFileSafe(launcherExtra, Paths.get(Globals.CONTENT_FOLDER, launcherExtra.toFile().getName()), true);
      }
      catch (Exception e) {
        LOGGER.warn("could not copy file '{}' from v4 - '{}'", launcherExtra.toFile().getName(), e.getMessage());
      }
    }

    // spawn our process
    ProcessBuilder pb = TmmOsUtils.getPBforTMMrestart();
    try {
      LOGGER.info("Going to execute: {}", pb.command());
      pb.start();
    }
    catch (Exception e) {
      LOGGER.error("Could not restart tinyMediaManager", e);
    }

    TinyMediaManager.shutdownLogger();

    System.exit(0);
  }

  public static Map<?, ?> loadOldDatabase(Path path) throws Exception {
    // Path to your JAR file
    File jarFile = new File("dbmigrator.jar");

    // Convert the file to a URL
    URL jarUrl = jarFile.toURI().toURL();

    try (URLClassLoader loader = new URLClassLoader(new URL[] { jarUrl }, MovieModuleManager.class.getClassLoader())) {
      // Step 2: Load the class by name
      Class<?> dbmigrator = loader.loadClass("org.tinymediamanager.dbmigrator.DatabaseMigrator");

      // Step 3: Instantiate the class
      Object pluginInstance = dbmigrator.getDeclaredConstructor().newInstance();

      // Step 4: Call a method reflectively
      Method executeMethod = dbmigrator.getMethod("loadOldDatabase", String.class);

      return (Map<?, ?>) executeMethod.invoke(pluginInstance, path.toString());
    }
  }
}
