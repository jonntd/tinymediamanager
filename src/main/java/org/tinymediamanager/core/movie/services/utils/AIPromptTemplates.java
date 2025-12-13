package org.tinymediamanager.core.movie.services.utils;

import java.time.Year;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.Settings;

/**
 * AI提示词模板管理类 集中管理所有AI电影识别相关的提示词，支持自定义覆盖
 */
public final class AIPromptTemplates {

    private AIPromptTemplates() {
        // 工具类不允许实例化
    }

    /**
     * 获取当前年份+2作为最大有效年份
     */
    private static int getMaxValidYear() {
        return Year.now().getValue() + 2;
    }

    /**
     * 获取电影识别的系统提示词（统一使用批量格式） 使用双尖括号索引格式 <<序号>> 确保输入输出精确匹配
     * 
     * @return 电影识别系统提示词
     */
    public static String getBatchMovieRecognitionPrompt() {
        // 优先使用用户自定义提示词
        String customPrompt = Settings.getInstance().getOpenAiExtractionPrompt();
        if (StringUtils.isNotBlank(customPrompt)) {
            return customPrompt;
        }

        int maxYear = getMaxValidYear();

        return "你是电影识别专家。遇到不规则或难以识别的文件名时，**必须利用联网搜索工具**分析文件特征（如别名、演员、特定关键词）以确定其真实身份。\n\n" + "## ⚠️ 严格禁止（违反将导致识别失败）\n"
                + "- ❌ JSON 格式（如 `{\"title\": ...}` 或 `[{...}]`）\n" + "- ❌ 代码块（如 `code_output` 或 ```）\n" + "- ❌ URL 链接或搜索结果列表\n" + "- ❌ 任何解释、说明\n\n"
                + "## 重要提示 - 输出格式\n" + "**你必须直接输出纯文本结果，每行格式为：`<<序号>> 标题 年份`**\n" + "**禁止返回以下任何格式：**\n" + "- JSON 格式（如 {\"title\": ...} 或 [{...}]）\n"
                + "- 搜索结果列表或 URL 链接\n" + "- 代码块或 code_output\n" + "- 任何解释、说明或错误信息\n\n" + "## 输入处理\n" + "- 专注于识别电影标题，忽略所有格式标签如：\n"
                + "  - 分辨率(720p, 1080p, 4K)、编码(H.264, HEVC)\n" + "  - 音频(Atmos, AAC)、发布组(RARBG等)\n" + "- 过滤掉文件扩展名和无关技术信息\n\n" + "## 搜索与命名策略\n"
                + "- **数据源优先级**：优先以 **TMDB (The Movie Database)** 信息为准。\n" + "- **标题选择策略**（优先级从高到低）：\n" + "  1. **TMDB 原名 (Original Title)**：\n"
                + "     - **英文电影**：使用英文原名。\n" + "     - **华语电影**：使用中文原名。\n" + "  2. **TMDB 英文名 (English Title)**：\n"
                + "     - **其他语种**（如日语、韩语、法语等）：优先使用 TMDB 上的官方英文名，除非该片在中国极具知名度且你有十足把握，否则不使用中文译名。\n" + "- **严禁使用自造的直译名**，必须是 TMDB 上存在的官方标题。\n\n"
                + "## 输出格式要求\n" + "每行格式：`<<序号>> 标题 年份`\n" + "- 序号必须与输入完全对应\n" + "- 每行只输出一个结果，不要有额外解释\n" + "- 年份必须是4位数字(1888-" + maxYear + ")\n"
                + "- 无法识别时输出：`<<序号>> 未知电影 1900`\n" + "- **必须联网验证**：确保标题和年份与 TMDB 记录完全匹配。\n\n" + "## 示例\n" + "输入：\n"
                + "<<1>> /Movies/Interstellar.2014.1080p/\n" + "<<2>> /电影/流浪地球 (2019)/\n" + "<<3>> /Anime/Spirited Away 2001/\n"
                + "<<4>> /Movies/UnknownFile/\n" + "输出：\n" + "<<1>> Interstellar 2014\n" + "<<2>> 流浪地球 2019\n" + "<<3>> Spirited Away 2001\n"
                + "<<4>> 未知电影 1900";
    }

    /**
     * 转义提示词中的特殊字符以用于JSON
     * 
     * @param prompt
     *            原始提示词
     * @return 转义后的提示词
     */
    public static String escapeForJson(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
