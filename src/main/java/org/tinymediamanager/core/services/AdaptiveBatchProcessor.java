package org.tinymediamanager.core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.ArrayList;

/**
 * 自适应批量处理器
 * 根据API响应时间动态调整批量大小，实现最优性能
 */
public class AdaptiveBatchProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdaptiveBatchProcessor.class);
    private static volatile AdaptiveBatchProcessor instance;
    private static final Object LOCK = new Object();
    
    // 批量大小配置
    private static final int MIN_BATCH_SIZE = 3;
    private static final int MAX_BATCH_SIZE = 20;
    private static final int DEFAULT_BATCH_SIZE = 10;
    
    // 响应时间阈值（毫秒）
    private static final long FAST_RESPONSE_THRESHOLD = 2000;  // 2秒
    private static final long SLOW_RESPONSE_THRESHOLD = 5000;  // 5秒
    private static final long TIMEOUT_THRESHOLD = 10000;       // 10秒
    
    // 当前批量大小和统计
    private final AtomicInteger currentBatchSize = new AtomicInteger(DEFAULT_BATCH_SIZE);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicInteger consecutiveTimeouts = new AtomicInteger(0);
    private final AtomicInteger consecutiveFastResponses = new AtomicInteger(0);
    
    // 响应时间历史（用于移动平均）
    private final List<Long> responseTimeHistory = new ArrayList<>();
    private final ReentrantReadWriteLock historyLock = new ReentrantReadWriteLock();
    private static final int HISTORY_SIZE = 10;
    
    // 性能统计
    private final AtomicLong batchSizeAdjustments = new AtomicLong(0);
    private final AtomicLong performanceImprovements = new AtomicLong(0);
    
    private AdaptiveBatchProcessor() {
        LOGGER.info("AdaptiveBatchProcessor initialized with default batch size: {}", DEFAULT_BATCH_SIZE);
    }
    
    public static AdaptiveBatchProcessor getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new AdaptiveBatchProcessor();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取当前推荐的批量大小
     */
    public int getCurrentBatchSize() {
        return currentBatchSize.get();
    }
    
    /**
     * 记录批量处理的响应时间并调整批量大小
     */
    public void recordBatchResponse(long responseTimeMs, int batchSize, boolean success) {
        totalRequests.incrementAndGet();
        totalResponseTime.addAndGet(responseTimeMs);
        
        // 更新响应时间历史
        updateResponseTimeHistory(responseTimeMs);
        
        // 根据响应时间调整批量大小
        adjustBatchSize(responseTimeMs, batchSize, success);
        
        LOGGER.debug("Recorded batch response: {}ms for batch size {}, success: {}, new batch size: {}", 
                    responseTimeMs, batchSize, success, currentBatchSize.get());
    }
    
    /**
     * 根据响应时间智能调整批量大小
     */
    private void adjustBatchSize(long responseTimeMs, int actualBatchSize, boolean success) {
        int currentSize = currentBatchSize.get();
        int newSize = currentSize;
        boolean adjusted = false;
        
        if (!success || responseTimeMs >= TIMEOUT_THRESHOLD) {
            // 超时或失败：减小批量大小
            consecutiveTimeouts.incrementAndGet();
            consecutiveFastResponses.set(0);
            
            if (consecutiveTimeouts.get() >= 2) {
                newSize = Math.max(MIN_BATCH_SIZE, currentSize - 2);
                adjusted = true;
                LOGGER.warn("Consecutive timeouts detected, reducing batch size from {} to {}", currentSize, newSize);
            }
            
        } else if (responseTimeMs <= FAST_RESPONSE_THRESHOLD) {
            // 快速响应：可以增大批量大小
            consecutiveFastResponses.incrementAndGet();
            consecutiveTimeouts.set(0);
            
            if (consecutiveFastResponses.get() >= 3 && currentSize < MAX_BATCH_SIZE) {
                newSize = Math.min(MAX_BATCH_SIZE, currentSize + 1);
                adjusted = true;
                LOGGER.info("Fast responses detected, increasing batch size from {} to {}", currentSize, newSize);
            }
            
        } else if (responseTimeMs >= SLOW_RESPONSE_THRESHOLD) {
            // 慢响应：减小批量大小
            consecutiveTimeouts.incrementAndGet();
            consecutiveFastResponses.set(0);
            
            if (currentSize > MIN_BATCH_SIZE) {
                newSize = Math.max(MIN_BATCH_SIZE, currentSize - 1);
                adjusted = true;
                LOGGER.info("Slow response detected, reducing batch size from {} to {}", currentSize, newSize);
            }
            
        } else {
            // 正常响应：重置计数器
            consecutiveTimeouts.set(0);
            consecutiveFastResponses.set(0);
        }
        
        // 应用新的批量大小
        if (adjusted && newSize != currentSize) {
            currentBatchSize.set(newSize);
            batchSizeAdjustments.incrementAndGet();
            
            // 评估性能改进
            if (isPerformanceImprovement(newSize, currentSize)) {
                performanceImprovements.incrementAndGet();
            }
        }
    }
    
    /**
     * 更新响应时间历史
     */
    private void updateResponseTimeHistory(long responseTimeMs) {
        historyLock.writeLock().lock();
        try {
            responseTimeHistory.add(responseTimeMs);
            
            // 保持历史大小限制
            while (responseTimeHistory.size() > HISTORY_SIZE) {
                responseTimeHistory.remove(0);
            }
        } finally {
            historyLock.writeLock().unlock();
        }
    }
    
    /**
     * 计算移动平均响应时间
     */
    public double getAverageResponseTime() {
        historyLock.readLock().lock();
        try {
            if (responseTimeHistory.isEmpty()) {
                return 0.0;
            }
            
            long sum = 0;
            for (Long time : responseTimeHistory) {
                sum += time;
            }
            return (double) sum / responseTimeHistory.size();
        } finally {
            historyLock.readLock().unlock();
        }
    }
    
    /**
     * 评估是否为性能改进
     */
    private boolean isPerformanceImprovement(int newSize, int oldSize) {
        double avgResponseTime = getAverageResponseTime();
        
        // 如果平均响应时间较低且批量大小增加，或者响应时间较高且批量大小减少，则认为是改进
        if (avgResponseTime < FAST_RESPONSE_THRESHOLD && newSize > oldSize) {
            return true;
        } else if (avgResponseTime > SLOW_RESPONSE_THRESHOLD && newSize < oldSize) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取推荐的批量大小（考虑当前网络状况）
     */
    public int getRecommendedBatchSize() {
        double avgResponseTime = getAverageResponseTime();
        int currentSize = currentBatchSize.get();
        
        // 基于历史响应时间推荐批量大小
        if (avgResponseTime == 0.0) {
            return currentSize; // 没有历史数据，使用当前大小
        } else if (avgResponseTime < FAST_RESPONSE_THRESHOLD) {
            return Math.min(MAX_BATCH_SIZE, currentSize + 1); // 网络快，可以增大
        } else if (avgResponseTime > SLOW_RESPONSE_THRESHOLD) {
            return Math.max(MIN_BATCH_SIZE, currentSize - 1); // 网络慢，应该减小
        } else {
            return currentSize; // 网络正常，保持当前大小
        }
    }
    
    /**
     * 获取性能统计报告
     */
    public AdaptivePerformanceReport getPerformanceReport() {
        return new AdaptivePerformanceReport(
            currentBatchSize.get(),
            totalRequests.get(),
            getAverageResponseTime(),
            batchSizeAdjustments.get(),
            performanceImprovements.get(),
            consecutiveTimeouts.get(),
            consecutiveFastResponses.get(),
            getEfficiencyScore()
        );
    }
    
    /**
     * 计算效率分数（0-100）
     */
    private double getEfficiencyScore() {
        long requests = totalRequests.get();
        if (requests == 0) return 100.0;
        
        double avgResponseTime = getAverageResponseTime();
        int currentSize = currentBatchSize.get();
        
        // 基于响应时间和批量大小计算效率分数
        double timeScore = Math.max(0, 100 - (avgResponseTime / 100)); // 响应时间越短分数越高
        double sizeScore = (double) currentSize / MAX_BATCH_SIZE * 100; // 批量大小越大分数越高
        
        return (timeScore + sizeScore) / 2;
    }
    
    /**
     * 重置统计数据
     */
    public void resetStatistics() {
        totalRequests.set(0);
        totalResponseTime.set(0);
        batchSizeAdjustments.set(0);
        performanceImprovements.set(0);
        consecutiveTimeouts.set(0);
        consecutiveFastResponses.set(0);
        currentBatchSize.set(DEFAULT_BATCH_SIZE);
        
        historyLock.writeLock().lock();
        try {
            responseTimeHistory.clear();
        } finally {
            historyLock.writeLock().unlock();
        }
        
        LOGGER.info("AdaptiveBatchProcessor statistics reset");
    }
    
    /**
     * 自适应性能报告
     */
    public static class AdaptivePerformanceReport {
        public final int currentBatchSize;
        public final long totalRequests;
        public final double averageResponseTime;
        public final long batchSizeAdjustments;
        public final long performanceImprovements;
        public final int consecutiveTimeouts;
        public final int consecutiveFastResponses;
        public final double efficiencyScore;
        
        AdaptivePerformanceReport(int currentBatchSize, long totalRequests, double averageResponseTime,
                                 long batchSizeAdjustments, long performanceImprovements,
                                 int consecutiveTimeouts, int consecutiveFastResponses, double efficiencyScore) {
            this.currentBatchSize = currentBatchSize;
            this.totalRequests = totalRequests;
            this.averageResponseTime = averageResponseTime;
            this.batchSizeAdjustments = batchSizeAdjustments;
            this.performanceImprovements = performanceImprovements;
            this.consecutiveTimeouts = consecutiveTimeouts;
            this.consecutiveFastResponses = consecutiveFastResponses;
            this.efficiencyScore = efficiencyScore;
        }
        
        @Override
        public String toString() {
            return String.format(
                "AdaptivePerformanceReport{batchSize=%d, requests=%d, avgResponseTime=%.1fms, " +
                "adjustments=%d, improvements=%d, efficiency=%.1f%%}",
                currentBatchSize, totalRequests, averageResponseTime,
                batchSizeAdjustments, performanceImprovements, efficiencyScore
            );
        }
    }
}
