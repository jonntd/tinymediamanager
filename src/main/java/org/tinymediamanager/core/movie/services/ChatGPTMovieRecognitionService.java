package org.tinymediamanager.core.movie.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.services.AIApiRateLimiter;
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
 * ChatGPT电影识别服务 基于路径倒数三层目录名称使用ChatGPT识别电影名
 */
public class ChatGPTMovieRecognitionService {
    private static final Logger   LOGGER  = LoggerFactory.getLogger(ChatGPTMovieRecognitionService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private HttpClient            httpClient;
    private final Settings        settings;

    public ChatGPTMovieRecognitionService() {
        this.settings = Settings.getInstance();

        String apiKey = settings.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOGGER.warn("OpenAI API key is not configured in settings");
            return;
        }

        try {
            this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        }
        catch (Exception e) {
            LOGGER.error("Failed to initialize HTTP client: {}", e.getMessage());
        }
    }

    /**
     * 基于电影文件路径识别电影标题（带重试机制） 使用路径倒数三层目录名称作为识别依据
     */
    public String recognizeMovieTitle(Movie movie) {
        return recognizeMovieTitleWithRetry(movie, 3);
    }

    /**
     * 带重试机制的电影标题识别
     */
    private String recognizeMovieTitleWithRetry(Movie movie, int maxRetries) {
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

            // 添加空结果重试逻辑
            String recognizedTitle = null;
            String cleanedTitle = null;
            int retryCount = 0;

            while (retryCount <= maxRetries) {
                // 检查API频率限制
                AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
                if (!rateLimiter.waitForPermission("ChatGPTMovieRecognition", 30000)) {
                    LOGGER.warn("API call timed out for movie recognition after 30 seconds, attempt {}/{} ", retryCount + 1, maxRetries);
                    throw new RuntimeException("API rate limit exceeded");
                }

                if (retryCount > 0) {
                    LOGGER.info("Retrying AI recognition, attempt {}/{}", retryCount + 1, maxRetries);
                    // 指数退避 - 增加基础延迟
                    long delayMs = 3000L * (1L << (retryCount - 1)); // 3s, 6s, 12s...
                    try {
                        LOGGER.info("Waiting {}ms before retry", delayMs);
                        Thread.sleep(delayMs);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // 调用ChatGPT API，传递倒数三层目录信息
                recognizedTitle = callChatGPTAPI(pathContext);

                LOGGER.info("=== AI Recognition Complete (Attempt {}/{}) ===", retryCount + 1, maxRetries);
                LOGGER.info("Raw AI response: '{}'", recognizedTitle);

                // 如果获得了非空响应，进行处理
                if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
                    // 清理和验证识别结果
                    cleanedTitle = cleanAndValidateTitle(recognizedTitle);
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
                        }
                        else {
                            LOGGER.warn("Retry failed, returning original result: '{}'", cleanedTitle);
                            return cleanedTitle;
                        }
                    }
                    else if (cleanedTitle != null && containsValidYear(cleanedTitle)) {
                        // 第一次就包含年份，记录成功
                        FixStatistics.recordAIRecognitionWithYear();
                        return cleanedTitle;
                    }

                    // 如果清理后的标题不为空，也可以返回
                    if (cleanedTitle != null) {
                        return cleanedTitle;
                    }
                }

                // 如果是最后一次重试或者获得了空结果，继续循环
                if (recognizedTitle == null || recognizedTitle.trim().isEmpty()) {
                    LOGGER.warn("AI returned empty or null result, attempt {}/{}", retryCount + 1, maxRetries);
                    retryCount++;
                    // 记录空结果重试统计
                    FixStatistics.recordAIRecognitionRetry();
                }
                else {
                    // 其他情况，退出循环
                    break;
                }
            }

        }
        catch (Exception e) {
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
            // URL解码，处理WebDAV编码的路径（如 %E6%88%90%E9%BE%99 -> 成龙）
            String decodedPath = filePath;
            try {
                decodedPath = java.net.URLDecoder.decode(filePath, "UTF-8");
            }
            catch (Exception e) {
                LOGGER.debug("Failed to URL decode path '{}': {}", filePath, e.getMessage());
            }

            Path path = Paths.get(decodedPath);

            // 获取路径的所有部分
            int nameCount = path.getNameCount();
            if (nameCount <= 3) {
                // 如果路径层级不超过3层，返回相对路径
                return "/" + path.toString();
            }

            // 取倒数三层：倒数第三层目录/倒数第二层目录/文件名
            Path lastThreeLayers = path.subpath(nameCount - 3, nameCount);
            return "/" + lastThreeLayers.toString();

        }
        catch (Exception e) {
            LOGGER.warn("Failed to extract last three layers from path: {}", e.getMessage());
            // 尝试解码后返回
            try {
                return java.net.URLDecoder.decode(filePath, "UTF-8");
            }
            catch (Exception ex) {
                return filePath; // 回退到原始路径
            }
        }
    }

    /**
     * 调用ChatGPT API（使用HTTP请求，兼容非官方API）
     */
    private String callChatGPTAPI(String moviePath) {
        try {
            // 检查缓存
            AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
            String cacheKey = "ChatGPTMovieRecognition:" + moviePath;
            String cachedResult = rateLimiter.getFromCache(cacheKey);
            if (cachedResult != null) {
                return cachedResult;
            }

            String apiKey = settings.getOpenAiApiKey();
            String apiUrl = settings.getOpenAiApiUrl();
            String model = settings.getOpenAiModel();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                LOGGER.warn("OpenAI API key is not configured");
                return null;
            }

            // 验证API URL格式
            if (apiUrl == null || !apiUrl.startsWith("http")) {
                LOGGER.error("Invalid OpenAI API URL: {}", apiUrl);
                return null;
            }

            // 构建请求JSON - 使用电影专用提示词（保留主人的联网搜索功能）
            String systemPrompt = settings.getOpenAiExtractionPrompt();
            if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
                // 电影专用的优化提示词 - 改进版
                systemPrompt = "你是一个专业的电影信息识别和刮削助手。根据提供的文件路径，联网搜索并找到最准确的官方电影信息，然后严格按照指定格式输出结果。\n\n" + "## 核心要求\n\n" + "### 1. 输入处理\n"
                        + "- 接收电影文件路径作为输入\n" + "- 专注于识别电影标题，忽略所有格式标签如：\n" + "  - 分辨率标签(720p, 1080p, 2160p, 4K)\n"
                        + "  - 视频编码(H.264, H.265, x264, x265, HEVC)\n" + "  - 音频格式(DTS-HD, TrueHD, Atmos, AAC)\n" + "  - 发布组(RARBG, YTS, 各种中文字母组)\n"
                        + "  - 版本信息(Director's Cut, Extended)\n" + "- 过滤掉文件扩展名和无关技术信息\n\n" + "### 2. 搜索策略\n" + "- 使用提取的标题关键词进行精确匹配搜索\n"
                        + "- 优先查找知名权威来源：TMDB、IMDB、豆瓣电影等\n" + "- 确保识别结果与官方发行名称完全一致\n\n" + "### 3. 输出格式要求 - 请严格遵守！\n"
                        + "**严格按照以下格式输出，绝对不要返回任何解释或错误信息：**\n" + "```\n标题 年份\n```\n" + "- 标题优先使用英文原名作为主要标识符\n" + "- 仅在英文名称不可用时才考虑中文名称\n"
                        + "- 标题和年份之间用一个空格分隔\n" + "- **年份必须包含**：使用4位数字格式，范围1888-" + (java.time.Year.now().getValue() + 2) + "\n"
                        + "- 如果无法确定年份，必须通过搜索找到准确的发行年份\n" + "- 年份不能为空，不能省略，这是强制要求\n" + "- 不包含任何其他符号、括号或额外信息\n" + "- 如果搜索失败，输出：未知电影 1900\n\n"
                        + "### 4. 示例\n" + "输入：`/Movies/Interstellar.2014.1080p.BluRay.x264.DTS-HD.MA.5.1-RARBG/` → 输出：`Interstellar 2014`\n"
                        + "输入：`/电影/疯狂动物城.2016.国粤英三语.BluRay.1080p.x265.10bit/` → 输出：`Zootopia 2016`\n"
                        + "输入：`/path/to/unknown.movie/` → 输出：`未知电影 1900`";
            }

            LOGGER.info("=== Movie AI Recognition Debug ===");
            LOGGER.info("Input movie path: {}", moviePath);
            LOGGER.info("API URL: {}", apiUrl);
            LOGGER.info("Model: {}", model);
            LOGGER.info("System prompt length: {} characters", systemPrompt.length());

            String requestBody = String.format(
                    "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 500, \"temperature\": 0.3}",
                    model, systemPrompt.replace("\"", "\\\"").replace("\n", "\\n"), moviePath.replace("\"", "\\\"").replace("\n", "\\n"));

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
                            String content = responseBody.substring(contentStart, contentEnd).replace("\\\"", "\"").replace("\\n", "\n").trim();
                            LOGGER.debug("Extracted content: {}", content);

                            // 将结果存入缓存
                            rateLimiter.addToCache(cacheKey, content);

                            return content;
                        }
                    }

                    // 尝试Gemini格式或其他格式
                    // 如果包含"choices"字段但content解析失败，记录警告
                    int choicesStart = responseBody.indexOf("\"choices\":");
                    if (choicesStart != -1) {
                        LOGGER.warn("Failed to parse content field, but choices found. Response: {}", responseBody);
                    }
                    else {
                        // 尝试其他可能的响应格式
                        LOGGER.warn("Unexpected API response format: {}", responseBody);

                        // 如果响应包含用户提示要求提供目录结构，返回null
                        if (responseBody.contains("provide the directory structure") || responseBody.contains("share the information")) {
                            LOGGER.warn("API response indicates it needs directory structure information");
                            return null;
                        }
                    }
                }
                catch (Exception e) {
                    LOGGER.warn("Error parsing API response: {}", e.getMessage());
                }
            }
            else {
                LOGGER.error("API request failed with status: {}, response: {}", response.statusCode(), response.body());
            }

        }
        catch (java.net.http.HttpConnectTimeoutException e) {
            LOGGER.error("ChatGPT API call failed: Connection timed out - please check network connectivity and API URL");
            LOGGER.error("Detailed error: {}", e.getMessage());
        }
        catch (java.net.http.HttpTimeoutException e) {
            LOGGER.error("ChatGPT API call failed: Request timed out - API server may be slow or overloaded");
            LOGGER.error("Detailed error: {}", e.getMessage());
        }
        catch (java.net.ConnectException e) {
            LOGGER.error("ChatGPT API call failed: Could not connect to server - please check API URL and network settings");
            LOGGER.error("Detailed error: {}", e.getMessage());
        }
        catch (javax.net.ssl.SSLHandshakeException e) {
            LOGGER.error("ChatGPT API call failed: SSL handshake failed - please check if the API URL uses valid SSL certificate");
            LOGGER.error("Detailed error: {}", e.getMessage());
        }
        catch (javax.net.ssl.SSLException e) {
            LOGGER.error("ChatGPT API call failed: SSL error - please check SSL configuration");
            LOGGER.error("Detailed error: {}", e.getMessage());
        }
        catch (java.io.IOException e) {
            LOGGER.error("ChatGPT API call failed: IO error - {}", e.getMessage());
            // 特别处理"HTTP/1.1 header parser received no bytes"错误
            if (e.getMessage() != null && e.getMessage().contains("header parser received no bytes")) {
                LOGGER.error("This error typically occurs when:");
                LOGGER.error("1. API URL is invalid or points to non-existent endpoint");
                LOGGER.error("2. API server closed connection without sending response");
                LOGGER.error("3. Network connectivity issues (firewall, proxy)");
                LOGGER.error("4. Invalid API key causing server to reject connection");
            }
        }
        catch (Exception e) {
            LOGGER.error("ChatGPT API call failed: {}", e.getMessage());
            LOGGER.error("Full stack trace:", e);
        }

        return null;
    }

    /**
     * 清理和验证识别结果 - 增强版，添加更严格的格式验证
     */
    String cleanAndValidateTitle(String recognizedTitle) {
        if (recognizedTitle == null || recognizedTitle.trim().isEmpty()) {
            LOGGER.warn("识别结果为空");
            return null;
        }

        // 清理逻辑 - 移除标点符号和规范化空格
        String cleaned = recognizedTitle.replaceAll("^[\\s\\p{Punct}]+", "") // 移除开头的标点符号
                .replaceAll("[\\s\\p{Punct}]+$", "") // 移除结尾的标点符号
                .replaceAll("\\s+", " ") // 规范化空格
                .trim();

        // 检查是否包含错误提示词
        String lowerCleaned = cleaned.toLowerCase();
        if (lowerCleaned.contains("error") || lowerCleaned.contains("failed") || lowerCleaned.contains("unable") || lowerCleaned.contains("cannot")
                || lowerCleaned.contains("unable to") || lowerCleaned.contains("not possible") || lowerCleaned.contains("i'm")
                || lowerCleaned.contains("i am") || lowerCleaned.contains("sorry") || lowerCleaned.contains("apologize")) {
            LOGGER.warn("识别结果包含错误提示词: {}", cleaned);
            return null;
        }

        // 检查是否是未知电影格式
        if (lowerCleaned.equals("未知电影") || lowerCleaned.equals("unknown movie")) {
            LOGGER.warn("识别结果为未知电影");
            return "未知电影 1900"; // 返回标准未知电影格式
        }

        // 验证是否符合"标题 年份"格式
        // 提取年份（最后4个数字）
        int lastSpaceIndex = cleaned.lastIndexOf(' ');
        if (lastSpaceIndex <= 0 || lastSpaceIndex >= cleaned.length() - 4) {
            LOGGER.warn("识别结果不符合'标题 年份'格式: {}", cleaned);
            return null;
        }

        // 提取可能的年份部分
        String yearPart = cleaned.substring(lastSpaceIndex + 1);
        if (!yearPart.matches("\\d{4}")) {
            LOGGER.warn("年份格式不正确，必须是4位数字: {}", yearPart);
            return null;
        }

        // 验证年份范围
        try {
            int year = Integer.parseInt(yearPart);
            int currentYear = java.time.Year.now().getValue();
            if (year < 1888 || year > currentYear + 2) {
                LOGGER.warn("年份超出有效范围(1888-{})", currentYear + 2);
                return null;
            }
        }
        catch (NumberFormatException e) {
            LOGGER.warn("无法解析年份: {}", yearPart);
            return null;
        }

        // 提取标题部分
        String titlePart = cleaned.substring(0, lastSpaceIndex).trim();
        if (titlePart.isEmpty() || titlePart.length() < 2 || titlePart.length() > 90) {
            LOGGER.warn("标题部分长度不合理: {}", titlePart);
            return null;
        }

        LOGGER.debug("清理并验证后的标题: '{}' -> '{}'", recognizedTitle, cleaned);
        return cleaned;
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
            }
            catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }

        LOGGER.debug("No valid year found in text: '{}'", text);
        return false;
    }

    /**
     * 带年份要求的AI识别重试 - 增强版
     */
    private String retryWithYearRequirement(String pathContext) {
        // 定义一个更严格的提示词，明确要求年份
        String systemPrompt = "你是一个专业的电影识别专家。根据提供的电影文件路径信息，联网搜索并识别出正确的电影标题和发行年份。\n\n" + "**关键要求**：\n" + "1. 你的回答必须包含4位数字的年份\n"
                + "2. 格式：电影标题 年份（用空格分隔）\n" + "3. 年份范围：1888-" + (java.time.Year.now().getValue() + 2) + "\n" + "4. 如果不确定年份，请搜索确认\n" + "5. 绝对不能省略年份\n"
                + "6. 如果搜索失败，输出：未知电影 1900\n" + "7. 禁止返回'I am unable to'或任何错误说明\n\n" + "示例：\n" + "输入：`Inception.2010.mkv` → 输出：`盗梦空间 2010`\n"
                + "输入：`卒仔抽车.mkv` → 输出：`卒仔抽车 1980`";

        Exception lastException = null;
        final int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long startTime = System.currentTimeMillis();
            boolean success = false;

            try {
                LOGGER.info("=== 开始电影AI识别重试 (尝试 {}/{}) ===", attempt, maxRetries);
                LOGGER.info("待识别路径: {}", pathContext);

                // 检查API频率限制
                AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
                if (!rateLimiter.waitForPermission("ChatGPTMovieRecognition", 30000)) {
                    LOGGER.warn("API调用超时，等待重试...");
                    continue;
                }

                String apiKey = settings.getOpenAiApiKey();
                String apiUrl = settings.getOpenAiApiUrl();
                String model = settings.getOpenAiModel();

                String requestBody = String.format(
                        "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 500, \"temperature\": 0.1}",
                        model, systemPrompt.replace("\"", "\\\"").replace("\n", "\\n"), pathContext.replace("\"", "\\\"").replace("\n", "\\n"));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long responseTime = System.currentTimeMillis() - startTime;

                // 记录性能指标
                LOGGER.info("API响应时间: {}ms, 状态码: {}", responseTime, response.statusCode());

                if (response.statusCode() == 200) {
                    // 记录成功调用，减少动态等待时间
                    rateLimiter.recordSuccessfulCall();

                    String responseBody = response.body();
                    LOGGER.debug("Retry API response: {}", responseBody);

                    // 解析响应
                    int contentStart = responseBody.indexOf("\"content\":\"");
                    if (contentStart != -1) {
                        contentStart += "\"content\":\"".length();
                        int contentEnd = responseBody.indexOf('"', contentStart);
                        if (contentEnd != -1) {
                            String content = responseBody.substring(contentStart, contentEnd).replace("\\\"", "\"").replace("\\n", "\n").trim();
                            LOGGER.debug("Retry extracted content: {}", content);

                            // 验证结果
                            String cleanedResult = cleanAndValidateTitle(content);
                            if (cleanedResult != null) {
                                success = true;
                                LOGGER.info("=== 电影AI识别重试成功 ===");
                                return cleanedResult;
                            }
                        }
                    }
                }
                else {
                    LOGGER.warn("Retry API request failed with status: {}, 响应: {}", response.statusCode(), response.body());

                    // 处理429错误，增加动态等待时间
                    if (response.statusCode() == 429) {
                        rateLimiter.record429Error();
                    }
                    else {
                        // 非429错误，记录成功调用，逐渐恢复
                        rateLimiter.recordSuccessfulCall();
                    }
                }

                // 指数退避重试 - 增加基础延迟
                if (attempt < maxRetries) {
                    long delayMs = 3000L * (1L << (attempt - 1)); // 3s, 6s, 12s...
                    LOGGER.info("重试失败，等待 {}ms 后重试", delayMs);
                    Thread.sleep(delayMs);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("重试过程被中断");
                break;
            }
            catch (Exception e) {
                lastException = e;
                long responseTime = System.currentTimeMillis() - startTime;
                LOGGER.warn("重试过程中发生错误 ({}ms): {}", responseTime, e.getMessage());

                // 分类处理不同类型的错误
                if (e.getMessage() != null) {
                    String errorMsg = e.getMessage().toLowerCase();
                    // 针对特定错误类型的处理
                    if (errorMsg.contains("rate limit") || errorMsg.contains("quota") || errorMsg.contains("limit") || errorMsg.contains("usage")) {
                        LOGGER.warn("遇到API限制错误，增加等待时间");
                        try {
                            Thread.sleep(10000L * attempt); // 更长的等待时间
                        }
                        catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                // 普通重试的指数退避 - 增加基础延迟
                if (attempt < maxRetries) {
                    try {
                        long delayMs = 3000L * (1L << (attempt - 1));
                        LOGGER.info("等待 {}ms 后重试", delayMs);
                        Thread.sleep(delayMs);
                    }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        LOGGER.info("=== 电影AI识别所有重试均失败 ===");
        if (lastException != null) {
            LOGGER.error("最后一次错误: ", lastException);
        }
        return "未知电影 1900";
    }

}