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
package org.tinymediamanager.scraper;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.interfaces.IKodiMetadataProvider;
import org.tinymediamanager.scraper.interfaces.IMediaProvider;
import org.tinymediamanager.scraper.interfaces.IMovieArtworkProvider;
import org.tinymediamanager.scraper.interfaces.IMovieMetadataProvider;
import org.tinymediamanager.scraper.interfaces.IMovieSetMetadataProvider;
import org.tinymediamanager.scraper.interfaces.IMovieSubtitleProvider;
import org.tinymediamanager.scraper.interfaces.IMovieTrailerProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowArtworkProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowMetadataProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowSubtitleProvider;
import org.tinymediamanager.scraper.interfaces.ITvShowTrailerProvider;

/**
 * A class representing a media scraper that provides metadata, artwork, trailers, and subtitles for movies and TV shows. This class serves as a
 * wrapper around different media provider implementations and manages their configuration and functionality. It replaces the previous
 * MovieScrapers/TvShowScrapers enum-based system with a more flexible object-oriented approach.
 *
 * @author Manuel Laggner
 */
public class MediaScraper {
  private final IMediaProvider mediaProvider;

  private final URL            logoUrl;

  private String               id;
  private String               version;
  private String               name;
  private String               summary;
  private String               description;
  private int                  priority;
  private ScraperType          type;

  /**
   * Constructs a new MediaScraper with the specified type and provider. Initializes all metadata fields from the provider's information.
   *
   * @param type
   *          the type of scraper (movie, TV show, artwork, etc.)
   * @param mediaProvider
   *          the underlying media provider implementation
   */
  public MediaScraper(ScraperType type, IMediaProvider mediaProvider) {
    this.mediaProvider = mediaProvider;
    this.type = type;
    MediaProviderInfo mpi = mediaProvider.getProviderInfo();
    this.id = mpi.getId();
    this.name = mpi.getName();
    this.version = mpi.getVersion();
    this.description = mpi.getDescription();
    this.summary = mpi.getDescription();
    this.logoUrl = mpi.getProviderLogo();
    this.priority = mpi.getPriority();
  }

  /**
   * Returns a string representation of this scraper.
   *
   * @return the name of the scraper
   */
  @Override
  public String toString() {
    return name;
  }

  /**
   * Gets the unique identifier of this scraper.
   *
   * @return the scraper's ID
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the unique identifier of this scraper.
   *
   * @param id
   *          the ID to set
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Gets the version of this scraper.
   *
   * @return the version string
   */
  public String getVersion() {
    return version;
  }

  /**
   * Sets the version of this scraper.
   *
   * @param version
   *          the version to set
   */
  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * Gets the display name of this scraper.
   *
   * @return the scraper's name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the display name of this scraper.
   *
   * @param name
   *          the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the brief summary description of this scraper.
   *
   * @return the summary text
   */
  public String getSummary() {
    return summary;
  }

  /**
   * Sets the brief summary description of this scraper.
   *
   * @param summary
   *          the summary text to set
   */
  public void setSummary(String summary) {
    this.summary = summary;
  }

  /**
   * Gets the detailed description of this scraper.
   *
   * @return the description text
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the detailed description of this scraper.
   *
   * @param description
   *          the description text to set
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Gets the priority level of this scraper. Higher priority scrapers are preferred when multiple scrapers are available.
   *
   * @return the priority value
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Sets the priority level of this scraper.
   *
   * @param priority
   *          the priority value to set
   */
  public void setPriority(int priority) {
    this.priority = priority;
  }

  /**
   * Gets the type of this scraper (movie, TV show, artwork, etc.).
   *
   * @return the scraper type
   */
  public ScraperType getType() {
    return type;
  }

  /**
   * Sets the type of this scraper.
   *
   * @param type
   *          the scraper type to set
   */
  public void setType(ScraperType type) {
    this.type = type;
  }

  /**
   * Checks whether this scraper is currently active. A scraper is considered inactive if its media provider is null or inactive.
   *
   * @return true if the scraper is active, false otherwise
   */
  public boolean isActive() {
    if (mediaProvider == null) {
      return false;
    }
    return mediaProvider.isActive();
  }

  /**
   * Checks whether this scraper's features are enabled. A scraper is considered disabled if its media provider is null or its features are disabled.
   *
   * @return true if the scraper's features are enabled, false otherwise
   */
  public boolean isEnabled() {
    if (mediaProvider == null) {
      return false;
    }
    return mediaProvider.isFeatureEnabled();
  }

  /**
   * Gets the underlying media provider implementation.
   *
   * @return the media provider instance
   */
  public IMediaProvider getMediaProvider() {
    return this.mediaProvider;
  }

  /**
   * Gets the URL of this scraper's logo image.
   *
   * @return the logo URL
   */
  public URL getLogoURL() {
    return this.logoUrl;
  }

  /**
   * Retrieves all available media scrapers of the specified type. This method discovers scrapers through the plugin system and includes both standard
   * and Kodi-based scrapers.
   *
   * @param type
   *          the type of scrapers to retrieve
   * @return a list of all discovered media scrapers of the specified type
   */
  public static List<MediaScraper> getMediaScrapers(ScraperType type) {
    List<MediaScraper> scraper = new ArrayList<>();
    List<IMediaProvider> plugins = new ArrayList<>();

    Class<? extends IMediaProvider> clazz = getClassForType(type);
    if (clazz != null) {
      plugins.addAll(MediaProviders.getProvidersForInterface(clazz));
    }

    for (IMediaProvider p : plugins) {
      if (p.getProviderInfo() != null) {
        scraper.add(new MediaScraper(type, p));
      }
    }

    // Kodi scrapers
    for (IKodiMetadataProvider kodi : MediaProviders.getProvidersForInterface(IKodiMetadataProvider.class)) {
      try {
        for (IMediaProvider p : kodi.getPluginsForType(MediaType.toMediaType(type.name()))) {
          scraper.add(new MediaScraper(type, p));
        }
      }
      catch (Exception ignored) {
        // ignored
      }
    }

    return scraper;
  }

  /**
   * Retrieves a specific media scraper by its ID and type.
   *
   * @param id
   *          the unique identifier of the scraper
   * @param type
   *          the type of the scraper
   * @return the matching MediaScraper instance, or null if not found
   */
  public static MediaScraper getMediaScraperById(String id, ScraperType type) {
    if (StringUtils.isBlank(id)) {
      return null;
    }

    IMediaProvider mediaProvider = MediaProviders.getProviderById(id, getClassForType(type));
    if (mediaProvider != null && mediaProvider.getProviderInfo() != null) {
      return new MediaScraper(type, mediaProvider);
    }

    return null;
  }

  /**
   * Maps a scraper type to its corresponding media provider interface class. This private helper method is used internally to determine the
   * appropriate interface for different types of scrapers.
   *
   * @param type
   *          the scraper type to map
   * @return the corresponding IMediaProvider interface class, or null if no mapping exists
   */
  private static Class<? extends IMediaProvider> getClassForType(ScraperType type) {
    return switch (type) {
      case MOVIE -> IMovieMetadataProvider.class;
      case TV_SHOW -> ITvShowMetadataProvider.class;
      case MOVIE_SET -> IMovieSetMetadataProvider.class;
      case MOVIE_ARTWORK -> IMovieArtworkProvider.class;
      case TVSHOW_ARTWORK -> ITvShowArtworkProvider.class;
      case MOVIE_TRAILER -> IMovieTrailerProvider.class;
      case TVSHOW_TRAILER -> ITvShowTrailerProvider.class;
      case MOVIE_SUBTITLE -> IMovieSubtitleProvider.class;
      case TVSHOW_SUBTITLE -> ITvShowSubtitleProvider.class;
      default -> null;
    };
  }

  /**
   * Generates a hash code for this scraper based on its ID and type.
   *
   * @return the hash code value
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  /**
   * Compares this scraper with another object for equality. Two scrapers are considered equal if they have the same ID and type.
   *
   * @param obj
   *          the object to compare with
   * @return true if the objects are equal, false otherwise
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    MediaScraper other = (MediaScraper) obj;
    if (id == null) {
      if (other.id != null) {
        return false;
      }
    }
    else if (!id.equals(other.id)) {
      return false;
    }
    if (type != other.type) {
      return false;
    }
    return true;
  }
}
