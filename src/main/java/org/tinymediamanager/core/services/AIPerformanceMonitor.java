package org.tinymediamanager.core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * AI性能监控仪表板
 * 提供详细的AI服务性能统计和监控功能
 */
public class AIPerformanceMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AIPerformanceMonitor.class);
    private static volatile AIPerformanceMonitor instance;
    private static final Object LOCK = new Object();
    
    // 性能指标存储
    private final ConcurrentHashMap<String, ServiceMetrics> serviceMetrics = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock metricsLock = new ReentrantReadWriteLock();
    
    // 全局统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    
    // 时间窗口统计（滑动窗口）
    private final TimeWindowStats minuteStats = new TimeWindowStats(60 * 1000); // 1分钟
    private final TimeWindowStats hourStats = new TimeWindowStats(60 * 60 * 1000); // 1小时
    
    private AIPerformanceMonitor() {
        // 启动定期清理任务
        startPeriodicCleanup();
    }
    
    public static AIPerformanceMonitor getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new AIPerformanceMonitor();
                }
            }
        }
        return instance;
    }
    
    /**
     * 记录AI服务调用
     */
    public void recordAPICall(String serviceName, long responseTimeMs, boolean success) {
        // 更新全局统计
        totalRequests.incrementAndGet();
        totalResponseTime.addAndGet(responseTimeMs);
        
        if (success) {
            totalSuccesses.incrementAndGet();
        } else {
            totalFailures.incrementAndGet();
        }
        
        // 更新时间窗口统计
        long currentTime = System.currentTimeMillis();
        minuteStats.recordCall(currentTime, responseTimeMs, success);
        hourStats.recordCall(currentTime, responseTimeMs, success);
        
        // 更新服务级别统计
        serviceMetrics.computeIfAbsent(serviceName, k -> new ServiceMetrics(k))
                     .recordCall(responseTimeMs, success);
        
        LOGGER.debug("Recorded API call: service={}, responseTime={}ms, success={}", 
                    serviceName, responseTimeMs, success);
    }
    
    /**
     * 获取性能报告
     */
    public PerformanceReport getPerformanceReport() {
        metricsLock.readLock().lock();
        try {
            return new PerformanceReport(
                totalRequests.get(),
                totalSuccesses.get(),
                totalFailures.get(),
                getAverageResponseTime(),
                getSuccessRate(),
                minuteStats.getStats(),
                hourStats.getStats(),
                getServiceReports()
            );
        } finally {
            metricsLock.readLock().unlock();
        }
    }
    
    /**
     * 获取缓存性能统计
     */
    public CachePerformanceStats getCacheStats() {
        // 从TvShowEpisodeAndSeasonParser获取缓存统计
        return new CachePerformanceStats(
            org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.getCacheHits(),
            org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.getCacheMisses(),
            org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.getCacheSize()
        );
    }
    
    private double getAverageResponseTime() {
        long requests = totalRequests.get();
        return requests > 0 ? (double) totalResponseTime.get() / requests : 0.0;
    }
    
    private double getSuccessRate() {
        long requests = totalRequests.get();
        return requests > 0 ? (double) totalSuccesses.get() / requests * 100.0 : 0.0;
    }
    
    private List<ServiceReport> getServiceReports() {
        List<ServiceReport> reports = new ArrayList<>();
        for (ServiceMetrics metrics : serviceMetrics.values()) {
            reports.add(metrics.getReport());
        }
        return reports;
    }
    
    private void startPeriodicCleanup() {
        // 启动后台线程定期清理过期数据
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5 * 60 * 1000); // 5分钟清理一次
                    cleanupExpiredData();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("AI-Performance-Monitor-Cleanup");
        cleanupThread.start();
    }
    
    private void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();
        minuteStats.cleanup(currentTime);
        hourStats.cleanup(currentTime);
        LOGGER.debug("Performance monitor cleanup completed");
    }
    
    /**
     * 服务级别性能指标
     */
    private static class ServiceMetrics {
        private final String serviceName;
        private final AtomicLong requests = new AtomicLong(0);
        private final AtomicLong successes = new AtomicLong(0);
        private final AtomicLong failures = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private volatile long lastCallTime = 0;
        
        ServiceMetrics(String serviceName) {
            this.serviceName = serviceName;
        }
        
        void recordCall(long responseTimeMs, boolean success) {
            requests.incrementAndGet();
            totalResponseTime.addAndGet(responseTimeMs);
            lastCallTime = System.currentTimeMillis();
            
            if (success) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
        }
        
        ServiceReport getReport() {
            long reqs = requests.get();
            return new ServiceReport(
                serviceName,
                reqs,
                successes.get(),
                failures.get(),
                reqs > 0 ? (double) totalResponseTime.get() / reqs : 0.0,
                reqs > 0 ? (double) successes.get() / reqs * 100.0 : 0.0,
                lastCallTime
            );
        }
    }
    
    /**
     * 时间窗口统计
     */
    private static class TimeWindowStats {
        private final long windowSizeMs;
        private final List<CallRecord> calls = Collections.synchronizedList(new ArrayList<>());
        
        TimeWindowStats(long windowSizeMs) {
            this.windowSizeMs = windowSizeMs;
        }
        
        void recordCall(long timestamp, long responseTimeMs, boolean success) {
            calls.add(new CallRecord(timestamp, responseTimeMs, success));
        }
        
        WindowStats getStats() {
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - windowSizeMs;
            
            synchronized (calls) {
                List<CallRecord> windowCalls = new ArrayList<>();
                for (CallRecord call : calls) {
                    if (call.timestamp >= windowStart) {
                        windowCalls.add(call);
                    }
                }
                
                if (windowCalls.isEmpty()) {
                    return new WindowStats(0, 0, 0, 0.0, 0.0);
                }
                
                int total = windowCalls.size();
                int successes = 0;
                long totalResponseTime = 0;
                
                for (CallRecord call : windowCalls) {
                    if (call.success) successes++;
                    totalResponseTime += call.responseTimeMs;
                }
                
                return new WindowStats(
                    total,
                    successes,
                    total - successes,
                    (double) totalResponseTime / total,
                    (double) successes / total * 100.0
                );
            }
        }
        
        void cleanup(long currentTime) {
            long windowStart = currentTime - windowSizeMs;
            synchronized (calls) {
                calls.removeIf(call -> call.timestamp < windowStart);
            }
        }
    }
    
    /**
     * 调用记录
     */
    private static class CallRecord {
        final long timestamp;
        final long responseTimeMs;
        final boolean success;
        
        CallRecord(long timestamp, long responseTimeMs, boolean success) {
            this.timestamp = timestamp;
            this.responseTimeMs = responseTimeMs;
            this.success = success;
        }
    }
    
    // 数据传输对象
    public static class PerformanceReport {
        public final long totalRequests;
        public final long totalSuccesses;
        public final long totalFailures;
        public final double averageResponseTime;
        public final double successRate;
        public final WindowStats lastMinute;
        public final WindowStats lastHour;
        public final List<ServiceReport> serviceReports;
        
        PerformanceReport(long totalRequests, long totalSuccesses, long totalFailures,
                         double averageResponseTime, double successRate,
                         WindowStats lastMinute, WindowStats lastHour,
                         List<ServiceReport> serviceReports) {
            this.totalRequests = totalRequests;
            this.totalSuccesses = totalSuccesses;
            this.totalFailures = totalFailures;
            this.averageResponseTime = averageResponseTime;
            this.successRate = successRate;
            this.lastMinute = lastMinute;
            this.lastHour = lastHour;
            this.serviceReports = serviceReports;
        }
    }
    
    public static class ServiceReport {
        public final String serviceName;
        public final long requests;
        public final long successes;
        public final long failures;
        public final double averageResponseTime;
        public final double successRate;
        public final long lastCallTime;
        
        ServiceReport(String serviceName, long requests, long successes, long failures,
                     double averageResponseTime, double successRate, long lastCallTime) {
            this.serviceName = serviceName;
            this.requests = requests;
            this.successes = successes;
            this.failures = failures;
            this.averageResponseTime = averageResponseTime;
            this.successRate = successRate;
            this.lastCallTime = lastCallTime;
        }
    }
    
    public static class WindowStats {
        public final int totalCalls;
        public final int successfulCalls;
        public final int failedCalls;
        public final double averageResponseTime;
        public final double successRate;
        
        WindowStats(int totalCalls, int successfulCalls, int failedCalls,
                   double averageResponseTime, double successRate) {
            this.totalCalls = totalCalls;
            this.successfulCalls = successfulCalls;
            this.failedCalls = failedCalls;
            this.averageResponseTime = averageResponseTime;
            this.successRate = successRate;
        }
    }
    
    public static class CachePerformanceStats {
        public final long cacheHits;
        public final long cacheMisses;
        public final int cacheSize;
        public final double hitRate;
        
        CachePerformanceStats(long cacheHits, long cacheMisses, int cacheSize) {
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheSize = cacheSize;
            long total = cacheHits + cacheMisses;
            this.hitRate = total > 0 ? (double) cacheHits / total * 100.0 : 0.0;
        }
    }
}
