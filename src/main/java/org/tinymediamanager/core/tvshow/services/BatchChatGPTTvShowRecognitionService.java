package org.tinymediamanager.core.tvshow.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.services.RetryUtils;
import org.tinymediamanager.core.services.AIApiRateLimiter;
import org.tinymediamanager.core.services.AdaptiveBatchProcessor;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.services.utils.TvShowAIPromptTemplates;
import org.tinymediamanager.core.tvshow.services.utils.TvShowAIResponseParser;
import org.tinymediamanager.core.tvshow.services.utils.TvShowPathUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量ChatGPT电视剧识别服务 支持单次API调用处理多个电视剧，减少API调用次数
 */
public class BatchChatGPTTvShowRecognitionService {
    private static final Logger          LOGGER                 = LoggerFactory.getLogger(BatchChatGPTTvShowRecognitionService.class);
    private static final Duration        TIMEOUT                = Duration.ofSeconds(60);
    private static final ObjectMapper    OBJECT_MAPPER          = new ObjectMapper();

    private HttpClient                   httpClient;
    private final Settings               settings;

    // 自适应批量处理器
    private final AdaptiveBatchProcessor adaptiveBatchProcessor = AdaptiveBatchProcessor.getInstance();

    // 双尖括号索引匹配正则：<<数字>> 内容
    private static final Pattern         INDEXED_RESULT_PATTERN = Pattern.compile("^<<(\\d+)>>\\s*(.+)$");

    /**
     * 批量识别回调接口 每当一批电视剧识别成功后会调用此接口，允许调用方立即处理识别结果
     */
    public interface BatchRecognitionCallback {
        /**
         * 当一批电视剧识别成功时调用
         * 
         * @param batchResults
         *            本批次识别结果 (dbId -> recognizedTitle)
         * @param recognizedTvShows
         *            本批次识别成功的电视剧列表
         */
        void onBatchRecognized(Map<String, String> batchResults, List<TvShow> recognizedTvShows);
    }

    public BatchChatGPTTvShowRecognitionService() {
        this.settings = Settings.getInstance();

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
        }
        else {
            try {
                this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
                LOGGER.debug("HTTP client initialized successfully");
            }
            catch (Exception e) {
                LOGGER.error("Failed to initialize HTTP client: {}", e.getMessage());
            }
        }
    }

    /**
     * 识别单个电视剧标题（使用批量模式，格式 <<1>> 路径） 内部将单个电视剧转为批量大小为1的调用，复用批量识别的 Prompt 和解析逻辑
     * 
     * @param tvShow
     *            电视剧对象
     * @return 识别的标题（格式：标题 年份），如果识别失败返回null
     */
    public String recognizeTvShowTitle(TvShow tvShow) {
        if (tvShow == null) {
            return null;
        }

        if (!settings.isEnableAi()) {
            LOGGER.info("AI scraping is disabled in settings. Skipping TV show recognition.");
            return null;
        }

        if (httpClient == null) {
            LOGGER.warn("HTTP client is not initialized - please check OpenAI API key configuration");
            return null;
        }

        List<TvShow> singleBatch = Collections.singletonList(tvShow);
        Map<String, String> results = batchRecognizeTvShowTitles(singleBatch, 1, settings.getAiMaxRetries());

        String result = results.get(tvShow.getDbId().toString());
        if (result != null) {
            LOGGER.info("Single TV show recognition successful: '{}' -> '{}'", tvShow.getTitle(), result);
        }
        else {
            LOGGER.warn("Single TV show recognition failed for: '{}'", tvShow.getTitle());
        }

        return result;
    }

    public Map<String, String> batchRecognizeTvShowTitles(List<TvShow> tvShows) {
        return batchRecognizeTvShowTitles(tvShows, null);
    }

    /**
     * 批量识别电视剧标题（带回调） 每当一批识别成功后，立即通过回调通知调用方，实现边识别边处理
     *
     * @param tvShows
     *            待识别的电视剧列表
     * @param callback
     *            识别成功回调（可为null，则等待全部完成后返回）
     * @return 所有识别结果的汇总 (dbId -> recognizedTitle)
     */
    public Map<String, String> batchRecognizeTvShowTitles(List<TvShow> tvShows, BatchRecognitionCallback callback) {
        int batchSize = settings.getAiBatchSize();
        LOGGER.info("Using configured batch size: {} for {} TV shows", batchSize, tvShows.size());
        return batchRecognizeTvShowTitles(tvShows, batchSize, settings.getAiMaxRetries(), callback);
    }

    public Map<String, String> batchRecognizeTvShowTitles(List<TvShow> tvShows, int batchSize, int maxRetries) {
        return batchRecognizeTvShowTitles(tvShows, batchSize, maxRetries, null);
    }

    /**
     * 批量识别电视剧标题（完整参数版，带回调）
     *
     * @param tvShows
     *            待识别的电视剧列表
     * @param batchSize
     *            每批处理的数量
     * @param maxRetries
     *            最大重试次数
     * @param callback
     *            识别成功回调（可为null）
     * @return 所有识别结果的汇总
     */
    public Map<String, String> batchRecognizeTvShowTitles(List<TvShow> tvShows, int batchSize, int maxRetries, BatchRecognitionCallback callback) {
        Map<String, String> results = new HashMap<>();

        if (tvShows == null || tvShows.isEmpty()) {
            return results;
        }

        if (!settings.isEnableAi()) {
            LOGGER.info("AI scraping is disabled in settings. Skipping batch TV show recognition.");
            return results;
        }

        if (httpClient == null) {
            LOGGER.warn("HTTP client not initialized, falling back to individual recognition");
            return fallbackToIndividualRecognition(tvShows);
        }

        List<TvShow> validTvShows = new ArrayList<>();
        for (TvShow tvShow : tvShows) {
            if (tvShow != null && tvShow.getDbId() != null) {
                validTvShows.add(tvShow);
            }
        }

        if (validTvShows.isEmpty()) {
            return results;
        }

        LOGGER.info("Starting batch recognition for {} valid TV shows", validTvShows.size());

        for (int i = 0; i < validTvShows.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, validTvShows.size());
            List<TvShow> batch = validTvShows.subList(i, endIndex);

            LOGGER.debug("Processing batch {}/{}", (i / batchSize) + 1, (validTvShows.size() + batchSize - 1) / batchSize);

            Map<String, String> batchResults = processBatch(batch, maxRetries);
            results.putAll(batchResults);

            // 如果有回调且本批次有识别结果，立即通知调用方
            if (callback != null && !batchResults.isEmpty()) {
                // 找出本批次识别成功的电视剧
                List<TvShow> recognizedInBatch = new ArrayList<>();
                for (TvShow tvShow : batch) {
                    if (batchResults.containsKey(tvShow.getDbId().toString())) {
                        recognizedInBatch.add(tvShow);
                    }
                }
                LOGGER.info("Batch {} completed with {} recognized, invoking callback immediately", (i / batchSize) + 1, recognizedInBatch.size());
                callback.onBatchRecognized(batchResults, recognizedInBatch);
            }
        }

        return results;
    }

    private Map<String, String> processBatch(List<TvShow> batch, int maxRetries) {
        Map<String, String> results = new HashMap<>();
        boolean success = false;
        boolean isNetworkError = false; // 标记是否为网络错误
        int currentBatchSize = adaptiveBatchProcessor.getCurrentBatchSize(); // 只用于记录统计，实际大小由参数控制

        for (int attempt = 1; attempt <= maxRetries && !success; attempt++) {
            try {
                LOGGER.debug("Batch processing attempt {}/{} for {} TV shows", attempt, maxRetries, batch.size());

                String batchPrompt = buildBatchPrompt(batch);
                String apiResponse = callBatchAPI(batchPrompt, currentBatchSize);

                // API 成功返回，清除网络错误标记
                isNetworkError = false;

                if (apiResponse != null) {
                    Map<String, String> parsedResults = parseBatchResponse(apiResponse, batch);
                    if (!parsedResults.isEmpty()) {
                        results.putAll(parsedResults);
                        success = true;
                    }
                    else {
                        LOGGER.warn("批量处理尝试 {}/{} 失败: 解析结果为空, 原始响应: {}", attempt, maxRetries, apiResponse);
                        // 如果解析不到结果，可能是格式问题，重试
                        if (attempt < maxRetries) {
                            RetryUtils.waitBeforeRetry(attempt, "Empty batch results");
                        }
                    }
                }
                else {
                    LOGGER.warn("批量处理尝试 {}/{} 失败: API返回空", attempt, maxRetries);
                    if (attempt < maxRetries) {
                        RetryUtils.waitBeforeRetry(attempt, "Null API response");
                    }
                }
            }
            catch (Exception e) {
                // 检查是否为网络错误（IOException 被包装在 RuntimeException 中）
                Throwable cause = e.getCause();
                boolean isNetworkException = (cause instanceof java.io.IOException) || (e.getMessage() != null && e.getMessage().contains("HTTP"));

                if (isNetworkException) {
                    // 网络错误：标记为网络错误，不应降级到单个识别
                    isNetworkError = true;
                    LOGGER.warn("批量处理尝试 {}/{} 网络错误: {} (将继续重试，不降级)", attempt, maxRetries, e.getMessage());
                }
                else {
                    // 其他异常：可能是逻辑错误，可以考虑降级
                    isNetworkError = false;
                    LOGGER.error("批量处理尝试 {}/{} 异常: {}", attempt, maxRetries, e.getMessage());
                }

                if (attempt < maxRetries) {
                    RetryUtils.waitBeforeRetry(attempt, isNetworkException ? "Network Error" : "Batch Exception");
                }
            }
        }

        if (!success) {
            if (isNetworkError) {
                // 网络错误导致的失败，不降级，返回空结果让上层处理
                LOGGER.error("批量处理因网络错误失败，已重试 {} 次，不降级到单个识别", maxRetries);
            }
            else if (Settings.getInstance().isAiIndividualFallbackEnabled()) {
                // 非网络错误（如识别失败），可以降级到单个识别
                LOGGER.info("批量识别失败，降级到单个识别模式");
                results.putAll(fallbackToIndividualRecognition(batch));
            }
            else {
                LOGGER.error("批量处理失败，已重试 {} 次", maxRetries);
            }
        }

        return results;
    }

    private String buildBatchPrompt(List<TvShow> tvShows) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请识别以下电视剧：\n");

        // 使用双尖括号索引格式，确保输入输出精确匹配
        for (int i = 0; i < tvShows.size(); i++) {
            TvShow tvShow = tvShows.get(i);
            String tvShowPath = TvShowPathUtils.extractTvShowPath(tvShow);
            String pathContext = TvShowPathUtils.extractLastThreeDirectoryNames(tvShowPath);
            // 格式: <<序号>> 路径
            prompt.append("<<").append(i + 1).append(">> ").append(pathContext).append("\n");
        }
        return prompt.toString();
    }

    private String callBatchAPI(String userPrompt, int batchSizeForStats) {
        long startTime = System.currentTimeMillis();
        try {
            AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
            if (!rateLimiter.waitForPermission("BatchChatGPTTvShowRecognition", 30000)) {
                LOGGER.warn("API rate limit exceeded");
                throw new RuntimeException("API rate limit exceeded");
            }

            String apiKey = settings.getOpenAiApiKey();
            String apiUrl = settings.getOpenAiApiUrl();
            String model = settings.getOpenAiModel();
            String systemPrompt = TvShowAIPromptTemplates.getBatchTvShowRecognitionPrompt();

            LOGGER.debug("=== Batch API Call Details ===");
            LOGGER.debug("API URL: {}", apiUrl);
            LOGGER.debug("Model: {}", model);
            LOGGER.debug("System prompt length: {} characters", systemPrompt.length());
            LOGGER.debug("User prompt length: {} characters", userPrompt.length());

            ObjectNode requestJson = OBJECT_MAPPER.createObjectNode();
            requestJson.put("model", model);
            requestJson.put("max_tokens", 5000);
            requestJson.put("temperature", 0);

            ArrayNode messages = requestJson.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            // 输出AI请求摘要到活动日志（简化格式）
            LOGGER.info("[AI请求] 电视剧批量识别 | 模型: {} | 数量: {} 部", model, userPrompt.split("\n").length - 1);

            String requestBody = OBJECT_MAPPER.writeValueAsString(requestJson);
            LOGGER.debug("Request body size: {} bytes", requestBody.length());
            LOGGER.trace("Request body: {}", requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(BodyPublishers.ofString(requestBody))
                    .timeout(TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            long responseTime = System.currentTimeMillis() - startTime;

            LOGGER.debug("=== Batch API Response Details ===");
            LOGGER.debug("Status code: {}", response.statusCode());
            LOGGER.debug("Response time: {}ms", responseTime);
            LOGGER.debug("Response body size: {} bytes", response.body().length());
            LOGGER.trace("Response body: {}", response.body());

            if (response.statusCode() == 200) {
                adaptiveBatchProcessor.recordBatchResponse(responseTime, batchSizeForStats, true);

                // 从 response body 中提取 content（未解析的原始内容）
                String rawContent = TvShowAIResponseParser.extractContentFromResponse(response.body());

                // 输出原始 content 到活动日志
                if (rawContent != null && !rawContent.isEmpty()) {
                    LOGGER.info("[AI响应] 电视剧批量识别 | 耗时: {}ms | 原始content:\n{}", responseTime, rawContent);
                }
                LOGGER.debug("Extracted content: '{}'", rawContent);

                return rawContent;
            }
            else {
                adaptiveBatchProcessor.recordBatchResponse(responseTime, batchSizeForStats, false);
                LOGGER.error("API Error: {} - Response: {}", response.statusCode(), response.body());
                if (response.statusCode() == 429) {
                    rateLimiter.record429Error();
                }
                return null;
            }
        }
        catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            adaptiveBatchProcessor.recordBatchResponse(responseTime, batchSizeForStats, false);
            LOGGER.error("API Call Failed: {}", e.getMessage(), e);
            throw new RuntimeException("API Call Failed", e);
        }
    }

    private Map<String, String> parseBatchResponse(String response, List<TvShow> tvShows) {
        Map<String, String> results = new HashMap<>();
        if (StringUtils.isBlank(response))
            return results;

        // 建立索引到电视剧的映射（索引从1开始）
        Map<Integer, TvShow> indexToTvShow = new HashMap<>();
        for (int i = 0; i < tvShows.size(); i++) {
            indexToTvShow.put(i + 1, tvShows.get(i));
        }

        String[] lines = response.split("\n");
        int matchedCount = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("```"))
                continue;

            // 使用正则匹配双尖括号索引格式: <<数字>> 内容
            Matcher matcher = INDEXED_RESULT_PATTERN.matcher(line);
            if (matcher.matches()) {
                try {
                    int index = Integer.parseInt(matcher.group(1));
                    String title = matcher.group(2).trim();

                    TvShow tvShow = indexToTvShow.get(index);
                    if (tvShow != null && !title.isEmpty()) {
                        results.put(tvShow.getDbId().toString(), title);
                        matchedCount++;
                        LOGGER.debug("Matched [<<{}>>] -> {}", index, title);
                    }
                }
                catch (NumberFormatException e) {
                    LOGGER.warn("Invalid index in line: {}", line);
                }
            }
            else {
                // 兼容旧格式：尝试匹配其他常见格式如 "1. 标题" 或 "1: 标题"
                if (line.matches("^\\d+[\\.:\\)]\\s*.*")) {
                    String[] parts = line.split("[\\.:\\)]\\s*", 2);
                    if (parts.length == 2) {
                        try {
                            int index = Integer.parseInt(parts[0].trim());
                            String title = parts[1].trim();
                            TvShow tvShow = indexToTvShow.get(index);
                            if (tvShow != null && !title.isEmpty()) {
                                results.put(tvShow.getDbId().toString(), title);
                                matchedCount++;
                                LOGGER.debug("Fallback matched [{}] -> {}", index, title);
                            }
                        }
                        catch (NumberFormatException e) {
                            LOGGER.warn("Invalid fallback index in line: {}", line);
                        }
                    }
                }
                else {
                    LOGGER.debug("Line does not match indexed format: {}", line);
                }
            }
        }

        LOGGER.debug("Parsed {} results from response, expected {} TV shows", matchedCount, tvShows.size());
        return results;
    }

    private Map<String, String> fallbackToIndividualRecognition(List<TvShow> tvShows) {
        Map<String, String> results = new HashMap<>();
        TvShowAIRecognitionManager aiManager = TvShowAIRecognitionManager.getInstance();

        for (TvShow tvShow : tvShows) {
            try {
                String recognizedTitle = aiManager.getRecognizedTitle(tvShow, null);
                if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
                    results.put(tvShow.getDbId().toString(), recognizedTitle);
                }
            }
            catch (Exception e) {
                LOGGER.warn("Fallback failed for {}: {}", tvShow.getTitle(), e.getMessage());
            }
        }
        return results;
    }

    // 移除废弃的 cache 与 getCacheSize 方法
}
