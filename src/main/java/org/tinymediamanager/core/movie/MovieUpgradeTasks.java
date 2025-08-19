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
package org.tinymediamanager.core.movie;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.UpgradeTasks;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.entities.MovieSet;

/**
 * the class {@link MovieUpgradeTasks} is used to perform actions on {@link Movie}s and {@link MovieSet}s
 * 
 * @author Manuel Laggner
 */
public class MovieUpgradeTasks extends UpgradeTasks {
  private static final Logger LOGGER = LoggerFactory.getLogger(MovieUpgradeTasks.class);

  public MovieUpgradeTasks() {
    super();
  }

  @Override
  public void performSettingsUpgrades() {
    MovieSettings settings = MovieModuleManager.getInstance().getSettings();
    if (settings.getVersion() == 0) {
      settings.setVersion(5000);
    }

    LOGGER.info("Current movie settings version: {}", settings.getVersion());

    if (settings.getVersion() < 5201) {
      LOGGER.info("performing upgrade to ver: {}", 5201);
      // activate english title (scraper and quickfilter) and remove duplicates. Re-order in the right order
      Set<MovieScraperMetadataConfig> metadataConfig = new LinkedHashSet<>();
      for (MovieScraperMetadataConfig config : MovieScraperMetadataConfig.values()) {
        if (config == MovieScraperMetadataConfig.ENGLISH_TITLE || settings.getScraperMetadataConfig().contains(config)) {
          metadataConfig.add(config);
        }
      }
      settings.setScraperMetadataConfig(new ArrayList<>(metadataConfig));
      settings.setEnglishTitle(true);

      settings.setVersion(5201);
    }

    settings.saveSettings();
  }

  /**
   * Each DB version can only be executed once!<br>
   * Do not make changes to existing versions, use a new number!
   */
  @Override
  public void performDbUpgrades() {
    MovieModuleManager module = MovieModuleManager.getInstance();
    MovieList movieList = module.getMovieList();
    if (module.getDbVersion() == 0) {
      module.setDbVersion(5000);
    }

    LOGGER.info("Current movie DB version: {}", module.getDbVersion());

    if (module.getDbVersion() < 5001) {
      LOGGER.info("performing upgrade to ver: {}", 5001);
      for (Movie movie : movieList.getMovies()) {
        // migrate logo to clearlogo
        for (MediaFile mf : movie.getMediaFiles(MediaFileType.LOGO)) {
          // remove
          movie.removeFromMediaFiles(mf);
          // change type
          mf.setType(MediaFileType.CLEARLOGO);
          // and add ad the end
          movie.addToMediaFiles(mf);
        }

        String logoUrl = movie.getArtworkUrl(MediaFileType.LOGO);
        if (StringUtils.isNotBlank(logoUrl)) {
          movie.removeArtworkUrl(MediaFileType.LOGO);
          String clearlogoUrl = movie.getArtworkUrl(MediaFileType.CLEARLOGO);
          if (StringUtils.isBlank(clearlogoUrl)) {
            movie.setArtworkUrl(logoUrl, MediaFileType.CLEARLOGO);
          }
        }
        registerForSaving(movie);
      }
      module.setDbVersion(5001);
    }

    // fix ratings
    // we already did this for 5002, but we need to do this again to remove empty values
    // and since this was also the last upgrade, we just increment the number ;)
    if (module.getDbVersion() < 5003) {
      LOGGER.info("performing upgrade to ver: {}", 5003);
      for (Movie movie : movieList.getMovies()) {
        if (fixRatings(movie)) {
          registerForSaving(movie);
        }
      }
      module.setDbVersion(5003);
    }

    // removed HDR10, when also having HDR10+
    if (module.getDbVersion() < 5004) {
      LOGGER.info("performing upgrade to ver: {}", 5004);
      for (Movie movie : movieList.getMovies()) {
        if (fixHDR(movie)) {
          registerForSaving(movie);
        }
      }
      module.setDbVersion(5004);
    }

    // remove legacy IDs
    if (module.getDbVersion() < 5005) {
      for (Movie movie : movieList.getMovies()) {
        boolean changed = migrateIds(movie);

        if (changed) {
          registerForSaving(movie);
        }
      }

      module.setDbVersion(5005);
    }

    if (module.getDbVersion() < 5201) {
      // crew migration - just re-write the DB
      movieList.getMovies().forEach(this::registerForSaving);

      module.setDbVersion(5201);
    }

    saveAll();
  }

  @Override
  protected void saveAll() {
    for (MediaEntity mediaEntity : entitiesToSave) {
      if (mediaEntity instanceof Movie movie) {
        MovieModuleManager.getInstance().persistMovie(movie);
      }
      else if (mediaEntity instanceof MovieSet movieSet) {
        MovieModuleManager.getInstance().persistMovieSet(movieSet);
      }
    }
  }
}
