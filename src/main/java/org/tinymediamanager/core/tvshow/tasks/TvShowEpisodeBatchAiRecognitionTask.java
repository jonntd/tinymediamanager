package org.tinymediamanager.core.tvshow.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser;
import org.tinymediamanager.core.tvshow.TvShowEpisodeAndSeasonParser.EpisodeMatchingResult;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.scraper.entities.MediaEpisodeNumber;
import org.tinymediamanager.scraper.entities.MediaEpisodeGroup;

/**
 * 电视剧剧集批量AI识别任务
 * 用于批量识别复杂命名的剧集文件，自动提取季数和集数信息
 * 
 * @author AI Assistant
 */
public class TvShowEpisodeBatchAiRecognitionTask extends TmmThreadPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(TvShowEpisodeBatchAiRecognitionTask.class);
    
    private final List<TvShowEpisode> episodesToProcess;
    private final boolean useHybridMode;
    
    /**
     * 构造函数
     * 
     * @param episodes 要处理的剧集列表
     * @param useHybridMode 是否使用混合模式（传统解析失败时才用AI）
     */
    public TvShowEpisodeBatchAiRecognitionTask(List<TvShowEpisode> episodes, boolean useHybridMode) {
        super(TmmResourceBundle.getString("task.episodeairecognition"));
        this.episodesToProcess = new ArrayList<>(episodes);
        this.useHybridMode = useHybridMode;
    }
    
    @Override
    protected void doInBackground() {
        LOGGER.info("=== Starting Batch Episode AI Recognition ===");
        LOGGER.info("Episodes to process: {}", episodesToProcess.size());
        LOGGER.info("Recognition mode: {}", useHybridMode ? "Hybrid (Traditional + AI)" : "Pure AI");
        LOGGER.info("Processing strategy: {}", useHybridMode ?
            "Try traditional parsing first, fallback to AI if failed" :
            "Direct AI recognition for all episodes");

        // 统计需要处理的剧集类型
        int needsProcessing = 0;
        int alreadyProcessed = 0;
        for (TvShowEpisode ep : episodesToProcess) {
            if (ep.getAiredSeason() <= 0 || ep.getAiredEpisode() <= 0) {
                needsProcessing++;
            } else {
                alreadyProcessed++;
            }
        }
        LOGGER.info("Episodes needing processing: {}", needsProcessing);
        LOGGER.info("Episodes already processed: {}", alreadyProcessed);
        
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;
        
        initThreadPool(1, "episodeAI");
        
        for (TvShowEpisode episode : episodesToProcess) {
            if (cancel) {
                break;
            }
            
            submitTask(new EpisodeAiRecognitionTask(episode));
        }
        
        waitForCompletionOrCancel();
        
        // 统计结果
        List<String> successList = new ArrayList<>();
        List<String> failureList = new ArrayList<>();
        List<String> skippedList = new ArrayList<>();

        for (TvShowEpisode episode : episodesToProcess) {
            String episodeName = episode.getMainVideoFile() != null ?
                episode.getMainVideoFile().getFilename() : episode.getTitle();

            if (episode.getAiredSeason() > 0 && episode.getAiredEpisode() > 0) {
                successCount++;
                successList.add(String.format("%s -> S%02dE%02d",
                    episodeName, episode.getAiredSeason(), episode.getAiredEpisode()));
            } else if (episode.getTitle().contains("[SKIPPED]")) {
                skippedCount++;
                skippedList.add(episodeName);
            } else {
                failureCount++;
                failureList.add(episodeName);
            }
        }

        LOGGER.info("=== Batch Episode AI Recognition Complete ===");
        LOGGER.info("Total processed: {}", episodesToProcess.size());
        LOGGER.info("Success: {} ({:.1f}%)", successCount,
            (successCount * 100.0 / episodesToProcess.size()));
        LOGGER.info("Failed: {} ({:.1f}%)", failureCount,
            (failureCount * 100.0 / episodesToProcess.size()));
        LOGGER.info("Skipped: {} ({:.1f}%)", skippedCount,
            (skippedCount * 100.0 / episodesToProcess.size()));

        // 详细列出成功的识别结果
        if (!successList.isEmpty()) {
            LOGGER.info("=== Successful Recognitions ===");
            for (String success : successList) {
                LOGGER.info("✓ {}", success);
            }
        }

        // 详细列出失败的文件
        if (!failureList.isEmpty()) {
            LOGGER.info("=== Failed Recognitions ===");
            for (String failure : failureList) {
                LOGGER.info("✗ {}", failure);
            }
        }
        
        // 显示详细的完成消息
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("批量AI识别完成！\n");
        messageBuilder.append(String.format("总计: %d 个剧集\n", episodesToProcess.size()));
        messageBuilder.append(String.format("成功: %d (%.1f%%)\n",
            successCount, (successCount * 100.0 / episodesToProcess.size())));
        messageBuilder.append(String.format("失败: %d (%.1f%%)\n",
            failureCount, (failureCount * 100.0 / episodesToProcess.size())));
        messageBuilder.append(String.format("跳过: %d (%.1f%%)\n",
            skippedCount, (skippedCount * 100.0 / episodesToProcess.size())));
        messageBuilder.append(String.format("识别模式: %s",
            useHybridMode ? "混合模式" : "纯AI模式"));

        // 如果有成功的识别，显示前几个示例
        if (!successList.isEmpty()) {
            messageBuilder.append("\n\n成功示例:");
            int showCount = Math.min(3, successList.size());
            for (int i = 0; i < showCount; i++) {
                messageBuilder.append("\n• ").append(successList.get(i));
            }
            if (successList.size() > 3) {
                messageBuilder.append(String.format("\n... 还有 %d 个", successList.size() - 3));
            }
        }

        MessageLevel level = failureCount > successCount ? MessageLevel.WARN : MessageLevel.INFO;
        MessageManager.getInstance().pushMessage(
            new Message(level, "批量AI识别", messageBuilder.toString()));
    }

    @Override
    public void callback(Object obj) {
        // 回调方法，处理任务完成后的操作
        // 这里可以添加额外的处理逻辑
    }
    
    /**
     * 单个剧集AI识别任务
     */
    private class EpisodeAiRecognitionTask implements Callable<Object> {
        private final TvShowEpisode episode;
        
        public EpisodeAiRecognitionTask(TvShowEpisode episode) {
            this.episode = episode;
        }
        
        @Override
        public Object call() {
            try {
                // 检查是否需要处理
                if (!needsProcessing(episode)) {
                    LOGGER.debug("Skipping episode (already has season/episode): {}", 
                        episode.getMainVideoFile().getFilename());
                    episode.setTitle(episode.getTitle() + " [SKIPPED]");
                    return null;
                }
                
                // 获取文件名和电视剧标题
                String filename = getEpisodeFilename(episode);
                String tvShowTitle = episode.getTvShow().getTitle();
                
                if (filename == null || filename.trim().isEmpty()) {
                    LOGGER.warn("Cannot get filename for episode: {}", episode.getDbId());
                    return null;
                }
                
                LOGGER.info("=== Processing Episode {} of {} ===",
                    episodesToProcess.indexOf(episode) + 1, episodesToProcess.size());
                LOGGER.info("Episode filename: {}", filename);
                LOGGER.info("TV show title: {}", tvShowTitle);
                LOGGER.info("Current season/episode: S{}E{}",
                    episode.getAiredSeason(), episode.getAiredEpisode());
                
                // 执行AI识别
                EpisodeMatchingResult result;
                if (useHybridMode) {
                    // 混合模式：先传统解析，失败时用AI
                    result = TvShowEpisodeAndSeasonParser.detectEpisodeHybrid(filename, tvShowTitle);
                } else {
                    // 纯AI模式
                    result = TvShowEpisodeAndSeasonParser.detectEpisodeWithAI(filename, tvShowTitle);
                }
                
                // 应用识别结果
                if (result != null && result.season != -1 && !result.episodes.isEmpty()) {
                    int oldSeason = episode.getAiredSeason();
                    int oldEpisode = episode.getAiredEpisode();
                    int newSeason = result.season;
                    int newEpisode = result.episodes.get(0);

                    LOGGER.info("=== AI Recognition Result Comparison ===");
                    LOGGER.info("Before: S{}E{}", oldSeason, oldEpisode);
                    LOGGER.info("After:  S{}E{}", newSeason, newEpisode);
                    LOGGER.info("Changed: {}", (oldSeason != newSeason || oldEpisode != newEpisode));

                    // 更新任务描述，显示当前处理的剧集信息
                    publishState(String.format("处理中: %s S%02dE%02d",
                        episode.getTvShow().getTitle(), newSeason, newEpisode));

                    // 发送单个识别成功消息到Message history
                    String successMsg = String.format("AI识别: %s → S%02dE%02d",
                        filename, newSeason, newEpisode);
                    MessageManager.getInstance().pushMessage(
                        new Message(MessageLevel.INFO, "批量AI识别", successMsg));

                    // 更新剧集信息 - 使用正确的API
                    MediaEpisodeNumber newEpisodeNumber = new MediaEpisodeNumber(
                        MediaEpisodeGroup.DEFAULT_AIRED, newSeason, newEpisode);
                    episode.setEpisode(newEpisodeNumber);
                    
                    // 保存到数据库
                    episode.saveToDb();
                    episode.writeNFO();
                    
                } else {
                    LOGGER.warn("=== AI Recognition Failed ===");
                    LOGGER.warn("Episode filename: {}", filename);
                    LOGGER.warn("TV show title: {}", tvShowTitle);
                    LOGGER.warn("Recognition mode: {}", useHybridMode ? "Hybrid" : "Pure AI");
                    if (result != null) {
                        LOGGER.warn("Result season: {}, episodes: {}", result.season, result.episodes);
                    } else {
                        LOGGER.warn("Result is null");
                    }

                    // 更新任务描述显示失败信息
                    publishState(String.format("失败: %s",
                        episode.getMainVideoFile() != null ?
                        episode.getMainVideoFile().getFilename() : episode.getTitle()));

                    // 发送单个识别失败消息到Message history
                    String failMsg = String.format("AI识别失败: %s", filename);
                    MessageManager.getInstance().pushMessage(
                        new Message(MessageLevel.WARN, "批量AI识别", failMsg));
                }
                
            } catch (Exception e) {
                LOGGER.error("Error processing episode {}: {}", 
                    episode.getMainVideoFile().getFilename(), e.getMessage());
            } finally {
                publishState(progressDone);
            }
            
            return null;
        }
        
        /**
         * 检查剧集是否需要处理
         */
        private boolean needsProcessing(TvShowEpisode episode) {
            // 如果已经有季数和集数，且不是默认值，则跳过
            return episode.getAiredSeason() <= 0 || episode.getAiredEpisode() <= 0;
        }
        
        /**
         * 获取剧集文件名
         */
        private String getEpisodeFilename(TvShowEpisode episode) {
            MediaFile mainFile = episode.getMainVideoFile();
            if (mainFile != null) {
                return mainFile.getFilename();
            }
            
            // 回退到标题
            return episode.getTitle();
        }
    }
}
