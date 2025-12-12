package org.tinymediamanager.core.webdav;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test for WebDavPath class
 */
public class WebDavPathTest {

  private static final String TEST_SOURCE_ID = "22aff338-9e15-4c81-95f6-d6c99d0719eb";
  
  @Test
  public void testConstructorWithFullPath() {
    String fullPath = "webdav://" + TEST_SOURCE_ID + "/无间道系列 (2002-2003)/test";
    WebDavPath path = new WebDavPath(fullPath);
    
    assertEquals(TEST_SOURCE_ID, path.getSourceId());
    assertEquals("/无间道系列 (2002-2003)/test", path.getRemotePath());
  }
  
  @Test
  public void testConstructorWithEncodedPath() {
    String fullPath = "webdav://" + TEST_SOURCE_ID + "/%E6%97%A0%E9%97%B4%E9%81%93%E7%B3%BB%E5%88%97%20%282002-2003%29/test";
    WebDavPath path = new WebDavPath(fullPath);
    
    assertEquals(TEST_SOURCE_ID, path.getSourceId());
    // Should be decoded internally
    assertEquals("/无间道系列 (2002-2003)/test", path.getRemotePath());
  }
  
  @Test
  public void testConstructorWithSourceIdAndPath() {
    WebDavPath path = new WebDavPath(TEST_SOURCE_ID, "/无间道系列/test");
    
    assertEquals(TEST_SOURCE_ID, path.getSourceId());
    assertEquals("/无间道系列/test", path.getRemotePath());
  }
  
  @Test
  public void testGetRelativePath() {
    WebDavPath basePath = new WebDavPath(TEST_SOURCE_ID, "/无间道系列 (2002-2003)");
    WebDavPath fullPath = new WebDavPath(TEST_SOURCE_ID, "/无间道系列 (2002-2003)/test/subdir");
    
    String relative = fullPath.getRelativePath(basePath);
    
    assertEquals("test/subdir", relative);
  }
  
  @Test
  public void testGetRelativePathWithTrailingSlash() {
    WebDavPath basePath = new WebDavPath(TEST_SOURCE_ID, "/无间道系列 (2002-2003)/");
    WebDavPath fullPath = new WebDavPath(TEST_SOURCE_ID, "/无间道系列 (2002-2003)/test/");
    
    String relative = fullPath.getRelativePath(basePath);
    
    assertEquals("test", relative);
  }
  
  @Test
  public void testGetRelativePathDifferentSources() {
    WebDavPath basePath = new WebDavPath("source1", "/path");
    WebDavPath fullPath = new WebDavPath("source2", "/path/subdir");
    
    String relative = fullPath.getRelativePath(basePath);
    
    assertNull("Should return null for different sources", relative);
  }
  
  @Test
  public void testResolve() {
    WebDavPath basePath = new WebDavPath(TEST_SOURCE_ID, "/无间道系列");
    WebDavPath resolved = basePath.resolve("test/file.mkv");
    
    assertEquals(TEST_SOURCE_ID, resolved.getSourceId());
    assertEquals("/无间道系列/test/file.mkv", resolved.getRemotePath());
  }
  
  @Test
  public void testResolveWithLeadingSlash() {
    WebDavPath basePath = new WebDavPath(TEST_SOURCE_ID, "/无间道系列");
    WebDavPath resolved = basePath.resolve("/test/file.mkv");
    
    assertEquals("/无间道系列/test/file.mkv", resolved.getRemotePath());
  }
  
  @Test
  public void testGetParent() {
    WebDavPath path = new WebDavPath(TEST_SOURCE_ID, "/无间道系列/test/file.mkv");
    WebDavPath parent = path.getParent();
    
    assertNotNull(parent);
    assertEquals(TEST_SOURCE_ID, parent.getSourceId());
    assertEquals("/无间道系列/test", parent.getRemotePath());
  }
  
  @Test
  public void testGetParentOfRoot() {
    WebDavPath path = new WebDavPath(TEST_SOURCE_ID, "/");
    WebDavPath parent = path.getParent();
    
    assertNull("Root should have no parent", parent);
  }
  
  @Test
  public void testGetFileName() {
    WebDavPath path = new WebDavPath(TEST_SOURCE_ID, "/无间道系列/test/file.mkv");
    
    assertEquals("file.mkv", path.getFileName());
  }
  
  @Test
  public void testGetFileNameOfDirectory() {
    WebDavPath path = new WebDavPath(TEST_SOURCE_ID, "/无间道系列/test/");
    
    assertEquals("test", path.getFileName());
  }
  
  @Test
  public void testToString() {
    WebDavPath path = new WebDavPath(TEST_SOURCE_ID, "/无间道系列 (2002-2003)/test");
    String result = path.toString();
    
    assertTrue("Should start with webdav://", result.startsWith("webdav://"));
    assertTrue("Should contain source ID", result.contains(TEST_SOURCE_ID));
    // Path should be URL-encoded
    assertTrue("Should contain encoded path", result.contains("%E6%97%A0%E9%97%B4%E9%81%93"));
  }
  
  @Test
  public void testToStringDecoded() {
    WebDavPath path = new WebDavPath(TEST_SOURCE_ID, "/无间道系列 (2002-2003)/test");
    String result = path.toStringDecoded();
    
    assertEquals("webdav://" + TEST_SOURCE_ID + "/无间道系列 (2002-2003)/test", result);
  }
  
  @Test
  public void testStartsWith() {
    WebDavPath basePath = new WebDavPath(TEST_SOURCE_ID, "/无间道系列");
    WebDavPath fullPath = new WebDavPath(TEST_SOURCE_ID, "/无间道系列/test");
    
    assertTrue(fullPath.startsWith(basePath));
    assertFalse(basePath.startsWith(fullPath));
  }
  
  @Test
  public void testEquals() {
    WebDavPath path1 = new WebDavPath(TEST_SOURCE_ID, "/无间道系列/test");
    WebDavPath path2 = new WebDavPath(TEST_SOURCE_ID, "/无间道系列/test");
    WebDavPath path3 = new WebDavPath(TEST_SOURCE_ID, "/无间道系列/other");
    
    assertEquals(path1, path2);
    assertNotEquals(path1, path3);
  }
  
  @Test
  public void testEqualsWithEncodedPath() {
    // Even if constructed with encoded path, should equal decoded version
    WebDavPath path1 = new WebDavPath("webdav://" + TEST_SOURCE_ID + "/%E6%97%A0%E9%97%B4%E9%81%93/test");
    WebDavPath path2 = new WebDavPath(TEST_SOURCE_ID, "/无间道/test");
    
    assertEquals(path1, path2);
  }
  
  @Test
  public void testRoundTrip() {
    // Test that we can construct, convert to string, and construct again
    String original = "webdav://" + TEST_SOURCE_ID + "/无间道系列 (2002-2003)/test/file.mkv";
    WebDavPath path1 = new WebDavPath(original);
    String encoded = path1.toString();
    WebDavPath path2 = new WebDavPath(encoded);
    
    assertEquals(path1, path2);
    assertEquals(path1.getRemotePath(), path2.getRemotePath());
  }
}
