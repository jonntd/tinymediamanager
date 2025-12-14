package org.tinymediamanager.core.tvshow.services.utils;

/**
 * 电视剧AI提示词模板管理类
 */
public class TvShowAIPromptTemplates {

    /**
     * 获取批量电视剧识别提示词 使用双尖括号索引格式 <<序号>> 确保输入输出精确匹配
     */
    public static String getBatchTvShowRecognitionPrompt() {
        // 优先使用用户自定义提示词
        String customPrompt = org.tinymediamanager.core.Settings.getInstance().getOpenAiExtractionPrompt();
        if (customPrompt != null && !customPrompt.trim().isEmpty()) {
            return customPrompt;
        }

        return "你是电视剧识别专家。遇到不规则或难以识别的文件名时，**必须利用联网搜索工具**分析文件特征（如别名、演员、特定关键词）以确定其真实身份。\n\n" + "## ⚠️ 严格禁止（违反将导致识别失败）\n"
                + "- ❌ JSON 格式（如 `{\"title\": ...}` 或 `[{...}]`）\n" + "- ❌ 代码块（如 `code_output` 或 ```）\n" + "- ❌ URL 链接或搜索结果列表\n" + "- ❌ 任何解释、说明\n\n"
                + "## 重要提示 - 输出格式\n" + "**你必须直接输出纯文本结果，每行格式为：`<<序号>> 标题`**\n" + "**禁止返回以下任何格式：**\n" + "- JSON 格式（如 {\"title\": ...} 或 [{...}]）\n"
                + "- 搜索结果列表或 URL 链接\n" + "- 代码块或 code_output\n" + "- 任何解释、说明或错误信息\n\n" + "## 输入处理\n" + "- 专注于识别电视剧标题，忽略所有格式标签如：\n"
                + "  - 季数(S01, 第一季)、集数(E01)\n" + "  - 分辨率(720p, 1080p, 4K)、编码(H.264, HEVC)\n" + "  - 音频(Atmos, AAC)、发布组(RARBG等)\n"
                + "- 过滤掉文件扩展名和无关技术信息\n\n" + "## 搜索与命名策略\n" + "- **数据源优先级**：优先以 **TMDB (The Movie Database)** 或 **TVDB** 信息为准。\n"
                + "- **标题选择策略**（优先级从高到低，中文优先）：\n" + "  1. **华语剧集**：使用中文原名。\n" + "  2. **非华语剧集**：\n" + "     - 若 TMDB/TVDB 存在官方中文译名，则优先使用中文译名。\n"
                + "     - 若无中文译名，则使用官方英文原名 (Original Title)。\n" + "- **严禁使用自造的直译名**，必须是官方存在的标题。\n\n" + "## 输出格式要求\n" + "每行格式：`<<序号>> 标题`\n"
                + "- 序号必须与输入完全对应\n" + "- 每行只输出一个结果，不要有额外解释\n" + "- 无法识别时：直接返回从文件名中提取的原始标题（去除技术标签后的核心名称）\n"
                + "- **必须联网验证**：确保标题与 TMDB/TVDB 记录完全匹配。\n\n" + "## 示例\n" + "输入：\n" + "<<1>> /TV/Breaking.Bad.S01[1080p]/\n"
                + "<<2>> /TV/合集[1]/权力的游戏.S08/\n" + "<<3>> /Series/Sense8.S01/\n" + "<<4>> /TV/MyShow.2024/\n" + "输出：\n" + "<<1>> 绝命毒师\n"
                + "<<2>> 权力的游戏\n" + "<<3>> 超感猎杀\n" + "<<4>> MyShow";
    }

    /**
     * 获取批量剧集（Episode）识别提示词 使用双尖括号索引格式 <<序号>> 确保输入输出精确匹配
     */
    public static String getBatchEpisodeRecognitionPrompt() {
        return "你是剧集别名与编号解析专家。你的任务是根据提供的**剧集文件名**和**所属电视剧**，利用联网搜索工具核对官方（TMDB/TVDB）数据，精准提取该文件的**季数 (Season)** 和 **集数 (Episode)**。\n\n"
                + "## ⚠️ 严格禁止（违反将导致识别失败）\n" + "- ❌ JSON 格式（如 `{\"title\": ...}` 或 `[{...}]`）\n" + "- ❌ 代码块（如 `code_output` 或 ```）\n"
                + "- ❌ URL 链接或搜索结果列表\n" + "- ❌ 任何解释、说明\n\n" + "## 输入格式\n" + "每行格式：`<<序号>> 剧集文件: 文件名 电视剧: 剧名`\n\n" + "## 输出格式 - 纯文本\n"
                + "每行格式：`<<序号>> 季数 集数`\n" + "- 序号必须与输入完全对应\n" + "- 季数和集数仅用**空格**分隔，不仅含任何其他符号\n" + "- 无法识别时输出：`<<序号>> 0 0`\n\n" + "## 核心识别规则\n"
                + "1. **强制联网验证**：遇到非标准命名（如仅含标题、特别篇），必须搜索 TMDB/TVDB 确认其官方归属的季数和集数。\n" + "2. **动漫/长篇剧集**：\n"
                + "   - 遇到绝对集数（如 _One Piece - 1000_），请自动转换为当前所属的 `季数 集数`（或保持 S01 + 绝对集数，取决于 TMDB 命名惯例）。\n" + "3. **特别篇 (Specials/OVA)**：\n"
                + "   - 通常归类为 **第 0 季** (Season 0)。\n" + "   - 请查找其在 Season 0 中的具体集数编号。\n"
                + "4. **季数推断**：如果文件名中未明确标注季数，但通过标题可确定属于某季（如副标题对应特定季），请填充正确季数。无法确定时默认为第 1 季。\n\n" + "## 示例\n" + "输入：\n"
                + "<<1>> 剧集文件: Breaking.Bad.S01E05.mkv 电视剧: Breaking Bad\n" + "<<2>> 剧集文件: One.Piece.ep900.mp4 电视剧: One Piece\n"
                + "<<3>> 剧集文件: 权力的游戏.第三季.第08集.mkv 电视剧: 权力的游戏\n" + "<<4>> 剧集文件: Sherlock.The.Abominable.Bride.mkv 电视剧: Sherlock (特别篇)\n" + "输出：\n"
                + "<<1>> 1 5\n" + "<<2>> 1 900\n" + "<<3>> 3 8\n" + "<<4>> 0 10"; // 示例：可恶的新娘是 S00E10
    }

    /**
     * JSON转义辅助方法
     */
    public static String escapeForJson(String input) {
        if (input == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;

                case '\\':
                    escaped.append("\\\\");
                    break;

                case '\b':
                    escaped.append("\\b");
                    break;

                case '\f':
                    escaped.append("\\f");
                    break;

                case '\n':
                    escaped.append("\\n");
                    break;

                case '\r':
                    escaped.append("\\r");
                    break;

                case '\t':
                    escaped.append("\\t");
                    break;

                default:
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        escaped.append(c);
                    }
                    break;
            }
        }
        return escaped.toString();
    }
}
