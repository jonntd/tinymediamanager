package org.tinymediamanager.core.tvshow.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.services.AIApiRateLimiter;
import org.tinymediamanager.core.services.AdaptiveBatchProcessor;
import org.tinymediamanager.core.services.RetryUtils;
import org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.EpisodeMatchingResult;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.services.utils.TvShowAIPromptTemplates;
import org.tinymediamanager.core.tvshow.services.utils.TvShowAIResponseParser;
import org.tinymediamanager.core.tvshow.services.utils.TvShowPathUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量剧集AI识别服务 真正的批量处理，使用自适应批量大小策略
 */
public class BatchChatGPTEpisodeRecognitionService {
    private static final Logger          LOGGER                 = LoggerFactory.getLogger(BatchChatGPTEpisodeRecognitionService.class);
    private static final Duration        TIMEOUT                = Duration.ofSeconds(60);
    private static final ObjectMapper    OBJECT_MAPPER          = new ObjectMapper();

    private final HttpClient             httpClient;
    private final AdaptiveBatchProcessor adaptiveBatchProcessor = AdaptiveBatchProcessor.getInstance();

    // 双尖括号索引匹配正则：<<数字>> 内容
    private static final Pattern         INDEXED_RESULT_PATTERN = Pattern.compile("^<<(\\d+)>>\\s*(.+)$");

    public BatchChatGPTEpisodeRecognitionService() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * 识别单个剧集的季数和集数（使用批量模式） 内部将单个剧集转为批量大小为1的调用，复用批量识别的 Prompt 和解析逻辑
     * 
     * @param episode
     *            剧集对象
     * @return 识别结果，如果识别失败返回空的 EpisodeMatchingResult
     */
    public EpisodeMatchingResult recognizeEpisode(TvShowEpisode episode) {
        if (episode == null) {
            return new EpisodeMatchingResult();
        }

        List<TvShowEpisode> singleBatch = Collections.singletonList(episode);
        Map<String, EpisodeMatchingResult> results = batchRecognizeEpisodes(singleBatch);

        EpisodeMatchingResult result = results.get(episode.getDbId().toString());
        if (result != null && (result.season > 0 || !result.episodes.isEmpty())) {
            LOGGER.info("Single episode recognition successful: S{} E{}", result.season, result.episodes);
            return result;
        }
        else {
            LOGGER.warn("Single episode recognition failed for: '{}'", episode.getTitle());
            return new EpisodeMatchingResult();
        }
    }

    /**
     * 批量识别剧集信息
     */
    public Map<String, EpisodeMatchingResult> batchRecognizeEpisodes(List<TvShowEpisode> episodes) {
        Map<String, EpisodeMatchingResult> results = new HashMap<>();

        if (episodes == null || episodes.isEmpty()) {
            return results;
        }

        LOGGER.info("Starting batch episode recognition for {} episodes with adaptive batching", episodes.size());

        int i = 0;
        int totalEpisodes = episodes.size();

        while (i < totalEpisodes) {
            // 获取当前建议的批量大小
            int batchSize = adaptiveBatchProcessor.getCurrentBatchSize();
            int endIndex = Math.min(i + batchSize, totalEpisodes);

            // 确保不越界，且至少处理一个（虽然逻辑上 batchSize >= 3）
            if (endIndex <= i)
                break;

            List<TvShowEpisode> currentBatch = episodes.subList(i, endIndex);
            int currentActualBatchSize = currentBatch.size();

            LOGGER.info("Processing episode batch ({} episodes), progress {}/{}", currentActualBatchSize, endIndex, totalEpisodes);

            long startTime = System.currentTimeMillis();
            boolean batchSuccess = false;

            try {
                Map<String, EpisodeMatchingResult> batchResults = processBatch(currentBatch);

                if (!batchResults.isEmpty()) {
                    results.putAll(batchResults);
                    batchSuccess = true;
                }
                else {
                    // 如果结果为空，可能是解析失败也可能是API失败，processBatch内部会记录日志
                    // 但这里我们主要关心API是否连通
                    // 如果processBatch返回空map但没有抛出异常，通常意味着API调用成功但没解析出结果，或者是失败了
                    // 只有在明确知道是API失败时才记录为失败
                    // 为了简单起见，如果拿到结果就算成功，否则视为（部分）失败?
                    // 实际上 processBatch 已经处理了重试。
                    // 我们可以认为只要不抛出异常，就算在"网络层"是成功的，具体的解析质量由 AdaptiveBatchProcessor 的调用方决定是否算success?
                    // 查看 AdaptiveBatchProcessor 逻辑，如果 !success 就会减小 batch size
                    // 所以如果没有解析出任何结果，减小 batch size 是合理的 (可能是太长了导致AI乱了)
                    batchSuccess = !batchResults.isEmpty();
                }
            }
            catch (Exception e) {
                batchSuccess = false;
                LOGGER.error("Batch processing exception: {}", e.getMessage());
            }
            finally {
                long responseTime = System.currentTimeMillis() - startTime;
                adaptiveBatchProcessor.recordBatchResponse(responseTime, currentActualBatchSize, batchSuccess);
            }

            i += currentActualBatchSize;

            // 简单的防速率限制延迟 (如果不需要严格的 Sleep, RateLimiter 会处理)
            // 但为了给其他任务喘息机会，还是可以 sleep 一下，或者移除 explicit sleep 依赖 RateLimiter
            // BatchChatGPTTvShowRecognitionService 移除了 explicit sleep 依赖 RateLimiter?
            // 检查 BatchChatGPTTvShowRecognitionService (Step 204)，它没有 explicit sleep loop.
            // 之前的 `BatchChatGPTEpisodeRecognitionService` had existing sleep.
            // 建议移除 explicit sleep，让 RateLimiter 控制。
        }

        LOGGER.info("Batch episode recognition completed: {} episodes processed, {} successful", episodes.size(), results.size());
        return results;
    }

    /**
     * 处理单个批次
     */
    private Map<String, EpisodeMatchingResult> processBatch(List<TvShowEpisode> batch) {
        Map<String, EpisodeMatchingResult> results = new HashMap<>();

        try {
            // 构建批量请求
            String batchRequest = buildBatchRequest(batch);
            if (batchRequest == null || batchRequest.trim().isEmpty()) {
                LOGGER.warn("Failed to build batch request");
                return results;
            }

            // 调用AI API
            String response = callBatchAPI(batchRequest);
            if (response != null && !response.trim().isEmpty()) {
                // 解析批量响应
                results = parseBatchResponse(response, batch);
            }

        }
        catch (Exception e) {
            LOGGER.error("Batch episode recognition failed inside processBatch: {}", e.getMessage());
            throw e; // 抛出异常以便外层捕获并记录为失败
        }

        return results;
    }

    /**
     * 构建批量请求 使用双尖括号索引格式确保精确匹配
     */
    private String buildBatchRequest(List<TvShowEpisode> episodes) {
        StringBuilder request = new StringBuilder();

        for (int i = 0; i < episodes.size(); i++) {
            TvShowEpisode episode = episodes.get(i);

            // 使用倒数三层路径而非仅文件名，提供更多上下文
            String episodePath = "";
            if (episode.getMainFile() != null && episode.getMainFile().getFileAsPath() != null) {
                episodePath = TvShowPathUtils.extractLastThreeDirectoryNames(episode.getMainFile().getFileAsPath().toString());
            }
            if (episodePath.isEmpty()) {
                episodePath = episode.getMainFile() != null ? episode.getMainFile().getFilename() : episode.getTitle();
            }

            String tvShowTitle = episode.getTvShow() != null ? episode.getTvShow().getTitle() : "";

            // 格式: <<序号>> 剧集文件: xxx 电视剧: xxx
            request.append("<<").append(i + 1).append(">> ");
            request.append("剧集文件: ").append(episodePath);
            if (!tvShowTitle.isEmpty()) {
                request.append(" 电视剧: ").append(tvShowTitle);
            }
            request.append("\n");
        }

        return request.toString();
    }

    /**
     * 调用批量API（带重试机制）
     */
    private String callBatchAPI(String batchRequest) {
        return callBatchAPIWithRetry(batchRequest, 3);
    }

    /**
     * 带重试机制的批量API调用
     */
    private String callBatchAPIWithRetry(String batchRequest, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long startTime = System.currentTimeMillis();
            try {
                LOGGER.debug("Batch episode API call attempt {}/{}", attempt, maxRetries);

                // 检查API频率限制
                AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
                if (!rateLimiter.waitForPermission("BatchChatGPTEpisodeRecognition", 30000)) {
                    LOGGER.warn("API call timed out for batch episode recognition");
                    throw new RuntimeException("API rate limit exceeded");
                }

                Settings settings = Settings.getInstance();
                String apiKey = settings.getOpenAiApiKey();
                String apiUrl = settings.getOpenAiApiUrl();
                String model = settings.getOpenAiModel();

                if (apiKey == null || apiKey.trim().isEmpty()) {
                    LOGGER.warn("OpenAI API key not configured");
                    return null;
                }

                String systemPrompt = TvShowAIPromptTemplates.getBatchEpisodeRecognitionPrompt();

                ObjectNode requestJson = OBJECT_MAPPER.createObjectNode();
                requestJson.put("model", model);
                requestJson.put("max_tokens", 5000);
                requestJson.put("temperature", 0);

                ArrayNode messages = requestJson.putArray("messages");
                messages.addObject().put("role", "system").put("content", systemPrompt);
                messages.addObject().put("role", "user").put("content", batchRequest);

                // 输出AI请求摘要到活动日志
                int episodeCount = batchRequest.split("\n").length;
                LOGGER.info("[AI请求] 剧集批量识别 | 模型: {} | 数量: {} 集", model, episodeCount);

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
                    String content = TvShowAIResponseParser.extractContentFromResponse(response.body());
                    if (content != null && !content.isEmpty()) {
                        long responseTime = System.currentTimeMillis() - startTime;
                        LOGGER.info("[AI响应] 剧集批量识别 | 耗时: {}ms | 原始content:\n{}", responseTime, content);
                        return content;
                    }
                    else {
                        LOGGER.warn("Batch episode API returned empty content on attempt {}", attempt);
                        if (attempt < maxRetries) {
                            RetryUtils.waitBeforeRetry(attempt, "Empty content");
                        }
                    }
                }
                else {
                    LOGGER.warn("Batch API request failed with status: {} on attempt {}/{}", response.statusCode(), attempt, maxRetries);
                    if (attempt < maxRetries) {
                        RetryUtils.waitBeforeRetry(attempt, "Status code error");
                    }
                }

            }
            catch (Exception e) {
                lastException = e;
                LOGGER.warn("Batch episode API failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    RetryUtils.waitBeforeRetry(attempt, "Exception retry");
                }
            }
        }

        LOGGER.error("Batch episode API failed after {} attempts", maxRetries, lastException);
        throw new RuntimeException("Batch API failed after retries", lastException);
    }

    /**
     * 解析批量响应 使用索引匹配而非顺序匹配，提高容错性
     */
    private Map<String, EpisodeMatchingResult> parseBatchResponse(String response, List<TvShowEpisode> episodes) {
        Map<String, EpisodeMatchingResult> results = new HashMap<>();

        if (response == null || response.trim().isEmpty()) {
            return results;
        }

        // 建立索引到剧集的映射（索引从1开始）
        Map<Integer, TvShowEpisode> indexToEpisode = new HashMap<>();
        for (int i = 0; i < episodes.size(); i++) {
            indexToEpisode.put(i + 1, episodes.get(i));
        }

        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("```"))
                continue;

            // 使用正则匹配双尖括号索引格式: <<数字>> 内容
            Matcher matcher = INDEXED_RESULT_PATTERN.matcher(line);
            if (matcher.matches()) {
                try {
                    int index = Integer.parseInt(matcher.group(1));
                    String content = matcher.group(2).trim();

                    TvShowEpisode episode = indexToEpisode.get(index);
                    if (episode != null) {
                        EpisodeMatchingResult result = TvShowAIResponseParser.parseEpisodeInfo(content);
                        if (result != null && result.season > -1 && !result.episodes.isEmpty()) {
                            if (!(result.season == 0 && result.episodes.size() == 1 && result.episodes.contains(0))) {
                                results.put(episode.getDbId().toString(), result);
                                LOGGER.debug("Matched [<<{}>>] -> S{} E{}", index, result.season, result.episodes);
                            }
                        }
                    }
                }
                catch (NumberFormatException e) {
                    LOGGER.warn("Invalid index in line: {}", line);
                }
            }
            else {
                LOGGER.debug("Line does not match indexed format: {}", line);
            }
        }

        return results;
    }
}
