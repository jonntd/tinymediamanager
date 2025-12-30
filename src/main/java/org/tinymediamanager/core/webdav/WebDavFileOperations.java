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

import java.util.List;
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
   * Move/rename a WebDAV file using a shared client (for batch operations). This avoids creating a new connection for each file, significantly
   * improving performance.
   * 
   * @param client
   *          the shared WebDAV client (must be already connected)
   * @param sourceId
   *          the source ID (UUID or name)
   * @param sourcePath
   *          the source path (relative to WebDAV root, e.g., "/folder/file.mkv")
   * @param destPath
   *          the destination path (relative to WebDAV root)
   * @return the actual destination path if move was successful, or null if move failed
   */
  public static String moveWebDavFileWithClient(WebDavClient client, String sourceId, String sourcePath, String destPath) {
    try {
      // Check if source and destination are the same
      if (sourcePath.equals(destPath)) {
        LOGGER.debug("Source and destination are the same, skipping move: {}", sourcePath);
        return "webdav://" + sourceId + destPath;
      }

      // Ensure parent directory exists
      String destParent = getParentPath(destPath);
      if (destParent != null && !destParent.isEmpty()) {
        // Use per-directory lock to prevent concurrent creation of the same directory
        String lockKey = sourceId + "/" + destParent;
        Object lock = DIRECTORY_LOCKS.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
          if (!client.exists(destParent)) {
            LOGGER.debug("Creating parent directory: {}", destParent);
            if (!client.createDirectory(destParent)) {
              LOGGER.error("Failed to create parent directory: {}", destParent);
              return null;
            }
          }
        }
        DIRECTORY_LOCKS.remove(lockKey, lock);
      }

      // Check if destination file already exists and handle accordingly
      String actualDestPath = destPath;
      if (client.exists(destPath)) {
        boolean sourceIsDir = isWebDavDirectory(client, sourcePath);
        boolean destIsDir = isWebDavDirectory(client, destPath);

        // Optimization: If both are directories, perform a "merge"
        if (sourceIsDir && destIsDir) {
          LOGGER.info("Both source and destination are directories, merging content: '{}' -> '{}'", sourcePath, destPath);
          if (mergeWebDavDirectories(client, sourcePath, destPath)) {
            LOGGER.info("Successfully merged WebDAV directory from '{}' to '{}'", sourcePath, destPath);
            return "webdav://" + sourceId + destPath;
          }
          else {
            LOGGER.error("Failed to merge WebDAV directory from '{}' to '{}'", sourcePath, destPath);
            return null;
          }
        }

        LOGGER.warn("Destination file already exists: {}", destPath);
        actualDestPath = generateUniqueDestPath(client, destPath);
        LOGGER.info("Using unique destination path: {}", actualDestPath);
      }

      // Build the actual WebDAV path with prefix for return value
      String actualDestWebDavPath = "webdav://" + sourceId + actualDestPath;

      // Move the file
      // 使用 overwrite=false 进行防御性移动，以便在 AList 缓存假阴性时能触发后续的冲突自愈逻辑
      if (client.move(sourcePath, actualDestPath, false)) {
        LOGGER.info("Moved WebDAV file from '{}' to '{}'", sourcePath, actualDestPath);
        return actualDestWebDavPath;
      }

      else {
        // [修复 500 虚假失败 & 冲突自愈]
        try {
          Thread.sleep(1000); // 给服务器一点处理内部事务的时间

          // 场景 A: 实际上后台已经 Move 成功了 (后端超时但已完成)
          if (!client.exists(sourcePath) && client.exists(actualDestPath)) {
            LOGGER.info("Move command for '{}' returned error, but source is gone and dest exists. Assuming backend success.", sourcePath);
            return actualDestWebDavPath;
          }

          // 场景 B: 目标路径真的发生了碰撞，且 exists() 也没查出来 (AList 缓存假阴性)
          if (client.exists(sourcePath) && client.exists(actualDestPath)) {
            LOGGER.warn("Collision detected for '{}' -> '{}' after error. Generating absolute unique path to recover.", sourcePath, actualDestPath);
            String absoluteUniquePath = generateAbsoluteUniqueDestPath(client, actualDestPath);
            if (client.move(sourcePath, absoluteUniquePath)) {
              LOGGER.info("Successfully recovered from collision by moving to unique path: {}", absoluteUniquePath);
              return "webdav://" + sourceId + absoluteUniquePath;
            }
          }
        }
        catch (Exception ex) {
          LOGGER.debug("Conflict self-healing validation failed: {}", ex.getMessage());
        }

        // Fallback: Copy and Delete

        // 在进入降级逻辑前静默观察 1500ms，给服务器（如 123 盘）一些同步索引的时间，避免由于瞬时 500 导致 copy 也失败
        try {
          Thread.sleep(1500);
        }
        catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }

        LOGGER.warn("Move failed, attempting copy and delete fallback for '{}' to '{}' (after 1.5s buffer)", sourcePath, actualDestPath);
        if (client.copy(sourcePath, actualDestPath)) {
          if (client.delete(sourcePath)) {
            LOGGER.info("Successfully moved (via copy+delete) WebDAV file from '{}' to '{}'", sourcePath, actualDestPath);
            return actualDestWebDavPath;
          }
          else {
            LOGGER.error("Failed to delete source file after copy: '{}'", sourcePath);
            client.delete(actualDestPath);
            return null;
          }
        }
        LOGGER.error("Failed to move WebDAV file from '{}' to '{}' (Move and fallback Copy both failed)", sourcePath, actualDestPath);
        return null;
      }

    }
    catch (Exception e) {
      LOGGER.error("Error moving WebDAV file from '{}' to '{}': {}", sourcePath, destPath, e.getMessage());
      return null;
    }
  }

  /**
   * Copy a WebDAV file using a shared client (for batch operations). This avoids creating a new connection for each file, significantly improving
   * performance.
   * 
   * @param client
   *          the shared WebDAV client (must be already connected)
   * @param sourceId
   *          the source ID (UUID or name)
   * @param sourcePath
   *          the source path (relative to WebDAV root)
   * @param destPath
   *          the destination path (relative to WebDAV root)
   * @return true if the copy was successful
   */
  public static boolean copyWebDavFileWithClient(WebDavClient client, String sourceId, String sourcePath, String destPath) {
    try {
      // Check if source and destination are the same
      if (sourcePath.equals(destPath)) {
        LOGGER.debug("Source and destination are the same, skipping copy: {}", sourcePath);
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
      // 使用 overwrite=false 以便识别潜在冲突
      if (client.copy(sourcePath, destPath, false)) {
        LOGGER.info("Copied WebDAV file from '{}' to '{}'", sourcePath, destPath);
        return true;
      }

      else {
        // [冲突自愈] 同理，Copy 如果返回 500 也执行自愈验证
        try {
          if (client.exists(destPath)) {
            LOGGER.info("Copy command for '{}' returned error, but destination already exists. Assuming backend success or conflict.", sourcePath);
            return true;
          }
        }
        catch (Exception ex) {
          // ignore
        }
        LOGGER.error("Failed to copy WebDAV file from '{}' to '{}' after retries and self-healing effort", sourcePath, destPath);
        return false;
      }

    }
    catch (Exception e) {
      LOGGER.error("Error copying WebDAV file from '{}' to '{}': {}", sourcePath, destPath, e.getMessage());
      return false;
    }

  }

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
    String sourcePath = null;
    String destPath = null;
    try {
      // Parse source path
      String[] sourceParts = WebDavDataSourceHelper.parseWebDavPath(sourceWebDavPath);
      if (sourceParts == null || sourceParts.length < 2) {
        LOGGER.error("Invalid source WebDAV path: {}", WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath));
        return null;
      }
      String sourceId = sourceParts[0];
      sourcePath = sourceParts[1];

      // Parse destination path
      String[] destParts = WebDavDataSourceHelper.parseWebDavPath(destWebDavPath);
      if (destParts == null || destParts.length < 2) {

        LOGGER.error("Invalid destination WebDAV path: {}", WebDavDataSourceHelper.decodeWebDavPath(destWebDavPath));
        return null;
      }
      String destId = destParts[0];
      destPath = destParts[1];

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

      // Fix for malformed source path (missing slash after sourceId)
      // Example: src="webdav://aaaPath/...", dest="webdav://aaa/Path/..."
      // This happens if URI construction missed a slash. If we can match destId as prefix, we assume it's the same server.
      if (!sameServer && sourceId.startsWith(destId)) {
        String expectedPrefix = "webdav://" + destId;
        if (sourceWebDavPath.startsWith(expectedPrefix) && !sourceWebDavPath.startsWith(expectedPrefix + "/")) {
          // Try to inject the missing slash
          String fixedSourcePath = expectedPrefix + "/" + sourceWebDavPath.substring(expectedPrefix.length());
          String[] fixedSourceParts = WebDavDataSourceHelper.parseWebDavPath(fixedSourcePath);

          if (fixedSourceParts != null && fixedSourceParts[0].equals(destId)) {
            LOGGER.warn("Detected malformed WebDAV source path (possible missing slash), auto-fixing: '{}' -> '{}'",
                WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath), WebDavDataSourceHelper.decodeWebDavPath(fixedSourcePath));

            // Assign fixed values
            sourceId = fixedSourceParts[0];
            sourcePath = fixedSourceParts[1];
            sourceWebDavPath = fixedSourcePath; // Update the path variable for subsequent use
            sameServer = true;
          }
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

      // Check if destination file already exists and handle accordingly
      String actualDestPath = destPath;
      if (client.exists(destPath)) {
        boolean sourceIsDir = isWebDavDirectory(client, sourcePath);
        boolean destIsDir = isWebDavDirectory(client, destPath);
        LOGGER.debug("Conflict detected - sourceIsDir: {}, destIsDir: {}", sourceIsDir, destIsDir);

        // Optimization: If both are directories, perform a "merge" instead of renaming the destination
        if (sourceIsDir && destIsDir) {
          LOGGER.info("Both source and destination are directories, merging content: '{}' -> '{}'", sourcePath, destPath);
          if (mergeWebDavDirectories(client, sourcePath, destPath)) {
            LOGGER.info("Successfully merged WebDAV directory from '{}' to '{}'", sourcePath, destPath);
            return "webdav://" + destId + destPath;
          }
          else {
            LOGGER.error("Failed to merge WebDAV directory from '{}' to '{}'", sourcePath, destPath);
            return null;
          }
        }

        LOGGER.warn("Destination file already exists: {}", destPath);
        // Generate unique filename by adding suffix (fallback for files or failed directory merges)
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
        // [容错验证] 检查是否实际上已经成功了（后端超时但已完成，或之前的操作已移动）
        try {
          Thread.sleep(1000); // 给服务器一点处理内部事务的时间

          // 场景 A: 源已消失 + 原始目标已存在（可能之前的操作已移动到目标位置）
          if (!client.exists(sourcePath) && client.exists(destPath)) {
            LOGGER.info("Move command for '{}' returned error, but source is gone and original dest '{}' exists. Assuming previous success.",
                sourcePath, destPath);
            return "webdav://" + destId + destPath;
          }

          // 场景 B: 源已消失 + 实际目标已存在（刚才的操作成功了）
          if (!client.exists(sourcePath) && client.exists(actualDestPath)) {
            LOGGER.info("Move command for '{}' returned error, but source is gone and dest exists. Assuming backend success.", sourcePath);
            return actualDestWebDavPath;
          }

          // 场景 C: 源和目标都存在（冲突，尝试自愈）
          if (client.exists(sourcePath) && client.exists(actualDestPath)) {
            LOGGER.warn("Collision detected for '{}' -> '{}' after error. Generating unique path to recover.", sourcePath, actualDestPath);
            String absoluteUniquePath = generateAbsoluteUniqueDestPath(client, actualDestPath);
            if (client.move(sourcePath, absoluteUniquePath)) {
              LOGGER.info("Successfully recovered from collision by moving to unique path: {}", absoluteUniquePath);
              return "webdav://" + destId + absoluteUniquePath;
            }
          }
        }
        catch (Exception ex) {
          LOGGER.debug("Conflict self-healing validation failed: {}", ex.getMessage());
        }

        // 在进入降级逻辑前静默观察，给服务器一些同步索引的时间
        try {
          Thread.sleep(1500);
        }
        catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }

        // Fallback: Copy and Delete (Standard workaround for buggy WebDAV servers returning 500/409 on MOVE)
        LOGGER.warn("Move failed (possible server error 500/409), attempting copy and delete fallback regarding '{}'", sourcePath);
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
   * Check if a path is a directory on WebDAV
   * 
   * @param client
   *          the WebDAV client
   * @param path
   *          the path to check
   * @return true if it is a directory
   */
  private static boolean isWebDavDirectory(WebDavClient client, String path) {
    try {
      // PROPFIND (list) on the path itself is the most reliable way.
      // Sardine's list() returns the directory itself as the first element.
      // We explicitly request to includeSelf to get the metadata of the path itself.
      List<WebDavFile> response = client.list(path, true);
      if (response != null && !response.isEmpty()) {
        WebDavFile self = response.get(0);

        // Use multiple comparison strategies to handle path format differences
        String p1 = WebDavDataSourceHelper.normalizeWebDavPath(path);
        String p2 = WebDavDataSourceHelper.normalizeWebDavPath(self.getPath());

        // Strategy 1: Direct path comparison
        if (p1.equals(p2)) {
          boolean isDir = self.isDirectory();
          LOGGER.trace("isWebDavDirectory: matched by full path '{}' - isDirectory={}", p1, isDir);
          return isDir;
        }

        // Strategy 2: Compare by path ending (handles relative vs absolute path differences)
        // Extract the ending part after the last common segment
        String name1 = p1.contains("/") ? p1.substring(p1.lastIndexOf("/") + 1) : p1;
        String name2 = p2.contains("/") ? p2.substring(p2.lastIndexOf("/") + 1) : p2;

        if (!name1.isEmpty() && name1.equals(name2)) {
          // Additional check: verify p1 ends with p2 or vice versa (for nested path scenarios)
          if (p1.endsWith(p2) || p2.endsWith(p1) || p1.endsWith("/" + name2) || p2.endsWith("/" + name1)) {
            boolean isDir = self.isDirectory();
            LOGGER.trace("isWebDavDirectory: matched by name '{}' - isDirectory={}", name1, isDir);
            return isDir;
          }
        }

        LOGGER.trace("isWebDavDirectory: path mismatch! input='{}' (normalized='{}'), entity='{}' (normalized='{}')", path, p1, self.getPath(), p2);
      }

      // Heuristic fallback: TMM directories in DB often don't have extensions,
      // but physically they might end with / on server.
      return path.endsWith("/");
    }
    catch (Exception e) {
      LOGGER.trace("Could not determine if '{}' is a directory: {}", path, e.getMessage());
      return false;
    }
  }

  /**
   * Recursively merge source directory into destination directory
   * 
   * @param client
   *          the WebDAV client
   * @param sourcePath
   *          source directory path
   * @param destPath
   *          destination directory path
   * @return true if successful
   */
  private static boolean mergeWebDavDirectories(WebDavClient client, String sourcePath, String destPath) {
    try {
      List<WebDavFile> children = client.list(sourcePath);
      for (WebDavFile child : children) {
        String childName = child.getName();
        String sourceChildPath = sourcePath + (sourcePath.endsWith("/") ? "" : "/") + childName;
        String destChildPath = destPath + (destPath.endsWith("/") ? "" : "/") + childName;

        if (child.isDirectory()) {
          // If destination directory doesn't exist, we can just move the whole subdirectory
          if (!client.exists(destChildPath)) {
            if (!client.move(sourceChildPath, destChildPath)) {
              return false;
            }
          }
          else {
            // Destination subdirectory exists, recurse
            if (!mergeWebDavDirectories(client, sourceChildPath, destChildPath)) {
              return false;
            }
          }
        }
        else {
          // It's a file. If it exists in destination, we might want to overwrite or skip.
          // For TV shows, usually these are NFOs or unique episode files. Overwriting is usually safe for NFOs.
          if (client.exists(destChildPath)) {
            LOGGER.debug("File already exists in destination, deleting to overwrite: {}", destChildPath);
            client.delete(destChildPath);
          }
          if (!client.move(sourceChildPath, destChildPath)) {
            return false;
          }
        }
      }
      // Finally delete the (now empty) source directory
      return client.delete(sourcePath);
    }
    catch (Exception e) {
      LOGGER.error("Failed to merge WebDAV directories '{}' -> '{}': {}", sourcePath, destPath, e.getMessage());
      return false;
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
    String sourcePath = null;
    String destPath = null;
    try {
      // Parse source path
      String[] sourceParts = WebDavDataSourceHelper.parseWebDavPath(sourceWebDavPath);
      if (sourceParts == null || sourceParts.length < 2) {
        LOGGER.error("Invalid source WebDAV path: {}", WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath));
        return false;
      }
      String sourceId = sourceParts[0];
      sourcePath = sourceParts[1];

      // Parse destination path
      String[] destParts = WebDavDataSourceHelper.parseWebDavPath(destWebDavPath);
      if (destParts == null || destParts.length < 2) {
        LOGGER.error("Invalid destination WebDAV path: {}", WebDavDataSourceHelper.decodeWebDavPath(destWebDavPath));
        return false;
      }
      String destId = destParts[0];
      destPath = destParts[1];

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

      // Fix for malformed source path (missing slash after sourceId)
      if (!sameServer && sourceId.startsWith(destId)) {
        String expectedPrefix = "webdav://" + destId;
        if (sourceWebDavPath.startsWith(expectedPrefix) && !sourceWebDavPath.startsWith(expectedPrefix + "/")) {
          String fixedSourcePath = expectedPrefix + "/" + sourceWebDavPath.substring(expectedPrefix.length());
          String[] fixedSourceParts = WebDavDataSourceHelper.parseWebDavPath(fixedSourcePath);

          if (fixedSourceParts != null && fixedSourceParts[0].equals(destId)) {
            LOGGER.warn("Detected malformed WebDAV source path (possible missing slash), auto-fixing: '{}' -> '{}'",
                WebDavDataSourceHelper.decodeWebDavPath(sourceWebDavPath), WebDavDataSourceHelper.decodeWebDavPath(fixedSourcePath));

            sourceId = fixedSourceParts[0];
            sourcePath = fixedSourceParts[1];
            sourceWebDavPath = fixedSourcePath;
            sameServer = true;
          }
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
      if (client.copy(sourcePath, destPath, false)) {
        LOGGER.info("Copied WebDAV file from '{}' to '{}'", sourcePath, destPath);
        return true;
      }
      else {
        // [冲突自愈] 同理，Copy 如果返回 500 也执行自愈验证（123 盘可能因为已存在或超时报 500）
        try {
          if (client.exists(destPath)) {
            LOGGER.info("Copy command for '{}' returned error, but destination already exists. Assuming backend success or conflict.", sourcePath);
            return true;
          }
        }
        catch (Exception ex) {
          // ignore
        }
        LOGGER.error("Failed to copy WebDAV file from '{}' to '{}' after retries and self-healing effort", sourcePath, destPath);
        return false;
      }
    }
    catch (Exception e) {
      LOGGER.error("Error copying WebDAV file from '{}' to '{}': {}", sourcePath, destPath, e.getMessage());
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
   * 生成一个带有毫秒时间戳的绝对唯一目标路径，用于从死循环的冲突中恢复。
   */
  private static String generateAbsoluteUniqueDestPath(WebDavClient client, String destPath) {
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

    // 使用时间戳确保即便在 AList 缓存未更新时也能撞正一个不存在的路径
    String uniquePath = basePath + "_" + System.currentTimeMillis() + extension;
    LOGGER.debug("Generated absolute unique recovery path: {}", uniquePath);
    return uniquePath;
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
    for (int i = 1; i <= 20; i++) {
      String newPath = basePath + "_" + i + extension;
      if (!client.exists(newPath)) {
        return newPath;
      }
    }

    // Fallback: use timestamp to ensure uniqueness even in high concurrency
    return basePath + "_" + System.currentTimeMillis() + extension;

  }

  /**
   * 递归删除 WebDAV 空目录 从指定路径开始，向下遍历并删除所有空的子目录
   *
   * @param webDavPath
   *          WebDAV 路径 (格式: webdav://source-id/path)
   * @return 删除的目录数量
   */
  public static int deleteEmptyDirectoriesRecursive(String webDavPath) {
    WebDavClient client = null;
    try {
      // 解析 WebDAV 路径
      String[] parts = WebDavDataSourceHelper.parseWebDavPath(webDavPath);
      if (parts == null || parts.length < 2) {
        LOGGER.debug("Invalid WebDAV path for empty directory cleanup: {}", webDavPath);
        return 0;
      }
      String sourceId = parts[0];
      String basePath = parts[1];

      // 获取 WebDAV 源
      WebDavSource source = WebDavDataSourceHelper.getWebDavSource(sourceId);
      if (source == null) {
        LOGGER.debug("WebDAV source not found for empty directory cleanup: {}", sourceId);
        return 0;
      }

      // 创建客户端
      client = WebDavDataSourceHelper.createClient(source);
      if (client == null) {
        LOGGER.debug("Failed to create WebDAV client for empty directory cleanup: {}", sourceId);
        return 0;
      }

      return deleteEmptyDirectoriesInternal(client, basePath);
    }
    catch (Exception e) {
      LOGGER.warn("Error during WebDAV empty directory cleanup for '{}': {}", webDavPath, e.getMessage());
      return 0;
    }
    finally {
      if (client != null) {
        client.disconnect();
      }
    }
  }

  /**
   * 递归删除 WebDAV 空目录，使用共享客户端（性能优化：避免创建新连接）
   *
   * @param client
   *          已连接的 WebDAV 客户端
   * @param basePath
   *          相对路径（相对于 WebDAV 根目录）
   * @return 删除的目录数量
   */
  public static int deleteEmptyDirectoriesRecursiveWithClient(WebDavClient client, String basePath) {
    if (client == null || basePath == null) {
      return 0;
    }
    try {
      return deleteEmptyDirectoriesInternal(client, basePath);
    }
    catch (Exception e) {
      LOGGER.warn("Error during WebDAV empty directory cleanup for '{}': {}", basePath, e.getMessage());
      return 0;
    }
  }

  /**
   * 内部递归删除空目录方法
   */
  private static int deleteEmptyDirectoriesInternal(WebDavClient client, String path) {
    int deletedCount = 0;
    try {
      // 列出目录内容
      List<WebDavFile> children = client.list(path);
      if (children == null) {
        return 0;
      }

      // 先递归处理子目录
      for (WebDavFile child : children) {
        if (child.isDirectory()) {
          String childPath = path + (path.endsWith("/") ? "" : "/") + child.getName();
          deletedCount += deleteEmptyDirectoriesInternal(client, childPath);
        }
      }

      // 重新检查当前目录是否为空（子目录可能已被删除）
      children = client.list(path);
      if (children != null && children.isEmpty()) {
        // 目录为空，删除它
        if (client.delete(path)) {
          LOGGER.debug("Deleted empty WebDAV directory: {}", path);
          deletedCount++;
        }
      }
    }
    catch (Exception e) {
      LOGGER.trace("Could not check/delete WebDAV directory '{}': {}", path, e.getMessage());
    }
    return deletedCount;
  }
}
