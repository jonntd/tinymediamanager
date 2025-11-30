package org.tinymediamanager.core.services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;

/**
 * 全局AI API调用频率限制器
 * 防止AI服务疯狂调用，保护API配额和避免被限制
 */
public class AIApiRateLimiter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIApiRateLimiter.class);
    
    // 服务统计类
    private static class ServiceStats {
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private final AtomicInteger successfulCalls = new AtomicInteger(0);
        private final AtomicInteger failedCalls = new AtomicInteger(0);
        private final AtomicInteger timedOutCalls = new AtomicInteger(0);
        private final ConcurrentLinkedQueue<LocalDateTime> callHistory = new ConcurrentLinkedQueue<>();
        
        public void recordCall(boolean success, boolean timedOut) {
            totalCalls.incrementAndGet();
            if (success) {
                successfulCalls.incrementAndGet();
            } else {
                failedCalls.incrementAndGet();
            }
            if (timedOut) {
                timedOutCalls.incrementAndGet();
            }
            callHistory.offer(LocalDateTime.now());
            // 清理1小时前的记录
            LocalDateTime now = LocalDateTime.now();
            callHistory.removeIf(time -> ChronoUnit.HOURS.between(time, now) > 1);
        }
        
        public long getCallsInLastMinute() {
            LocalDateTime now = LocalDateTime.now();
            return callHistory.stream()
                .filter(time -> ChronoUnit.MINUTES.between(time, now) == 0)
                .count();
        }
        
        public long getCallsInLastHour() {
            LocalDateTime now = LocalDateTime.now();
            return callHistory.stream()
                .filter(time -> ChronoUnit.HOURS.between(time, now) == 0)
                .count();
        }
        
        public int getTotalCalls() {
            return totalCalls.get();
        }
        
        public int getSuccessfulCalls() {
            return successfulCalls.get();
        }
        
        public int getFailedCalls() {
            return failedCalls.get();
        }
        
        public int getTimedOutCalls() {
            return timedOutCalls.get();
        }
        
        public void reset() {
            totalCalls.set(0);
            successfulCalls.set(0);
            failedCalls.set(0);
            timedOutCalls.set(0);
            callHistory.clear();
        }
    }
    
    // 单例实例
    private static volatile AIApiRateLimiter instance;
    
    // 配置参数 - 从用户设置中动态获取
    private Settings getSettings() {
        return Settings.getInstance();
    }

    private int getMaxCallsPerMinute() {
        return getSettings().getAiMaxCallsPerMinute();
    }

    private int getMaxCallsPerHour() {
        return getSettings().getAiMaxCallsPerHour();
    }

    private long getMinIntervalMs() {
        return getSettings().getAiMinIntervalSeconds() * 1000L;
    }

    private boolean isRateLimitEnabled() {
        return getSettings().isAiRateLimitEnabled();
    }
    
    // 调用记录
    private final ConcurrentLinkedQueue<LocalDateTime> callHistory = new ConcurrentLinkedQueue<>();
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private volatile LocalDateTime lastCallTime = null;
    
    // 服务级统计
    private final Map<String, ServiceStats> serviceStatsMap = new ConcurrentHashMap<>();
    
    // 动态速率调整
    private volatile long dynamicWaitTimeMs = 0; // 动态调整的等待时间
    private volatile int consecutive429Errors = 0; // 连续429错误计数
    private static final long MAX_DYNAMIC_WAIT_TIME = 60000; // 最大动态等待时间（60秒）
    private static final long BASE_DYNAMIC_WAIT_TIME = 1000; // 基础动态等待时间（1秒）
    
    // AI请求结果缓存
    private static class CacheEntry {
        private final String result;
        private final LocalDateTime timestamp;
        
        public CacheEntry(String result) {
            this.result = result;
            this.timestamp = LocalDateTime.now();
        }
        
        public String getResult() {
            return result;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public boolean isExpired() {
            // 缓存有效期为24小时
            return ChronoUnit.HOURS.between(timestamp, LocalDateTime.now()) > 24;
        }
    }
    
    private final Map<String, CacheEntry> aiCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000; // 最大缓存条目数
    
    private AIApiRateLimiter() {
        LOGGER.info("AI API Rate Limiter initialized - Configuration will be read from user settings");
    }
    
    /**
     * 获取单例实例
     */
    public static AIApiRateLimiter getInstance() {
        if (instance == null) {
            synchronized (AIApiRateLimiter.class) {
                if (instance == null) {
                    instance = new AIApiRateLimiter();
                }
            }
        }
        return instance;
    }
    
    /**
     * 请求API调用许可
     * @param serviceName 服务名称（用于日志）
     * @return true如果允许调用，false如果被限制
     */
    public synchronized boolean requestPermission(String serviceName) {
        // 检查是否启用频率限制
        if (!isRateLimitEnabled()) {
            LOGGER.debug("Rate limiting disabled, allowing API call for {}", serviceName);
            // 记录服务统计
            serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(true, false);
            return true;
        }

        LocalDateTime now = LocalDateTime.now();

        // 清理过期的调用记录
        cleanupOldRecords(now);

        // 获取当前配置
        long minIntervalMs = getMinIntervalMs();
        int maxCallsPerMinute = getMaxCallsPerMinute();
        int maxCallsPerHour = getMaxCallsPerHour();

        // 检查最小间隔
        if (lastCallTime != null) {
            long intervalMs = ChronoUnit.MILLIS.between(lastCallTime, now);
            if (intervalMs < minIntervalMs) {
                LOGGER.warn("API call rejected for {} - too frequent ({}ms < {}ms)",
                           serviceName, intervalMs, minIntervalMs);
                // 记录服务统计 - 失败
                serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(false, false);
                return false;
            }
        }
        
        // 检查每分钟限制
        long callsInLastMinute = callHistory.stream()
            .filter(time -> ChronoUnit.MINUTES.between(time, now) == 0)
            .count();

        if (callsInLastMinute >= maxCallsPerMinute) {
            LOGGER.warn("API call rejected for {} - minute limit exceeded ({}/{})",
                       serviceName, callsInLastMinute, maxCallsPerMinute);
            // 记录服务统计 - 失败
            serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(false, false);
            return false;
        }

        // 检查每小时限制
        long callsInLastHour = callHistory.stream()
            .filter(time -> ChronoUnit.HOURS.between(time, now) == 0)
            .count();

        if (callsInLastHour >= maxCallsPerHour) {
            LOGGER.warn("API call rejected for {} - hour limit exceeded ({}/{})",
                       serviceName, callsInLastHour, maxCallsPerHour);
            // 记录服务统计 - 失败
            serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(false, false);
            return false;
        }
        
        // 记录调用
        callHistory.offer(now);
        lastCallTime = now;
        int currentTotal = totalCalls.incrementAndGet();
        
        // 记录服务统计 - 成功
        serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(true, false);
        
        LOGGER.debug("API call permitted for {} - Total calls: {}, Last minute: {}, Last hour: {}", 
                    serviceName, currentTotal, callsInLastMinute + 1, callsInLastHour + 1);
        
        return true;
    }
    
    /**
     * 强制等待直到可以调用
     * @param serviceName 服务名称
     * @param maxWaitMs 最大等待时间（毫秒）
     * @return true如果获得许可，false如果超时
     */
    public boolean waitForPermission(String serviceName, long maxWaitMs) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            if (requestPermission(serviceName)) {
                return true;
            }
            
            try {
                // 计算等待时间：基础间隔 + 动态调整时间
                long waitTime = Math.min(getMinIntervalMs() + dynamicWaitTimeMs, 5000);
                LOGGER.debug("Waiting {}ms (min: {}ms + dynamic: {}ms) before next attempt", 
                           waitTime, getMinIntervalMs(), dynamicWaitTimeMs);
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 记录服务统计 - 失败，中断
                serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(false, false);
                return false;
            }
        }
        
        LOGGER.error("API call timeout for {} after {}ms", serviceName, maxWaitMs);
        // 记录服务统计 - 超时
        serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(false, true);
        return false;
    }
    
    /**
     * 清理过期的调用记录
     */
    private void cleanupOldRecords(LocalDateTime now) {
        // 清理1小时前的记录
        callHistory.removeIf(time -> ChronoUnit.HOURS.between(time, now) > 1);
    }
    
    /**
     * 获取统计信息
     */
    public String getStatistics() {
        LocalDateTime now = LocalDateTime.now();
        cleanupOldRecords(now);
        cleanupExpiredCache();
        
        long callsInLastMinute = callHistory.stream()
            .filter(time -> ChronoUnit.MINUTES.between(time, now) == 0)
            .count();
            
        long callsInLastHour = callHistory.stream()
            .filter(time -> ChronoUnit.HOURS.between(time, now) == 0)
            .count();
        
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("AI API Stats - Total: %d, Last minute: %d/%d, Last hour: %d/%d, Rate limit: %s%n",
                           totalCalls.get(), callsInLastMinute, getMaxCallsPerMinute(),
                           callsInLastHour, getMaxCallsPerHour(), isRateLimitEnabled() ? "ON" : "OFF"));
        
        // 添加缓存统计
        stats.append(String.format("Cache Stats - Entries: %d, Max: %d%n", aiCache.size(), MAX_CACHE_SIZE));
        
        // 添加动态速率调整统计
        stats.append(String.format("Dynamic Rate Stats - Wait time: %dms, Consecutive 429 errors: %d%n", 
                           dynamicWaitTimeMs, consecutive429Errors));
        
        // 添加服务级统计
        if (!serviceStatsMap.isEmpty()) {
            stats.append("Service-level Stats:\n");
            for (Map.Entry<String, ServiceStats> entry : serviceStatsMap.entrySet()) {
                String serviceName = entry.getKey();
                ServiceStats serviceStats = entry.getValue();
                stats.append(String.format("  %s: Total: %d, Success: %d, Failed: %d, Timed out: %d, Last min: %d, Last hour: %d%n",
                           serviceName, serviceStats.getTotalCalls(), serviceStats.getSuccessfulCalls(),
                           serviceStats.getFailedCalls(), serviceStats.getTimedOutCalls(),
                           serviceStats.getCallsInLastMinute(), serviceStats.getCallsInLastHour()));
            }
        }
        
        return stats.toString();
    }
    
    /**
     * 记录429 Too Many Requests错误
     */
    public synchronized void record429Error() {
        consecutive429Errors++;
        // 指数退避：1s, 2s, 4s, 8s... 最大60s
        dynamicWaitTimeMs = Math.min(BASE_DYNAMIC_WAIT_TIME * (1L << (consecutive429Errors - 1)), MAX_DYNAMIC_WAIT_TIME);
        LOGGER.warn("Recorded 429 error, consecutive count: {}, dynamic wait time: {}ms", consecutive429Errors, dynamicWaitTimeMs);
    }
    
    /**
     * 记录成功的API调用
     */
    public synchronized void recordSuccessfulCall() {
        if (consecutive429Errors > 0) {
            consecutive429Errors--;
            // 成功调用后，逐渐减少动态等待时间
            dynamicWaitTimeMs = Math.max(0, dynamicWaitTimeMs / 2);
            LOGGER.info("Recorded successful call, consecutive 429 count: {}, dynamic wait time: {}ms", consecutive429Errors, dynamicWaitTimeMs);
        }
    }
    
    /**
     * 获取当前动态等待时间
     */
    public long getDynamicWaitTimeMs() {
        return dynamicWaitTimeMs;
    }
    
    /**
     * 计算最佳批次大小
     * @param maxBatchSize 最大批次大小
     * @return 计算出的最佳批次大小
     */
    public int calculateOptimalBatchSize(int maxBatchSize) {
        if (!isRateLimitEnabled()) {
            return maxBatchSize;
        }
        
        LocalDateTime now = LocalDateTime.now();
        cleanupOldRecords(now);
        
        // 获取当前配置
        int maxCallsPerMinute = getMaxCallsPerMinute();
        
        // 计算最近1分钟的调用次数
        long callsInLastMinute = callHistory.stream()
            .filter(time -> ChronoUnit.MINUTES.between(time, now) == 0)
            .count();
        
        // 计算剩余可用调用次数
        int remainingCalls = maxCallsPerMinute - (int) callsInLastMinute;
        if (remainingCalls <= 0) {
            return 1; // 剩余调用次数不足，使用最小批次
        }
        
        // 考虑动态等待时间，调整批次大小
        int optimalBatchSize;
        if (dynamicWaitTimeMs > 0) {
            // 有动态等待时间，说明最近遇到了限制，减小批次大小
            optimalBatchSize = Math.max(1, Math.min(remainingCalls, maxBatchSize / 2));
        } else {
            // 没有动态等待时间，使用较大批次
            optimalBatchSize = Math.min(remainingCalls, maxBatchSize);
        }
        
        LOGGER.debug("Calculated optimal batch size: {} (max: {}, remaining calls: {}, dynamic wait: {}ms)",
                   optimalBatchSize, maxBatchSize, remainingCalls, dynamicWaitTimeMs);
        
        return optimalBatchSize;
    }
    
    /**
     * 检查缓存中是否有结果
     * @param cacheKey 缓存键
     * @return 缓存结果，如果没有则返回null
     */
    public String getFromCache(String cacheKey) {
        // 关闭缓存，直接返回null
        LOGGER.debug("Cache disabled, skipping cache check for key: {}", cacheKey);
        return null;
    }
    
    /**
     * 将结果添加到缓存
     * @param cacheKey 缓存键
     * @param result 缓存结果
     */
    public void addToCache(String cacheKey, String result) {
        // 关闭缓存，不执行任何操作
        LOGGER.debug("Cache disabled, skipping cache addition for key: {}", cacheKey);
    }
    
    /**
     * 清理过期缓存
     */
    private void cleanupExpiredCache() {
        LocalDateTime now = LocalDateTime.now();
        aiCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 从缓存中移除指定键
     * @param cacheKey 缓存键
     */
    public void removeFromCache(String cacheKey) {
        if (cacheKey != null) {
            aiCache.remove(cacheKey);
            LOGGER.debug("Removed from cache: {}", cacheKey);
        }
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存统计信息
     */
    public String getCacheStatistics() {
        cleanupExpiredCache();
        return String.format("Cache: %d entries, max: %d", aiCache.size(), MAX_CACHE_SIZE);
    }
    
    /**
     * 重置统计信息
     */
    public synchronized void reset() {
        callHistory.clear();
        totalCalls.set(0);
        lastCallTime = null;
        // 重置所有服务统计
        for (ServiceStats stats : serviceStatsMap.values()) {
            stats.reset();
        }
        serviceStatsMap.clear();
        // 重置动态速率调整
        dynamicWaitTimeMs = 0;
        consecutive429Errors = 0;
        // 重置缓存
        aiCache.clear();
        LOGGER.info("AI API Rate Limiter statistics and cache reset");
    }
}
