package org.tinymediamanager.core.movie.services;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.services.utils.AIPromptTemplates;
import org.tinymediamanager.core.movie.services.utils.AIResponseParser;
import org.tinymediamanager.core.movie.services.utils.MoviePathUtils;
import org.tinymediamanager.core.services.AdaptiveBatchProcessor;
import org.tinymediamanager.core.services.AIApiRateLimiter;
import org.tinymediamanager.core.services.RetryUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 批量ChatGPT电影识别服务 支持单次API调用处理多个电影，使用自适应批量大小策略
 */
public class BatchChatGPTMovieRecognitionService {
    private static final Logger          LOGGER                 = LoggerFactory.getLogger(BatchChatGPTMovieRecognitionService.class);
    private static final Duration        TIMEOUT                = Duration.ofSeconds(60);
    private static final ObjectMapper    OBJECT_MAPPER          = new ObjectMapper();

    private HttpClient                   httpClient;
    private final Settings               settings;
    private final AdaptiveBatchProcessor adaptiveBatchProcessor = AdaptiveBatchProcessor.getInstance();

    public BatchChatGPTMovieRecognitionService() {
        this.settings = Settings.getInstance();

        if (LOGGER.isDebugEnabled()) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder("BatchChatGPTMovieRecognitionService created from:\n");
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
     * 识别单个电影标题（使用批量模式，格式 <<1>> 路径） 内部将单个电影转为批量大小为1的调用，复用批量识别的 Prompt 和解析逻辑
     * 
     * @param movie
     *            电影对象
     * @return 识别的标题（格式：标题 年份），如果识别失败返回null
     */
    public String recognizeMovieTitle(Movie movie) {
        if (movie == null) {
            return null;
        }

        if (httpClient == null) {
            LOGGER.warn("HTTP client is not initialized - please check OpenAI API key configuration");
            return null;
        }

        List<Movie> singleBatch = Collections.singletonList(movie);
        Map<String, String> results = batchRecognizeMovieTitles(singleBatch, settings.getAiMaxRetries());

        String result = results.get(movie.getDbId().toString());
        if (result != null) {
            LOGGER.info("Single movie recognition successful: '{}' -> '{}'", movie.getTitle(), result);
        }
        else {
            LOGGER.warn("Single movie recognition failed for: '{}'", movie.getTitle());
        }

        return result;
    }

    /**
     * 批量识别电影标题（使用自适应批量大小）
     */
    public Map<String, String> batchRecognizeMovieTitles(List<Movie> movies) {
        LOGGER.info("Starting batch recognition for {} movies with adaptive batching", movies.size());
        return batchRecognizeMovieTitles(movies, settings.getAiMaxRetries());
    }

    /**
     * 批量识别电影标题（自适应批量大小版本）
     */
    public Map<String, String> batchRecognizeMovieTitles(List<Movie> movies, int maxRetries) {
        Map<String, String> results = new HashMap<>();

        if (movies == null || movies.isEmpty()) {
            return results;
        }

        // 检查API配置状态
        String apiKey = settings.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOGGER.warn("OpenAI API key is not configured");
            return results; // 返回空结果，由上层 MovieAIRecognitionManager 处理
        }

        if (httpClient == null) {
            LOGGER.warn("HTTP client is not initialized");
            return results; // 返回空结果
        }

        // 验证电影列表
        List<Movie> validMovies = new ArrayList<>();
        for (Movie movie : movies) {
            if (movie != null) {
                String cacheKey = MoviePathUtils.generateCacheKey(movie);
                if (cacheKey != null) {
                    validMovies.add(movie);
                }
            }
        }

        if (validMovies.isEmpty()) {
            LOGGER.warn("No valid movies to process");
            return results;
        }

        LOGGER.info("Processing {} valid movies with adaptive batch sizing", validMovies.size());

        // 使用自适应批量大小处理
        int i = 0;
        int totalMovies = validMovies.size();

        while (i < totalMovies) {
            // 使用配置的批量大小
            int batchSize = settings.getAiBatchSize();
            int endIndex = Math.min(i + batchSize, totalMovies);

            if (endIndex <= i)
                break;

            List<Movie> currentBatch = validMovies.subList(i, endIndex);
            int currentActualBatchSize = currentBatch.size();

            LOGGER.info("Processing movie batch ({} movies), progress {}/{}", currentActualBatchSize, endIndex, totalMovies);

            long startTime = System.currentTimeMillis();
            boolean batchSuccess = false;

            try {
                Map<String, String> batchResults = processBatchWithRetry(currentBatch, maxRetries);

                if (!batchResults.isEmpty()) {
                    results.putAll(batchResults);
                    batchSuccess = batchResults.size() == currentActualBatchSize;
                }
            }
            catch (Exception e) {
                batchSuccess = false;
                LOGGER.error("批次处理异常: {}", e.getMessage());
            }
            finally {
                long responseTime = System.currentTimeMillis() - startTime;
                adaptiveBatchProcessor.recordBatchResponse(responseTime, currentActualBatchSize, batchSuccess);
            }

            i += currentActualBatchSize;
        }

        LOGGER.info("Batch recognition completed: {} movies processed, {} successful", validMovies.size(), results.size());
        return results;
    }

    /**
     * 处理单个批次，带重试机制
     */
    private Map<String, String> processBatchWithRetry(List<Movie> batch, int maxRetries) {
        Map<String, String> results = new HashMap<>();

        if (batch == null || batch.isEmpty()) {
            return results;
        }

        // 检查API频率限制
        AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
        if (!rateLimiter.waitForPermission("BatchChatGPTMovieRecognition", 30000)) {
            LOGGER.warn("API call timed out for batch movie recognition after 30 seconds");
            return results;
        }

        boolean isNetworkError = false; // 标记是否为网络错误

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                LOGGER.debug("Attempt {} for batch of {} movies", attempt, batch.size());

                // 构建批量请求
                String batchRequest = buildBatchRequest(batch);
                if (batchRequest == null || batchRequest.trim().isEmpty()) {
                    LOGGER.warn("Failed to build batch request for attempt {}", attempt);
                    if (attempt < maxRetries) {
                        RetryUtils.waitBeforeRetry(attempt, "Build request failed");
                    }
                    continue;
                }

                // 调用API
                String response = callChatGPTBatchAPI(batchRequest);
                if (response != null && !response.trim().isEmpty()) {
                    // 解析批量响应
                    Map<String, String> batchResults = parseBatchResponse(response, batch);

                    if (batchResults == null) {
                        LOGGER.warn("AI returned empty content, retrying...");
                        if (attempt < maxRetries) {
                            RetryUtils.waitBeforeRetry(attempt, "Empty content retry");
                        }
                        continue;
                    }

                    // 验证结果数量
                    if (batchResults.size() == batch.size()) {
                        LOGGER.debug("Batch processed successfully: {} results for {} movies", batchResults.size(), batch.size());
                        return batchResults;
                    }
                    else {
                        LOGGER.warn("Result count mismatch: expected {}, got {}. Retrying...", batch.size(), batchResults.size());
                        if (attempt < maxRetries) {
                            RetryUtils.waitBeforeRetry(attempt, "Result mismatch retry");
                        }
                    }
                }
                else {
                    LOGGER.warn("Empty or null response from API. Retrying...");
                    if (attempt < maxRetries) {
                        RetryUtils.waitBeforeRetry(attempt, "Empty response retry");
                    }
                }
            }
            catch (Exception e) {
                // 检查是否为网络错误（IOException 被包装在 RuntimeException 中）
                Throwable cause = e.getCause();
                boolean isNetworkException = (cause instanceof java.io.IOException) || (e.getMessage() != null && e.getMessage().contains("HTTP"));

                if (isNetworkException) {
                    isNetworkError = true;
                    LOGGER.warn("批量处理尝试 {}/{} 网络错误: {} (将继续重试，不降级)", attempt, maxRetries, e.getMessage());
                }
                else {
                    isNetworkError = false;
                    LOGGER.error("批量处理尝试 {}/{} 异常: {}", attempt, maxRetries, e.getMessage());
                }

                if (attempt < maxRetries) {
                    RetryUtils.waitBeforeRetry(attempt, isNetworkException ? "Network Error" : "Exception retry");
                }
            }
        }

        // 根据错误类型决定是否降级
        if (isNetworkError) {
            // 网络错误导致的失败，不降级，返回空结果
            LOGGER.error("批量处理因网络错误失败，已重试 {} 次，不降级到单个识别", maxRetries);
        }
        else if (Settings.getInstance().isAiIndividualFallbackEnabled()) {
            // 非网络错误，记录失败，由上层 MovieAIRecognitionManager 处理单独识别回退
            LOGGER.warn("批量识别失败，返回空结果（单独识别回退由上层管理）");
        }
        else {
            LOGGER.error("批量处理失败，已重试 {} 次", maxRetries);
        }

        return results;
    }

    /**
     * 构建批量请求内容 使用双尖括号索引格式确保精确匹配
     */
    private String buildBatchRequest(List<Movie> movies) {
        StringBuilder requestBuilder = new StringBuilder();

        for (int i = 0; i < movies.size(); i++) {
            Movie movie = movies.get(i);
            String path = MoviePathUtils.extractMoviePathForAI(movie);
            if (path != null) {
                // 格式: <<序号>> 路径
                requestBuilder.append("<<").append(i + 1).append(">> ").append(path).append("\n");
            }
        }

        if (requestBuilder.length() == 0) {
            return null;
        }

        return requestBuilder.toString();
    }

    /**
     * 调用ChatGPT批量API（带内部重试）
     */
    private String callChatGPTBatchAPI(String batchRequest) {
        try {
            String apiKey = settings.getOpenAiApiKey();
            String apiUrl = settings.getOpenAiApiUrl();
            String model = settings.getOpenAiModel();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                LOGGER.warn("OpenAI API key is not configured");
                return null;
            }

            // 获取批量Prompt模板
            String systemPrompt = AIPromptTemplates.getBatchMovieRecognitionPrompt();

            // 使用 Jackson 构造 JSON 请求体
            ObjectNode requestJson = OBJECT_MAPPER.createObjectNode();
            requestJson.put("model", model);
            requestJson.put("max_tokens", 5000);
            requestJson.put("temperature", 0);

            ArrayNode messages = requestJson.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", batchRequest);

            // 输出AI请求摘要到活动日志（简化格式）
            LOGGER.info("[AI请求] 电影批量识别 | 模型: {} | 数量: {} 部", model, batchRequest.split("\n").length);

            String requestBody = OBJECT_MAPPER.writeValueAsString(requestJson);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(BodyPublishers.ofString(requestBody))
                    .timeout(TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }
            else {
                LOGGER.warn("Batch movie API request failed with status: {}", response.statusCode());
                return null;
            }

        }
        catch (Exception e) {
            LOGGER.warn("Batch movie API call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析批量响应 使用 AIResponseParser 统一解析索引格式
     */
    private Map<String, String> parseBatchResponse(String response, List<Movie> movies) {
        Map<String, String> results = new HashMap<>();

        try {
            String content = AIResponseParser.extractContentFromResponse(response);
            if (content == null || content.trim().isEmpty()) {
                LOGGER.warn("Empty content in API response - needs retry");
                return null;
            }

            // 输出原始 content 到活动日志
            LOGGER.info("[AI响应] 电影批量识别 | 原始content:\n{}", content);

            // 建立索引到电影的映射（索引从1开始）
            Map<Integer, Movie> indexToMovie = new HashMap<>();
            for (int i = 0; i < movies.size(); i++) {
                indexToMovie.put(i + 1, movies.get(i));
            }

            // 使用统一的索引解析方法
            Map<Integer, String> parsedResults = AIResponseParser.parseIndexedResponse(content);

            for (Map.Entry<Integer, String> entry : parsedResults.entrySet()) {
                int index = entry.getKey();
                String recognizedTitle = entry.getValue();

                Movie movie = indexToMovie.get(index);
                if (movie != null && !recognizedTitle.isEmpty() && !recognizedTitle.startsWith("未知电影")) {
                    results.put(movie.getDbId().toString(), recognizedTitle);
                    LOGGER.debug("Matched [<<{}>>] -> {}", index, recognizedTitle);
                }
            }

            LOGGER.debug("Parsed {} results from response, expected {} movies", results.size(), movies.size());
        }
        catch (Exception e) {
            LOGGER.error("Failed to parse batch response: {}", e.getMessage());
        }

        return results;
    }
}