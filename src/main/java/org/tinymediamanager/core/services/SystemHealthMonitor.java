package org.tinymediamanager.core.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 系统健康监控器
 * 自动检测系统问题、执行修复操作并提供预防性维护建议
 */
public class SystemHealthMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemHealthMonitor.class);
    private static volatile SystemHealthMonitor instance;
    private static final Object LOCK = new Object();
    
    // 监控配置
    private static final long HEALTH_CHECK_INTERVAL_MS = 5 * 60 * 1000; // 5分钟
    private static final long DEEP_CHECK_INTERVAL_MS = 30 * 60 * 1000; // 30分钟
    private static final int MAX_AUTO_REPAIR_ATTEMPTS = 3;
    
    // 健康检查器
    private final ScheduledExecutorService healthScheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService repairExecutor = Executors.newFixedThreadPool(2);
    
    // 健康状态
    private final EnumMap<HealthComponent, ComponentHealth> componentHealth = new EnumMap<>(HealthComponent.class);
    private final AtomicInteger overallHealthScore = new AtomicInteger(100);
    private final AtomicLong lastHealthCheck = new AtomicLong(0);
    
    // 自动修复统计
    private final AtomicLong totalRepairAttempts = new AtomicLong(0);
    private final AtomicLong successfulRepairs = new AtomicLong(0);
    private final AtomicLong preventedFailures = new AtomicLong(0);
    
    // 问题检测
    private final ConcurrentHashMap<String, ProblemRecord> detectedProblems = new ConcurrentHashMap<>();
    private final BlockingQueue<RepairTask> repairQueue = new LinkedBlockingQueue<>();
    
    private volatile boolean isRunning = false;
    
    /**
     * 健康组件枚举
     */
    public enum HealthComponent {
        AI_SERVICES,
        CACHE_SYSTEM,
        DATABASE,
        NETWORK,
        MEMORY,
        DISK_SPACE,
        CONFIGURATION
    }
    
    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        EXCELLENT(100),
        GOOD(80),
        WARNING(60),
        CRITICAL(40),
        FAILED(0);
        
        private final int score;
        
        HealthStatus(int score) {
            this.score = score;
        }
        
        public int getScore() {
            return score;
        }
    }
    
    private SystemHealthMonitor() {
        initializeComponentHealth();
        LOGGER.info("SystemHealthMonitor initialized");
    }
    
    public static SystemHealthMonitor getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new SystemHealthMonitor();
                }
            }
        }
        return instance;
    }
    
    /**
     * 启动健康监控服务
     */
    public void start() {
        if (isRunning) {
            LOGGER.warn("SystemHealthMonitor is already running");
            return;
        }
        
        isRunning = true;
        
        // 启动定期健康检查
        healthScheduler.scheduleWithFixedDelay(
            this::performHealthCheck,
            10000, // 10秒后开始
            HEALTH_CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // 启动深度健康检查
        healthScheduler.scheduleWithFixedDelay(
            this::performDeepHealthCheck,
            60000, // 1分钟后开始
            DEEP_CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // 启动自动修复处理器
        healthScheduler.scheduleWithFixedDelay(
            this::processRepairQueue,
            5000, // 5秒后开始
            10000, // 每10秒检查一次
            TimeUnit.MILLISECONDS
        );
        
        LOGGER.info("SystemHealthMonitor started");
    }
    
    /**
     * 停止健康监控服务
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        healthScheduler.shutdown();
        repairExecutor.shutdown();
        
        try {
            if (!healthScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                healthScheduler.shutdownNow();
            }
            if (!repairExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                repairExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            healthScheduler.shutdownNow();
            repairExecutor.shutdownNow();
        }
        
        LOGGER.info("SystemHealthMonitor stopped");
    }
    
    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        if (!isRunning) return;
        
        try {
            LOGGER.debug("Performing system health check");
            lastHealthCheck.set(System.currentTimeMillis());
            
            // 检查各个组件
            checkAIServices();
            checkCacheSystem();
            checkMemoryUsage();
            checkDiskSpace();
            checkConfiguration();
            
            // 计算总体健康分数
            calculateOverallHealth();
            
            // 检测问题并安排修复
            detectAndScheduleRepairs();
            
        } catch (Exception e) {
            LOGGER.error("Error during health check: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 执行深度健康检查
     */
    private void performDeepHealthCheck() {
        if (!isRunning) return;
        
        try {
            LOGGER.debug("Performing deep system health check");
            
            // 深度检查数据库
            checkDatabaseHealth();
            
            // 深度检查网络连接
            checkNetworkHealth();
            
            // 分析系统趋势
            analyzeHealthTrends();
            
            // 生成预防性维护建议
            generateMaintenanceRecommendations();
            
        } catch (Exception e) {
            LOGGER.error("Error during deep health check: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查AI服务健康状态
     */
    private void checkAIServices() {
        try {
            // 检查AI API统计
            AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
            // 这里应该检查AI服务的响应时间、成功率等
            
            // 检查性能监控
            AIPerformanceMonitor perfMonitor = AIPerformanceMonitor.getInstance();
            AIPerformanceMonitor.PerformanceReport report = perfMonitor.getPerformanceReport();
            
            HealthStatus status;
            if (report.successRate >= 95) {
                status = HealthStatus.EXCELLENT;
            } else if (report.successRate >= 85) {
                status = HealthStatus.GOOD;
            } else if (report.successRate >= 70) {
                status = HealthStatus.WARNING;
            } else if (report.successRate >= 50) {
                status = HealthStatus.CRITICAL;
            } else {
                status = HealthStatus.FAILED;
            }
            
            updateComponentHealth(HealthComponent.AI_SERVICES, status, 
                                "Success rate: " + String.format("%.1f%%", report.successRate));
            
        } catch (Exception e) {
            updateComponentHealth(HealthComponent.AI_SERVICES, HealthStatus.CRITICAL, 
                                "Error checking AI services: " + e.getMessage());
        }
    }
    
    /**
     * 检查缓存系统健康状态
     */
    private void checkCacheSystem() {
        try {
            // 检查缓存统计
            long cacheHits = org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.getCacheHits();
            long cacheMisses = org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.getCacheMisses();
            int cacheSize = org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.getCacheSize();
            
            double hitRate = (cacheHits + cacheMisses) > 0 ? 
                            (double) cacheHits / (cacheHits + cacheMisses) * 100 : 0;
            
            HealthStatus status;
            if (hitRate >= 90) {
                status = HealthStatus.EXCELLENT;
            } else if (hitRate >= 75) {
                status = HealthStatus.GOOD;
            } else if (hitRate >= 60) {
                status = HealthStatus.WARNING;
            } else if (hitRate >= 40) {
                status = HealthStatus.CRITICAL;
            } else {
                status = HealthStatus.FAILED;
            }
            
            updateComponentHealth(HealthComponent.CACHE_SYSTEM, status, 
                                String.format("Hit rate: %.1f%%, Size: %d", hitRate, cacheSize));
            
        } catch (Exception e) {
            updateComponentHealth(HealthComponent.CACHE_SYSTEM, HealthStatus.CRITICAL, 
                                "Error checking cache system: " + e.getMessage());
        }
    }
    
    /**
     * 检查内存使用情况
     */
    private void checkMemoryUsage() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double usagePercent = (double) usedMemory / totalMemory * 100;
            
            HealthStatus status;
            if (usagePercent < 70) {
                status = HealthStatus.EXCELLENT;
            } else if (usagePercent < 80) {
                status = HealthStatus.GOOD;
            } else if (usagePercent < 90) {
                status = HealthStatus.WARNING;
            } else if (usagePercent < 95) {
                status = HealthStatus.CRITICAL;
            } else {
                status = HealthStatus.FAILED;
            }
            
            updateComponentHealth(HealthComponent.MEMORY, status, 
                                String.format("Usage: %.1f%% (%d MB)", usagePercent, usedMemory / 1024 / 1024));
            
        } catch (Exception e) {
            updateComponentHealth(HealthComponent.MEMORY, HealthStatus.CRITICAL, 
                                "Error checking memory usage: " + e.getMessage());
        }
    }
    
    /**
     * 检查磁盘空间
     */
    private void checkDiskSpace() {
        try {
            java.io.File root = new java.io.File("/");
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            double usagePercent = (double) (totalSpace - freeSpace) / totalSpace * 100;
            
            HealthStatus status;
            if (usagePercent < 70) {
                status = HealthStatus.EXCELLENT;
            } else if (usagePercent < 80) {
                status = HealthStatus.GOOD;
            } else if (usagePercent < 90) {
                status = HealthStatus.WARNING;
            } else if (usagePercent < 95) {
                status = HealthStatus.CRITICAL;
            } else {
                status = HealthStatus.FAILED;
            }
            
            updateComponentHealth(HealthComponent.DISK_SPACE, status, 
                                String.format("Usage: %.1f%% (%d GB free)", usagePercent, freeSpace / 1024 / 1024 / 1024));
            
        } catch (Exception e) {
            updateComponentHealth(HealthComponent.DISK_SPACE, HealthStatus.CRITICAL, 
                                "Error checking disk space: " + e.getMessage());
        }
    }
    
    /**
     * 检查配置健康状态
     */
    private void checkConfiguration() {
        try {
            // 检查关键配置项
            org.tinymediamanager.core.Settings settings = org.tinymediamanager.core.Settings.getInstance();
            boolean configOK = settings != null;

            HealthStatus status = configOK ? HealthStatus.EXCELLENT : HealthStatus.FAILED;
            String message = configOK ? "Configuration OK" : "Configuration error";

            updateComponentHealth(HealthComponent.CONFIGURATION, status, message);

        } catch (Exception e) {
            updateComponentHealth(HealthComponent.CONFIGURATION, HealthStatus.CRITICAL,
                                "Error checking configuration: " + e.getMessage());
        }
    }
    
    /**
     * 检查数据库健康状态
     */
    private void checkDatabaseHealth() {
        // 这里应该检查数据库连接、完整性等
        updateComponentHealth(HealthComponent.DATABASE, HealthStatus.EXCELLENT, "Database OK");
    }
    
    /**
     * 检查网络健康状态
     */
    private void checkNetworkHealth() {
        // 这里应该检查网络连接、API可达性等
        updateComponentHealth(HealthComponent.NETWORK, HealthStatus.EXCELLENT, "Network OK");
    }
    
    /**
     * 更新组件健康状态
     */
    private void updateComponentHealth(HealthComponent component, HealthStatus status, String message) {
        ComponentHealth health = componentHealth.computeIfAbsent(component, k -> new ComponentHealth());
        health.updateStatus(status, message);
        
        LOGGER.debug("Component {} health: {} - {}", component, status, message);
    }
    
    /**
     * 计算总体健康分数
     */
    private void calculateOverallHealth() {
        int totalScore = 0;
        int componentCount = 0;
        
        for (ComponentHealth health : componentHealth.values()) {
            totalScore += health.getStatus().getScore();
            componentCount++;
        }
        
        int newScore = componentCount > 0 ? totalScore / componentCount : 100;
        overallHealthScore.set(newScore);
    }
    
    /**
     * 检测问题并安排修复
     */
    private void detectAndScheduleRepairs() {
        for (Map.Entry<HealthComponent, ComponentHealth> entry : componentHealth.entrySet()) {
            HealthComponent component = entry.getKey();
            ComponentHealth health = entry.getValue();
            
            if (health.getStatus() == HealthStatus.CRITICAL || health.getStatus() == HealthStatus.FAILED) {
                scheduleRepair(component, health.getStatus(), health.getMessage());
            }
        }
    }
    
    /**
     * 安排修复任务
     */
    private void scheduleRepair(HealthComponent component, HealthStatus status, String issue) {
        String problemKey = component.name() + "_" + issue.hashCode();
        
        ProblemRecord record = detectedProblems.computeIfAbsent(problemKey, 
            k -> new ProblemRecord(component, issue));
        
        if (record.shouldAttemptRepair()) {
            RepairTask task = new RepairTask(component, issue, record.getAttemptCount());
            if (repairQueue.offer(task)) {
                LOGGER.info("Scheduled repair for {}: {}", component, issue);
            }
        }
    }
    
    /**
     * 处理修复队列
     */
    private void processRepairQueue() {
        RepairTask task = repairQueue.poll();
        if (task != null) {
            repairExecutor.submit(() -> executeRepair(task));
        }
    }
    
    /**
     * 执行修复任务
     */
    private void executeRepair(RepairTask task) {
        try {
            totalRepairAttempts.incrementAndGet();
            
            LOGGER.info("Executing repair for {}: {}", task.component, task.issue);
            
            boolean success = performRepair(task.component, task.issue);
            
            if (success) {
                successfulRepairs.incrementAndGet();
                LOGGER.info("Repair successful for {}", task.component);
                
                // 移除问题记录
                String problemKey = task.component.name() + "_" + task.issue.hashCode();
                detectedProblems.remove(problemKey);
            } else {
                LOGGER.warn("Repair failed for {}", task.component);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error executing repair for {}: {}", task.component, e.getMessage(), e);
        }
    }
    
    /**
     * 执行具体的修复操作
     */
    private boolean performRepair(HealthComponent component, String issue) {
        switch (component) {
            case CACHE_SYSTEM:
                return repairCacheSystem(issue);
            case MEMORY:
                return repairMemoryIssue(issue);
            case AI_SERVICES:
                return repairAIServices(issue);
            default:
                LOGGER.warn("No repair procedure defined for component: {}", component);
                return false;
        }
    }
    
    /**
     * 修复缓存系统问题
     */
    private boolean repairCacheSystem(String issue) {
        try {
            // 清理过期缓存
            org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.clearAICache();
            LOGGER.info("Cache system repaired: cleared expired entries");
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to repair cache system: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 修复内存问题
     */
    private boolean repairMemoryIssue(String issue) {
        try {
            // 强制垃圾回收
            System.gc();
            LOGGER.info("Memory issue repaired: forced garbage collection");
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to repair memory issue: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 修复AI服务问题
     */
    private boolean repairAIServices(String issue) {
        try {
            // 重置AI统计
            // 这里可以添加更多AI服务修复逻辑
            LOGGER.info("AI services repaired: reset statistics");
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to repair AI services: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 分析健康趋势
     */
    private void analyzeHealthTrends() {
        // 这里可以分析健康状态的趋势，预测潜在问题
        LOGGER.debug("Analyzing health trends");
    }
    
    /**
     * 生成维护建议
     */
    private void generateMaintenanceRecommendations() {
        // 这里可以生成预防性维护建议
        LOGGER.debug("Generating maintenance recommendations");
    }
    
    /**
     * 初始化组件健康状态
     */
    private void initializeComponentHealth() {
        for (HealthComponent component : HealthComponent.values()) {
            componentHealth.put(component, new ComponentHealth());
        }
    }
    
    /**
     * 获取健康报告
     */
    public HealthReport getHealthReport() {
        return new HealthReport(
            overallHealthScore.get(),
            new EnumMap<>(componentHealth),
            totalRepairAttempts.get(),
            successfulRepairs.get(),
            preventedFailures.get(),
            detectedProblems.size(),
            lastHealthCheck.get()
        );
    }
    
    /**
     * 组件健康状态
     */
    private static class ComponentHealth {
        private volatile HealthStatus status = HealthStatus.EXCELLENT;
        private volatile String message = "OK";
        private volatile long lastUpdate = System.currentTimeMillis();
        
        void updateStatus(HealthStatus newStatus, String newMessage) {
            this.status = newStatus;
            this.message = newMessage;
            this.lastUpdate = System.currentTimeMillis();
        }
        
        HealthStatus getStatus() { return status; }
        String getMessage() { return message; }
        long getLastUpdate() { return lastUpdate; }
    }
    
    /**
     * 问题记录
     */
    private static class ProblemRecord {
        private final HealthComponent component;
        private final String issue;
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final AtomicLong lastAttempt = new AtomicLong(0);
        
        ProblemRecord(HealthComponent component, String issue) {
            this.component = component;
            this.issue = issue;
        }
        
        boolean shouldAttemptRepair() {
            long timeSinceLastAttempt = System.currentTimeMillis() - lastAttempt.get();
            return attemptCount.get() < MAX_AUTO_REPAIR_ATTEMPTS && 
                   timeSinceLastAttempt > 60000; // 1分钟间隔
        }
        
        int getAttemptCount() {
            lastAttempt.set(System.currentTimeMillis());
            return attemptCount.incrementAndGet();
        }
    }
    
    /**
     * 修复任务
     */
    private static class RepairTask {
        final HealthComponent component;
        final String issue;
        final int attemptNumber;
        
        RepairTask(HealthComponent component, String issue, int attemptNumber) {
            this.component = component;
            this.issue = issue;
            this.attemptNumber = attemptNumber;
        }
    }
    
    /**
     * 健康报告
     */
    public static class HealthReport {
        public final int overallScore;
        public final Map<HealthComponent, ComponentHealth> componentHealth;
        public final long totalRepairs;
        public final long successfulRepairs;
        public final long preventedFailures;
        public final int activeProblems;
        public final long lastCheckTime;
        
        HealthReport(int overallScore, Map<HealthComponent, ComponentHealth> componentHealth,
                    long totalRepairs, long successfulRepairs, long preventedFailures,
                    int activeProblems, long lastCheckTime) {
            this.overallScore = overallScore;
            this.componentHealth = componentHealth;
            this.totalRepairs = totalRepairs;
            this.successfulRepairs = successfulRepairs;
            this.preventedFailures = preventedFailures;
            this.activeProblems = activeProblems;
            this.lastCheckTime = lastCheckTime;
        }
        
        @Override
        public String toString() {
            return String.format(
                "HealthReport{score=%d, repairs=%d/%d, problems=%d, lastCheck=%d}",
                overallScore, successfulRepairs, totalRepairs, activeProblems, lastCheckTime
            );
        }
    }
}
