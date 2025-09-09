package org.tinymediamanager.ui.tvshows.actions;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.tvshow.entities.TvShow;
import org.tinymediamanager.core.tvshow.entities.TvShowEpisode;
import org.tinymediamanager.core.tvshow.entities.TvShowSeason;
import org.tinymediamanager.core.tvshow.tasks.TvShowEpisodeBatchAiRecognitionTask;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.MainWindow;
import org.tinymediamanager.ui.actions.TmmAction;
import org.tinymediamanager.ui.tvshows.TvShowUIModule;
import org.tinymediamanager.ui.tvshows.TvShowSelectionModel.SelectedObjects;

/**
 * 电视剧剧集批量AI识别Action
 * 
 * @author AI Assistant
 */
public class TvShowEpisodeBatchAiRecognitionAction extends TmmAction {
    
    public TvShowEpisodeBatchAiRecognitionAction() {
        putValue(NAME, "批量AI识别剧集");
        putValue(SHORT_DESCRIPTION, "使用AI批量识别选中剧集的季数和集数");
        putValue(SMALL_ICON, IconManager.SEARCH);
        putValue(LARGE_ICON_KEY, IconManager.SEARCH);
    }
    
    @Override
    protected void processAction(ActionEvent e) {
        SelectedObjects selectedObjects = TvShowUIModule.getInstance().getSelectionModel().getSelectedObjects();

        if (selectedObjects.isEmpty()) {
            JOptionPane.showMessageDialog(MainWindow.getInstance(),
                "请先选择要处理的电视剧或剧集", "批量AI识别", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 收集所有需要处理的剧集
        List<TvShowEpisode> episodesToProcess = new ArrayList<>();

        // 添加选中的电视剧的所有剧集
        for (TvShow tvShow : selectedObjects.getTvShows()) {
            episodesToProcess.addAll(tvShow.getEpisodes());
        }

        // 添加选中的季的所有剧集
        for (TvShowSeason season : selectedObjects.getSeasons()) {
            episodesToProcess.addAll(season.getEpisodes());
        }

        // 添加直接选中的剧集
        episodesToProcess.addAll(selectedObjects.getEpisodes());
        
        if (episodesToProcess.isEmpty()) {
            JOptionPane.showMessageDialog(MainWindow.getInstance(), 
                "没有找到可处理的剧集", "批量AI识别", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 显示确认对话框
        String[] options = {"混合模式（推荐）", "纯AI模式", "取消"};
        String message = String.format(
            "即将处理 %d 个剧集\n\n" +
            "混合模式：先尝试传统解析，失败时使用AI识别\n" +
            "纯AI模式：直接使用AI识别所有剧集\n\n" +
            "请选择识别模式：", 
            episodesToProcess.size());
        
        int choice = JOptionPane.showOptionDialog(
            MainWindow.getInstance(),
            message,
            "批量AI识别设置",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );
        
        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            // 用户取消
            return;
        }
        
        boolean useHybridMode = (choice == 0);
        
        // 启动批量识别任务
        TvShowEpisodeBatchAiRecognitionTask task = new TvShowEpisodeBatchAiRecognitionTask(
            episodesToProcess, useHybridMode);

        TmmTaskManager.getInstance().addUnnamedTask(task);

        // 发送启动消息到消息历史记录（无弹窗打断）
        String startMsg = String.format("批量AI识别任务已启动 - 模式: %s, 剧集数量: %d",
                         useHybridMode ? "混合模式" : "纯AI模式",
                         episodesToProcess.size());
        MessageManager.getInstance().pushMessage(
            new Message(MessageLevel.INFO, "批量AI识别", startMsg));
    }
}
