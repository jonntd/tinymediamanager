package org.tinymediamanager.core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 重试工具类 提供指数退避和重试相关的通用方法
 */
public final class RetryUtils {
    private static final Logger LOGGER                = LoggerFactory.getLogger(RetryUtils.class);

    // 默认基础延迟时间（毫秒）
    public static final long    DEFAULT_BASE_DELAY_MS = 3000L;

    // 最大延迟时间（毫秒）
    public static final long    MAX_DELAY_MS          = 60000L;

    private RetryUtils() {
        // 工具类不允许实例化
    }

    /**
     * 计算指数退避延迟时间
     * 
     * @param attempt
     *            当前尝试次数（从1开始）
     * @param baseDelayMs
     *            基础延迟时间（毫秒）
     * @return 计算后的延迟时间
     */
    public static long exponentialBackoffDelay(int attempt, long baseDelayMs) {
        if (attempt <= 0) {
            return baseDelayMs;
        }

        // 使用位移运算计算指数：baseDelay * 2^(attempt-1)
        long delay = baseDelayMs * (1L << (attempt - 1));

        // 限制最大延迟时间
        return Math.min(delay, MAX_DELAY_MS);
    }

    /**
     * 使用默认基础延迟计算指数退避延迟时间
     * 
     * @param attempt
     *            当前尝试次数（从1开始）
     * @return 计算后的延迟时间 (3s, 6s, 12s, 24s, ...)
     */
    public static long exponentialBackoffDelay(int attempt) {
        return exponentialBackoffDelay(attempt, DEFAULT_BASE_DELAY_MS);
    }

    /**
     * 带中断处理的睡眠
     * 
     * @param millis
     *            睡眠毫秒数
     * @return true如果正常完成睡眠，false如果被中断
     */
    public static boolean sleepWithInterruptHandling(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.debug("Sleep interrupted after {}ms", millis);
            return false;
        }
    }

    /**
     * 带日志的重试等待（使用指数退避）
     * 
     * @param attempt
     *            当前尝试次数
     * @param context
     *            用于日志的上下文描述
     * @return true如果正常等待完成，false如果被中断
     */
    public static boolean waitBeforeRetry(int attempt, String context) {
        long delayMs = exponentialBackoffDelay(attempt);
        LOGGER.info("{}: 等待 {}ms 后重试 (尝试 {})", context, delayMs, attempt);
        return sleepWithInterruptHandling(delayMs);
    }

    /**
     * 带日志的重试等待
     * 
     * @param delayMs
     *            等待毫秒数
     * @param context
     *            用于日志的上下文描述
     * @return true如果正常等待完成，false如果被中断
     */
    public static boolean waitBeforeRetry(long delayMs, String context) {
        LOGGER.info("{}: 等待 {}ms 后重试", context, delayMs);
        return sleepWithInterruptHandling(delayMs);
    }

    /**
     * 检查是否应该继续重试
     * 
     * @param currentAttempt
     *            当前尝试次数
     * @param maxRetries
     *            最大重试次数
     * @return true如果可以继续重试
     */
    public static boolean shouldRetry(int currentAttempt, int maxRetries) {
        return currentAttempt < maxRetries;
    }

    /**
     * 检查错误消息是否表示速率限制
     * 
     * @param errorMessage
     *            错误消息
     * @return true如果是速率限制错误
     */
    public static boolean isRateLimitError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        String lower = errorMessage.toLowerCase();
        return lower.contains("rate limit") || lower.contains("quota") || lower.contains("limit exceeded") || lower.contains("too many requests")
                || lower.contains("usage");
    }

    /**
     * 获取速率限制错误的推荐等待时间
     * 
     * @param attempt
     *            当前尝试次数
     * @return 推荐的等待时间（毫秒）
     */
    public static long getRateLimitWaitTime(int attempt) {
        // 速率限制错误使用更长的等待时间
        return 10000L * attempt;
    }
}
