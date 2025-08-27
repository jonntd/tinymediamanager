package org.tinymediamanager.core.movie.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.movie.entities.Movie;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量ChatGPT电影识别服务
 * 支持单次API调用处理多个电影，减少API调用次数
 */
public class BatchChatGPTMovieRecognitionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchChatGPTMovieRecognitionService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    
    private HttpClient httpClient;
    private final Settings settings;
    
    // 缓存已识别的结果，避免重复调用
    private static final Map<String, String> recognitionCache = new ConcurrentHashMap<>();
    
    public BatchChatGPTMovieRecognitionService() {
        this.settings = Settings.getInstance();

        // 添加调用栈追踪，帮助调试启动时的意外调用
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
     * 批量识别电影标题（支持分组处理和重试机制）
     * @param movies 待识别的电影列表
     * @return 电影ID到识别标题的映射
     */
    public Map<String, String> batchRecognizeMovieTitles(List<Movie> movies) {
        return batchRecognizeMovieTitles(movies, 20, 6);
    }
    
    /**
     * 批量识别电影标题（增强版，带详细日志和验证）
     * @param movies 待识别的电影列表
     * @param batchSize 每批次处理的电影数量
     * @param maxRetries 最大重试次数
     * @return 电影ID到识别标题的映射
     */
    public Map<String, String> batchRecognizeMovieTitles(List<Movie> movies, int batchSize, int maxRetries) {
        Map<String, String> results = new HashMap<>();

        if (movies == null || movies.isEmpty()) {
            // 添加调用栈追踪，帮助调试空列表调用的来源
            if (LOGGER.isDebugEnabled()) {
                StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                StringBuilder sb = new StringBuilder("Empty movie list provided, called from:\n");
                for (int i = 2; i < Math.min(stackTrace.length, 6); i++) {
                    sb.append("  at ").append(stackTrace[i].toString()).append("\n");
                }
                LOGGER.debug(sb.toString());
            } else {
                LOGGER.debug("Empty movie list provided");
            }
            return results;
        }
        
        LOGGER.info("Starting batch recognition for {} movies", movies.size());
        
        // 检查API配置状态
        String apiKey = settings.getOpenAiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOGGER.warn("OpenAI API key is not configured - falling back to individual recognition");
            return fallbackToIndividualRecognition(movies);
        }
        
        if (httpClient == null) {
            LOGGER.warn("HTTP client is not initialized - falling back to individual recognition");
            return fallbackToIndividualRecognition(movies);
        }
        
        // 验证电影列表
        List<Movie> validMovies = new ArrayList<>();
        for (Movie movie : movies) {
            if (movie != null) {
                validMovies.add(movie);
                LOGGER.debug("Processing movie: {} (ID: {})", movie.getTitle(), movie.getDbId());
            } else {
                LOGGER.warn("Null movie object found in list");
            }
        }
        
        if (validMovies.isEmpty()) {
            LOGGER.warn("No valid movies to process");
            return results;
        }
        
        LOGGER.info("Starting batch recognition for {} valid movies (batch size: {}, max retries: {})", 
                   validMovies.size(), batchSize, maxRetries);
        
        // 过滤掉已经缓存的结果
        List<Movie> needRecognition = new ArrayList<>();
        for (Movie movie : validMovies) {
            String cacheKey = generateCacheKey(movie);
            if (cacheKey == null) {
                LOGGER.warn("Failed to generate cache key for movie: {} (ID: {})", movie.getTitle(), movie.getDbId());
                continue;
            }
            
            if (recognitionCache.containsKey(cacheKey)) {
                results.put(movie.getDbId().toString(), recognitionCache.get(cacheKey));
                LOGGER.debug("Using cached result for movie: {} (Key: {})", movie.getTitle(), cacheKey);
                continue;
            } else {
                needRecognition.add(movie);
                LOGGER.debug("Adding to processing queue: {} (Key: {})", movie.getTitle(), cacheKey);
            }
        }
        
        if (needRecognition.isEmpty()) {
            LOGGER.debug("All movies already cached, skipping API call");
            return results;
        }
        
        // 分组处理
        int totalBatches = (int) Math.ceil((double) needRecognition.size() / batchSize);
        LOGGER.info("Processing {} movies in {} batches", needRecognition.size(), totalBatches);
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int startIndex = batchIndex * batchSize;
            int endIndex = Math.min(startIndex + batchSize, needRecognition.size());
            List<Movie> currentBatch = needRecognition.subList(startIndex, endIndex);
            
            LOGGER.info("Processing batch {}/{} ({} movies)", 
                       batchIndex + 1, totalBatches, currentBatch.size());
            
            Map<String, String> batchResults = processBatchWithRetry(currentBatch, maxRetries);
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
        
        LOGGER.info("Batch recognition completed: {} movies processed, {} successful", 
                   needRecognition.size(), results.size());
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
        
        int retryCount = 0;
        boolean success = false;
        
        while (retryCount <= maxRetries && !success) {
            try {
                LOGGER.debug("Attempt {} for batch of {} movies", retryCount + 1, batch.size());
                
                // 构建批量请求
                String batchRequest = buildBatchRequest(batch);
                if (batchRequest == null || batchRequest.trim().isEmpty()) {
                    LOGGER.warn("Failed to build batch request for retry {}", retryCount + 1);
                    retryCount++;
                    continue;
                }
                
                // 调用API
                String response = callChatGPTBatchAPI(batchRequest);
                if (response != null && !response.trim().isEmpty()) {
                    // 解析批量响应
                    Map<String, String> batchResults = parseBatchResponse(response, batch);
                    
                    // 验证结果数量
                    if (batchResults.size() == batch.size()) {
                        LOGGER.debug("Batch processed successfully: {} results for {} movies", 
                                   batchResults.size(), batch.size());
                        
                        // 合并结果并缓存
                        for (Map.Entry<String, String> entry : batchResults.entrySet()) {
                            String movieId = entry.getKey();
                            String recognizedTitle = entry.getValue();
                            
                            results.put(movieId, recognizedTitle);
                            
                            // 缓存结果
                            String cacheKey = generateCacheKey(getMovieById(batch, movieId));
                            if (cacheKey != null) {
                                recognitionCache.put(cacheKey, recognizedTitle);
                            }
                        }
                        success = true;
                    } else {
                        LOGGER.warn("Result count mismatch: expected {}, got {}. Retrying...", 
                                   batch.size(), batchResults.size());
                        retryCount++;
                        
                        if (retryCount <= maxRetries) {
                            // 重试前等待
                            try {
                                Thread.sleep(2000 * retryCount);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                } else {
                    LOGGER.warn("Empty or null response from API. Retrying...");
                    retryCount++;
                }
            } catch (Exception e) {
                LOGGER.error("Batch processing failed on attempt {}: {}", retryCount + 1, e.getMessage());
                retryCount++;
                
                if (retryCount <= maxRetries) {
                    try {
                        Thread.sleep(2000 * retryCount);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        if (!success) {
            LOGGER.error("Batch processing failed after {} retries for {} movies", 
                        maxRetries, batch.size());
            
            // 回退到逐个识别
            LOGGER.info("Falling back to individual recognition for failed batch");
            for (Movie movie : batch) {
                try {
                    ChatGPTMovieRecognitionService individualService = new ChatGPTMovieRecognitionService();
                    String title = individualService.recognizeMovieTitle(movie);
                    if (title != null) {
                        results.put(movie.getDbId().toString(), title);
                        String cacheKey = generateCacheKey(movie);
                        recognitionCache.put(cacheKey, title);
                    }
                } catch (Exception ex) {
                    LOGGER.error("Individual recognition failed for movie {}: {}", movie.getTitle(), ex.getMessage());
                }
            }
        }
        
        return results;
    }
    
    /**
     * 构建批量请求内容
     */
    private String buildBatchRequest(List<Movie> movies) {
        StringBuilder requestBuilder = new StringBuilder();
        
        // 为每部电影生成识别信息
        Map<String, Movie> movieMap = new HashMap<>();
        for (Movie movie : movies) {
            String path = extractMoviePath(movie);
            if (path != null) {
                movieMap.put(path, movie);
                requestBuilder.append(path).append("\n");
            }
        }
        
        if (requestBuilder.length() == 0) {
            return null;
        }
        
        return requestBuilder.toString();
    }
    
    /**
     * 调用ChatGPT批量API
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
            
            // 构建批量请求JSON
            String systemPrompt = settings.getOpenAiExtractionPrompt();
            if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
                // 使用专业的批量电影AI识别提示词
                systemPrompt = "你是一个专业的电影信息识别和刮削助手。根据提供的文件路径列表，联网搜索并找到最准确的官方电影信息，然后严格按照指定格式输出结果。\n\n" +
                              "## 核心要求\n\n" +
                              "### 1. 输入处理\n" +
                              "- 接收电影文件路径列表作为输入，每行一个\n" +
                              "- 从每个文件名中提取电影标题、年份等信息\n" +
                              "- 忽略文件扩展名和技术标记\n\n" +
                              "### 2. 搜索策略\n" +
                              "- 对每个文件进行独立的精确搜索\n" +
                              "- 查找官方来源：IMDb、豆瓣电影、TMDb等\n" +
                              "- 验证搜索结果的准确性\n\n" +
                              "### 3. 输出格式要求\n" +
                              "**严格按照以下格式输出，每行一个结果：**\n" +
                              "```\n标题 年份\n```\n" +
                              "- 标题使用官方中文名称（如果有），否则使用英文原名\n" +
                              "- 标题和年份之间用一个空格分隔\n" +
                              "- 年份使用4位数字格式\n" +
                              "- 输出行数必须与输入行数完全一致\n\n" +
                              "### 4. 示例\n" +
                              "输入：\n```\nInception.2010.1080p.mkv\nAvatar.2009.4K.mkv\n```\n" +
                              "输出：\n```\n盗梦空间 2010\n阿凡达 2009\n```";
                LOGGER.debug("Using professional Chinese extraction prompt with search capabilities");
            }
            
            String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"system\", \"content\": \"%s\"}, {\"role\": \"user\", \"content\": \"%s\"}], \"max_tokens\": 2000, \"temperature\": 0.3}",
                model,
                systemPrompt.replace("\"", "\\\"").replace("\n", "\\n"),
                batchRequest.replace("\"", "\\\"").replace("\n", "\\n")
            );
            
            LOGGER.debug("Batch API request body: {} characters", requestBody.length());
            LOGGER.info("Batch API request body content:\n{}", requestBody);
            
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
                LOGGER.debug("Batch API response: {} characters", responseBody.length());
                LOGGER.info("Batch API response content:\n{}", responseBody);
                return responseBody;
            } else {
                LOGGER.error("Batch API request failed with status: {}, response: {}", response.statusCode(), response.body());
            }
            
        } catch (Exception e) {
            LOGGER.error("Batch ChatGPT API call failed: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 解析批量响应（增强版，带结果验证）
     */
    private Map<String, String> parseBatchResponse(String response, List<Movie> movies) {
        Map<String, String> results = new HashMap<>();
        
        try {
            // 尝试解析JSON格式的响应
            String content = extractContentFromResponse(response);
            if (content == null || content.trim().isEmpty()) {
                LOGGER.warn("Empty content in API response");
                return results;
            }
            
            // 按行分割结果
            String[] lines = content.split("\n");
            List<String> validResults = new ArrayList<>();

            LOGGER.info("AI response content to parse:\n{}", content);
            LOGGER.info("Split into {} lines:", lines.length);
            for (int i = 0; i < lines.length; i++) {
                LOGGER.info("  Line {}: '{}'", i + 1, lines[i]);
            }

            // 过滤空行和无效结果
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("null")) {
                    validResults.add(trimmed);
                    LOGGER.info("Added valid result: '{}'", trimmed);
                } else {
                    LOGGER.debug("Skipped invalid line: '{}'", line);
                }
            }

            LOGGER.debug("Parsed {} valid results from {} lines", validResults.size(), lines.length);
            
            // 验证结果数量
            if (validResults.size() != movies.size()) {
                LOGGER.warn("Result count mismatch: expected {} movies, got {} results", 
                           movies.size(), validResults.size());
            }
            
            // 匹配结果到电影
            int minSize = Math.min(validResults.size(), movies.size());
            LOGGER.info("Matching {} results to {} movies:", validResults.size(), movies.size());
            for (int i = 0; i < minSize; i++) {
                Movie movie = movies.get(i);
                String recognizedTitle = validResults.get(i).trim();
                if (!recognizedTitle.isEmpty()) {
                    results.put(movie.getDbId().toString(), recognizedTitle);
                    LOGGER.info("  Movie '{}' (ID: {}) -> AI recognized: '{}'",
                               movie.getTitle(), movie.getDbId(), recognizedTitle);
                } else {
                    LOGGER.warn("  Movie '{}' (ID: {}) -> Empty AI result",
                               movie.getTitle(), movie.getDbId());
                }
            }
            
            // 如果结果数量不足，记录警告
            if (validResults.size() < movies.size()) {
                LOGGER.warn("Missing results for {} movies", movies.size() - validResults.size());
            } else if (validResults.size() > movies.size()) {
                LOGGER.warn("Extra results received: {} more than expected", validResults.size() - movies.size());
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to parse batch response: {}", e.getMessage());
        }
        
        return results;
    }
    
    /**
     * 从API响应中提取内容
     */
    private String extractContentFromResponse(String response) {
        try {
            // 方法1：尝试JSON解析
            if (response.contains("\"content\":\"")) {
                int contentStart = response.indexOf("\"content\":\"");
                if (contentStart != -1) {
                    contentStart += "\"content\":\"".length();
                    int contentEnd = response.indexOf('"', contentStart);
                    if (contentEnd != -1) {
                        return response.substring(contentStart, contentEnd)
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .trim();
                    }
                }
            }
            
            // 方法2：尝试直接提取内容（兼容格式）
            if (response.contains("content")) {
                // 简单的内容提取
                String[] parts = response.split("content");
                if (parts.length > 1) {
                    String contentPart = parts[1];
                    int quoteStart = contentPart.indexOf('"');
                    if (quoteStart != -1) {
                        int quoteEnd = contentPart.indexOf('"', quoteStart + 1);
                        if (quoteEnd != -1) {
                            return contentPart.substring(quoteStart + 1, quoteEnd)
                                .replace("\\\"", "\"")
                                .replace("\\n", "\n")
                                .trim();
                        }
                    }
                }
            }
            
            // 方法3：直接返回响应内容
            return response.trim();
            
        } catch (Exception e) {
            LOGGER.error("Failed to extract content from response: {}", e.getMessage());
            return response;
        }
    }
    
    /**
     * 从电影对象中提取文件路径（倒数三层，与单个识别保持一致）
     */
    private String extractMoviePath(Movie movie) {
        if (movie == null) {
            LOGGER.warn("Movie object is null");
            return "unknown_movie";
        }

        // 优先使用主要视频文件路径
        org.tinymediamanager.core.entities.MediaFile mainFile = movie.getMainFile();
        if (mainFile != null && mainFile != org.tinymediamanager.core.entities.MediaFile.EMPTY_MEDIAFILE) {
            try {
                String mainFilePath = mainFile.getFileAsPath().toString();
                if (mainFilePath != null && !mainFilePath.trim().isEmpty()) {
                    // 提取倒数三层目录，与单个识别保持一致
                    return extractLastThreeDirectoryNames(mainFilePath);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to get main file path for movie {}: {}", movie.getTitle(), e.getMessage());
            }
        }

        // 如果没有有效的主要文件，尝试使用第一个有效的媒体文件
        List<org.tinymediamanager.core.entities.MediaFile> mediaFiles = movie.getMediaFiles();
        for (org.tinymediamanager.core.entities.MediaFile mediaFile : mediaFiles) {
            if (mediaFile != org.tinymediamanager.core.entities.MediaFile.EMPTY_MEDIAFILE) {
                try {
                    String mediaFilePath = mediaFile.getFileAsPath().toString();
                    if (mediaFilePath != null && !mediaFilePath.trim().isEmpty()) {
                        // 提取倒数三层目录，与单个识别保持一致
                        return extractLastThreeDirectoryNames(mediaFilePath);
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to get media file path: {}", e.getMessage());
                }
            }
        }

        // 如果所有文件路径都无效，使用电影标题作为备用方案
        String movieTitle = movie.getTitle();
        if (movieTitle != null && !movieTitle.trim().isEmpty()) {
            LOGGER.info("Using movie title as fallback path: {}", movieTitle);
            return movieTitle;
        }

        // 最终回退方案
        LOGGER.warn("No valid path found for movie, using default identifier");
        return "movie_" + movie.getDbId();
    }

    /**
     * 提取路径倒数三层（与单个识别服务保持一致）
     */
    private String extractLastThreeDirectoryNames(String filePath) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(filePath);

            // 获取路径的所有部分
            int nameCount = path.getNameCount();
            if (nameCount <= 3) {
                // 如果路径层级不超过3层，返回相对路径
                return "/" + path.toString();
            }

            // 取倒数三层：倒数第三层目录/倒数第二层目录/文件名
            java.nio.file.Path lastThreeLayers = path.subpath(nameCount - 3, nameCount);
            return "/" + lastThreeLayers.toString();

        } catch (Exception e) {
            LOGGER.warn("Failed to extract last three layers from path: {}", e.getMessage());
            return filePath; // 回退到原始路径
        }
    }
    
    /**
     * 根据ID获取电影
     */
    private Movie getMovieById(List<Movie> movies, String movieId) {
        for (Movie movie : movies) {
            if (movie.getDbId().toString().equals(movieId)) {
                return movie;
            }
        }
        return null;
    }
    
    /**
     * 回退到单个识别模式
     */
    private Map<String, String> fallbackToIndividualRecognition(List<Movie> movies) {
        Map<String, String> results = new HashMap<>();
        ChatGPTMovieRecognitionService individualService = new ChatGPTMovieRecognitionService();
        
        LOGGER.info("Falling back to individual movie recognition for {} movies", movies.size());
        
        for (Movie movie : movies) {
            try {
                String recognizedTitle = individualService.recognizeMovieTitle(movie);
                if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
                    String cacheKey = generateCacheKey(movie);
                    String movieId = movie.getDbId().toString();

                    // 使用电影ID作为结果键，确保与MovieScrapeTask中的查找一致
                    results.put(movieId, recognizedTitle);
                    // 同时更新缓存
                    recognitionCache.put(cacheKey, recognizedTitle);

                    LOGGER.debug("Individual recognition result for movie '{}' (ID: {}): '{}'",
                               movie.getTitle(), movieId, recognizedTitle);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed individual recognition for movie {}: {}", movie.getTitle(), e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(Movie movie) {
    if (movie == null) {
      return null;
    }

    String path = extractMoviePath(movie);
    if (path != null) {
      return path;
    }

    return movie.getDbId().toString();
  }
    
    /**
     * 清除缓存
     */
    public static void clearCache() {
        recognitionCache.clear();
    }
    
    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return recognitionCache.size();
    }
}