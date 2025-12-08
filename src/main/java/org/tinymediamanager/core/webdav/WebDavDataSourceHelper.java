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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;

/**
 * Helper class for WebDAV data source operations
 * 
 * @author Manuel Laggner
 */
public class WebDavDataSourceHelper {
  private static final Logger LOGGER              = LoggerFactory.getLogger(WebDavDataSourceHelper.class);

  public static final String  WEBDAV_PREFIX       = "webdav://";
  public static final String  WINDOWS_SAFE_PREFIX = "webdav-safe-";

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
    // Also support Windows-safe prefix
    return path != null && (path.startsWith(WEBDAV_PREFIX) || path.startsWith("webdav:/") || path.startsWith(WINDOWS_SAFE_PREFIX));
  }

  /**
   * Normalize WebDAV path format: ensure "webdav://" instead of "webdav:/" Also fixes malformed paths where UUID is directly followed by URL-encoded
   * path This is needed because Path.toString() may output single slash format
   * 
   * @param path
   *          the path to normalize
   * @return the normalized path
   */
  public static String normalizeWebDavPath(String path) {
    if (path == null) {
      return path;
    }

    String result = path;

    // Step 0: Handle Windows-safe prefix (convert back to webdav://)
    // path could be "webdav-safe-uuid\folder\file" (Windows separators)
    if (result.startsWith(WINDOWS_SAFE_PREFIX)) {
      // replace backslashes with forward slashes first
      result = result.replace('\\', '/');
      // replace prefix
      result = WEBDAV_PREFIX + result.substring(WINDOWS_SAFE_PREFIX.length());
    }

    // Step 1: Ensure webdav:// format (double slash)
    // Match exactly "webdav:/" followed by anything that is NOT a second slash
    if (result.startsWith("webdav:/") && !result.startsWith(WEBDAV_PREFIX)) {
      result = WEBDAV_PREFIX + result.substring(8); // 8 = length of "webdav:/"
    }

    // Step 2: Fix malformed paths where UUID is directly followed by URL-encoded content
    // Example: webdav://uuid%E5%88%AE%E5%89%8A... -> webdav://uuid/%E5%88%AE%E5%89%8A...
    if (result.startsWith(WEBDAV_PREFIX)) {
      String afterPrefix = result.substring(WEBDAV_PREFIX.length());

      // Check if this looks like a UUID followed by URL-encoded content without slash
      if (afterPrefix.length() >= 36) {
        String potentialUuid = afterPrefix.substring(0, 36);
        if (isValidUuidFormat(potentialUuid)) {
          // Check if the character right after UUID is not a slash but starts with % (URL encoding)
          if (afterPrefix.length() > 36) {
            char afterUuid = afterPrefix.charAt(36);
            if (afterUuid == '%') {
              // Missing slash after UUID, insert it
              String remotePath = afterPrefix.substring(36);
              result = WEBDAV_PREFIX + potentialUuid + "/" + remotePath;
              LOGGER.debug("Fixed malformed WebDAV path: added missing slash after UUID. Original: '{}', Fixed: '{}'", path, result);
            }
          }
        }
      }
    }

    // Step 3: Decode URL-encoded characters in the path (after UUID)
    // This converts %E5%88%AE%E5%89%8A%E6%B5%8B%E8%AF%95 -> 刮削测试
    if (result.startsWith(WEBDAV_PREFIX) && result.contains("%")) {
      try {
        String afterPrefix = result.substring(WEBDAV_PREFIX.length());
        int firstSlash = afterPrefix.indexOf('/');
        if (firstSlash > 0) {
          String sourceId = afterPrefix.substring(0, firstSlash);
          String remotePath = afterPrefix.substring(firstSlash);
          // Decode the remote path portion only if it contains URL-encoded characters
          if (remotePath.contains("%")) {
            // Preserve '+' by pre-encoding it before decoding
            String preservedPlus = remotePath.replace("+", "%2B");
            String decodedPath = java.net.URLDecoder.decode(preservedPlus, "UTF-8");
            result = WEBDAV_PREFIX + sourceId + decodedPath;
            LOGGER.debug("Decoded URL-encoded WebDAV path: '{}' -> '{}'", path, result);
          }
        }
      }
      catch (Exception e) {
        LOGGER.warn("Failed to decode WebDAV path: {} - {}", result, e.getMessage());
      }
    }

    return result;
  }

  /**
   * Create a Path object from a WebDAV path string. Handles Windows-specific safety (replacing "webdav://" with "webdav-safe-") to avoid
   * InvalidPathException.
   * 
   * @param path
   *          the WebDAV path (e.g. "webdav://...")
   * @return a Path object (safe for the current OS)
   */
  public static Path getWebDavPath(String path) {
    String normalized = normalizeWebDavPath(path);
    if (SystemUtils.IS_OS_WINDOWS) {
      normalized = normalized.replace(WEBDAV_PREFIX, WINDOWS_SAFE_PREFIX);
    }
    return Paths.get(normalized);
  }

  /**
   * Parse a WebDAV path and return the source identifier and remote path Format: webdav://[source-id-or-name]/remote/path
   * 
   * @param webDavPath
   *          the WebDAV path
   * @return String array with [sourceIdentifier, remotePath] or null if invalid
   */
  public static String[] parseWebDavPath(String webDavPath) {
    if (!isWebDavPath(webDavPath)) {
      return null;
    }

    // Normalize the WebDAV path format first
    // Convert "webdav:/" to "webdav://" for consistent parsing
    String normalizedPath = webDavPath;
    if (normalizedPath.startsWith("webdav:/") && !normalizedPath.startsWith(WEBDAV_PREFIX)) {
      normalizedPath = normalizedPath.replaceFirst("webdav:/", WEBDAV_PREFIX);
    }

    // Remove prefix - now we only need to handle "webdav://" format
    String pathWithoutPrefix;
    if (normalizedPath.startsWith(WEBDAV_PREFIX)) {
      pathWithoutPrefix = normalizedPath.substring(WEBDAV_PREFIX.length());
    }
    else {
      return null;
    }

    // Normalize multiple slashes to single slash at the beginning
    // This handles cases like "webdav://source-id//path" -> "source-id/path"
    while (pathWithoutPrefix.startsWith("/")) {
      pathWithoutPrefix = pathWithoutPrefix.substring(1);
    }

    // Now pathWithoutPrefix should be in format: "source-identifier/remote/path" or "source-identifier"
    // The source identifier can be either a UUID (format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
    // or a display name (e.g., "aaa", "bbb", "ccc")

    String sourceIdentifier;
    String remotePath;

    // First, try to extract UUID from the beginning (most reliable method for legacy paths)
    if (pathWithoutPrefix.length() >= 36) {
      String potentialUuid = pathWithoutPrefix.substring(0, 36);
      if (isValidUuidFormat(potentialUuid)) {
        // Valid UUID found at the beginning
        sourceIdentifier = potentialUuid;

        // The rest is the remote path
        if (pathWithoutPrefix.length() > 36) {
          remotePath = pathWithoutPrefix.substring(36);
          // Ensure remotePath starts with '/'
          if (!remotePath.startsWith("/")) {
            remotePath = "/" + remotePath;
          }
        }
        else {
          remotePath = "/";
        }
      }
      else {
        // Not a valid UUID, use slash-based parsing (supports display names)
        int slashIndex = pathWithoutPrefix.indexOf('/');
        if (slashIndex == -1) {
          return new String[] { pathWithoutPrefix, "/" };
        }
        sourceIdentifier = pathWithoutPrefix.substring(0, slashIndex);
        remotePath = pathWithoutPrefix.substring(slashIndex);
      }
    }
    else {
      // Path too short to contain UUID, use slash-based parsing (supports display names)
      int slashIndex = pathWithoutPrefix.indexOf('/');
      if (slashIndex == -1) {
        return new String[] { pathWithoutPrefix, "/" };
      }
      sourceIdentifier = pathWithoutPrefix.substring(0, slashIndex);
      remotePath = pathWithoutPrefix.substring(slashIndex);
    }

    if (StringUtils.isBlank(remotePath)) {
      remotePath = "/";
    }

    return new String[] { sourceIdentifier, remotePath };
  }

  /**
   * Check if a string matches the UUID format (8-4-4-4-12)
   * 
   * @param str
   *          the string to check
   * @return true if it matches UUID format
   */
  private static boolean isValidUuidFormat(String str) {
    if (str == null || str.length() != 36) {
      return false;
    }

    // Check the pattern: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
    // Hyphens should be at positions 8, 13, 18, 23
    if (str.charAt(8) != '-' || str.charAt(13) != '-' || str.charAt(18) != '-' || str.charAt(23) != '-') {
      return false;
    }

    // Check that other characters are hex digits
    for (int i = 0; i < 36; i++) {
      if (i == 8 || i == 13 || i == 18 || i == 23) {
        continue; // Skip hyphens
      }
      char c = str.charAt(i);
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Get the WebDAV source by ID or name
   * 
   * @param sourceIdOrName
   *          the source ID or display name
   * @return the WebDavSource or null if not found
   */
  public static WebDavSource getWebDavSource(String sourceIdOrName) {
    return Settings.getInstance().getWebDavSourceByIdOrName(sourceIdOrName);
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

    // Decode the remote path for display
    String decodedPath = decodeUrlPath(parsed[1]);
    return "WebDAV: " + source.getName() + decodedPath;
  }

  /**
   * Decode URL-encoded characters in a WebDAV path for display purposes
   * 
   * @param webDavPath
   *          the WebDAV path (can be full webdav://... or just a path component)
   * @return the decoded path with readable characters
   */
  public static String decodeWebDavPath(String webDavPath) {
    if (webDavPath == null || webDavPath.isEmpty()) {
      return webDavPath;
    }

    // If it's a full WebDAV path, parse it first
    if (isWebDavPath(webDavPath)) {
      String[] parsed = parseWebDavPath(webDavPath);
      if (parsed == null) {
        return decodeUrlPath(webDavPath);
      }

      // Reconstruct with decoded path
      String decodedRemotePath = decodeUrlPath(parsed[1]);

      // Get source name if possible
      WebDavSource source = getWebDavSource(parsed[0]);
      if (source != null) {
        return "webdav://" + parsed[0] + decodedRemotePath;
      }

      return "webdav://" + parsed[0] + decodedRemotePath;
    }

    // Otherwise just decode the path component
    return decodeUrlPath(webDavPath);
  }

  /**
   * Decode URL-encoded characters in a path string
   * 
   * @param path
   *          the URL-encoded path
   * @return the decoded path
   */
  private static String decodeUrlPath(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }

    try {
      // IMPORTANT: URLDecoder.decode() follows application/x-www-form-urlencoded spec
      // which converts '+' to space. But in URL paths, '+' is a valid character and
      // should NOT be converted to space. Preserve '+' by pre-encoding it.
      String preservedPlus = path.replace("+", "%2B");
      return java.net.URLDecoder.decode(preservedPlus, "UTF-8");
    }
    catch (Exception e) {
      LOGGER.debug("Failed to decode URL path '{}': {}", path, e.getMessage());
      return path;
    }
  }
}
