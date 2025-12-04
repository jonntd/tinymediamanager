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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class WebDavFileOperations - helper class for WebDAV file operations
 * 
 * @author Manuel Laggner
 */
public class WebDavFileOperations {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavFileOperations.class);

  /**
   * Move/rename a WebDAV file
   * 
   * @param sourceWebDavPath
   *          the source WebDAV path (format: webdav://source-id/path)
   * @param destWebDavPath
   *          the destination WebDAV path (format: webdav://source-id/path)
   * @return true if the move was successful
   */
  public static boolean moveWebDavFile(String sourceWebDavPath, String destWebDavPath) {
    WebDavClient client = null;
    try {
      // Parse source path
      String[] sourceParts = WebDavDataSourceHelper.parseWebDavPath(sourceWebDavPath);
      if (sourceParts == null || sourceParts.length < 2) {
        LOGGER.error("Invalid source WebDAV path: {}", sourceWebDavPath);
        return false;
      }
      String sourceId = sourceParts[0];
      String sourcePath = sourceParts[1];

      // Parse destination path
      String[] destParts = WebDavDataSourceHelper.parseWebDavPath(destWebDavPath);
      if (destParts == null || destParts.length < 2) {
        LOGGER.error("Invalid destination WebDAV path: {}", destWebDavPath);
        return false;
      }
      String destId = destParts[0];
      String destPath = destParts[1];

      // Check if source and destination are on the same WebDAV server
      if (!sourceId.equals(destId)) {
        LOGGER.error("Cannot move files between different WebDAV servers: {} -> {}", sourceWebDavPath, destWebDavPath);
        return false;
      }

      // Get WebDAV source
      WebDavSource source = WebDavDataSourceHelper.getWebDavSource(sourceId);
      if (source == null) {
        LOGGER.error("WebDAV source not found: {}", sourceId);
        return false;
      }

      // Get WebDAV client
      client = WebDavDataSourceHelper.createClient(source);
      if (client == null) {
        LOGGER.error("Failed to create WebDAV client for source: {}", sourceId);
        return false;
      }

      // Ensure parent directory exists
      String destParent = getParentPath(destPath);
      if (destParent != null && !destParent.isEmpty() && !client.exists(destParent)) {
        LOGGER.debug("Creating parent directory: {}", destParent);
        if (!client.createDirectory(destParent)) {
          LOGGER.error("Failed to create parent directory: {}", destParent);
          return false;
        }
      }

      // Move the file
      return client.move(sourcePath, destPath);
    }
    catch (Exception e) {
      LOGGER.error("Error moving WebDAV file from '{}' to '{}': {}", sourceWebDavPath, destWebDavPath, e.getMessage());
      return false;
    }
    finally {
      // Always disconnect the client to prevent resource leaks
      if (client != null) {
        client.disconnect();
      }
    }
  }

  /**
   * Copy a WebDAV file
   * 
   * @param sourceWebDavPath
   *          the source WebDAV path (format: webdav://source-id/path)
   * @param destWebDavPath
   *          the destination WebDAV path (format: webdav://source-id/path)
   * @return true if the copy was successful
   */
  public static boolean copyWebDavFile(String sourceWebDavPath, String destWebDavPath) {
    WebDavClient client = null;
    try {
      // Parse source path
      String[] sourceParts = WebDavDataSourceHelper.parseWebDavPath(sourceWebDavPath);
      if (sourceParts == null || sourceParts.length < 2) {
        LOGGER.error("Invalid source WebDAV path: {}", sourceWebDavPath);
        return false;
      }
      String sourceId = sourceParts[0];
      String sourcePath = sourceParts[1];

      // Parse destination path
      String[] destParts = WebDavDataSourceHelper.parseWebDavPath(destWebDavPath);
      if (destParts == null || destParts.length < 2) {
        LOGGER.error("Invalid destination WebDAV path: {}", destWebDavPath);
        return false;
      }
      String destId = destParts[0];
      String destPath = destParts[1];

      // Check if source and destination are on the same WebDAV server
      if (!sourceId.equals(destId)) {
        LOGGER.error("Cannot copy files between different WebDAV servers: {} -> {}", sourceWebDavPath, destWebDavPath);
        return false;
      }

      // Get WebDAV source
      WebDavSource source = WebDavDataSourceHelper.getWebDavSource(sourceId);
      if (source == null) {
        LOGGER.error("WebDAV source not found: {}", sourceId);
        return false;
      }

      // Get WebDAV client
      client = WebDavDataSourceHelper.createClient(source);
      if (client == null) {
        LOGGER.error("Failed to create WebDAV client for source: {}", sourceId);
        return false;
      }

      // Ensure parent directory exists
      String destParent = getParentPath(destPath);
      if (destParent != null && !destParent.isEmpty() && !client.exists(destParent)) {
        LOGGER.debug("Creating parent directory: {}", destParent);
        if (!client.createDirectory(destParent)) {
          LOGGER.error("Failed to create parent directory: {}", destParent);
          return false;
        }
      }

      // Copy the file
      return client.copy(sourcePath, destPath);
    }
    catch (Exception e) {
      LOGGER.error("Error copying WebDAV file from '{}' to '{}': {}", sourceWebDavPath, destWebDavPath, e.getMessage());
      return false;
    }
    finally {
      // Always disconnect the client to prevent resource leaks
      if (client != null) {
        client.disconnect();
      }
    }
  }

  /**
   * Get the parent path of a given path
   * 
   * @param path
   *          the path
   * @return the parent path, or null if there is no parent
   */
  private static String getParentPath(String path) {
    if (path == null || path.isEmpty() || path.equals("/")) {
      return null;
    }
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash <= 0) {
      return "/";
    }
    return path.substring(0, lastSlash);
  }
}

