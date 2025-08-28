package org.tinymediamanager.core.services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
                return false;
            }
        }
        
        // 检查每分钟限制
        long callsInLastMinute = callHistory.stream()
            .mapToLong(time -> ChronoUnit.MINUTES.between(time, now) == 0 ? 1 : 0)
            .sum();

        if (callsInLastMinute >= maxCallsPerMinute) {
            LOGGER.warn("API call rejected for {} - minute limit exceeded ({}/{})",
                       serviceName, callsInLastMinute, maxCallsPerMinute);
            return false;
        }

        // 检查每小时限制
        long callsInLastHour = callHistory.stream()
            .mapToLong(time -> ChronoUnit.HOURS.between(time, now) == 0 ? 1 : 0)
            .sum();

        if (callsInLastHour >= maxCallsPerHour) {
            LOGGER.warn("API call rejected for {} - hour limit exceeded ({}/{})",
                       serviceName, callsInLastHour, maxCallsPerHour);
            return false;
        }
        
        // 记录调用
        callHistory.offer(now);
        lastCallTime = now;
        int currentTotal = totalCalls.incrementAndGet();
        
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
                Thread.sleep(Math.min(getMinIntervalMs(), 5000)); // 最多等待5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        LOGGER.error("API call timeout for {} after {}ms", serviceName, maxWaitMs);
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
        
        long callsInLastMinute = callHistory.stream()
            .mapToLong(time -> ChronoUnit.MINUTES.between(time, now) == 0 ? 1 : 0)
            .sum();
            
        long callsInLastHour = callHistory.stream()
            .mapToLong(time -> ChronoUnit.HOURS.between(time, now) == 0 ? 1 : 0)
            .sum();
        
        return String.format("AI API Stats - Total: %d, Last minute: %d/%d, Last hour: %d/%d, Rate limit: %s",
                           totalCalls.get(), callsInLastMinute, getMaxCallsPerMinute(),
                           callsInLastHour, getMaxCallsPerHour(), isRateLimitEnabled() ? "ON" : "OFF");
    }
    
    /**
     * 重置统计信息
     */
    public synchronized void reset() {
        callHistory.clear();
        totalCalls.set(0);
        lastCallTime = null;
        LOGGER.info("AI API Rate Limiter statistics reset");
    }
}
