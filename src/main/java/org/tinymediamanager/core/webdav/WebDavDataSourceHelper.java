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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;

/**
 * Helper class for WebDAV data source operations
 * 
 * @author Manuel Laggner
 */
public class WebDavDataSourceHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavDataSourceHelper.class);
  
  public static final String WEBDAV_PREFIX = "webdav://";

  private WebDavDataSourceHelper() {
    // hide constructor
  }

  /**
   * Check if the given path is a WebDAV data source
   *
   * @param path
   *          the path to check
   * @return true if it's a WebDAV path
   */
  public static boolean isWebDavPath(String path) {
    // Support both "webdav://" and "webdav:/" formats
    // (Paths.get() normalizes "//" to "/")
    return path != null && (path.startsWith(WEBDAV_PREFIX) || path.startsWith("webdav:/"));
  }

  /**
   * Parse a WebDAV path and return the source ID and remote path
   * Format: webdav://[source-id]/remote/path
   * 
   * @param webDavPath
   *          the WebDAV path
   * @return String array with [sourceId, remotePath] or null if invalid
   */
  public static String[] parseWebDavPath(String webDavPath) {
    if (!isWebDavPath(webDavPath)) {
      return null;
    }

    // Remove prefix - support both "webdav://" and "webdav:/" formats
    String pathWithoutPrefix;
    if (webDavPath.startsWith(WEBDAV_PREFIX)) {
      pathWithoutPrefix = webDavPath.substring(WEBDAV_PREFIX.length());
    }
    else if (webDavPath.startsWith("webdav:/")) {
      pathWithoutPrefix = webDavPath.substring("webdav:/".length());
    }
    else {
      return null;
    }

    int slashIndex = pathWithoutPrefix.indexOf('/');

    if (slashIndex == -1) {
      // No remote path, just source ID
      return new String[] { pathWithoutPrefix, "/" };
    }

    String sourceId = pathWithoutPrefix.substring(0, slashIndex);
    String remotePath = pathWithoutPrefix.substring(slashIndex);

    if (StringUtils.isBlank(remotePath)) {
      remotePath = "/";
    }

    return new String[] { sourceId, remotePath };
  }

  /**
   * Get the WebDAV source by ID
   * 
   * @param sourceId
   *          the source ID
   * @return the WebDavSource or null if not found
   */
  public static WebDavSource getWebDavSource(String sourceId) {
    return Settings.getInstance().getWebDavSourceById(sourceId);
  }

  /**
   * Create a WebDAV client for the given source
   * 
   * @param source
   *          the WebDAV source
   * @return the WebDavClient or null if connection failed
   */
  public static WebDavClient createClient(WebDavSource source) {
    if (source == null) {
      return null;
    }

    WebDavClient client = null;
    try {
      client = new WebDavClient(source);
      if (client.testConnection()) {
        return client;
      }
      else {
        // Connection test failed, disconnect to prevent resource leak
        client.disconnect();
        return null;
      }
    }
    catch (Exception e) {
      LOGGER.error("Failed to create WebDAV client for source '{}': {}", source.getName(), e.getMessage());
      // Disconnect on error to prevent resource leak
      if (client != null) {
        client.disconnect();
      }
      return null;
    }
  }

  /**
   * List files in a WebDAV directory
   * 
   * @param webDavPath
   *          the WebDAV path (webdav://[source-id]/remote/path)
   * @return list of WebDavFile objects
   */
  public static List<WebDavFile> listFiles(String webDavPath) {
    List<WebDavFile> files = new ArrayList<>();

    String[] parsed = parseWebDavPath(webDavPath);
    if (parsed == null) {
      return files;
    }

    WebDavSource source = getWebDavSource(parsed[0]);
    if (source == null) {
      LOGGER.error("WebDAV source not found for ID: {}", parsed[0]);
      return files;
    }

    WebDavClient client = createClient(source);
    if (client == null) {
      return files;
    }

    try {
      files = client.list(parsed[1]);
    }
    catch (Exception e) {
      LOGGER.error("Failed to list WebDAV directory '{}': {}", webDavPath, e.getMessage());
    }
    finally {
      client.disconnect();
    }

    return files;
  }

  /**
   * Build a display name for a WebDAV data source
   * 
   * @param webDavPath
   *          the WebDAV path
   * @return display name like "WebDAV: SourceName/path"
   */
  public static String getDisplayName(String webDavPath) {
    String[] parsed = parseWebDavPath(webDavPath);
    if (parsed == null) {
      return webDavPath;
    }

    WebDavSource source = getWebDavSource(parsed[0]);
    if (source == null) {
      return webDavPath;
    }

    return "WebDAV: " + source.getName() + parsed[1];
  }
}

