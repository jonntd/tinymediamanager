package org.tinymediamanager.core.tvshow.services.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.tvshow.entities.TvShow;

/**
 * 电视剧路径处理工具类
 */
public class TvShowPathUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TvShowPathUtils.class);

    /**
     * 提取电视剧的主要路径（优先使用目录路径）
     */
    public static String extractTvShowPath(TvShow tvShow) {
        if (tvShow == null) {
            return null;
        }

        // 优先使用电视剧目录路径
        if (tvShow.getPathNIO() != null) {
            return tvShow.getPathNIO().toString();
        }

        // 如果没有目录路径，尝试从媒体文件中获取
        List<MediaFile> mediaFiles = tvShow.getMediaFiles();
        if (!mediaFiles.isEmpty()) {
            MediaFile firstFile = mediaFiles.get(0);
            if (firstFile.getFileAsPath() != null) {
                return firstFile.getFileAsPath().toString();
            }
        }

        // 最终回退方案
        LOGGER.warn("No valid path found for TV show: {}", tvShow.getTitle());
        return "tvshow_" + tvShow.getDbId();
    }

    /**
     * 为AI识别提取路径（处理URL编码）
     */
    public static String extractTvShowPathForAI(TvShow tvShow) {
        String path = extractTvShowPath(tvShow);
        if (path == null) {
            return null;
        }

        // URL解码路径，处理特殊字符
        String decodedPath = urlDecode(path);

        // 提取倒数三层目录名称
        return extractLastThreeDirectoryNames(decodedPath);
    }

    /**
     * 提取倒数三层目录名称（保持路径结构）
     */
    public static String extractLastThreeDirectoryNames(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return filePath;
        }

        try {
            // 简单处理：如果包含 tvshow_ 前缀（ID回退情况），直接返回
            if (filePath.startsWith("tvshow_")) {
                return filePath;
            }

            Path path = Paths.get(filePath);

            // 获取路径的所有部分
            int nameCount = path.getNameCount();
            if (nameCount <= 3) {
                // 如果路径层级不超过3层，返回相对路径
                return "/" + path.toString();
            }

            // 取倒数三层：倒数第三层目录/倒数第二层目录/文件名
            Path lastThreeLayers = path.subpath(nameCount - 3, nameCount);
            return "/" + lastThreeLayers.toString();

        }
        catch (Exception e) {
            LOGGER.warn("Failed to extract last three layers from path: {}", e.getMessage());
            return filePath; // 回退到原始路径
        }
    }

    /**
     * URL解码字符串
     */
    public static String urlDecode(String input) {
        if (input == null) {
            return null;
        }
        try {
            return URLDecoder.decode(input, StandardCharsets.UTF_8.name());
        }
        catch (Exception e) {
            LOGGER.warn("Failed to URL decode path: {}", input);
            return input;
        }
    }

    /**
     * 生成电视剧缓存键
     */
    public static String generateCacheKey(TvShow tvShow) {
        String path = extractTvShowPath(tvShow);
        if (path != null && !path.startsWith("tvshow_")) {
            return path;
        }
        return "tvshow_" + tvShow.getDbId();
    }

    /**
     * 提取倒数三层目录名称（针对普通文件路径字符串，无需TvShow对象）
     */
    public static String extractPathContext(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            return fullPath;
        }

        // 先进行URL解码
        String decodedPath = urlDecode(fullPath);

        return extractLastThreeDirectoryNames(decodedPath);
    }
}
