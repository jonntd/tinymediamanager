package org.tinymediamanager.core.tvshow.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.services.AIApiRateLimiter;
import org.tinymediamanager.core.services.AdaptiveBatchProcessor;
import org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.EpisodeMatchingResult;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量剧集AI识别服务
 * 真正的批量处理，避免一条一条调用AI
 */
public class BatchChatGPTEpisodeRecognitionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchChatGPTEpisodeRecognitionService.class);
    
    private static final Pattern SEASON_EPISODE_PATTERN = Pattern.compile("(\\d+)\\s+(\\d+)");
    private final HttpClient httpClient;
    
    public BatchChatGPTEpisodeRecognitionService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * 批量识别剧集信息
     */
    public Map<String, EpisodeMatchingResult> batchRecognizeEpisodes(List<TvShowEpisode> episodes) {
        Map<String, EpisodeMatchingResult> results = new HashMap<>();
        
        if (episodes == null || episodes.isEmpty()) {
            return results;
        }
        
        LOGGER.info("Starting batch episode recognition for {} episodes", episodes.size());
        
        // 分批处理，每批10个剧集
        int batchSize = 10;
        int totalBatches = (int) Math.ceil((double) episodes.size() / batchSize);
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, episodes.size());
            List<TvShowEpisode> currentBatch = episodes.subList(startIndex, endIndex);
            
            LOGGER.info("Processing episode batch {}/{} ({} episodes)", 
                       batchIndex + 1, totalBatches, currentBatch.size());
            
            Map<String, EpisodeMatchingResult> batchResults = processBatch(currentBatch);
            results.putAll(batchResults);
            
            // 添加小延迟避免API限制
            if (batchIndex < totalBatches - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Batch processing interrupted");
                    break;
                }
            }
        }
        
        LOGGER.info("Batch episode recognition completed: {} episodes processed, {} successful", 
                   episodes.size(), results.size());
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
            
        } catch (Exception e) {
            LOGGER.error("Batch episode recognition failed: {}", e.getMessage());
        }
        
        return results;
    }
    
    /**
     * 构建批量请求
     */
    private String buildBatchRequest(List<TvShowEpisode> episodes) {
        StringBuilder request = new StringBuilder();
        request.append("请识别以下剧集文件的季数和集数，每行一个结果，格式：季数 集数\n\n");
        
        for (int i = 0; i < episodes.size(); i++) {
            TvShowEpisode episode = episodes.get(i);
            String filename = episode.getMainFile() != null ? episode.getMainFile().getFilename() : episode.getTitle();
            String tvShowTitle = episode.getTvShow() != null ? episode.getTvShow().getTitle() : "";
            
            request.append(String.format("%d. 剧集文件: %s\n", i + 1, filename));
            if (!tvShowTitle.isEmpty()) {
                request.append(String.format("   电视剧: %s\n", tvShowTitle));
            }
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
            try {
                LOGGER.debug("Batch episode API call attempt {}/{}", attempt, maxRetries);

                // 检查API频率限制并记录统计
                AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
                if (!rateLimiter.requestPermission("BatchChatGPTEpisodeRecognition")) {
                    LOGGER.warn("API rate limit exceeded for batch episode recognition on attempt {}/{}", attempt, maxRetries);
                    throw new RuntimeException("API rate limit exceeded");
                }
                Settings settings = Settings.getInstance();
                String apiKey = settings.getOpenAiApiKey();
                String apiUrl = settings.getOpenAiApiUrl();
                String model = settings.getOpenAiModel();

                if (apiKey == null || apiKey.trim().isEmpty()) {
                    LOGGER.warn("OpenAI API key not configured");
                    throw new RuntimeException("OpenAI API key not configured");
                }
            
            String systemPrompt = "你是一个专业的剧集识别专家。根据提供的剧集文件信息，联网搜索并识别出正确的季数和集数。\n" +
                                 "请严格按照以下格式输出，每行一个结果，绝对不要返回任何解释或错误信息：\n" +
                                 "季数 集数\n" +
                                 "例如：\n" +
                                 "1 5\n" +
                                 "2 10\n" +
                                 "如果搜索失败，请输出：0 0\n" +
                                 "禁止返回'I am unable to'或任何错误说明";
            
            String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 1000, \"temperature\": 0.1}",
                model,
                systemPrompt.replace("\"", "\\\"").replace("\n", "\\n"),
                batchRequest.replace("\"", "\\\"").replace("\n", "\\n")
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    LOGGER.debug("Batch API response: {}", responseBody);

                    // 解析响应
                    int contentStart = responseBody.indexOf("\"content\":\"");
                    if (contentStart != -1) {
                        contentStart += "\"content\":\"".length();
                        int contentEnd = responseBody.indexOf('"', contentStart);
                        if (contentEnd != -1) {
                            String result = responseBody.substring(contentStart, contentEnd)
                                .replace("\\\"", "\"")
                                .replace("\\n", "\n")
                                .trim();

                            if (result != null && !result.isEmpty()) {
                                LOGGER.info("Batch episode API successful on attempt {}", attempt);
                                return result;
                            } else {
                                LOGGER.warn("Batch API returned empty result on attempt {}/{}", attempt, maxRetries);
                            }
                        }
                    }
                } else {
                    LOGGER.warn("Batch API request failed with status: {} on attempt {}/{}", response.statusCode(), attempt, maxRetries);
                }

                if (attempt < maxRetries) {
                    // 指数退避重试
                    long delayMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...
                    LOGGER.info("Retrying batch episode API after {}ms delay", delayMs);
                    Thread.sleep(delayMs);
                }

            } catch (Exception e) {
                lastException = e;
                LOGGER.warn("Batch episode API failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    // 指数退避重试
                    long delayMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...
                    try {
                        LOGGER.info("Retrying batch episode API after {}ms delay", delayMs);
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("Batch episode API retry delay interrupted");
                        break;
                    }
                }
            }
        }

        // 所有重试都失败
        LOGGER.error("Batch episode API failed after {} attempts", maxRetries, lastException);
        return null;
    }
    
    /**
     * 解析批量响应
     */
    private Map<String, EpisodeMatchingResult> parseBatchResponse(String response, List<TvShowEpisode> episodes) {
        Map<String, EpisodeMatchingResult> results = new HashMap<>();
        
        String[] lines = response.split("\n");
        
        for (int i = 0; i < Math.min(lines.length, episodes.size()); i++) {
            String line = lines[i].trim();
            TvShowEpisode episode = episodes.get(i);
            
            Matcher matcher = SEASON_EPISODE_PATTERN.matcher(line);
            if (matcher.find()) {
                try {
                    int season = Integer.parseInt(matcher.group(1));
                    int episodeNum = Integer.parseInt(matcher.group(2));
                    
                    if (season > 0 && episodeNum > 0) {
                        EpisodeMatchingResult result = new EpisodeMatchingResult();
                        result.season = season;
                        result.episodes.add(episodeNum);
                        
                        results.put(episode.getDbId().toString(), result);
                        LOGGER.debug("Parsed episode {}: S{}E{}", i + 1, season, episodeNum);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("Failed to parse episode {}: {}", i + 1, line);
                }
            }
        }
        
        return results;
    }
}
