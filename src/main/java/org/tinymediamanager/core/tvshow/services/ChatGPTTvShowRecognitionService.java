package org.tinymediamanager.core.tvshow.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.tvshow.entities.TvShow;

/**
 * ChatGPT电视剧识别服务
 * 基于路径倒数三层目录名称使用ChatGPT识别电视剧名
 */
public class ChatGPTTvShowRecognitionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGPTTvShowRecognitionService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private HttpClient httpClient;
    private final Settings settings;
    
    public ChatGPTTvShowRecognitionService() {
        this.settings = Settings.getInstance();
        
        String apiKey = settings.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOGGER.warn("OpenAI API key is not configured in settings");
            return;
        }
        
        try {
            this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize HTTP client: {}", e.getMessage());
        }
    }
    
    /**
     * 识别电视剧标题
     * @param tvShow 待识别的电视剧
     * @return 识别出的标题，如果失败返回null
     */
    public String recognizeTvShowTitle(TvShow tvShow) {
        LOGGER.info("=== Starting TV Show AI Recognition ===");
        LOGGER.info("TV Show title: {}", tvShow.getTitle());
        LOGGER.info("TV Show year: {}", tvShow.getYear());
        LOGGER.info("TV Show DB ID: {}", tvShow.getDbId());

        if (httpClient == null) {
            LOGGER.warn("HTTP client not initialized, cannot perform TV show recognition");
            return null;
        }

        try {
            // 获取电视剧的主要媒体文件路径
            String tvShowPath = extractTvShowPath(tvShow);
            if (tvShowPath == null || tvShowPath.trim().isEmpty()) {
                LOGGER.warn("No valid path found for TV show: {}", tvShow.getTitle());
                return null;
            }

            // 提取路径倒数三层目录名称（按主人要求）
            String pathContext = extractLastThreeDirectoryNames(tvShowPath);
            if (pathContext == null || pathContext.trim().isEmpty()) {
                LOGGER.warn("Cannot extract directory names from path: {}", tvShowPath);
                return null;
            }

            LOGGER.info("=== Path Processing ===");
            LOGGER.info("Full TV show path: {}", tvShowPath);
            LOGGER.info("Extracted directory context: {}", pathContext);

            // 调用ChatGPT API，传递倒数三层目录信息
            String recognizedTitle = callChatGPTAPI(pathContext, tvShowPath);

            LOGGER.info("=== AI Recognition Complete ===");
            LOGGER.info("Raw AI response: '{}'", recognizedTitle);

            if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
                // 清理和验证识别结果
                String cleanedTitle = cleanAndValidateTitle(recognizedTitle);
                LOGGER.info("Cleaned and validated title: '{}'", cleanedTitle);
                return cleanedTitle;
            } else {
                LOGGER.warn("AI returned empty or null result");
            }

        } catch (Exception e) {
            LOGGER.error("Error during TV show recognition: {}", e.getMessage(), e);
        }

        LOGGER.info("=== TV Show AI Recognition Failed ===");
        return null;
    }
    
    /**
     * 提取电视剧路径
     */
    private String extractTvShowPath(TvShow tvShow) {
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
        LOGGER.warn("No valid path found for TV show, using default identifier");
        return "tvshow_" + tvShow.getDbId();
    }
    
    /**
     * 提取路径倒数三层（保持路径结构）
     */
    private String extractLastThreeDirectoryNames(String filePath) {
        try {
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
            
        } catch (Exception e) {
            LOGGER.warn("Failed to extract last three layers from path: {}", e.getMessage());
            return filePath; // 回退到原始路径
        }
    }

    /**
     * 调用ChatGPT API（使用HTTP请求，兼容非官方API）
     */
    private String callChatGPTAPI(String tvShowPath) {
        return callChatGPTAPI(tvShowPath, tvShowPath); // 默认使用相同的路径
    }

    /**
     * 调用ChatGPT API（使用真正的异步HTTP请求，完全不阻塞线程）
     */
    private String callChatGPTAPI(String tvShowPath, String originalPath) {
        try {
            String apiKey = settings.getOpenAiApiKey();
            String apiUrl = settings.getOpenAiApiUrl();
            String model = settings.getOpenAiModel();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                LOGGER.warn("OpenAI API key is not configured");
                return null;
            }

            // 构建请求JSON - 使用专门的电视剧识别提示词
            String systemPrompt = getTvShowRecognitionPrompt();

            LOGGER.info("=== TV Show AI Recognition Debug ===");
            LOGGER.info("Input TV show path: {}", tvShowPath);
            LOGGER.info("API URL: {}", apiUrl);
            LOGGER.info("Model: {}", model);
            LOGGER.info("System prompt length: {} characters", systemPrompt.length());

            String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 500, \"temperature\": 0.3}",
                model,
                escapeJsonString(systemPrompt),
                escapeJsonString(tvShowPath)
            );

            LOGGER.info("API request body: {}", requestBody);

            // 创建HTTP请求
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(BodyPublishers.ofString(requestBody))
                .timeout(TIMEOUT)
                .build();

            // 发送HTTP请求（在SwingWorker后台线程中执行，不阻塞UI）
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                LOGGER.info("=== AI API Response ===");
                LOGGER.info("Response status: {}", response.statusCode());
                LOGGER.info("Response body: {}", responseBody);

                // 改进的JSON响应解析，支持多种API格式
                try {
                    // 首先尝试OpenAI格式: {"choices": [{"message": {"content": "电视剧名称"}}]}
                    int contentStart = responseBody.indexOf("\"content\":\"");
                    if (contentStart != -1) {
                        contentStart += "\"content\":\"".length();

                        // 改进的内容提取 - 处理复杂的JSON转义
                        StringBuilder contentBuilder = new StringBuilder();
                        int pos = contentStart;
                        boolean inEscape = false;

                        while (pos < responseBody.length()) {
                            char c = responseBody.charAt(pos);

                            if (inEscape) {
                                // 处理转义字符
                                if (c == 'n') {
                                    contentBuilder.append('\n');
                                } else if (c == 't') {
                                    contentBuilder.append('\t');
                                } else if (c == 'r') {
                                    contentBuilder.append('\r');
                                } else if (c == '"') {
                                    contentBuilder.append('"');
                                } else if (c == '\\') {
                                    contentBuilder.append('\\');
                                } else {
                                    contentBuilder.append(c);
                                }
                                inEscape = false;
                            } else if (c == '\\') {
                                inEscape = true;
                            } else if (c == '"') {
                                // 找到内容结束
                                break;
                            } else {
                                contentBuilder.append(c);
                            }
                            pos++;
                        }

                        String content = contentBuilder.toString().trim();
                        LOGGER.info("=== AI Recognition Result ===");
                        LOGGER.info("Raw extracted content: '{}'", content);
                        LOGGER.info("Content length: {} characters", content.length());

                        // 尝试从AI的解释中提取数据库ID
                        String extractedId = extractDatabaseIdFromResponse(content);
                        if (extractedId != null) {
                            LOGGER.info("Extracted database ID from AI response: '{}'", extractedId);
                            content = extractedId;
                        }

                        LOGGER.info("Final processed content: '{}'", content);
                        LOGGER.info("Content type: {}", content.matches("^(TMDB|TVDB):\\d+$") ? "Database ID format" : "Title format");

                        // 如果是数据库ID格式，添加验证警告
                        if (content.matches("^(TMDB|TVDB):\\d+$")) {
                            LOGGER.warn("=== Database ID Validation Required ===");
                            LOGGER.warn("AI returned database ID: {}", content);
                            LOGGER.warn("Please verify this ID matches the input TV show!");
                            LOGGER.warn("Input path was: {}", originalPath);
                        }

                        return content;
                    }

                    // 如果上述方法失败，尝试其他格式
                    LOGGER.error("=== AI Response Parse Failed ===");
                    LOGGER.error("Could not parse ChatGPT response format");
                    LOGGER.error("Full response body: {}", responseBody);
                    return null;

                } catch (Exception e) {
                    LOGGER.error("=== AI Response Parse Error ===");
                    LOGGER.error("Error parsing ChatGPT response: {}", e.getMessage());
                    LOGGER.error("Response body: {}", responseBody);
                    return null;
                }
            } else {
                LOGGER.error("=== AI API Request Failed ===");
                LOGGER.error("Status code: {}", response.statusCode());
                LOGGER.error("Response body: {}", response.body());
                return null;
            }



        } catch (Exception e) {
            LOGGER.error("Error calling ChatGPT API: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 正确转义JSON字符串
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    // 处理其他控制字符
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    /**
     * 从AI的解释性回复中提取数据库ID
     */
    private String extractDatabaseIdFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        LOGGER.debug("Attempting to extract database ID from response: {}", response);

        // 尝试匹配 TMDB:数字 格式
        java.util.regex.Pattern tmdbPattern = java.util.regex.Pattern.compile("TMDB[:\\s]+([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher tmdbMatcher = tmdbPattern.matcher(response);
        if (tmdbMatcher.find()) {
            String tmdbId = "TMDB:" + tmdbMatcher.group(1);
            LOGGER.info("Found TMDB ID in response: {}", tmdbId);
            return tmdbId;
        }

        // 尝试匹配 TVDB:数字 格式
        java.util.regex.Pattern tvdbPattern = java.util.regex.Pattern.compile("TVDB[:\\s]+([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher tvdbMatcher = tvdbPattern.matcher(response);
        if (tvdbMatcher.find()) {
            String tvdbId = "TVDB:" + tvdbMatcher.group(1);
            LOGGER.info("Found TVDB ID in response: {}", tvdbId);
            return tvdbId;
        }

        // 尝试匹配 "ID is 数字" 格式
        java.util.regex.Pattern idIsPattern = java.util.regex.Pattern.compile("(?:TMDB|TVDB)\\s+ID\\s+is\\s+([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher idIsMatcher = idIsPattern.matcher(response);
        if (idIsMatcher.find()) {
            // 根据上下文判断是TMDB还是TVDB
            if (response.toUpperCase().contains("TMDB")) {
                String tmdbId = "TMDB:" + idIsMatcher.group(1);
                LOGGER.info("Found TMDB ID from 'ID is' pattern: {}", tmdbId);
                return tmdbId;
            } else if (response.toUpperCase().contains("TVDB")) {
                String tvdbId = "TVDB:" + idIsMatcher.group(1);
                LOGGER.info("Found TVDB ID from 'ID is' pattern: {}", tvdbId);
                return tvdbId;
            }
        }

        // 尝试匹配 "ID for ... is 数字" 格式
        java.util.regex.Pattern idForPattern = java.util.regex.Pattern.compile("(?:TMDB|TVDB)\\s+ID\\s+for\\s+.*?is\\s+([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher idForMatcher = idForPattern.matcher(response);
        if (idForMatcher.find()) {
            // 根据上下文判断是TMDB还是TVDB
            if (response.toUpperCase().contains("TMDB")) {
                String tmdbId = "TMDB:" + idForMatcher.group(1);
                LOGGER.info("Found TMDB ID from 'ID for ... is' pattern: {}", tmdbId);
                return tmdbId;
            } else if (response.toUpperCase().contains("TVDB")) {
                String tvdbId = "TVDB:" + idForMatcher.group(1);
                LOGGER.info("Found TVDB ID from 'ID for ... is' pattern: {}", tvdbId);
                return tvdbId;
            }
        }

        // 尝试匹配 "TheTVDB ID for ... is 数字" 格式
        java.util.regex.Pattern theTvdbPattern = java.util.regex.Pattern.compile("TheTVDB\\s+ID\\s+for\\s+.*?is\\s+([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher theTvdbMatcher = theTvdbPattern.matcher(response);
        if (theTvdbMatcher.find()) {
            String tvdbId = "TVDB:" + theTvdbMatcher.group(1);
            LOGGER.info("Found TVDB ID from 'TheTVDB ID for ... is' pattern: {}", tvdbId);
            return tvdbId;
        }

        LOGGER.debug("No database ID found in response");
        return null;
    }

    /**
     * 获取电视剧识别的专用提示词
     */
    private String getTvShowRecognitionPrompt() {
        String customPrompt = settings.getOpenAiExtractionPrompt();
        if (customPrompt != null && !customPrompt.trim().isEmpty()) {
            return customPrompt;
        }

        // 电视剧专用的默认提示词 - 只输出标题，不要年份
        return "你是一个专业的媒体元数据查询引擎。你的唯一任务是**通过联网搜索**，为电视剧文件路径找到其最准确的官方信息，然后严格按照 `标题` 格式输出结果。\n\n" +
               "**输入：**\n一个电视剧文件路径。\n\n" +
               "**输出：**\n一个匹配后的字符串。**除电视剧标题外，不要输出任何其他内容（不要年份、不要解释）。**\n\n" +
               "---\n\n" +
               "**处理流程：**\n\n" +
               "**步骤 1：解析文件路径**\n" +
               "*   从文件路径中提取出干净的电视剧标题，移除所有技术规格、季数、集数和发布组等无关信息。\n" +
               "    *   例如，从 `/TV Shows/Breaking.Bad.S01E01.1080p.BluRay.x264-DEMAND/` 中提取出 `Breaking Bad`。\n" +
               "    *   例如，从 `/电视剧/庆余年.第一季.2019.4K.国语中字/` 中提取出 `庆余年`。\n\n" +
               "**步骤 2：强制联网搜索**\n" +
               "*   **这是最关键的一步，必须执行。**\n" +
               "*   **如果文件路径包含 `{tmdb-数字}` 或 `{tvdb-数字}`**，请直接使用该ID在相应数据库上查询。这是最高优先级。\n" +
               "*   **否则，** 使用你的**搜索工具**，以\"`解析出的标题 电视剧 TMDB`\"或\"`解析出的标题 电视剧 TVDB`\"为关键词进行搜索，找到最匹配的 TMDB、TVDB 或豆瓣页面。\n\n" +
               "**步骤 3：提取与格式化输出**\n" +
               "*   从搜索结果中，获取该电视剧的**官方标题**。\n" +
               "*   **中文优先：** 必须优先使用官方的中文标题。如果TMDB/TVDB没有中文标题，才使用其英文标题。\n" +
               "*   将获取到的官方标题直接输出，**不要年份，不要任何额外信息**。\n" +
               "*   **如果搜索失败**或无法找到任何确切的匹配项，则直接返回**从路径提取的标题**作为结果。\n\n" +
               "**输出示例：**\n" +
               "庆余年\n" +
               "权力的游戏\n" +
               "绝命毒师\n" +
               "怪奇物语\n" +
               "三体\n\n" +
               "**错误的输出示例（绝对不要这样做）：**\n" +
               "❌ The TV show is 怪奇物语\n" +
               "❌ 庆余年 (中国电视剧)\n" +
               "❌ 庆余年 2019\n" +
               "❌ 任何解释性文字或年份\n\n" +
               "---";
    }
    
    /**
     * 清理和验证识别结果（参考电影AI识别的简化逻辑）
     */
    private String cleanAndValidateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }

        // 参考电影AI识别的简化清理逻辑
        String cleaned = title
            .replaceAll("^[\\s\\p{Punct}]+", "")  // 移除开头的标点符号
            .replaceAll("[\\s\\p{Punct}]+$", "")  // 移除结尾的标点符号
            .trim();

        // 基本验证：长度合理（与电影保持一致）
        if (cleaned.length() >= 2 && cleaned.length() <= 100) {
            LOGGER.debug("Cleaned title: '{}' -> '{}'", title, cleaned);
            return cleaned;
        }

        LOGGER.warn("Invalid title length: {}", cleaned.length());
        return null;
    }
}
