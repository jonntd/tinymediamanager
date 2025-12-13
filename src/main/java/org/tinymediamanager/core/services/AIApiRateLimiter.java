package org.tinymediamanager.core.services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Settings;

/**
 * 全局AI API调用频率限制器 防止AI服务疯狂调用，保护API配额和避免被限制
 */
public class AIApiRateLimiter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIApiRateLimiter.class);

    // 服务统计类
    private static class ServiceStats {
        private final AtomicInteger                        totalCalls      = new AtomicInteger(0);
        private final AtomicInteger                        successfulCalls = new AtomicInteger(0);
        private final AtomicInteger                        failedCalls     = new AtomicInteger(0);
        private final AtomicInteger                        timedOutCalls   = new AtomicInteger(0);
        private final ConcurrentLinkedQueue<LocalDateTime> callHistory     = new ConcurrentLinkedQueue<>();

        public void recordCall(boolean success, boolean timedOut) {
            totalCalls.incrementAndGet();
            if (success) {
                successfulCalls.incrementAndGet();
            }
            else {
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
            return callHistory.stream().filter(time -> ChronoUnit.MINUTES.between(time, now) == 0).count();
        }

        public long getCallsInLastHour() {
            LocalDateTime now = LocalDateTime.now();
            return callHistory.stream().filter(time -> ChronoUnit.HOURS.between(time, now) == 0).count();
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
    private final ConcurrentLinkedQueue<LocalDateTime> callHistory            = new ConcurrentLinkedQueue<>();
    private final AtomicInteger                        totalCalls             = new AtomicInteger(0);
    private volatile LocalDateTime                     lastCallTime           = null;

    // 服务级统计
    private final Map<String, ServiceStats>            serviceStatsMap        = new ConcurrentHashMap<>();

    // 动态速率调整
    private volatile long                              dynamicWaitTimeMs      = 0;                            // 动态调整的等待时间
    private volatile int                               consecutive429Errors   = 0;                            // 连续429错误计数
    private static final long                          MAX_DYNAMIC_WAIT_TIME  = 60000;                        // 最大动态等待时间（60秒）
    private static final long                          BASE_DYNAMIC_WAIT_TIME = 1000;                         // 基础动态等待时间（1秒）

    // AI请求结果缓存
    private static class CacheEntry {
        private final String        result;
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

    private final Map<String, CacheEntry> aiCache        = new ConcurrentHashMap<>();
    private static final int              MAX_CACHE_SIZE = 1000;                     // 最大缓存条目数

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
     * 
     * @param serviceName
     *            服务名称（用于日志）
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
        LOGGER.debug("=== Rate Limit Check for {} ===", serviceName);

        // 清理过期的调用记录
        cleanupOldRecords(now);

        // 获取当前配置
        long minIntervalMs = getMinIntervalMs();
        int maxCallsPerMinute = getMaxCallsPerMinute();
        int maxCallsPerHour = getMaxCallsPerHour();
        LOGGER.debug("Current rate limit config: minInterval={}ms, maxPerMinute={}, maxPerHour={}, dynamicWait={}ms", minIntervalMs,
                maxCallsPerMinute, maxCallsPerHour, dynamicWaitTimeMs);

        // 检查最小间隔
        if (lastCallTime != null) {
            long intervalMs = ChronoUnit.MILLIS.between(lastCallTime, now);
            LOGGER.debug("Last call was {}ms ago, min required: {}ms", intervalMs, minIntervalMs);
            if (intervalMs < minIntervalMs) {
                LOGGER.warn("API call rejected for {} - too frequent ({}ms < {}ms)", serviceName, intervalMs, minIntervalMs);
                // 记录服务统计 - 失败
                serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(false, false);
                return false;
            }
        }

        // 检查每分钟限制
        long callsInLastMinute = callHistory.stream().filter(time -> ChronoUnit.MINUTES.between(time, now) == 0).count();
        LOGGER.debug("Calls in last minute: {}/{} (max)", callsInLastMinute, maxCallsPerMinute);

        if (callsInLastMinute >= maxCallsPerMinute) {
            LOGGER.warn("API call rejected for {} - minute limit exceeded ({}/{})", serviceName, callsInLastMinute, maxCallsPerMinute);
            // 记录服务统计 - 失败
            serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(false, false);
            return false;
        }

        // 检查每小时限制
        long callsInLastHour = callHistory.stream().filter(time -> ChronoUnit.HOURS.between(time, now) == 0).count();
        LOGGER.debug("Calls in last hour: {}/{} (max)", callsInLastHour, maxCallsPerHour);

        if (callsInLastHour >= maxCallsPerHour) {
            LOGGER.warn("API call rejected for {} - hourly limit exceeded ({}/{})", serviceName, callsInLastHour, maxCallsPerHour);
            // 记录服务统计 - 失败
            serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(false, false);
            return false;
        }

        // 动态限流检查
        if (dynamicWaitTimeMs > 0) {
            if (lastCallTime != null) {
                long timeSinceLast = ChronoUnit.MILLIS.between(lastCallTime, now);
                if (timeSinceLast < dynamicWaitTimeMs) {
                    LOGGER.warn("API call rejected for {} - dynamic rate limiting active (wait {}ms)", serviceName,
                            dynamicWaitTimeMs - timeSinceLast);
                    return false;
                }
            }
        }

        // 允许调用
        lastCallTime = now;
        callHistory.offer(now);
        serviceStatsMap.computeIfAbsent(serviceName, k -> new ServiceStats()).recordCall(true, false);
        return true;
    }

    /**
     * 报告API调用成功 用于逐渐减少动态等待时间
     */
    public synchronized void recordSuccessfulCall() {
        if (consecutive429Errors > 0) {
            consecutive429Errors = 0;
            dynamicWaitTimeMs = 0;
            LOGGER.info("API call successful, resetting dynamic wait time");
        }
    }

    /**
     * 报告API调用遇到429错误 用于触发动态限流
     */
    public synchronized void record429Error() {
        consecutive429Errors++;
        // 指数退避: 1s, 2s, 4s, 8s... up to 60s
        long waitTime = Math.min(MAX_DYNAMIC_WAIT_TIME, BASE_DYNAMIC_WAIT_TIME * (1L << (consecutive429Errors - 1)));
        dynamicWaitTimeMs = waitTime;
        LOGGER.warn("Received 429 too many requests. Increasing dynamic wait time to {}ms (consecutive errors: {})", dynamicWaitTimeMs,
                consecutive429Errors);
    }

    /**
     * 获取缓存结果
     */
    public String getFromCache(String cacheKey) {
        CacheEntry entry = aiCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            return entry.getResult();
        }
        if (entry != null && entry.isExpired()) {
            aiCache.remove(cacheKey);
        }
        return null; // 未命中或已过期
    }

    /**
     * 更新缓存
     */
    public void addToCache(String cacheKey, String result) {
        if (aiCache.size() >= MAX_CACHE_SIZE) {
            aiCache.clear();
        }
        aiCache.put(cacheKey, new CacheEntry(result));
    }

    private void cleanupOldRecords(LocalDateTime now) {
        // 清理超过1小时的记录
        callHistory.removeIf(time -> ChronoUnit.HOURS.between(time, now) > 1);

        // 清理过期缓存
        aiCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 获取统计信息字符串
     */
    public String getStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        if (serviceStatsMap.isEmpty()) {
            sb.append("No activity yet");
        }
        else {
            serviceStatsMap.forEach((name, stats) -> {
                sb.append(String.format("<b>%s</b>: %d calls (%d OK, %d Fail)<br>", name, stats.getTotalCalls(), stats.getSuccessfulCalls(),
                        stats.getFailedCalls()));
            });
            // Total summary
            long total = serviceStatsMap.values().stream().mapToInt(ServiceStats::getTotalCalls).sum();
            sb.append(String.format("<br>Total Calls: %d", total));
            if (dynamicWaitTimeMs > 0) {
                sb.append(String.format("<br><font color='red'>Rate Limited (Wait %dms)</font>", dynamicWaitTimeMs));
            }
        }
        sb.append("</html>");
        return sb.toString();
    }

    /**
     * 重置所有统计信息
     */
    public boolean waitForPermission(String serviceName, int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (requestPermission(serviceName)) {
                return true;
            }
            try {
                long wait = dynamicWaitTimeMs > 0 ? dynamicWaitTimeMs : 1000;
                Thread.sleep(Math.min(wait, 1000));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 重置所有统计信息
     */
    public synchronized void reset() {
        callHistory.clear();
        totalCalls.set(0);
        lastCallTime = null;
        dynamicWaitTimeMs = 0;
        consecutive429Errors = 0;
        serviceStatsMap.clear();
        aiCache.clear();
        LOGGER.info("AI Rate Limiter statistics reset");
    }
}