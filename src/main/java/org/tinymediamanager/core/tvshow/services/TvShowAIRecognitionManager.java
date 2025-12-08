package org.tinymediamanager.core.tvshow.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.services.AIApiRateLimiter;

/**
 * 统一的电视剧AI识别管理器 提供会话级缓存、调用次数限制、服务实例复用
 * 
 * 设计目的： 1. 避免同一部电视剧被重复识别多次 2. 仅缓存已验证（刮削成功）的结果 3. 简化调用方代码
 */
public class TvShowAIRecognitionManager {
    private static final Logger                        LOGGER                   = LoggerFactory.getLogger(TvShowAIRecognitionManager.class);

    // 单例实例
    private static volatile TvShowAIRecognitionManager instance;

    // 每部电视剧最多AI识别次数（批量1次 + 回退1次）
    private static final int                           MAX_AI_ATTEMPTS_PER_SHOW = 2;

    // 会话级缓存：仅在当前刮削任务期间有效
    private final Map<String, CacheEntry>              sessionCache             = new ConcurrentHashMap<>();

    // 每部电视剧的AI调用计数
    private final Map<String, AtomicInteger>           attemptCounters          = new ConcurrentHashMap<>();

    // 复用的单个识别服务实例
    private ChatGPTTvShowRecognitionService            individualService;

    /**
     * 缓存条目
     */
    @SuppressWarnings("unused") // timestamp 保留用于将来的缓存过期功能
    private static class CacheEntry {
        final String  recognizedTitle;
        final boolean validated;      // 是否经过刮削验证（成功匹配）
        final long    timestamp;      // 用于将来的缓存过期功能

        CacheEntry(String recognizedTitle, boolean validated) {
            this.recognizedTitle = recognizedTitle;
            this.validated = validated;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private TvShowAIRecognitionManager() {
        LOGGER.info("TvShowAIRecognitionManager initialized");
    }

    /**
     * 获取单例实例
     */
    public static TvShowAIRecognitionManager getInstance() {
        if (instance == null) {
            synchronized (TvShowAIRecognitionManager.class) {
                if (instance == null) {
                    instance = new TvShowAIRecognitionManager();
                }
            }
        }
        return instance;
    }

    /**
     * 开始新的刮削会话，清空缓存和计数器 应在每次批量刮削任务开始时调用
     */
    public synchronized void startNewSession() {
        LOGGER.info("Starting new TV show AI recognition session, clearing {} cached entries and {} attempt counters", sessionCache.size(),
                attemptCounters.size());
        sessionCache.clear();
        attemptCounters.clear();
    }

    /**
     * 获取电视剧的AI识别结果
     * 
     * @param tvShow
     *            电视剧对象
     * @param batchResults
     *            批量识别结果（可为null）
     * @return 识别的标题，如果无法识别返回null
     */
    public String getRecognizedTitle(TvShow tvShow, Map<String, String> batchResults) {
        if (tvShow == null) {
            return null;
        }

        String showId = tvShow.getDbId().toString();
        String cacheKey = generateCacheKey(tvShow);

        // 1. 优先使用批量识别结果
        if (batchResults != null && batchResults.containsKey(showId)) {
            String batchResult = batchResults.get(showId);
            LOGGER.debug("Using batch AI result for TV show '{}': '{}'", tvShow.getTitle(), batchResult);
            // 批量结果也记入缓存（未验证状态）
            sessionCache.put(cacheKey, new CacheEntry(batchResult, false));
            return batchResult;
        }

        // 2. 检查已验证的缓存结果
        // 缓存已禁用
        // CacheEntry cached = sessionCache.get(cacheKey);
        // if (cached != null && cached.validated) {
        // LOGGER.debug("Using validated cache for TV show '{}': '{}'", tvShow.getTitle(), cached.recognizedTitle);
        // return cached.recognizedTitle;
        // }

        // 3. 检查调用次数限制
        AtomicInteger counter = attemptCounters.computeIfAbsent(cacheKey, k -> new AtomicInteger(0));
        int currentAttempts = counter.get();
        // 兼容旧逻辑，即使缓存禁用可能仍有引用
        CacheEntry cached = null;

        if (currentAttempts >= MAX_AI_ATTEMPTS_PER_SHOW) {
            LOGGER.debug("Max AI attempts ({}) reached for TV show '{}', skipping further recognition", MAX_AI_ATTEMPTS_PER_SHOW, tvShow.getTitle());
            return null;
        }

        // 4. 检查用户是否启用了单独AI回退
        if (!Settings.getInstance().isAiIndividualFallbackEnabled()) {
            LOGGER.debug("Individual AI fallback disabled by user for TV show '{}'", tvShow.getTitle());
            return null;
        }

        // 5. 执行单独AI识别
        return performIndividualRecognition(tvShow, cacheKey, counter);
    }

    /**
     * 执行单独AI识别 注意：只有成功获取有效结果时才增加调用计数，网络错误或空结果不计入
     */
    private String performIndividualRecognition(TvShow tvShow, String cacheKey, AtomicInteger counter) {
        try {
            // 检查速率限制
            AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
            if (!rateLimiter.waitForPermission("TvShowAIRecognitionManager", 30000)) {
                LOGGER.warn("API rate limit timeout for TV show '{}', not counting as attempt", tvShow.getTitle());
                return null; // 速率限制超时不计入调用次数
            }

            LOGGER.info("Attempting individual AI recognition for TV show '{}' (current attempts: {}/{})", tvShow.getTitle(), counter.get(),
                    MAX_AI_ATTEMPTS_PER_SHOW);

            // 使用复用的服务实例
            ChatGPTTvShowRecognitionService service = getIndividualService();
            String recognizedTitle = service.recognizeTvShowTitle(tvShow);

            if (recognizedTitle != null && !recognizedTitle.trim().isEmpty()) {
                // 只有成功获取有效结果时才增加调用计数
                int attemptNumber = counter.incrementAndGet();
                LOGGER.info("Individual AI recognition successful for TV show '{}': '{}' (attempt {}/{})", tvShow.getTitle(), recognizedTitle,
                        attemptNumber, MAX_AI_ATTEMPTS_PER_SHOW);
                // 缓存已禁用
                // sessionCache.put(cacheKey, new CacheEntry(recognizedTitle, false));
                return recognizedTitle;
            }
            else {
                // AI返回空结果，不增加调用计数，让下次有机会重试
                LOGGER.warn("Individual AI recognition returned empty for TV show '{}', not counting as attempt (allows retry)", tvShow.getTitle());
            }

        }
        catch (Exception e) {
            // 异常情况也不增加调用计数，可能是临时网络问题
            LOGGER.error("Error during individual AI recognition for TV show '{}': {} (not counting as attempt)", tvShow.getTitle(), e.getMessage());
        }

        return null;
    }

    /**
     * 标记识别结果为已验证（刮削成功后调用）
     * 
     * @param tvShow
     *            电视剧对象
     * @param recognizedTitle
     *            识别的标题
     */
    public void markAsValidated(TvShow tvShow, String recognizedTitle) {
        if (tvShow == null || recognizedTitle == null) {
            return;
        }

        // 缓存已禁用
        // String cacheKey = generateCacheKey(tvShow);
        // sessionCache.put(cacheKey, new CacheEntry(recognizedTitle, true));
        // LOGGER.debug("Marked AI result as validated for TV show '{}': '{}'", tvShow.getTitle(), recognizedTitle);
    }

    /**
     * 检查是否还可以进行AI识别
     * 
     * @param tvShow
     *            电视剧对象
     * @return true如果还有剩余识别次数
     */
    public boolean canAttemptRecognition(TvShow tvShow) {
        if (tvShow == null) {
            return false;
        }

        String cacheKey = generateCacheKey(tvShow);
        AtomicInteger counter = attemptCounters.get(cacheKey);

        if (counter == null) {
            return true;
        }

        return counter.get() < MAX_AI_ATTEMPTS_PER_SHOW;
    }

    /**
     * 获取电视剧的AI调用次数
     */
    public int getAttemptCount(TvShow tvShow) {
        if (tvShow == null) {
            return 0;
        }

        String cacheKey = generateCacheKey(tvShow);
        AtomicInteger counter = attemptCounters.get(cacheKey);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(TvShow tvShow) {
        // 使用电视剧路径作为缓存键
        String path = tvShow.getPath();
        if (path != null && !path.trim().isEmpty()) {
            return path;
        }

        // 回退到使用数据库ID
        return "tvshow_" + tvShow.getDbId();
    }

    /**
     * 获取复用的单个识别服务实例
     */
    private synchronized ChatGPTTvShowRecognitionService getIndividualService() {
        if (individualService == null) {
            individualService = new ChatGPTTvShowRecognitionService();
        }
        return individualService;
    }

    /**
     * 获取会话统计信息
     */
    public String getSessionStatistics() {
        int totalCached = sessionCache.size();
        int validatedCount = (int) sessionCache.values().stream().filter(e -> e.validated).count();
        int totalAttempts = attemptCounters.values().stream().mapToInt(AtomicInteger::get).sum();

        return String.format("TV Show AI Session Stats - Cached: %d (validated: %d), Total attempts: %d", totalCached, validatedCount, totalAttempts);
    }

    /**
     * 重置管理器状态（用于测试）
     */
    public synchronized void reset() {
        sessionCache.clear();
        attemptCounters.clear();
        individualService = null;
        LOGGER.info("TvShowAIRecognitionManager reset");
    }
}
