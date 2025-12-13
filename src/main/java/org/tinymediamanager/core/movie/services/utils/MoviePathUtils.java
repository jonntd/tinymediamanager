package org.tinymediamanager.core.movie.services.utils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.movie.entities.Movie;

/**
 * 电影路径处理工具类 提供从电影对象提取路径、URL解码、目录层级提取等功能
 */
public final class MoviePathUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoviePathUtils.class);

    private MoviePathUtils() {
        // 工具类不允许实例化
    }

    /**
     * 从电影对象中提取文件路径
     * 
     * @param movie
     *            电影对象
     * @return 电影文件路径，如果无法提取返回null
     */
    public static String extractMoviePath(Movie movie) {
        if (movie == null) {
            LOGGER.warn("Movie object is null");
            return null;
        }

        // 优先使用主要视频文件路径（排除EMPTY_MEDIAFILE）
        MediaFile mainFile = movie.getMainFile();
        if (mainFile != null && mainFile != MediaFile.EMPTY_MEDIAFILE) {
            String mainFilePath = mainFile.getFileAsPath().toString();
            if (mainFilePath != null && !mainFilePath.trim().isEmpty()) {
                return mainFilePath;
            }
        }

        // 如果没有有效的主要文件，尝试使用第一个有效的媒体文件
        List<MediaFile> mediaFiles = movie.getMediaFiles();
        for (MediaFile mediaFile : mediaFiles) {
            if (mediaFile != MediaFile.EMPTY_MEDIAFILE) {
                String mediaFilePath = mediaFile.getFileAsPath().toString();
                if (mediaFilePath != null && !mediaFilePath.trim().isEmpty()) {
                    return mediaFilePath;
                }
            }
        }

        return null;
    }

    /**
     * 从电影对象提取路径并转换为倒数三层目录格式
     * 
     * @param movie
     *            电影对象
     * @return 倒数三层目录路径，如果无法提取返回默认标识符
     */
    public static String extractMoviePathForAI(Movie movie) {
        if (movie == null) {
            LOGGER.warn("Movie object is null");
            return "unknown_movie";
        }

        // 尝试从主文件或媒体文件提取路径
        String fullPath = extractMoviePath(movie);
        if (fullPath != null) {
            return extractLastThreeDirectoryNames(fullPath);
        }

        // 如果所有文件路径都无效，使用电影标题作为备用方案
        String movieTitle = movie.getTitle();
        if (movieTitle != null && !movieTitle.trim().isEmpty()) {
            LOGGER.debug("Using movie title as fallback path: {}", movieTitle);
            return movieTitle;
        }

        // 最终回退方案
        LOGGER.warn("No valid path found for movie, using default identifier");
        return "movie_" + movie.getDbId();
    }

    /**
     * 提取路径倒数三层（保持路径结构） 例如：/a/b/c/d/e.mkv -> /c/d/e.mkv
     * 
     * @param filePath
     *            完整文件路径
     * @return 倒数三层目录路径
     */
    public static String extractLastThreeDirectoryNames(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return filePath;
        }

        try {
            // URL解码，处理WebDAV编码的路径（如 %E6%88%90%E9%BE%99 -> 成龙）
            String decodedPath = urlDecode(filePath);

            Path path = Paths.get(decodedPath);

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
            // 尝试解码后返回
            return urlDecode(filePath);
        }
    }

    /**
     * URL解码路径字符串
     * 
     * @param path
     *            可能包含URL编码的路径
     * @return 解码后的路径
     */
    public static String urlDecode(String path) {
        if (path == null) {
            return null;
        }

        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        }
        catch (Exception e) {
            LOGGER.debug("Failed to URL decode path '{}': {}", path, e.getMessage());
            return path; // 回退到原始路径
        }
    }

    /**
     * 生成电影的缓存键
     * 
     * @param movie
     *            电影对象
     * @return 缓存键字符串
     */
    public static String generateCacheKey(Movie movie) {
        if (movie == null) {
            return null;
        }

        // 使用电影路径作为缓存键，确保相同路径的电影共享缓存
        MediaFile mainFile = movie.getMainFile();
        if (mainFile != null && mainFile != MediaFile.EMPTY_MEDIAFILE) {
            String path = mainFile.getFileAsPath().toString();
            if (path != null && !path.trim().isEmpty()) {
                return path;
            }
        }

        // 回退到使用数据库ID
        return "movie_" + movie.getDbId();
    }
}
