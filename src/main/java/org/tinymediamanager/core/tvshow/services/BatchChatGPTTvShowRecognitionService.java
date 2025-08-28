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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.tvshow.entities.TvShow;

/**
 * 批量ChatGPT电视剧识别服务
 * 支持单次API调用处理多个电视剧，减少API调用次数
 */
public class BatchChatGPTTvShowRecognitionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchChatGPTTvShowRecognitionService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    
    private HttpClient httpClient;
    private final Settings settings;
    
    // 缓存已识别的结果，避免重复调用
    private static final Map<String, String> recognitionCache = new ConcurrentHashMap<>();
    
    public BatchChatGPTTvShowRecognitionService() {
        this.settings = Settings.getInstance();

        // 添加调用栈追踪，帮助调试启动时的意外调用
        if (LOGGER.isDebugEnabled()) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder("BatchChatGPTTvShowRecognitionService created from:\n");
            for (int i = 2; i < Math.min(stackTrace.length, 8); i++) {
                sb.append("  at ").append(stackTrace[i].toString()).append("\n");
            }
            LOGGER.debug(sb.toString());
        }

        String apiKey = settings.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOGGER.warn("OpenAI API key is not configured in settings");
            // 不立即返回，允许后续的单个识别回退
        } else {
            try {
                this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
                LOGGER.debug("HTTP client initialized successfully");
            } catch (Exception e) {
                LOGGER.error("Failed to initialize HTTP client: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 批量识别电视剧标题（支持分组处理和重试机制）
     * @param tvShows 待识别的电视剧列表
     * @return 电视剧ID到识别标题的映射
     */
    public Map<String, String> batchRecognizeTvShowTitles(List<TvShow> tvShows) {
        return batchRecognizeTvShowTitles(tvShows, 20, 6);
    }
    
    /**
     * 批量识别电视剧标题（支持分组处理和重试机制）
     * @param tvShows 待识别的电视剧列表
     * @param batchSize 每批处理的数量
     * @param maxRetries 最大重试次数
     * @return 电视剧ID到识别标题的映射
     */
    public Map<String, String> batchRecognizeTvShowTitles(List<TvShow> tvShows, int batchSize, int maxRetries) {
        Map<String, String> results = new HashMap<>();
        
        if (tvShows == null || tvShows.isEmpty()) {
            LOGGER.debug("No TV shows to process");
            return results;
        }
        
        if (httpClient == null) {
            LOGGER.warn("HTTP client not initialized, falling back to individual recognition");
            return fallbackToIndividualRecognition(tvShows);
        }
        
        // 过滤有效的电视剧并检查缓存
        List<TvShow> validTvShows = new ArrayList<>();
        for (TvShow tvShow : tvShows) {
            if (tvShow == null || tvShow.getDbId() == null) {
                LOGGER.warn("Skipping invalid TV show: {}", tvShow);
                continue;
            }
            
            String cacheKey = generateCacheKey(tvShow);
            if (recognitionCache.containsKey(cacheKey)) {
                String cachedResult = recognitionCache.get(cacheKey);
                results.put(tvShow.getDbId().toString(), cachedResult);
                LOGGER.debug("Using cached result for TV show: {} (Key: {})", tvShow.getTitle(), cacheKey);
                continue;
            }
            
            validTvShows.add(tvShow);
            LOGGER.debug("Processing TV show: {} (ID: {})", tvShow.getTitle(), tvShow.getDbId());
        }
        
        if (validTvShows.isEmpty()) {
            LOGGER.debug("All TV shows already cached, skipping API call");
            return results;
        }
        
        LOGGER.info("Starting batch recognition for {} valid TV shows (batch size: {}, max retries: {})", 
                   validTvShows.size(), batchSize, maxRetries);
        
        // 分批处理
        for (int i = 0; i < validTvShows.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, validTvShows.size());
            List<TvShow> batch = validTvShows.subList(i, endIndex);
            
            LOGGER.debug("Processing batch {}/{}: {} TV shows", 
                        (i / batchSize) + 1, 
                        (validTvShows.size() + batchSize - 1) / batchSize, 
                        batch.size());
            
            Map<String, String> batchResults = processBatch(batch, maxRetries);
            results.putAll(batchResults);
        }
        
        return results;
    }
    
    /**
     * 处理单个批次
     */
    private Map<String, String> processBatch(List<TvShow> batch, int maxRetries) {
        Map<String, String> results = new HashMap<>();
        
        boolean success = false;
        for (int attempt = 1; attempt <= maxRetries && !success; attempt++) {
            try {
                LOGGER.debug("Batch processing attempt {}/{} for {} TV shows", attempt, maxRetries, batch.size());
                
                String batchPrompt = buildBatchPrompt(batch);
                String apiResponse = callBatchAPI(batchPrompt);
                
                if (apiResponse != null) {
                    Map<String, String> parsedResults = parseBatchResponse(apiResponse, batch);
                    if (!parsedResults.isEmpty()) {
                        results.putAll(parsedResults);
                        
                        // 缓存成功的结果
                        for (TvShow tvShow : batch) {
                            String tvShowId = tvShow.getDbId().toString();
                            if (parsedResults.containsKey(tvShowId)) {
                                String cacheKey = generateCacheKey(tvShow);
                                recognitionCache.put(cacheKey, parsedResults.get(tvShowId));
                            }
                        }
                        
                        success = true;
                        LOGGER.debug("Batch processing successful on attempt {}", attempt);
                    } else {
                        LOGGER.warn("Batch processing attempt {} failed: no valid results parsed", attempt);
                    }
                } else {
                    LOGGER.warn("Batch processing attempt {} failed: API call returned null", attempt);
                }
                
            } catch (Exception e) {
                LOGGER.error("Batch processing attempt {} failed: {}", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    LOGGER.error("All batch processing attempts failed", e);
                }
            }
        }
        
        if (!success) {
            LOGGER.error("Batch processing failed after {} retries for {} TV shows", 
                        maxRetries, batch.size());
            
            // 回退到逐个识别
            LOGGER.info("Falling back to individual recognition for failed batch");
            for (TvShow tvShow : batch) {
                try {
                    ChatGPTTvShowRecognitionService individualService = new ChatGPTTvShowRecognitionService();
                    String title = individualService.recognizeTvShowTitle(tvShow);
                    if (title != null) {
                        results.put(tvShow.getDbId().toString(), title);
                        String cacheKey = generateCacheKey(tvShow);
                        recognitionCache.put(cacheKey, title);
                    }
                } catch (Exception ex) {
                    LOGGER.error("Individual recognition failed for TV show {}: {}", tvShow.getTitle(), ex.getMessage());
                }
            }
        }
        
        return results;
    }
    
    /**
     * 构建批量处理的提示词
     */
    private String buildBatchPrompt(List<TvShow> tvShows) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下电视剧文件路径识别正确的标题，每行一个结果，格式为 \"ID: 标题\"：\n\n");

        for (TvShow tvShow : tvShows) {
            String tvShowPath = extractTvShowPath(tvShow);
            String pathContext = extractLastThreeDirectoryNames(tvShowPath);
            prompt.append(tvShow.getDbId().toString()).append(": ").append(pathContext).append("\n");
        }

        prompt.append("\n请严格按照 \"ID: 标题\" 格式输出，每行一个结果。\n");
        prompt.append("示例：\"123: 庆余年\"、\"456: 权力的游戏\"\n");
        return prompt.toString();
    }
    
    /**
     * 调用批量API（带重试机制）
     */
    private String callBatchAPI(String prompt) {
        return callBatchAPIWithRetry(prompt, 3);
    }

    /**
     * 带重试机制的批量API调用
     */
    private String callBatchAPIWithRetry(String prompt, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                LOGGER.debug("Batch TV show API call attempt {}/{}", attempt, maxRetries);
            String apiKey = settings.getOpenAiApiKey();
            String apiUrl = settings.getOpenAiApiUrl();
            String model = settings.getOpenAiModel();
            
            String systemPrompt = getTvShowBatchRecognitionPrompt();
            
            String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 2000, \"temperature\": 0.3}",
                model,
                escapeJsonString(systemPrompt),
                escapeJsonString(prompt)
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(BodyPublishers.ofString(requestBody))
                .timeout(TIMEOUT)
                .build();
            
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    LOGGER.info("=== Batch AI API Response (Attempt {}) ===", attempt);
                    LOGGER.info("Response status: {}", response.statusCode());
                    LOGGER.info("Response body: {}", responseBody);

                    // 解析响应
                    int contentStart = responseBody.indexOf("\"content\":\"");
                    if (contentStart != -1) {
                        contentStart += "\"content\":\"".length();
                        int contentEnd = responseBody.indexOf('"', contentStart);
                        if (contentEnd != -1) {
                            String content = responseBody.substring(contentStart, contentEnd)
                                .replace("\\\"", "\"")
                                .replace("\\n", "\n")
                                .trim();

                            if (content != null && !content.isEmpty()) {
                                LOGGER.info("=== Batch AI Recognition Result (Attempt {}) ===", attempt);
                                LOGGER.info("Raw extracted content: '{}'", content);
                                LOGGER.info("Content length: {} characters", content.length());
                                return content;
                            } else {
                                LOGGER.warn("Batch TV show API returned empty content on attempt {}/{}", attempt, maxRetries);
                            }
                        }
                    }
                    LOGGER.warn("=== Batch AI Response Parse Failed (Attempt {}) ===", attempt);
                    LOGGER.warn("Could not find content in response: {}", responseBody);
                } else {
                    LOGGER.warn("=== Batch AI API Request Failed (Attempt {}/{}) ===", attempt, maxRetries);
                    LOGGER.warn("Status code: {}", response.statusCode());
                    LOGGER.warn("Response body: {}", response.body());
                }

                if (attempt < maxRetries) {
                    // 指数退避重试
                    long delayMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...
                    LOGGER.info("Retrying batch TV show API after {}ms delay", delayMs);
                    Thread.sleep(delayMs);
                }

            } catch (Exception e) {
                lastException = e;
                LOGGER.warn("Batch TV show API failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    // 指数退避重试
                    long delayMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...
                    try {
                        LOGGER.info("Retrying batch TV show API after {}ms delay", delayMs);
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("Batch TV show API retry delay interrupted");
                        break;
                    }
                }
            }
        }

        // 所有重试都失败
        LOGGER.error("Batch TV show API failed after {} attempts", maxRetries, lastException);
        return null;
    }
    
    /**
     * 解析批量响应
     */
    private Map<String, String> parseBatchResponse(String response, List<TvShow> tvShows) {
        Map<String, String> results = new HashMap<>();
        
        if (StringUtils.isBlank(response)) {
            return results;
        }
        
        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // 解析格式: "ID: 标题 年份"
            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String id = line.substring(0, colonIndex).trim();
                String title = line.substring(colonIndex + 1).trim();
                
                if (!id.isEmpty() && !title.isEmpty()) {
                    results.put(id, title);
                    LOGGER.debug("Parsed batch result: {} -> {}", id, title);
                }
            }
        }
        
        return results;
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
     * 获取电视剧批量识别的优化提示词
     */
    private String getTvShowBatchRecognitionPrompt() {
        return "你是一个专业的电视剧信息识别助手。根据提供的文件路径列表，联网搜索并找到最准确的官方电视剧信息，然后严格按照指定格式输出结果。\n\n" +
               "## 核心要求\n\n" +
               "### 1. 输入处理\n" +
               "- 接收电视剧文件路径列表，每行格式：\"ID: 路径\"\n" +
               "- 从每个路径中提取电视剧标题\n" +
               "- 忽略技术信息和无关内容\n\n" +
               "### 2. 搜索策略\n" +
               "- 对每个电视剧进行独立搜索\n" +
               "- 查找官方来源：TMDB、TVDB、豆瓣等\n" +
               "- 验证搜索结果的准确性\n\n" +
               "### 3. 输出格式要求\n" +
               "**严格按照以下格式输出，每行一个结果：**\n" +
               "```\nID: 标题\n```\n" +
               "- 使用官方中文名称（如果有），否则使用英文原名\n" +
               "- 输出行数必须与输入行数完全一致\n" +
               "- 保持ID顺序不变\n\n" +
               "### 4. 示例\n" +
               "输入：\n```\n123: /TV Shows/Breaking.Bad.S01/\n456: /电视剧/庆余年.第一季/\n```\n" +
               "输出：\n```\n123: 绝命毒师\n456: 庆余年\n```";
    }
    
    /**
     * 提取电视剧路径
     */
    private String extractTvShowPath(TvShow tvShow) {
        if (tvShow.getPathNIO() != null) {
            return tvShow.getPathNIO().toString();
        }
        
        List<MediaFile> mediaFiles = tvShow.getMediaFiles();
        if (!mediaFiles.isEmpty()) {
            MediaFile firstFile = mediaFiles.get(0);
            if (firstFile.getFileAsPath() != null) {
                return firstFile.getFileAsPath().toString();
            }
        }
        
        return "tvshow_" + tvShow.getDbId();
    }
    
    /**
     * 提取路径倒数三层
     */
    private String extractLastThreeDirectoryNames(String filePath) {
        try {
            Path path = Paths.get(filePath);
            int nameCount = path.getNameCount();
            if (nameCount <= 3) {
                return "/" + path.toString();
            }
            Path lastThreeLayers = path.subpath(nameCount - 3, nameCount);
            return "/" + lastThreeLayers.toString();
        } catch (Exception e) {
            LOGGER.warn("Failed to extract last three layers from path: {}", e.getMessage());
            return filePath;
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(TvShow tvShow) {
        String path = extractTvShowPath(tvShow);
        String pathContext = extractLastThreeDirectoryNames(path);
        return pathContext;
    }
    
    /**
     * 回退到单个识别模式
     */
    private Map<String, String> fallbackToIndividualRecognition(List<TvShow> tvShows) {
        Map<String, String> results = new HashMap<>();
        ChatGPTTvShowRecognitionService individualService = new ChatGPTTvShowRecognitionService();
        
        LOGGER.info("Falling back to individual TV show recognition for {} TV shows", tvShows.size());
        
        for (TvShow tvShow : tvShows) {
            try {
                String title = individualService.recognizeTvShowTitle(tvShow);
                if (title != null) {
                    results.put(tvShow.getDbId().toString(), title);
                }
            } catch (Exception e) {
                LOGGER.error("Individual recognition failed for TV show {}: {}", tvShow.getTitle(), e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * 获取缓存大小（用于调试）
     */
    public static int getCacheSize() {
        return recognitionCache.size();
    }
    
    /**
     * 清除缓存（用于调试）
     */
    public static void clearCache() {
        recognitionCache.clear();
        LOGGER.info("TV show recognition cache cleared");
    }
}
