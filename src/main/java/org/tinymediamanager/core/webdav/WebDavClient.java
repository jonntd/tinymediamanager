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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

/**
 * The class WebDavClient - a wrapper around Sardine WebDAV client
 * 
 * @author Manuel Laggner
 */
public class WebDavClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavClient.class);

  private final WebDavSource  source;
  private Sardine             sardine;

  public WebDavClient(WebDavSource source) {
    this.source = source;
  }

  /**
   * Connect to the WebDAV server
   * 
   * @throws IOException
   *           if connection fails
   */
  public void connect() throws IOException {
    if (StringUtils.isNotBlank(source.getUsername()) && StringUtils.isNotBlank(source.getPassword())) {
      sardine = SardineFactory.begin(source.getUsername(), source.getPassword());
    }
    else {
      sardine = SardineFactory.begin();
    }
    // Enable preemptive authentication for better performance
    sardine.enablePreemptiveAuthentication(source.getUrl());
  }

  /**
   * Test the connection to the WebDAV server
   * 
   * @return true if connection is successful
   */
  public boolean testConnection() {
    try {
      connect();
      // Try to list the root directory
      sardine.list(source.getUrl());
      return true;
    }
    catch (Exception e) {
      LOGGER.error("WebDAV connection test failed: {}", e.getMessage());
      return false;
    }
    finally {
      disconnect();
    }
  }

  /**
   * Disconnect from the WebDAV server
   */
  public void disconnect() {
    if (sardine != null) {
      try {
        sardine.shutdown();
      }
      catch (Exception e) {
        LOGGER.warn("Error shutting down WebDAV connection: {}", e.getMessage());
      }
      sardine = null;
    }
  }

  /**
   * List files and directories at the given path
   * 
   * @param path
   *          the path to list (relative to the WebDAV root)
   * @return list of WebDavFile objects
   * @throws IOException
   *           if listing fails
   */
  public List<WebDavFile> list(String path) throws IOException {
    ensureConnected();
    String fullUrl = buildUrl(path);
    List<WebDavFile> files = new ArrayList<>();

    try {
      List<DavResource> resources = sardine.list(fullUrl);

      // Extract the path part from the full URL for comparison
      String urlPath = null;
      try {
        java.net.URI uri = new java.net.URI(fullUrl);
        urlPath = uri.getPath();
        // Normalize (remove trailing slash for comparison)
        if (urlPath.endsWith("/")) {
          urlPath = urlPath.substring(0, urlPath.length() - 1);
        }
      }
      catch (Exception e) {
        LOGGER.warn("Failed to parse URL '{}': {}", fullUrl, e.getMessage());
      }

      for (DavResource resource : resources) {
        String href = resource.getHref().toString();

        // Skip the parent directory entry
        // The href is usually an absolute path like "/webdav/folder/"
        // Note: href may be URL-encoded, so we need to decode it for comparison
        if (urlPath != null) {
          String normalizedHref = href.endsWith("/") ? href.substring(0, href.length() - 1) : href;

          // Decode the href for comparison (handle URL encoding like %E6%97%A0)
          String decodedHref = normalizedHref;
          try {
            decodedHref = java.net.URLDecoder.decode(normalizedHref, "UTF-8");
          }
          catch (Exception e) {
            LOGGER.warn("Failed to decode href '{}': {}", normalizedHref, e.getMessage());
          }

          LOGGER.debug("Comparing href='{}' (decoded='{}') with urlPath='{}'", normalizedHref, decodedHref, urlPath);
          if (decodedHref.equals(urlPath) || normalizedHref.equals(urlPath)) {
            LOGGER.debug("Skipping parent directory: {}", href);
            continue;
          }
        }

        files.add(new WebDavFile(resource, source.getUrl()));
      }
    }
    catch (IOException e) {
      LOGGER.error("Failed to list WebDAV directory '{}': {}", fullUrl, e.getMessage());
      throw e;
    }

    return files;
  }

  /**
   * Check if a path exists on the WebDAV server
   *
   * @param path
   *          the path to check
   * @return true if the path exists
   */
  public boolean exists(String path) {
    ensureConnected();
    try {
      return sardine.exists(buildUrl(path));
    }
    catch (IOException e) {
      LOGGER.warn("Error checking if path exists: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Move/rename a file or directory on the WebDAV server
   *
   * @param sourcePath
   *          the source path (relative to WebDAV root)
   * @param destPath
   *          the destination path (relative to WebDAV root)
   * @return true if the move was successful
   */
  public boolean move(String sourcePath, String destPath) {
    ensureConnected();
    try {
      String sourceUrl = buildUrl(sourcePath);
      String destUrl = buildUrl(destPath);
      LOGGER.debug("Moving WebDAV file from '{}' to '{}'", sourceUrl, destUrl);
      LOGGER.debug("Raw source path: '{}', raw dest path: '{}'", sourcePath, destPath);
      sardine.move(sourceUrl, destUrl);
      return true;
    }
    catch (IOException e) {
      LOGGER.error("Failed to move WebDAV file from '{}' to '{}': {}", sourcePath, destPath, e.getMessage());
      LOGGER.error("Exception details: {}", e.toString());
      LOGGER.debug("Full stack trace:", e);
      return false;
    }
  }

  /**
   * Copy a file or directory on the WebDAV server
   *
   * @param sourcePath
   *          the source path (relative to WebDAV root)
   * @param destPath
   *          the destination path (relative to WebDAV root)
   * @return true if the copy was successful
   */
  public boolean copy(String sourcePath, String destPath) {
    ensureConnected();
    try {
      String sourceUrl = buildUrl(sourcePath);
      String destUrl = buildUrl(destPath);
      LOGGER.debug("Copying WebDAV file from '{}' to '{}'", sourceUrl, destUrl);
      sardine.copy(sourceUrl, destUrl);
      return true;
    }
    catch (IOException e) {
      LOGGER.error("Failed to copy WebDAV file from '{}' to '{}': {}", sourcePath, destPath, e.getMessage());
      return false;
    }
  }

  /**
   * Create a directory on the WebDAV server
   *
   * @param path
   *          the directory path (relative to WebDAV root)
   * @return true if the directory was created successfully
   */
  public boolean createDirectory(String path) {
    ensureConnected();
    try {
      String url = buildUrl(path);
      LOGGER.debug("Creating WebDAV directory '{}'", url);
      sardine.createDirectory(url);
      return true;
    }
    catch (IOException e) {
      LOGGER.error("Failed to create WebDAV directory '{}': {}", path, e.getMessage());
      return false;
    }
  }

  /**
   * Delete a file or directory on the WebDAV server
   *
   * @param path
   *          the path to delete (relative to WebDAV root)
   * @return true if the deletion was successful
   */
  public boolean delete(String path) {
    ensureConnected();
    try {
      String url = buildUrl(path);
      LOGGER.debug("Deleting WebDAV file/directory '{}'", url);
      sardine.delete(url);
      return true;
    }
    catch (IOException e) {
      LOGGER.error("Failed to delete WebDAV file/directory '{}': {}", path, e.getMessage());
      return false;
    }
  }

  private void ensureConnected() {
    if (sardine == null) {
      try {
        connect();
      }
      catch (IOException e) {
        throw new RuntimeException("Failed to connect to WebDAV server", e);
      }
    }
  }

  private String buildUrl(String path) {
    String baseUrl = source.getUrl();
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    if (path == null || path.isEmpty() || path.equals("/")) {
      return baseUrl;
    }
    if (path.startsWith("/")) {
      path = path.substring(1);
    }

    // URL encode the path - split by "/" and encode each segment
    // This handles both already-encoded and non-encoded paths
    try {
      String[] segments = path.split("/");
      StringBuilder encodedPath = new StringBuilder();
      for (int i = 0; i < segments.length; i++) {
        if (i > 0) {
          encodedPath.append("/");
        }
        String segment = segments[i];
        if (!segment.isEmpty()) {
          // Only decode if the segment contains percent-encoded characters
          if (segment.contains("%")) {
            try {
              // Decode first (in case it's already encoded), then encode
              String decoded = java.net.URLDecoder.decode(segment, "UTF-8");
              String encoded = java.net.URLEncoder.encode(decoded, "UTF-8")
                  .replace("+", "%20")  // URLEncoder uses + for space, but we need %20
                  .replace("%2F", "/"); // Don't encode forward slashes
              encodedPath.append(encoded);
            }
            catch (IllegalArgumentException e) {
              // If decoding fails, use the original segment
              LOGGER.warn("Failed to decode segment '{}': {}", segment, e.getMessage());
              encodedPath.append(segment);
            }
          }
          else {
            // If no percent-encoded characters, just encode directly
            String encoded = java.net.URLEncoder.encode(segment, "UTF-8")
                .replace("+", "%20")  // URLEncoder uses + for space, but we need %20
                .replace("%2F", "/"); // Don't encode forward slashes
            encodedPath.append(encoded);
          }
        }
      }
      String finalUrl = baseUrl + encodedPath.toString();
      LOGGER.debug("Built URL: {} from path: {}", finalUrl, path);
      return finalUrl;
    }
    catch (Exception e) {
      LOGGER.warn("Failed to URL encode path '{}': {}", path, e.getMessage());
      return baseUrl + path;
    }
  }
}

