package org.tinymediamanager.core.utils;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import static org.junit.Assert.*;

import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.MediaFileHelper;
import org.tinymediamanager.core.MediaFileType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.io.File;

public class FileSizeUpdateTest {
    
    private Path tempDir;
    
    @Before
    public void setUp() throws IOException {
        // 创建临时目录
        tempDir = Files.createTempDirectory("tmm-test");
    }
    
    @After
    public void tearDown() throws IOException {
        // 清理临时目录
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
    
    @Test
    public void testNormalFileSizeUpdate() throws IOException {
        // 创建测试文件
        Path testFile = tempDir.resolve("test.mp4");
        Files.write(testFile, "test content".getBytes());
        
        MediaFile mediaFile = new MediaFile(testFile, MediaFileType.VIDEO);
        
        long originalSize = mediaFile.getFilesize();
        
        // 修改文件大小
        Files.write(testFile, "modified test content with more data".getBytes());
        
        // 调用gatherFileInformation更新文件大小
        boolean changed = MediaFileHelper.gatherFileInformation(mediaFile);
        
        assertTrue("文件大小应该发生变化", changed);
        assertEquals("文件大小应该更新为新大小", Files.size(testFile), mediaFile.getFilesize());
    }
    
    @Test
    public void testZeroSizeFileHandling() throws IOException {
        // 创建零字节文件
        Path zeroFile = tempDir.resolve("zero.mp4");
        Files.createFile(zeroFile);
        
        MediaFile mediaFile = new MediaFile(zeroFile, MediaFileType.VIDEO);
        mediaFile.setFilesize(1024); // 设置一个非零的旧值
        
        // 更新文件大小
        boolean changed = MediaFileHelper.gatherFileInformation(mediaFile);
        
        assertTrue("应该检测到文件大小变化", changed);
        assertEquals("零字节文件的大小应该为0", 0, mediaFile.getFilesize());
    }
    
    @Test
    public void testDirectorySizeCalculation() throws IOException {
        // 创建测试目录
        Path dirPath = tempDir.resolve("testdir");
        Files.createDirectories(dirPath);
        
        // 创建目录中的文件
        Files.write(dirPath.resolve("file1.mp4"), "content1".getBytes());
        Files.write(dirPath.resolve("file2.mp4"), "content2 content".getBytes());
        
        MediaFile mediaFile = new MediaFile(dirPath, MediaFileType.VIDEO);
        
        // 更新目录大小
        boolean changed = MediaFileHelper.gatherFileInformation(mediaFile);
        
        assertTrue("目录大小应该被计算", changed);
        assertTrue("目录大小应该大于0", mediaFile.getFilesize() > 0);
    }
    
    @Test
  public void testMissingFileHandling() throws IOException {
    // 创建不存在的文件路径
    Path missingPath = tempDir.resolve("missing.mp4");
    
    MediaFile mediaFile = new MediaFile(missingPath, MediaFileType.VIDEO);
    mediaFile.setFilesize(1024); // 设置一个旧值
    
    // 更新文件大小（文件不存在）
    boolean changed = MediaFileHelper.gatherFileInformation(mediaFile);
    
    // 文件不存在时，应该保持原来的值
    assertFalse("文件不存在时不应该改变大小", changed);
    assertEquals("文件大小应该保持原值", 1024, mediaFile.getFilesize());
  }
  
  @Test
  public void testNoUnnecessaryUpdates() throws IOException {
    // 测试当文件大小没有变化时不应该触发更新
    Path testFile = tempDir.resolve("unchanged.mp4");
    String content = "test content that won't change";
    Files.write(testFile, content.getBytes());
    
    MediaFile mediaFile = new MediaFile(testFile, MediaFileType.VIDEO);
    mediaFile.setFilesize(content.length()); // 设置正确的初始大小
    
    // 文件大小没有变化，不应该触发更新
    boolean changed = MediaFileHelper.gatherFileInformation(mediaFile);
    
    assertFalse("文件大小没有变化时不应该触发更新", changed);
    assertEquals("文件大小应该保持不变", content.length(), mediaFile.getFilesize());
  }
    
    @Test
    public void testNegativeSizeHandling() throws IOException {
        // 创建测试文件
        Path testFile = tempDir.resolve("negative.mp4");
        Files.write(testFile, "test content".getBytes());
        
        MediaFile mediaFile = new MediaFile(testFile, MediaFileType.VIDEO);
        mediaFile.setFilesize(-1); // 设置一个负值
        
        // 更新文件大小
        boolean changed = MediaFileHelper.gatherFileInformation(mediaFile);
        
        assertTrue("应该检测到文件大小变化", changed);
        assertEquals("负值应该被重置为0", 0, mediaFile.getFilesize());
    }
}