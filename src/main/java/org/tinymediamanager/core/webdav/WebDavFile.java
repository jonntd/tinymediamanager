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
package org.tinymediamanager.core.webdav;

import java.util.Date;
import java.util.Locale;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;

import com.github.sardine.DavResource;

/**
 * The class WebDavFile - represents a file or directory on a WebDAV server
 *
 * @author Manuel Laggner
 */
public class WebDavFile {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavFile.class);

  private final String        name;
  private final String        path;
  private final String        fullUrl;
  private final boolean       directory;
  private final long          size;
  private final Date          modified;
  private final String        contentType;

  public WebDavFile(DavResource resource, String baseUrl) {
    this.name = resource.getName();
    this.directory = resource.isDirectory();
    this.size = resource.getContentLength() != null ? resource.getContentLength() : 0;
    this.modified = resource.getModified() != null ? resource.getModified() : new Date(0);
    this.contentType = resource.getContentType() != null ? resource.getContentType() : "";

    // Build full URL and calculate relative path
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    String href = resource.getHref().toString();
    String computedUrl;
    String relativePath;

    if (href.startsWith("/")) {
      // Relative path - need to construct full URL
      try {
        java.net.URI baseUri = new java.net.URI(baseUrl);
        String basePath = baseUri.getRawPath();
        if (!basePath.endsWith("/")) {
          basePath += "/";
        }

        // LOGGER.trace("WebDavFile: baseUrl={}, href={}, basePath={}", baseUrl, href, basePath);

        // Calculate relative path by removing base path from href
        if (href.startsWith(basePath)) {
          relativePath = href.substring(basePath.length());
          // LOGGER.trace("WebDavFile: Calculated relativePath={}", relativePath);
        }
        else {
          relativePath = href;
          // LOGGER.trace("WebDavFile: href does not start with basePath, using href as relativePath");
        }

        computedUrl = baseUri.getScheme() + "://" + baseUri.getHost() + (baseUri.getPort() > 0 ? ":" + baseUri.getPort() : "") + href;
      }
      catch (Exception e) {
        LOGGER.warn("WebDavFile: Failed to parse baseUrl, using href as relativePath: {}", e.getMessage());
        relativePath = href;
        computedUrl = baseUrl + href.substring(1);
      }
    }
    else {
      relativePath = href;
      computedUrl = href;
    }

    // URL 解码 relativePath（WebDAV 服务器返回的路径可能是 URL 编码的）
    try {
      // 保留 '+' 符号，避免被转换为空格
      String preserved = relativePath.replace("+", "%2B");
      relativePath = java.net.URLDecoder.decode(preserved, "UTF-8");
    }
    catch (Exception e) {
      LOGGER.debug("Failed to URL decode path: {}", relativePath);
    }

    this.path = relativePath;
    this.fullUrl = computedUrl;
  }

  public String getName() {
    return name;
  }

  public String getPath() {
    return path;
  }

  public String getFullUrl() {
    return fullUrl;
  }

  public boolean isDirectory() {
    return directory;
  }

  public long getSize() {
    return size;
  }

  public Date getModified() {
    return modified;
  }

  public String getContentType() {
    return contentType;
  }

  /**
   * Get the file extension
   * 
   * @return the file extension (lowercase) or empty string if none
   */
  public String getExtension() {
    return FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT);
  }

  /**
   * Check if this file is a video file based on extension
   * 
   * @return true if this is a video file
   */
  public boolean isVideoFile() {
    if (directory) {
      return false;
    }
    String ext = "." + getExtension();
    return Settings.getInstance().getVideoFileType().contains(ext);
  }

  /**
   * Check if this file is a subtitle file based on extension
   * 
   * @return true if this is a subtitle file
   */
  public boolean isSubtitleFile() {
    if (directory) {
      return false;
    }
    String ext = "." + getExtension();
    return Settings.getInstance().getSubtitleFileType().contains(ext);
  }

  @Override
  public String toString() {
    return name + (directory ? "/" : "");
  }
}
