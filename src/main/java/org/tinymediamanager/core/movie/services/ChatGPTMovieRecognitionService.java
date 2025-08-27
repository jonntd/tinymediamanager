package org.tinymediamanager.core.movie.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.utils.FixStatistics;
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

            // 记录AI识别尝试
            FixStatistics.recordAIRecognitionAttempt();

            // 调用ChatGPT API，传递倒数三层目录信息
            String recognizedTitle = callChatGPTAPI(pathContext);

            LOGGER.info("=== AI Recognition Complete ===");
            LOGGER.info("Raw AI response: '{}'", recognizedTitle);
            
            if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
                // 清理和验证识别结果
                String cleanedTitle = cleanAndValidateTitle(recognizedTitle);
                LOGGER.info("Cleaned and validated title: '{}'", cleanedTitle);

                // 验证是否包含年份
                if (cleanedTitle != null && !containsValidYear(cleanedTitle)) {
                    LOGGER.warn("AI response does not contain valid year: '{}'", cleanedTitle);
                    LOGGER.warn("Attempting to retry with explicit year requirement...");

                    // 记录重试统计
                    FixStatistics.recordAIRecognitionRetry();

                    // 重试一次，明确要求年份
                    String retryResult = retryWithYearRequirement(pathContext);
                    if (retryResult != null && containsValidYear(retryResult)) {
                        LOGGER.info("Retry successful with year: '{}'", retryResult);
                        FixStatistics.recordAIRecognitionRetrySuccess();
                        FixStatistics.recordAIRecognitionWithYear();
                        return retryResult;
                    } else {
                        LOGGER.warn("Retry failed, returning original result: '{}'", cleanedTitle);
                        return cleanedTitle;
                    }
                } else if (cleanedTitle != null && containsValidYear(cleanedTitle)) {
                    // 第一次就包含年份，记录成功
                    FixStatistics.recordAIRecognitionWithYear();
                }

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
                // 电影专用的新提示词 - 基于专业AI电影识别刮削系统
                systemPrompt = "你是一个专业的电影信息识别和刮削助手。根据提供的文件路径，联网搜索并找到最准确的官方电影信息，然后严格按照指定格式输出结果。\n\n" +
                              "## 核心要求\n\n" +
                              "### 1. 输入处理\n" +
                              "- 接收电影文件路径作为输入\n" +
                              "- 从文件名中提取可能的电影标题、年份、分辨率等信息\n" +
                              "- 忽略文件扩展名（.mp4, .mkv, .avi等）\n" +
                              "- 过滤掉常见的发布组标识、编码信息、分辨率标记等无关内容\n\n" +
                              "### 2. 搜索策略\n" +
                              "- 优先使用提取的标题和年份进行精确搜索\n" +
                              "- 查找官方来源：IMDb、豆瓣电影、The Movie Database (TMDb)等\n" +
                              "- 验证搜索结果的准确性和权威性\n\n" +
                              "### 3. 输出格式要求\n" +
                              "**严格按照以下格式输出：**\n" +
                              "```\n标题 年份\n```\n" +
                              "- 标题使用官方中文名称（如果有），否则使用英文原名\n" +
                              "- 标题和年份之间用一个空格分隔\n" +
                              "- **年份必须包含**：使用4位数字格式，范围1888-" + (java.time.Year.now().getValue() + 2) + "\n" +
                              "- 如果文件名中没有年份，必须通过搜索找到正确的发行年份\n" +
                              "- 年份不能为空，不能省略，这是强制要求\n" +
                              "- 不包含任何其他符号、括号或额外信息\n\n" +
                              "### 4. 示例\n" +
                              "输入：`Inception.2010.1080p.BluRay.mkv` → 输出：`盗梦空间 2010`\n" +
                              "输入：`Avatar.2009.4K.UHD.mkv` → 输出：`阿凡达 2009`";
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

    /**
     * 检查字符串是否包含有效年份
     */
    private boolean containsValidYear(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 查找4位数字年份
        java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("\\b(\\d{4})\\b");
        java.util.regex.Matcher matcher = yearPattern.matcher(text);

        while (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int currentYear = java.time.Year.now().getValue();
                if (year >= 1888 && year <= currentYear + 2) {
                    LOGGER.debug("Found valid year in text: {}", year);
                    return true;
                }
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }

        LOGGER.debug("No valid year found in text: '{}'", text);
        return false;
    }

    /**
     * 重试AI识别，明确要求年份
     */
    private String retryWithYearRequirement(String pathContext) {
        try {
            String apiKey = settings.getOpenAiApiKey();
            String apiUrl = settings.getOpenAiApiUrl();
            String model = settings.getOpenAiModel();

            // 更强烈的年份要求提示词
            String systemPrompt = "你是一个专业的电影识别专家。根据提供的电影文件路径信息，识别出正确的电影标题和发行年份。\n\n" +
                                 "**关键要求**：\n" +
                                 "1. 你的回答必须包含4位数字的年份\n" +
                                 "2. 格式：电影标题 年份（用空格分隔）\n" +
                                 "3. 年份范围：1888-" + (java.time.Year.now().getValue() + 2) + "\n" +
                                 "4. 如果不确定年份，请搜索确认\n" +
                                 "5. 绝对不能省略年份\n\n" +
                                 "示例：\n" +
                                 "输入：`Inception.2010.mkv` → 输出：`盗梦空间 2010`\n" +
                                 "输入：`Avatar.mkv` → 输出：`阿凡达 2009`";

            String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 500, \"temperature\": 0.1}",
                model,
                systemPrompt.replace("\"", "\\\"").replace("\n", "\\n"),
                pathContext.replace("\"", "\\\"").replace("\n", "\\n")
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                LOGGER.debug("Retry API response: {}", responseBody);

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
                        LOGGER.debug("Retry extracted content: {}", content);
                        return cleanAndValidateTitle(content);
                    }
                }
            } else {
                LOGGER.warn("Retry API request failed with status: {}", response.statusCode());
            }

        } catch (Exception e) {
            LOGGER.warn("Retry AI recognition failed: {}", e.getMessage());
        }

        return null;
    }

}