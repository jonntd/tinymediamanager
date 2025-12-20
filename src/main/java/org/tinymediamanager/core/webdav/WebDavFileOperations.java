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

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class WebDavFileOperations - helper class for WebDAV file operations
 * 
 * @author Manuel Laggner
 */
public class WebDavFileOperations {
  private static final Logger                            LOGGER          = LoggerFactory.getLogger(WebDavFileOperations.class);

  // Lock map for directory creation - prevents concurrent creation of the same directory
  // Key format: "sourceId/path" (e.g., "uuid-123/Season 0")
  private static final ConcurrentHashMap<String, Object> DIRECTORY_LOCKS = new ConcurrentHashMap<>();

  /**
   * Move/rename a WebDAV file
   * 
   * @param sourceWebDavPath
   *          the source WebDAV path (format: webdav://source-id/path)
   * @param destWebDavPath
   *          the destination WebDAV path (format: webdav://source-id/path)
   * @return the actual destination path if move was successful (may differ from destWebDavPath if file already existed), or null if move failed
   */
  public static String moveWebDavFile(String sourceWebDavPath, String destWebDavPath) {
    WebDavClient client = null;
    try {
      // Parse source path
      String[] sourceParts = WebDavDataSourceHelper.parseWebDavPath(sourceWebDavPath);
      if (sourceParts == null || sourceParts.length < 2) {
        LOGGER.error("Invalid source WebDAV path: {}", WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath));
        return null;
      }
      String sourceId = sourceParts[0];
      String sourcePath = sourceParts[1];

      // Parse destination path
      String[] destParts = WebDavDataSourceHelper.parseWebDavPath(destWebDavPath);
      if (destParts == null || destParts.length < 2) {
        LOGGER.error("Invalid destination WebDAV path: {}", WebDavDataSourceHelper.decodeWebDavPath(destWebDavPath));
        return null;
      }
      String destId = destParts[0];
      String destPath = destParts[1];

      // Check if source and destination are on the same WebDAV server
      boolean sameServer = sourceId.equals(destId);

      // Relaxed check: valid if both resolve to the same WebDavSource object
      if (!sameServer) {
        WebDavSource srcSource = WebDavDataSourceHelper.getWebDavSource(sourceId);
        WebDavSource destSource = WebDavDataSourceHelper.getWebDavSource(destId);

        // Try decoding IDs if direct lookup fails (handle potential encoding mismatch)
        if (srcSource == null) {
          srcSource = WebDavDataSourceHelper.getWebDavSource(WebDavDataSourceHelper.decodeWebDavPath(sourceId));
        }
        if (destSource == null) {
          destSource = WebDavDataSourceHelper.getWebDavSource(WebDavDataSourceHelper.decodeWebDavPath(destId));
        }

        if (srcSource != null && destSource != null && srcSource.equals(destSource)) {
          sameServer = true;
        }
      }

      if (!sameServer) {
        LOGGER.error("Cannot move files between different WebDAV servers: {} -> {}", WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath),
            WebDavDataSourceHelper.decodeWebDavPath(destWebDavPath));
        return null;
      }

      // Get WebDAV source
      WebDavSource source = WebDavDataSourceHelper.getWebDavSource(sourceId);
      if (source == null) {
        LOGGER.error("WebDAV source not found: {}", sourceId);
        return null;
      }

      // Get WebDAV client
      client = WebDavDataSourceHelper.createClient(source);
      if (client == null) {
        LOGGER.error("Failed to create WebDAV client for source: {}", sourceId);
        return null;
      }

      // Check if source and destination are the same
      if (sourcePath.equals(destPath)) {
        LOGGER.info("Source and destination are the same, skipping move: {}", sourcePath);
        return destWebDavPath;
      }

      // Ensure parent directory exists
      String destParent = getParentPath(destPath);
      if (destParent != null && !destParent.isEmpty()) {
        // Use per-directory lock to prevent concurrent creation of the same directory
        // This eliminates 423 Locked errors from WebDAV server
        String lockKey = sourceId + "/" + destParent;
        Object lock = DIRECTORY_LOCKS.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
          // Double-check if directory exists (another thread might have created it while we waited for lock)
          if (!client.exists(destParent)) {
            LOGGER.debug("Creating parent directory: {}", destParent);
            if (!client.createDirectory(destParent)) {
              LOGGER.error("Failed to create parent directory: {}", destParent);
              return null;
            }
            LOGGER.debug("Successfully created parent directory: {}", destParent);
          }
          else {
            LOGGER.debug("Parent directory already exists: {}", destParent);
          }
        }

        // Clean up lock object if no longer needed (optional, prevents memory leak)
        // Only remove if no other threads are waiting
        DIRECTORY_LOCKS.remove(lockKey, lock);
      }

      // Check if destination file already exists and generate unique filename if needed
      String actualDestPath = destPath;
      if (client.exists(destPath)) {
        LOGGER.warn("Destination file already exists: {}", destPath);
        // Generate unique filename by adding suffix
        actualDestPath = generateUniqueDestPath(client, destPath);
        LOGGER.info("Using unique destination path: {}", actualDestPath);
      }

      // Build the actual WebDAV path with prefix for return value
      String actualDestWebDavPath = "webdav://" + destId + actualDestPath;

      // Move the file
      if (client.move(sourcePath, actualDestPath)) {
        LOGGER.info("Moved WebDAV file from '{}' to '{}'", sourcePath, actualDestPath);
        return actualDestWebDavPath;
      }
      else {
        // Fallback: Copy and Delete (Standard workaround for buggy WebDAV servers returning 500/409 on MOVE)
        LOGGER.warn("Move failed (possible server error 500), attempting copy and delete fallback regarding '{}'", sourcePath);
        if (client.copy(sourcePath, actualDestPath)) {
          if (client.delete(sourcePath)) {
            LOGGER.info("Successfully moved (via copy+delete) WebDAV file from '{}' to '{}'", sourcePath, actualDestPath);
            return actualDestWebDavPath;
          }
          else {
            // Copy succeeded, delete failed. Return null to prevent path update.
            // This leaves the source file intact, and we clean up the destination copy to avoid duplication.
            LOGGER.error("Failed to delete source file after copy: '{}'. Cleaning up copy and returning null to prevent path update.", sourcePath);
            client.delete(actualDestPath);
            return null;
          }
        }

        LOGGER.error("Failed to move (and copy fallback) WebDAV file from '{}' to '{}'", sourcePath, actualDestPath);
        return null;
      }
    }
    catch (Exception e) {
      LOGGER.error("Error moving WebDAV file from '{}' to '{}': {}", WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath),
          WebDavDataSourceHelper.decodeWebDavPath(destWebDavPath), e.getMessage());
      return null;
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
        LOGGER.error("Invalid source WebDAV path: {}", WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath));
        return false;
      }
      String sourceId = sourceParts[0];
      String sourcePath = sourceParts[1];

      // Parse destination path
      String[] destParts = WebDavDataSourceHelper.parseWebDavPath(destWebDavPath);
      if (destParts == null || destParts.length < 2) {
        LOGGER.error("Invalid destination WebDAV path: {}", WebDavDataSourceHelper.decodeWebDavPath(destWebDavPath));
        return false;
      }
      String destId = destParts[0];
      String destPath = destParts[1];

      // Check if source and destination are on the same WebDAV server
      boolean sameServer = sourceId.equals(destId);

      // Relaxed check: valid if both resolve to the same WebDavSource object
      if (!sameServer) {
        WebDavSource srcSource = WebDavDataSourceHelper.getWebDavSource(sourceId);
        WebDavSource destSource = WebDavDataSourceHelper.getWebDavSource(destId);

        // Try decoding IDs if direct lookup fails
        if (srcSource == null) {
          srcSource = WebDavDataSourceHelper.getWebDavSource(WebDavDataSourceHelper.decodeWebDavPath(sourceId));
        }
        if (destSource == null) {
          destSource = WebDavDataSourceHelper.getWebDavSource(WebDavDataSourceHelper.decodeWebDavPath(destId));
        }

        if (srcSource != null && destSource != null && srcSource.equals(destSource)) {
          sameServer = true;
        }
      }

      if (!sameServer) {
        LOGGER.error("Cannot copy files between different WebDAV servers: {} -> {}", WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath),
            WebDavDataSourceHelper.decodeWebDavPath(destWebDavPath));
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

      // Check if source and destination are the same
      if (sourcePath.equals(destPath)) {
        LOGGER.info("Source and destination are the same, skipping copy: {}", sourcePath);
        return true;
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
      LOGGER.error("Error copying WebDAV file from '{}' to '{}': {}", WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath),
          WebDavDataSourceHelper.decodeWebDavPath(destWebDavPath), e.getMessage());
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

  /**
   * Generate a unique destination path by adding suffix if the file already exists
   * 
   * @param client
   *          the WebDAV client
   * @param destPath
   *          the original destination path
   * @return a unique path that doesn't exist on the server
   */
  private static String generateUniqueDestPath(WebDavClient client, String destPath) {
    // Extract base name and extension
    int lastDot = destPath.lastIndexOf('.');
    int lastSlash = destPath.lastIndexOf('/');

    String basePath;
    String extension;

    if (lastDot > lastSlash) {
      basePath = destPath.substring(0, lastDot);
      extension = destPath.substring(lastDot);
    }
    else {
      basePath = destPath;
      extension = "";
    }

    // Try adding suffix until we find a unique name
    for (int i = 1; i <= 99; i++) {
      String newPath = basePath + "_" + i + extension;
      if (!client.exists(newPath)) {
        return newPath;
      }
    }

    // Fallback: use timestamp
    return basePath + "_" + System.currentTimeMillis() + extension;
  }
}
