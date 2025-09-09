package org.tinymediamanager.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 修复统计工具类
 * 用于监控和统计各种修复功能的效果
 */
public class FixStatistics {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixStatistics.class);
    
    // 文件大小修复统计
    private static final AtomicInteger fileSizeUpdatesCount = new AtomicInteger(0);
    private static final AtomicInteger fileSizeUpdateFailures = new AtomicInteger(0);
    private static final AtomicLong totalFileSizeUpdated = new AtomicLong(0);
    
    // AI识别统计
    private static final AtomicInteger aiRecognitionAttempts = new AtomicInteger(0);
    private static final AtomicInteger aiRecognitionWithYear = new AtomicInteger(0);
    private static final AtomicInteger aiRecognitionRetries = new AtomicInteger(0);
    private static final AtomicInteger aiRecognitionRetrySuccess = new AtomicInteger(0);
    
    // TMDB搜索统计
    private static final AtomicInteger tmdbSearches = new AtomicInteger(0);
    private static final AtomicInteger tmdbSearchEarlyStops = new AtomicInteger(0);
    private static final AtomicLong totalPagesSearched = new AtomicLong(0);
    
    // 启动时间
    private static final LocalDateTime startTime = LocalDateTime.now();
    
    /**
     * 记录文件大小更新成功
     */
    public static void recordFileSizeUpdate(long fileSize) {
        fileSizeUpdatesCount.incrementAndGet();
        totalFileSizeUpdated.addAndGet(fileSize);
        LOGGER.trace("File size update recorded: {} bytes", fileSize);
    }
    
    /**
     * 记录文件大小更新失败
     */
    public static void recordFileSizeUpdateFailure() {
        fileSizeUpdateFailures.incrementAndGet();
        LOGGER.trace("File size update failure recorded");
    }
    
    /**
     * 记录AI识别尝试
     */
    public static void recordAIRecognitionAttempt() {
        aiRecognitionAttempts.incrementAndGet();
        LOGGER.trace("AI recognition attempt recorded");
    }
    
    /**
     * 记录AI识别成功（包含年份）
     */
    public static void recordAIRecognitionWithYear() {
        aiRecognitionWithYear.incrementAndGet();
        LOGGER.trace("AI recognition with year recorded");
    }
    
    /**
     * 记录AI识别重试
     */
    public static void recordAIRecognitionRetry() {
        aiRecognitionRetries.incrementAndGet();
        LOGGER.trace("AI recognition retry recorded");
    }
    
    /**
     * 记录AI识别重试成功
     */
    public static void recordAIRecognitionRetrySuccess() {
        aiRecognitionRetrySuccess.incrementAndGet();
        LOGGER.trace("AI recognition retry success recorded");
    }
    
    /**
     * 记录TMDB搜索
     */
    public static void recordTMDBSearch(int pagesSearched, boolean earlyStop) {
        tmdbSearches.incrementAndGet();
        totalPagesSearched.addAndGet(pagesSearched);
        if (earlyStop) {
            tmdbSearchEarlyStops.incrementAndGet();
        }
        LOGGER.trace("TMDB search recorded: {} pages, early stop: {}", pagesSearched, earlyStop);
    }
    
    /**
     * 获取统计报告
     */
    public static String getStatisticsReport() {
        StringBuilder report = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        report.append("=== tinyMediaManager 修复统计报告 ===\n");
        report.append("统计开始时间: ").append(startTime.format(formatter)).append("\n");
        report.append("当前时间: ").append(LocalDateTime.now().format(formatter)).append("\n\n");
        
        // 文件大小修复统计
        report.append("📁 文件大小修复统计:\n");
        report.append("  - 成功更新次数: ").append(fileSizeUpdatesCount.get()).append("\n");
        report.append("  - 失败次数: ").append(fileSizeUpdateFailures.get()).append("\n");
        report.append("  - 总更新文件大小: ").append(formatFileSize(totalFileSizeUpdated.get())).append("\n");
        if (fileSizeUpdatesCount.get() > 0) {
            double successRate = (double) fileSizeUpdatesCount.get() / 
                               (fileSizeUpdatesCount.get() + fileSizeUpdateFailures.get()) * 100;
            report.append("  - 成功率: ").append(String.format("%.1f%%", successRate)).append("\n");
        }
        report.append("\n");
        
        // AI识别统计
        report.append("🤖 AI识别统计:\n");
        report.append("  - 识别尝试次数: ").append(aiRecognitionAttempts.get()).append("\n");
        report.append("  - 包含年份次数: ").append(aiRecognitionWithYear.get()).append("\n");
        report.append("  - 重试次数: ").append(aiRecognitionRetries.get()).append("\n");
        report.append("  - 重试成功次数: ").append(aiRecognitionRetrySuccess.get()).append("\n");
        if (aiRecognitionAttempts.get() > 0) {
            double yearRate = (double) aiRecognitionWithYear.get() / aiRecognitionAttempts.get() * 100;
            report.append("  - 年份包含率: ").append(String.format("%.1f%%", yearRate)).append("\n");
        }
        if (aiRecognitionRetries.get() > 0) {
            double retrySuccessRate = (double) aiRecognitionRetrySuccess.get() / aiRecognitionRetries.get() * 100;
            report.append("  - 重试成功率: ").append(String.format("%.1f%%", retrySuccessRate)).append("\n");
        }
        report.append("\n");
        
        // TMDB搜索统计
        report.append("🔍 TMDB搜索统计:\n");
        report.append("  - 搜索次数: ").append(tmdbSearches.get()).append("\n");
        report.append("  - 提前停止次数: ").append(tmdbSearchEarlyStops.get()).append("\n");
        report.append("  - 总搜索页面数: ").append(totalPagesSearched.get()).append("\n");
        if (tmdbSearches.get() > 0) {
            double avgPages = (double) totalPagesSearched.get() / tmdbSearches.get();
            report.append("  - 平均搜索页面数: ").append(String.format("%.1f", avgPages)).append("\n");
            double earlyStopRate = (double) tmdbSearchEarlyStops.get() / tmdbSearches.get() * 100;
            report.append("  - 提前停止率: ").append(String.format("%.1f%%", earlyStopRate)).append("\n");
        }
        
        report.append("\n=== 报告结束 ===");
        return report.toString();
    }
    
    /**
     * 打印统计报告到日志
     */
    public static void logStatisticsReport() {
        String report = getStatisticsReport();
        LOGGER.info("\n{}", report);
    }
    
    /**
     * 重置所有统计数据
     */
    public static void resetStatistics() {
        fileSizeUpdatesCount.set(0);
        fileSizeUpdateFailures.set(0);
        totalFileSizeUpdated.set(0);
        
        aiRecognitionAttempts.set(0);
        aiRecognitionWithYear.set(0);
        aiRecognitionRetries.set(0);
        aiRecognitionRetrySuccess.set(0);
        
        tmdbSearches.set(0);
        tmdbSearchEarlyStops.set(0);
        totalPagesSearched.set(0);
        
        LOGGER.info("Statistics reset completed");
    }
    
    /**
     * 格式化文件大小显示
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    /**
     * 获取简要统计信息
     */
    public static String getBriefStatistics() {
        return String.format("修复统计 - 文件大小更新:%d次, AI识别:%d次(年份率:%.1f%%), TMDB搜索:%d次(平均%.1f页)",
            fileSizeUpdatesCount.get(),
            aiRecognitionAttempts.get(),
            aiRecognitionAttempts.get() > 0 ? (double) aiRecognitionWithYear.get() / aiRecognitionAttempts.get() * 100 : 0,
            tmdbSearches.get(),
            tmdbSearches.get() > 0 ? (double) totalPagesSearched.get() / tmdbSearches.get() : 0
        );
    }
}
