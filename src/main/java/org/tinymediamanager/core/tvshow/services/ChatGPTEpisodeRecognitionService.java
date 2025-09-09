package org.tinymediamanager.core.tvshow.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.services.AIApiRateLimiter;
import org.tinymediamanager.core.services.AIPerformanceMonitor;
import org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.EpisodeMatchingResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 电视剧剧集AI识别服务
 * 用于识别复杂或非标准命名的剧集文件，提取季数和集数信息
 * 
 * @author AI Assistant
 */
public class ChatGPTEpisodeRecognitionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGPTEpisodeRecognitionService.class);
    
    // 季数集数提取的正则表达式
    private static final Pattern SEASON_EPISODE_PATTERN = Pattern.compile("(\\d{1,3})\\s+(\\d{1,4})");
    
    /**
     * 使用AI识别剧集文件名，提取季数和集数（带重试机制）
     *
     * @param episodeFilename 剧集文件名
     * @param tvShowTitle 电视剧标题（用于上下文）
     * @return EpisodeMatchingResult 包含识别结果
     */
    public static EpisodeMatchingResult recognizeEpisode(String episodeFilename, String tvShowTitle) {
        return recognizeEpisodeWithRetry(episodeFilename, tvShowTitle, 3);
    }

    /**
     * 带重试机制的剧集识别
     */
    private static EpisodeMatchingResult recognizeEpisodeWithRetry(String episodeFilename, String tvShowTitle, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long startTime = System.currentTimeMillis();

            try {
                LOGGER.info("=== Starting Episode AI Recognition (Attempt {}/{}) ===", attempt, maxRetries);
                LOGGER.info("Episode filename: {}", episodeFilename);
                LOGGER.info("TV Show title: {}", tvShowTitle);

                // 检查API频率限制并记录统计
                AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
                if (!rateLimiter.requestPermission("ChatGPTEpisodeRecognition")) {
                    LOGGER.warn("API rate limit exceeded for episode recognition on attempt {}/{}", attempt, maxRetries);
                    throw new RuntimeException("API rate limit exceeded");
                }

                // 处理路径，提取倒数三层目录信息（参考电影AI识别）
                String pathContext = extractPathContext(episodeFilename);
                LOGGER.info("=== Path Processing ===");
                LOGGER.info("Full episode path: {}", episodeFilename);
                LOGGER.info("Extracted directory context: {}", pathContext);

                // 调用AI API识别剧集信息，使用路径上下文
                String recognizedInfo = callChatGPTAPI(pathContext, tvShowTitle);

                if (recognizedInfo != null && !recognizedInfo.trim().isEmpty()) {
                    // 解析AI返回的季数和集数
                    EpisodeMatchingResult result = parseAIResponse(recognizedInfo);

                    // 验证结果是否有效
                    if (result != null && (result.season > 0 || !result.episodes.isEmpty())) {
                        long responseTime = System.currentTimeMillis() - startTime;
                        // 记录成功的性能指标
                        AIPerformanceMonitor.getInstance().recordAPICall("ChatGPTEpisodeRecognition", responseTime, true);
                        LOGGER.info("=== Episode AI Recognition Complete (Attempt {}, {}ms) ===", attempt, responseTime);
                        LOGGER.info("Raw AI response: '{}'", recognizedInfo);
                        LOGGER.info("Parsed result - Season: {}, Episodes: {}", result.season, result.episodes);
                        return result;
                    } else {
                        long responseTime = System.currentTimeMillis() - startTime;
                        // 记录失败的性能指标
                        AIPerformanceMonitor.getInstance().recordAPICall("ChatGPTEpisodeRecognition", responseTime, false);
                        LOGGER.warn("AI returned invalid result on attempt {}/{} ({}ms)", attempt, maxRetries, responseTime);
                    }
                } else {
                    long responseTime = System.currentTimeMillis() - startTime;
                    // 记录失败的性能指标
                    AIPerformanceMonitor.getInstance().recordAPICall("ChatGPTEpisodeRecognition", responseTime, false);
                    LOGGER.warn("AI returned empty result on attempt {}/{} ({}ms)", attempt, maxRetries, responseTime);
                }

                if (attempt < maxRetries) {
                    // 指数退避重试
                    long delayMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...
                    LOGGER.info("Retrying episode recognition after {}ms delay", delayMs);
                    Thread.sleep(delayMs);
                }

            } catch (Exception e) {
                lastException = e;
                LOGGER.warn("Episode AI recognition failed on attempt {}/{}: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    // 指数退避重试
                    long delayMs = 1000L * (1L << (attempt - 1)); // 1s, 2s, 4s...
                    try {
                        LOGGER.info("Retrying episode recognition after {}ms delay", delayMs);
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.warn("Episode recognition retry delay interrupted");
                        break;
                    }
                }
            }
        }

        // 所有重试都失败
        LOGGER.error("Episode AI recognition failed after {} attempts", maxRetries, lastException);
        EpisodeMatchingResult emptyResult = new EpisodeMatchingResult();
        LOGGER.warn("Episode AI recognition failed, returning empty result");
        return emptyResult;
    }
    
    /**
     * 调用ChatGPT API进行剧集识别
     */
    private static String callChatGPTAPI(String episodeFilename, String tvShowTitle) throws IOException, InterruptedException {
        String apiUrl = Settings.getInstance().getOpenAiApiUrl();
        String apiKey = Settings.getInstance().getOpenAiApiKey();
        String model = Settings.getInstance().getOpenAiModel();
        
        if (apiUrl == null || apiUrl.trim().isEmpty() || 
            apiKey == null || apiKey.trim().isEmpty()) {
            LOGGER.warn("ChatGPT API configuration is missing");
            return null;
        }
        
        String systemPrompt = getEpisodeRecognitionPrompt();
        String userPrompt = String.format("电视剧: %s\n剧集文件: %s", tvShowTitle, episodeFilename);
        
        LOGGER.info("=== Episode AI Recognition Debug ===");
        LOGGER.info("Input episode filename: {}", episodeFilename);
        LOGGER.info("Input TV show title: {}", tvShowTitle);
        LOGGER.info("API URL: {}", apiUrl);
        LOGGER.info("Model: {}", model);
        LOGGER.info("System prompt length: {} characters", systemPrompt.length());
        
        String requestBody = String.format(
            "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 500, \"temperature\": 0.3}",
            model,
            systemPrompt.replace("\"", "\\\"").replace("\n", "\\n"),
            userPrompt.replace("\"", "\\\"").replace("\n", "\\n")
        );
        
        LOGGER.info("API request body: {}", requestBody);
        
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
            
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
            
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            String responseBody = response.body();
            LOGGER.info("=== AI API Response ===");
            LOGGER.info("Response status: {}", response.statusCode());
            LOGGER.info("Response body: {}", responseBody);
            
            // 解析JSON响应
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(responseBody);
            JsonNode choicesNode = rootNode.path("choices");
            
            if (choicesNode.isArray() && choicesNode.size() > 0) {
                JsonNode messageNode = choicesNode.get(0).path("message");
                String content = messageNode.path("content").asText();
                
                LOGGER.info("=== AI Recognition Result ===");
                LOGGER.info("Raw extracted content: '{}'", content);
                LOGGER.info("Content length: {} characters", content.length());

                // 解析并记录识别结果的详细信息
                EpisodeMatchingResult parseResult = parseAIResponse(content);
                if (parseResult != null && parseResult.season != -1 && !parseResult.episodes.isEmpty()) {
                    LOGGER.info("Parsed result - Season: {}, Episode: {}", parseResult.season, parseResult.episodes.get(0));
                    LOGGER.info("Content type: Season/Episode format");
                } else {
                    LOGGER.warn("Failed to parse AI response into valid season/episode numbers");
                    LOGGER.info("Content type: Invalid or unparseable format");
                }

                return content;
            }
        } else {
            LOGGER.error("ChatGPT API error: {} - {}", response.statusCode(), response.body());
        }
        
        return null;
    }
    
    /**
     * 获取剧集识别的系统提示词
     */
    private static String getEpisodeRecognitionPrompt() {
        return "你是一个专业的电视剧剧集文件名解析引擎。你的任务是从复杂的剧集文件名中准确提取季数和集数信息，必要时可以联网搜索确认。\n\n" +
               "**输入：**\n" +
               "电视剧标题和剧集文件名。\n\n" +
               "**输出：**\n" +
               "严格按照 `季数 集数` 格式输出，用空格分隔，绝对不要返回任何解释或错误信息。如果是特别篇或OVA，季数使用0。\n\n" +
               "**处理规则：**\n" +
               "1. 优先识别明确的季数集数标记（S01E01、第一季第01集等）\n" +
               "2. 对于动漫，识别\"第X话\"、\"第X集\"等格式\n" +
               "3. 特别篇、OVA、SP等归为第0季\n" +
               "4. 如果只有集数没有季数，默认为第1季\n" +
               "5. 如果完全无法识别，输出 \"1 1\"\n" +
               "6. 禁止返回'I am unable to'或任何错误说明\n\n" +
               "**输出示例：**\n" +
               "1 5\n" +
               "2 12\n" +
               "0 1\n" +
               "3 8\n\n" +
               "**错误示例（不要这样输出）：**\n" +
               "❌ 第1季第5集\n" +
               "❌ Season 1 Episode 5\n" +
               "❌ 1-5\n" +
               "❌ 任何解释性文字\n" +
               "❌ I am unable to...\n\n";
    }
    
    /**
     * 解析AI返回的季数集数信息
     */
    private static EpisodeMatchingResult parseAIResponse(String aiResponse) {
        EpisodeMatchingResult result = new EpisodeMatchingResult();
        
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return result;
        }
        
        String cleaned = aiResponse.trim();
        LOGGER.debug("Parsing AI response: '{}'", cleaned);
        
        // 尝试匹配 "季数 集数" 格式
        Matcher matcher = SEASON_EPISODE_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            try {
                int season = Integer.parseInt(matcher.group(1));
                int episode = Integer.parseInt(matcher.group(2));
                
                result.season = season;
                result.episodes.add(episode);
                
                LOGGER.debug("Successfully parsed - Season: {}, Episode: {}", season, episode);
                return result;
            } catch (NumberFormatException e) {
                LOGGER.warn("Failed to parse numbers from AI response: {}", cleaned);
            }
        }
        
        LOGGER.warn("Could not parse AI response: {}", cleaned);
        return result;
    }

    /**
     * 提取路径上下文信息（参考电影AI识别的路径处理）
     * 提取倒数三层目录信息，为AI提供更好的上下文
     */
    private static String extractPathContext(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            return fullPath;
        }

        try {
            // 规范化路径分隔符
            String normalizedPath = fullPath.replace('\\', '/');

            // 分割路径
            String[] pathParts = normalizedPath.split("/");

            // 提取倒数三层（包括文件名）
            int startIndex = Math.max(0, pathParts.length - 3);
            StringBuilder contextBuilder = new StringBuilder();

            for (int i = startIndex; i < pathParts.length; i++) {
                if (contextBuilder.length() > 0) {
                    contextBuilder.append("/");
                }
                contextBuilder.append(pathParts[i]);
            }

            String context = contextBuilder.toString();
            LOGGER.debug("Path context extraction: '{}' -> '{}'", fullPath, context);
            return context;

        } catch (Exception e) {
            LOGGER.warn("Failed to extract path context from: {}, using original path", fullPath);
            return fullPath;
        }
    }
}
