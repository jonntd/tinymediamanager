package org.tinymediamanager.core.webdav;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test for WebDavDataSourceHelper path parsing
 */
public class WebDavPathParsingTest {

  @Test
  public void testParseWebDavPath_WithUrlEncodedPath() {
    // Test case from the bug report
    // Source path with URL-encoded Chinese characters without proper separator
    String sourcePath = "webdav:/22aff338-9e15-4c81-95f6-d6c99d0719eb%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97%20%282002-2003%292/test/无间道 (2002) [tmdb-10775] 52.02 G.mkv";
    
    String[] result = WebDavDataSourceHelper.parseWebDavPath(sourcePath);
    
    assertNotNull("Result should not be null", result);
    assertEquals("Should return array of length 2", 2, result.length);
    
    // The sourceId should be the UUID only
    assertEquals("Source ID should be the UUID", 
                 "22aff338-9e15-4c81-95f6-d6c99d0719eb", 
                 result[0]);
    
    // The remote path should start with the encoded Chinese characters
    assertTrue("Remote path should start with /", result[1].startsWith("/"));
    System.out.println("Source ID: " + result[0]);
    System.out.println("Remote path: " + result[1]);
  }

  @Test
  public void testParseWebDavPath_WithProperFormat() {
    // Test with properly formatted path
    String destPath = "webdav:/22aff338-9e15-4c81-95f6-d6c99d0719eb/%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97%20%282002-2003%292/test/无间道 (2002) [tmdb-10775] 52.02 G.mkv";
    
    String[] result = WebDavDataSourceHelper.parseWebDavPath(destPath);
    
    assertNotNull("Result should not be null", result);
    assertEquals("Should return array of length 2", 2, result.length);
    
    // The sourceId should be the UUID only
    assertEquals("Source ID should be the UUID", 
                 "22aff338-9e15-4c81-95f6-d6c99d0719eb", 
                 result[0]);
    
    System.out.println("Source ID: " + result[0]);
    System.out.println("Remote path: " + result[1]);
  }

  @Test
  public void testParseWebDavPath_BothPathsShouldHaveSameSourceId() {
    // The bug: these two paths should have the same sourceId
    String sourcePath = "webdav:/22aff338-9e15-4c81-95f6-d6c99d0719eb%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97%20%282002-2003%292/test/file.mkv";
    String destPath = "webdav:/22aff338-9e15-4c81-95f6-d6c99d0719eb/%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97%20%282002-2003%292/test/file.mkv";
    
    String[] sourceResult = WebDavDataSourceHelper.parseWebDavPath(sourcePath);
    String[] destResult = WebDavDataSourceHelper.parseWebDavPath(destPath);
    
    assertNotNull("Source result should not be null", sourceResult);
    assertNotNull("Dest result should not be null", destResult);
    
    // This is the critical assertion - both should have the same sourceId
    assertEquals("Both paths should have the same source ID", 
                 sourceResult[0], 
                 destResult[0]);
    
    System.out.println("Source ID from source path: " + sourceResult[0]);
    System.out.println("Source ID from dest path: " + destResult[0]);
  }

  @Test
  public void testParseWebDavPath_WithDoubleSlash() {
    String path = "webdav://22aff338-9e15-4c81-95f6-d6c99d0719eb/path/to/file.mkv";
    
    String[] result = WebDavDataSourceHelper.parseWebDavPath(path);
    
    assertNotNull("Result should not be null", result);
    assertEquals("Source ID should be the UUID", 
                 "22aff338-9e15-4c81-95f6-d6c99d0719eb", 
                 result[0]);
    assertEquals("Remote path should be /path/to/file.mkv", 
                 "/path/to/file.mkv", 
                 result[1]);
  }

  @Test
  public void testParseWebDavPath_OnlySourceId() {
    String path = "webdav://22aff338-9e15-4c81-95f6-d6c99d0719eb";
    
    String[] result = WebDavDataSourceHelper.parseWebDavPath(path);
    
    assertNotNull("Result should not be null", result);
    assertEquals("Source ID should be the UUID", 
                 "22aff338-9e15-4c81-95f6-d6c99d0719eb", 
                 result[0]);
    assertEquals("Remote path should be /", 
                 "/", 
                 result[1]);
  }
}
