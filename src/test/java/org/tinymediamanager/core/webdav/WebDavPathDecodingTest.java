package org.tinymediamanager.core.webdav;

import org.junit.Test;
import org.tinymediamanager.core.entities.MediaFile;

import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * Test for WebDAV path decoding
 */
public class WebDavPathDecodingTest {

  @Test
  public void testDecodeWebDavPath_FullPath() {
    // Test decoding a full WebDAV path with URL-encoded Chinese characters
    String encodedPath = "webdav://22aff338-9e15-4c81-95f6-d6c99d0719eb/%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97%20%282002-2003%29/test/file.mkv";
    String decodedPath = WebDavDataSourceHelper.decodeWebDavPath(encodedPath);
    
    System.out.println("Encoded: " + encodedPath);
    System.out.println("Decoded: " + decodedPath);
    
    // Should contain readable Chinese characters
    assertTrue("Decoded path should contain Chinese characters", decodedPath.contains("无间道系列"));
    assertTrue("Decoded path should contain year range", decodedPath.contains("(2002-2003)"));
  }

  @Test
  public void testDecodeWebDavPath_PathComponent() {
    // Test decoding just a path component
    String encodedPath = "/%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97%20%282002-2003%29/test";
    String decodedPath = WebDavDataSourceHelper.decodeWebDavPath(encodedPath);
    
    System.out.println("Encoded: " + encodedPath);
    System.out.println("Decoded: " + decodedPath);
    
    // Should contain readable Chinese characters
    assertTrue("Decoded path should contain Chinese characters", decodedPath.contains("无间道系列"));
  }

  @Test
  public void testGetDisplayName_WithEncodedPath() {
    // This test requires a WebDavSource to be set up
    // When source is not found, it returns the original path
    String webDavPath = "webdav://test-id/%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97/file.mkv";
    String displayName = WebDavDataSourceHelper.getDisplayName(webDavPath);
    
    System.out.println("Display name: " + displayName);
    
    // Since we don't have a real WebDavSource, it will return the original path
    // But we can still test that the method doesn't crash
    assertNotNull("Display name should not be null", displayName);
  }

  @Test
  public void testMediaFile_GetPathDecoded() {
    // Create a MediaFile with WebDAV path
    MediaFile mf = new MediaFile();
    mf.setPath("webdav://test-id/%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97%20%282002-2003%29");
    mf.setFilename("file.mkv");
    
    String encodedPath = mf.getPath();
    String decodedPath = mf.getPathDecoded();
    
    System.out.println("Encoded path: " + encodedPath);
    System.out.println("Decoded path: " + decodedPath);
    
    // Encoded path should contain %E6...
    assertTrue("Encoded path should contain URL encoding", encodedPath.contains("%E6"));
    
    // Decoded path should contain readable Chinese
    assertTrue("Decoded path should contain Chinese characters", decodedPath.contains("无间道系列"));
  }

  @Test
  public void testMediaFile_GetFilenameDecoded() {
    // Create a MediaFile with URL-encoded filename
    MediaFile mf = new MediaFile();
    mf.setPath("webdav://test-id/path");
    mf.setFilename("%E6%97%A0%E9%97%B4%E9%81%93.mkv");
    
    String encodedFilename = mf.getFilename();
    String decodedFilename = mf.getFilenameDecoded();
    
    System.out.println("Encoded filename: " + encodedFilename);
    System.out.println("Decoded filename: " + decodedFilename);
    
    // Encoded filename should contain %E6...
    assertTrue("Encoded filename should contain URL encoding", encodedFilename.contains("%E6"));
    
    // Decoded filename should contain readable Chinese
    assertTrue("Decoded filename should contain Chinese characters", decodedFilename.contains("无间道"));
  }

  @Test
  public void testNonWebDavPath_NoDecoding() {
    // Test that non-WebDAV paths are not decoded
    String normalPath = "/home/user/movies/无间道";
    String result = WebDavDataSourceHelper.decodeWebDavPath(normalPath);
    
    // Should return the same path
    assertEquals("Non-WebDAV path should not be modified", normalPath, result);
  }
}
