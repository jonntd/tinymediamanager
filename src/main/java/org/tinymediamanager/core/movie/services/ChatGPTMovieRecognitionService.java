package org.tinymediamanager.core.movie.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.scraper.util.ParserUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ChatGPT电影识别服务
 * 基于路径倒数三层目录名称使用ChatGPT识别电影名
 */
public class ChatGPTMovieRecognitionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGPTMovieRecognitionService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private HttpClient httpClient;
    private final Settings settings;
    
    public ChatGPTMovieRecognitionService() {
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
     * 基于电影文件路径识别电影标题
     * 使用路径倒数三层目录名称作为识别依据
     */
    public String recognizeMovieTitle(Movie movie) {
        if (httpClient == null) {
            LOGGER.warn("HTTP client is not initialized - please check OpenAI API key configuration");
            return null;
        }
        
        try {
            // 获取电影的主要媒体文件路径
            String moviePath = extractMoviePath(movie);
            if (moviePath == null || moviePath.trim().isEmpty()) {
                LOGGER.warn("No valid path found for movie: {}", movie.getTitle());
                return null;
            }
            
            // 提取路径倒数三层目录名称（按主人要求）
            String pathContext = extractLastThreeDirectoryNames(moviePath);
            if (pathContext == null || pathContext.trim().isEmpty()) {
                LOGGER.warn("Cannot extract directory names from path: {}", moviePath);
                return null;
            }

            LOGGER.info("=== Path Processing ===");
            LOGGER.info("Full movie path: {}", moviePath);
            LOGGER.info("Extracted directory context: {}", pathContext);

            // 调用ChatGPT API，传递倒数三层目录信息
            String recognizedTitle = callChatGPTAPI(pathContext);

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
            LOGGER.error("ChatGPT movie recognition failed: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 从电影对象中提取文件路径
     */
    private String extractMoviePath(Movie movie) {
        // 优先使用主要视频文件路径，但需要检查是否是有效的媒体文件（不是EMPTY_MEDIAFILE）
        org.tinymediamanager.core.entities.MediaFile mainFile = movie.getMainFile();
        if (mainFile != null && mainFile != org.tinymediamanager.core.entities.MediaFile.EMPTY_MEDIAFILE) {
            String mainFilePath = mainFile.getFileAsPath().toString();
            if (mainFilePath != null && !mainFilePath.trim().isEmpty()) {
                return mainFilePath;
            }
        }
        
        // 如果没有有效的主要文件，尝试使用第一个有效的媒体文件
        List<org.tinymediamanager.core.entities.MediaFile> mediaFiles = movie.getMediaFiles();
        for (org.tinymediamanager.core.entities.MediaFile mediaFile : mediaFiles) {
            if (mediaFile != org.tinymediamanager.core.entities.MediaFile.EMPTY_MEDIAFILE) {
                String mediaFilePath = mediaFile.getFileAsPath().toString();
                if (mediaFilePath != null && !mediaFilePath.trim().isEmpty()) {
                    return mediaFilePath;
                }
            }
        }
        
        return null;
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
    private String callChatGPTAPI(String moviePath) {
        try {
            String apiKey = settings.getOpenAiApiKey();
            String apiUrl = settings.getOpenAiApiUrl();
            String model = settings.getOpenAiModel();
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                LOGGER.warn("OpenAI API key is not configured");
                return null;
            }
            
            // 构建请求JSON - 使用电影专用提示词（保留主人的联网搜索功能）
            String systemPrompt = settings.getOpenAiExtractionPrompt();
            if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
                // 电影专用的默认提示词 - 单个识别，保留完整的联网搜索功能
                systemPrompt = "你是一个专业的媒体元数据查询引擎。你的唯一任务是**通过联网搜索**，为文件路径找到其最准确的官方信息，然后严格按照 `标题 年份` 格式输出结果。\n\n" +
                              "**输入：**\n一个电影文件路径。\n\n" +
                              "**输出：**\n一个匹配后的字符串。**除 `标题 年份` 格式的字符串外，不要输出任何其他内容。**\n\n" +
                              "---\n\n" +
                              "**处理流程：**\n\n" +
                              "**步骤 1：解析文件路径**\n" +
                              "*   从文件路径中提取出干净的标题和年份，移除所有技术规格和发布组等无关信息。\n" +
                              "    *   例如，从 `The.Shawshank.Redemption.1994.iNTERNAL.1080p.BluRay.x264-MANiC` 中提取出 `The Shawshank Redemption` 和 `1994`。\n\n" +
                              "**步骤 2：强制联网搜索**\n" +
                              "*   **这是最关键的一步，必须执行。**\n" +
                              "*   **如果文件路径包含 `{tmdb-数字}`**，请直接使用该ID在 The Movie Database (TMDB) 上查询。这是最高优先级。\n" +
                              "*   **否则，** 使用你的**搜索工具**，以\"`解析出的标题 年份 TMDB`\"为关键词进行搜索，找到最匹配的 TMDB 或豆瓣页面。\n\n" +
                              "**步骤 3：提取与格式化输出**\n" +
                              "*   从搜索结果中，获取该影视作品的**官方标题**和**官方发行年份**。\n" +
                              "*   **中文优先：** 必须优先使用官方的中文标题。如果TMDB没有中文标题，才使用其英文标题。\n" +
                              "*   将获取到的官方标题和官方年份组合成 **`标题 年份`** 的格式并输出。\n" +
                              "*   **如果搜索失败**或无法找到任何确切的匹配项，则直接返回**原始文件名**作为该行的结果。\n\n" +
                              "---";
            }
            
            LOGGER.info("=== Movie AI Recognition Debug ===");
            LOGGER.info("Input movie path: {}", moviePath);
            LOGGER.info("API URL: {}", apiUrl);
            LOGGER.info("Model: {}", model);
            LOGGER.info("System prompt length: {} characters", systemPrompt.length());

            String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 500, \"temperature\": 0.3}",
                model,
                systemPrompt.replace("\"", "\\\"").replace("\n", "\\n"),
                moviePath.replace("\"", "\\\"").replace("\n", "\\n")
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
            
            // 发送请求并获取响应
            HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                LOGGER.info("=== AI API Response ===");
                LOGGER.info("Response status: {}", response.statusCode());
                LOGGER.info("Response body: {}", responseBody);
                
                // 改进的JSON响应解析，支持多种API格式
                try {
                    // 首先尝试OpenAI格式: {"choices": [{"message": {"content": "电影名称"}}]}
                    int contentStart = responseBody.indexOf("\"content\":\"");
                    if (contentStart != -1) {
                        contentStart += "\"content\":\"".length();
                        int contentEnd = responseBody.indexOf('"', contentStart);
                        if (contentEnd != -1) {
                            String content = responseBody.substring(contentStart, contentEnd)
                                .replace("\\\"", "\"")
                                .replace("\\n", "\n")
                                .trim();
                            LOGGER.debug("Extracted content: {}", content);
                            return content;
                        }
                    }
                    
                    // 尝试Gemini格式或其他格式
                    // 如果包含"choices"字段但content解析失败，记录警告
                    int choicesStart = responseBody.indexOf("\"choices\":");
                    if (choicesStart != -1) {
                        LOGGER.warn("Failed to parse content field, but choices found. Response: {}", responseBody);
                    } else {
                        // 尝试其他可能的响应格式
                        LOGGER.warn("Unexpected API response format: {}", responseBody);
                        
                        // 如果响应包含用户提示要求提供目录结构，返回null
                        if (responseBody.contains("provide the directory structure") || 
                            responseBody.contains("share the information")) {
                            LOGGER.warn("API response indicates it needs directory structure information");
                            return null;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Error parsing API response: {}", e.getMessage());
                }
            } else {
                LOGGER.error("API request failed with status: {}, response: {}", response.statusCode(), response.body());
            }
            
        } catch (Exception e) {
            LOGGER.error("ChatGPT API call failed: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 清理和验证识别结果（增强版，参考电视剧AI识别）
     */
    String cleanAndValidateTitle(String recognizedTitle) {
        if (recognizedTitle == null || recognizedTitle.trim().isEmpty()) {
            return null;
        }

        // 清理逻辑 - 移除标点符号和规范化空格
        String cleaned = recognizedTitle
            .replaceAll("^[\\s\\p{Punct}]+", "")  // 移除开头的标点符号
            .replaceAll("[\\s\\p{Punct}]+$", "")  // 移除结尾的标点符号
            .replaceAll("\\s+", " ")              // 规范化空格
            .trim();

        // 验证结果不为空且不是明显的错误（参考电视剧AI识别）
        if (cleaned.isEmpty() ||
            cleaned.toLowerCase().contains("error") ||
            cleaned.toLowerCase().contains("failed") ||
            cleaned.toLowerCase().contains("unknown")) {
            LOGGER.warn("Invalid recognition result: {}", recognizedTitle);
            return null;
        }

        // 基本验证：长度合理
        if (cleaned.length() >= 2 && cleaned.length() <= 100) {
            LOGGER.debug("Cleaned title: '{}' -> '{}'", recognizedTitle, cleaned);
            return cleaned;
        }

        LOGGER.warn("Invalid title length: {}", cleaned.length());
        return null;
    }

}