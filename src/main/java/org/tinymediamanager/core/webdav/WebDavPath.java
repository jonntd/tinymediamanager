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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * WebDavPath - A class to handle WebDAV path operations consistently
 * 
 * This class encapsulates WebDAV path handling, ensuring consistent URL encoding/decoding
 * and providing path manipulation methods similar to java.nio.file.Path
 * 
 * @author Manuel Laggner
 */
public class WebDavPath {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavPath.class);
  
  private final String sourceId;
  private final String remotePath; // Always stored in decoded form internally
  
  /**
   * Create a WebDavPath from a full WebDAV URL
   * 
   * @param fullPath the full WebDAV path (e.g., "webdav://uuid/path/to/file")
   */
  public WebDavPath(String fullPath) {
    if (fullPath == null || fullPath.isEmpty()) {
      throw new IllegalArgumentException("WebDAV path cannot be null or empty");
    }
    
    String[] parsed = WebDavDataSourceHelper.parseWebDavPath(fullPath);
    if (parsed == null || parsed.length != 2) {
      throw new IllegalArgumentException("Invalid WebDAV path: " + fullPath);
    }
    
    this.sourceId = parsed[0];
    this.remotePath = decodeUrlComponent(parsed[1]);
  }
  
  /**
   * Create a WebDavPath from sourceId and remotePath
   * 
   * @param sourceId the WebDAV source ID (UUID)
   * @param remotePath the remote path (will be decoded if needed)
   */
  public WebDavPath(String sourceId, String remotePath) {
    if (sourceId == null || sourceId.isEmpty()) {
      throw new IllegalArgumentException("Source ID cannot be null or empty");
    }
    
    this.sourceId = sourceId;
    String decodedPath = remotePath != null ? decodeUrlComponent(remotePath) : "/";
    if (!decodedPath.startsWith("/")) {
      decodedPath = "/" + decodedPath;
    }
    this.remotePath = decodedPath;
  }
  
  /**
   * Get the source ID (UUID)
   * 
   * @return the source ID
   */
  public String getSourceId() {
    return sourceId;
  }
  
  /**
   * Get the remote path (decoded)
   * 
   * @return the decoded remote path
   */
  public String getRemotePath() {
    return remotePath;
  }
  
  /**
   * Get the remote path (URL encoded)
   * 
   * @return the URL-encoded remote path
   */
  public String getRemotePathEncoded() {
    String encoded = encodeUrlPath(remotePath);
    if (!encoded.startsWith("/")) {
      return "/" + encoded;
    }
    return encoded;
  }
  
  /**
   * Calculate the relative path from a base path to this path
   * 
   * @param basePath the base path
   * @return the relative path, or null if paths are not related
   */
  public String getRelativePath(WebDavPath basePath) {
    if (basePath == null) {
      return remotePath;
    }
    
    // Must be from the same source
    if (!this.sourceId.equals(basePath.sourceId)) {
      LOGGER.warn("Cannot calculate relative path between different WebDAV sources: {} vs {}", 
                  this.sourceId, basePath.sourceId);
      return null;
    }
    
    String baseRemote = basePath.remotePath;
    String thisRemote = this.remotePath;
    
    // Normalize paths - ensure they end with / for directory comparison
    if (!baseRemote.endsWith("/")) {
      baseRemote = baseRemote + "/";
    }
    if (!thisRemote.endsWith("/")) {
      thisRemote = thisRemote + "/";
    }
    
    // Check if this path starts with base path
    if (thisRemote.startsWith(baseRemote)) {
      String relative = thisRemote.substring(baseRemote.length());
      // Remove trailing slash if present
      if (relative.endsWith("/")) {
        relative = relative.substring(0, relative.length() - 1);
      }
      return relative;
    }
    
    LOGGER.debug("Path '{}' does not start with base path '{}'", thisRemote, baseRemote);
    return null;
  }
  
  /**
   * Resolve a relative path against this path
   * 
   * @param relativePath the relative path to resolve
   * @return a new WebDavPath representing the resolved path
   */
  public WebDavPath resolve(String relativePath) {
    if (relativePath == null || relativePath.isEmpty()) {
      return this;
    }
    
    String newRemotePath = this.remotePath;
    
    // Ensure base path ends with /
    if (!newRemotePath.endsWith("/")) {
      newRemotePath = newRemotePath + "/";
    }
    
    // Remove leading / from relative path if present
    String cleanRelative = relativePath;
    if (cleanRelative.startsWith("/")) {
      cleanRelative = cleanRelative.substring(1);
    }
    
    newRemotePath = newRemotePath + cleanRelative;
    
    return new WebDavPath(this.sourceId, newRemotePath);
  }
  
  /**
   * Get the parent path
   * 
   * @return the parent WebDavPath, or null if this is the root
   */
  public WebDavPath getParent() {
    if (remotePath == null || remotePath.equals("/") || remotePath.isEmpty()) {
      return null;
    }
    
    // Remove trailing slash if present
    String path = remotePath;
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    
    // Find last slash
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash <= 0) {
      // Parent is root
      return new WebDavPath(sourceId, "/");
    }
    
    String parentPath = path.substring(0, lastSlash);
    return new WebDavPath(sourceId, parentPath);
  }
  
  /**
   * Get the file name (last component of the path)
   * 
   * @return the file name, or empty string if this is root
   */
  public String getFileName() {
    if (remotePath == null || remotePath.equals("/") || remotePath.isEmpty()) {
      return "";
    }
    
    // Remove trailing slash if present
    String path = remotePath;
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash < 0) {
      return path;
    }
    
    return path.substring(lastSlash + 1);
  }
  
  /**
   * Convert to a java.nio.file.Path
   * Note: This creates a Path object but it's still a WebDAV path string
   * 
   * @return a Path object representing this WebDAV path
   */
  public Path toPath() {
    return Paths.get(toString());
  }
  
  /**
   * Convert to a full WebDAV URL string (with URL encoding)
   * 
   * @return the full WebDAV URL
   */
  @Override
  public String toString() {
    return "webdav://" + sourceId + getRemotePathEncoded();
  }
  
  /**
   * Convert to a full WebDAV URL string (without URL encoding, for display)
   * 
   * @return the full WebDAV URL with decoded path
   */
  public String toStringDecoded() {
    return "webdav://" + sourceId + remotePath;
  }
  
  /**
   * Check if this path starts with the given path
   * 
   * @param other the other path
   * @return true if this path starts with the other path
   */
  public boolean startsWith(WebDavPath other) {
    if (other == null) {
      return false;
    }
    
    if (!this.sourceId.equals(other.sourceId)) {
      return false;
    }
    
    String thisPath = this.remotePath;
    String otherPath = other.remotePath;
    
    // Normalize for comparison
    if (!thisPath.endsWith("/")) {
      thisPath = thisPath + "/";
    }
    if (!otherPath.endsWith("/")) {
      otherPath = otherPath + "/";
    }
    
    return thisPath.startsWith(otherPath);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    
    WebDavPath other = (WebDavPath) obj;
    return sourceId.equals(other.sourceId) && remotePath.equals(other.remotePath);
  }
  
  @Override
  public int hashCode() {
    return 31 * sourceId.hashCode() + remotePath.hashCode();
  }
  
  /**
   * Decode a URL component
   * 
   * @param component the component to decode
   * @return the decoded component
   */
  private static String decodeUrlComponent(String component) {
    if (component == null || component.isEmpty()) {
      return component;
    }
    
    try {
      return URLDecoder.decode(component, "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      LOGGER.debug("Failed to decode URL component '{}': {}", component, e.getMessage());
      return component;
    }
  }
  
  /**
   * Encode a URL path (only the path part, not the entire URL)
   * 
   * @param path the path to encode
   * @return the encoded path
   */
  private static String encodeUrlPath(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    
    // Split path into segments and encode each segment separately
    // This preserves the / separators
    String[] segments = path.split("/", -1);
    StringBuilder encoded = new StringBuilder();
    
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        encoded.append("/");
      }
      
      if (!segments[i].isEmpty()) {
        try {
          // Encode the segment, but replace %2F back to / if it was encoded
          String encodedSegment = URLEncoder.encode(segments[i], "UTF-8");
          // URLEncoder encodes space as +, but we want %20 for paths
          encodedSegment = encodedSegment.replace("+", "%20");
          encoded.append(encodedSegment);
        }
        catch (UnsupportedEncodingException e) {
          LOGGER.debug("Failed to encode URL segment '{}': {}", segments[i], e.getMessage());
          encoded.append(segments[i]);
        }
      }
    }
    
    return encoded.toString();
  }
}
