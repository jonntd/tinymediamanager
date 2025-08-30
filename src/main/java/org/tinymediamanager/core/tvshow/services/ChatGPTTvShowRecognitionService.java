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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.services.AIApiRateLimiter;
import org.tinymediamanager.core.services.AIPerformanceMonitor;
import org.tinymediamanager.core.tvshow.entities.TvShow;

/**
 * ChatGPT电视剧识别服务
 * 基于路径倒数三层目录名称使用ChatGPT识别电视剧名
 */
public class ChatGPTTvShowRecognitionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGPTTvShowRecognitionService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // 简单的内存缓存，避免重复识别相同电视剧
    private static final Map<String, String> recognitionCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 500; // 最大缓存条目数

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
     * 识别电视剧标题（带重试机制）
     * @param tvShow 待识别的电视剧
     * @return 识别出的标题，如果失败返回null
     */
    public String recognizeTvShowTitle(TvShow tvShow) {
        return recognizeTvShowTitleWithRetry(tvShow, 3);
    }

    /**
     * 带重试机制的电视剧标题识别
     * @param tvShow 待识别的电视剧
     * @param maxRetries 最大重试次数
     * @return 识别出的标题，如果失败返回null
     */
    private String recognizeTvShowTitleWithRetry(TvShow tvShow, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long startTime = System.currentTimeMillis();
            boolean success = false;

            try {
                LOGGER.info("=== Starting TV Show AI Recognition (Attempt {}/{}) ===", attempt, maxRetries);
                LOGGER.info("TV Show title: {}", tvShow.getTitle());
                LOGGER.info("TV Show year: {}", tvShow.getYear());
                LOGGER.info("TV Show DB ID: {}", tvShow.getDbId());

                // 检查API频率限制并记录统计
                AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
                if (!rateLimiter.requestPermission("ChatGPTTvShowRecognition")) {
                    LOGGER.warn("API rate limit exceeded for TV show recognition on attempt {}/{}", attempt, maxRetries);
                    throw new RuntimeException("API rate limit exceeded");
                }

                String result = performSingleRecognition(tvShow);
                long responseTime = System.currentTimeMillis() - startTime;

                if (result != null && !result.trim().isEmpty()) {
                    success = true;
                    // 记录成功的性能指标
                    AIPerformanceMonitor.getInstance().recordAPICall("ChatGPTTvShowRecognition", responseTime, true);
                    LOGGER.info("AI recognition successful on attempt {} ({}ms)", attempt, responseTime);
                    return result;
                } else {
                    // 记录失败的性能指标
                    AIPerformanceMonitor.getInstance().recordAPICall("ChatGPTTvShowRecognition", responseTime, false);
                    LOGGER.warn("AI recognition returned empty result on attempt {}/{} ({}ms)", attempt, maxRetries, responseTime);

                    if (attempt < maxRetries) {
                        // 指数退避重试
                        long delayMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...
                        LOGGER.info("Retrying after {}ms delay", delayMs);
                        Thread.sleep(delayMs);
                    }
                }

            } catch (Exception e) {
                lastException = e;
                long responseTime = System.currentTimeMillis() - startTime;

                // 记录失败的性能指标
                AIPerformanceMonitor.getInstance().recordAPICall("ChatGPTTvShowRecognition", responseTime, false);
                LOGGER.warn("AI recognition failed on attempt {}/{} ({}ms): {}", attempt, maxRetries, responseTime, e.getMessage());

                if (attempt < maxRetries) {
                    // 指数退避重试
                    long delayMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...
                    try {
                        LOGGER.info("Retrying after {}ms delay", delayMs);
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("Retry delay interrupted");
                        break;
                    }
                }
            }
        }

        // 所有重试都失败
        LOGGER.error("AI recognition failed after {} attempts", maxRetries);
        if (lastException != null) {
            LOGGER.error("Last exception: ", lastException);
        }
        LOGGER.info("=== TV Show AI Recognition Failed ===");
        return null;
    }

    /**
     * 执行单次AI识别
     */
    private String performSingleRecognition(TvShow tvShow) {

        if (httpClient == null) {
            LOGGER.warn("HTTP client not initialized, cannot perform TV show recognition");
            throw new RuntimeException("HTTP client not initialized");
        }
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

            // 检查缓存，避免重复识别
            String cacheKey = pathContext;
            String cachedResult = recognitionCache.get(cacheKey);
            if (cachedResult != null) {
                LOGGER.info("=== Cache Hit ===");
                LOGGER.info("Using cached result: '{}'", cachedResult);
                return cachedResult;
            }

            // 调用ChatGPT API，传递倒数三层目录信息
            String recognizedTitle = callChatGPTAPI(pathContext, tvShowPath);

            LOGGER.info("=== AI Recognition Complete ===");
            LOGGER.info("Raw AI response: '{}'", recognizedTitle);

            if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
                // 清理和验证识别结果
                String cleanedTitle = cleanAndValidateTitle(recognizedTitle);
                LOGGER.info("Cleaned and validated title: '{}'", cleanedTitle);

                // 缓存成功的识别结果
                if (cleanedTitle != null && !cleanedTitle.trim().isEmpty()) {
                    // 如果缓存过大，清理一半
                    if (recognitionCache.size() >= MAX_CACHE_SIZE) {
                        LOGGER.info("Cache size limit reached, clearing half of the cache");
                        recognitionCache.entrySet().removeIf(entry ->
                            recognitionCache.size() > MAX_CACHE_SIZE / 2);
                    }
                    recognitionCache.put(cacheKey, cleanedTitle);
                    LOGGER.debug("Cached recognition result for: {} (cache size: {})",
                        cacheKey, recognitionCache.size());
                }

                return cleanedTitle;
            } else {
                LOGGER.warn("AI returned empty or null result");
                return null;
            }
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
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 50, \"temperature\": 0.1}",
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

        // 电视剧专用的优化提示词 - 简洁高效
        return "你是一个专业的电视剧信息识别助手。根据提供的文件路径，联网搜索并找到最准确的官方电视剧信息，然后严格按照指定格式输出结果。\n\n" +
               "## 核心要求\n\n" +
               "### 1. 输入处理\n" +
               "- 接收电视剧文件路径作为输入\n" +
               "- 从路径中提取电视剧标题\n" +
               "- 忽略季数、集数、分辨率、发布组等技术信息\n\n" +
               "### 2. 搜索策略\n" +
               "- 使用提取的标题进行精确搜索\n" +
               "- 查找官方来源：TMDB、TVDB、豆瓣等\n" +
               "- 验证搜索结果的准确性\n\n" +
               "### 3. 输出格式要求\n" +
               "**只输出电视剧标题，绝对不要返回任何解释或错误信息：**\n" +
               "- 使用官方中文名称（如果有），否则使用英文原名\n" +
               "- 不包含年份、括号、解释文字\n" +
               "- 不包含任何符号或额外信息\n" +
               "- 如果搜索失败，输出：未知电视剧\n" +
               "- 禁止返回'I am unable to'或任何错误说明\n\n" +
               "### 4. 示例\n" +
               "输入：`/TV Shows/Breaking.Bad.S01/` → 输出：`绝命毒师`\n" +
               "输入：`/电视剧/庆余年.第一季/` → 输出：`庆余年`";
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
