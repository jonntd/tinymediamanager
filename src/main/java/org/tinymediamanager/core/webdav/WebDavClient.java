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
    catch (com.github.sardine.impl.SardineException e) {
      // Extract HTTP status code for better error handling
      int statusCode = e.getStatusCode();
      String reasonPhrase = e.getResponsePhrase();

      if (statusCode == 401) {
        LOGGER.warn("WebDAV connection test failed: status code: {}, reason phrase: {}", statusCode, reasonPhrase);
        LOGGER.warn("Authentication issue for WebDAV source '{}'. This may be temporary - will retry if configured.", source.getName());
        LOGGER.info("If retries fail, please check your username and password in settings.");
      }
      else if (statusCode == 403) {
        LOGGER.error("WebDAV connection test failed: status code: {}, reason phrase: {}", statusCode, reasonPhrase);
        LOGGER.error("Access denied for WebDAV source '{}'. Please check your permissions.", source.getName());
      }
      else if (statusCode == 404) {
        LOGGER.error("WebDAV connection test failed: status code: {}, reason phrase: {}", statusCode, reasonPhrase);
        LOGGER.error("WebDAV URL not found: '{}'. Please check the URL in settings.", source.getUrl());
      }
      else if (statusCode >= 500) {
        LOGGER.warn("WebDAV connection test failed: status code: {}, reason phrase: {} (server error, may be temporary)", statusCode, reasonPhrase);
      }
      else {
        LOGGER.error("WebDAV connection test failed: status code: {}, reason phrase: {}", statusCode, reasonPhrase);
      }
      return false;
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
    return listWithRetry(path, true);
  }

  /**
   * Internal list method with retry support for connection pool shutdown
   */
  private List<WebDavFile> listWithRetry(String path, boolean allowRetry) throws IOException {
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
            // Preserve '+' in URL path (URLDecoder converts '+' to space)
            String preservedPlus = normalizedHref.replace("+", "%2B");
            decodedHref = java.net.URLDecoder.decode(preservedPlus, "UTF-8");
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
    catch (IllegalStateException e) {
      // Handle "Connection pool shut down" error by reconnecting and retrying once
      if (allowRetry && e.getMessage() != null && e.getMessage().contains("Connection pool shut down")) {
        LOGGER.warn("WebDAV connection pool was shut down, attempting to reconnect...");
        try {
          reconnect();
          return listWithRetry(path, false); // Retry once without further retries
        }
        catch (IOException reconnectError) {
          LOGGER.error("Failed to reconnect to WebDAV server: {}", reconnectError.getMessage());
          throw new IOException("WebDAV connection pool shut down and reconnection failed", e);
        }
      }
      else {
        LOGGER.error("Failed to list WebDAV directory '{}': {}", fullUrl, e.getMessage());
        throw new IOException("Failed to list WebDAV directory: " + e.getMessage(), e);
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

      // Check if source and destination are the same
      if (sourceUrl.equals(destUrl)) {
        LOGGER.info("Source and destination are the same, skipping move: {}", safeDecode(sourcePath));
        return true;
      }

      LOGGER.debug("Moving WebDAV file from '{}' to '{}'", safeDecode(sourcePath), safeDecode(destPath));
      LOGGER.debug("Raw source path: '{}', raw dest path: '{}'", safeDecode(sourcePath), safeDecode(destPath));
      sardine.move(sourceUrl, destUrl);
      return true;
    }
    catch (IOException e) {
      LOGGER.error("Failed to move WebDAV file from '{}' to '{}': {}", safeDecode(sourcePath), safeDecode(destPath), e.getMessage());
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
      LOGGER.debug("Copying WebDAV file from '{}' to '{}'", safeDecode(sourcePath), safeDecode(destPath));
      sardine.copy(sourceUrl, destUrl);
      return true;
    }
    catch (IOException e) {
      LOGGER.error("Failed to copy WebDAV file from '{}' to '{}': {}", safeDecode(sourcePath), safeDecode(destPath), e.getMessage());
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
      LOGGER.debug("Creating WebDAV directory '{}'", safeDecode(url));
      sardine.createDirectory(url);
      return true;
    }
    catch (IOException e) {
      // 423 Locked is common in concurrent operations - log as warning instead of error
      if (e.getMessage() != null && e.getMessage().contains("423")) {
        LOGGER.warn("WebDAV directory '{}' is locked (concurrent operation): {}", safeDecode(path), e.getMessage());
      }
      else {
        LOGGER.error("Failed to create WebDAV directory '{}': {}", safeDecode(path), e.getMessage());
      }
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
      LOGGER.debug("Deleting WebDAV file/directory '{}'", safeDecode(url));
      sardine.delete(url);
      return true;
    }
    catch (IOException e) {
      LOGGER.error("Failed to delete WebDAV file/directory '{}': {}", safeDecode(path), e.getMessage());
      return false;
    }
  }

  /**
   * Check if the client is currently connected
   * 
   * @return true if connected and ready to use
   */
  public boolean isConnected() {
    return sardine != null;
  }

  /**
   * Ensure the client is connected, reconnecting if necessary. This method is thread-safe and handles connection pool shutdown gracefully.
   */
  private synchronized void ensureConnected() {
    if (sardine == null) {
      try {
        LOGGER.debug("WebDAV client not connected, establishing connection...");
        connect();
        LOGGER.debug("WebDAV connection established successfully");
      }
      catch (IOException e) {
        throw new RuntimeException("Failed to connect to WebDAV server", e);
      }
    }
    else {
      // Test if the connection is still valid by checking if the underlying client is usable
      // Sardine doesn't expose a direct "isConnected" method, so we rely on try-catch in operations
      // But we can at least ensure the sardine instance is not null
      LOGGER.trace("WebDAV client already connected");
    }
  }

  /**
   * Reconnect to the WebDAV server. Useful if the connection was reset or the pool was shut down.
   * 
   * @throws IOException
   *           if reconnection fails
   */
  public synchronized void reconnect() throws IOException {
    LOGGER.info("Reconnecting to WebDAV server...");
    disconnect();
    connect();
    LOGGER.info("WebDAV reconnection successful");
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

    // For WebDAV paths, we need to be careful with URL encoding
    // The path might already be URL encoded from the WebDAV client
    // Just ensure proper URL format without double encoding
    try {
      // IMPORTANT: URLDecoder.decode() follows application/x-www-form-urlencoded spec
      // which converts '+' to space. But in URL paths, '+' is a valid character and
      // should NOT be converted to space. Only %2B represents a plus sign.
      // So we need to preserve '+' by pre-encoding it before decoding.
      String pathWithPreservedPlus = path.replace("+", "%2B");
      String decodedPath = java.net.URLDecoder.decode(pathWithPreservedPlus, "UTF-8");

      // Use URLEncoder to be more aggressive with encoding (e.g. handle parentheses)
      // but we need to preserve slashes and encoded spaces correctly
      String encodedPath = java.net.URLEncoder.encode(decodedPath, "UTF-8").replace("+", "%20").replace("%2F", "/");

      String finalUrl = baseUrl + encodedPath;
      // Use safeDecode for logging
      LOGGER.debug("Built URL: {} from path: {}", finalUrl, safeDecode(path));
      return finalUrl;
    }
    catch (Exception e) {
      LOGGER.warn("Failed to URL encode path '{}' (checking raw: {}): {}", safeDecode(path), path, e.getMessage());
      // Fallback: use the original path as-is
      String finalUrl = baseUrl + path;
      LOGGER.debug("Fallback URL: {} from path: {}", finalUrl, safeDecode(path));
      return finalUrl;
    }
  }

  /**
   * Safe decode for logging purposes
   */
  private String safeDecode(String path) {
    if (path == null) {
      return "";
    }
    try {
      // Preserve '+' in URL path (URLDecoder converts '+' to space)
      String preservedPlus = path.replace("+", "%2B");
      return java.net.URLDecoder.decode(preservedPlus, "UTF-8");
    }
    catch (Exception e) {
      return path;
    }
  }
}
