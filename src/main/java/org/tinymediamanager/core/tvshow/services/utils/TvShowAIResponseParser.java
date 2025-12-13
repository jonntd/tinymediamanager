package org.tinymediamanager.core.tvshow.services.utils;

import org.tinymediamanager.core.movie.services.utils.AIResponseParser;
import org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.EpisodeMatchingResult;

import java.util.List;
import java.util.Map;

/**
 * 电视剧AI响应解析工具类 委托给 AIResponseParser 处理通用逻辑，本类仅提供电视剧特定的适配
 */
public final class TvShowAIResponseParser {

    private TvShowAIResponseParser() {
        // 工具类不允许实例化
    }

    /**
     * 从API响应中提取内容 委托给 AIResponseParser
     */
    public static String extractContentFromResponse(String responseBody) {
        return AIResponseParser.extractContentFromResponse(responseBody);
    }

    /**
     * 清理和验证电视剧标题 委托给 AIResponseParser
     */
    public static String cleanAndValidateTvShowTitle(String title) {
        return AIResponseParser.cleanAndValidateTvShowTitle(title);
    }

    /**
     * 解析批量识别结果 委托给 AIResponseParser
     */
    public static List<String> parseBatchContent(String content) {
        return AIResponseParser.parseBatchContent(content);
    }

    /**
     * 解析带索引的AI响应 委托给 AIResponseParser
     */
    public static Map<Integer, String> parseIndexedResponse(String response) {
        return AIResponseParser.parseIndexedResponse(response);
    }

    /**
     * 解析剧集信息 (季数 集数) 适配 EpisodeMatchingResult 类型
     * 
     * @param content
     *            AI返回的剧集信息字符串
     * @return EpisodeMatchingResult 对象
     */
    public static EpisodeMatchingResult parseEpisodeInfo(String content) {
        EpisodeMatchingResult result = new EpisodeMatchingResult();
        if (content == null || content.trim().isEmpty()) {
            return result;
        }

        // 使用 AIResponseParser 的解析方法
        int[] parsed = AIResponseParser.parseEpisodeInfo(content);
        if (parsed != null && parsed.length == 2) {
            result.season = parsed[0];
            result.episodes.add(parsed[1]);
        }

        return result;
    }
}
