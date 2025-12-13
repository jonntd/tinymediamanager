package org.tinymediamanager.core.movie.services.utils;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI响应解析工具类 用于解析、清理和验证AI返回的电影识别结果
 */
public final class AIResponseParser {
    private static final Logger       LOGGER         = LoggerFactory.getLogger(AIResponseParser.class);
    private static final ObjectMapper OBJECT_MAPPER  = new ObjectMapper();

    // 有效年份范围
    private static final int          MIN_VALID_YEAR = 1888;

    // 年份匹配模式
    private static final Pattern      YEAR_PATTERN   = Pattern.compile("\\b(\\d{4})\\b");

    private AIResponseParser() {
        // 工具类不允许实例化
    }

    /**
     * 获取最大有效年份（当前年份+2）
     */
    private static int getMaxValidYear() {
        return Year.now().getValue() + 2;
    }

    /**
     * 从API响应中提取内容（使用Jackson解析JSON）
     * 
     * @param response
     *            API响应字符串
     * @return 提取的内容，如果解析失败返回原始响应
     */
    public static String extractContentFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        try {
            LOGGER.debug("Parsing response with Jackson: {}", response.substring(0, Math.min(response.length(), 200)));

            JsonNode rootNode = OBJECT_MAPPER.readTree(response);

            // 尝试获取 choices[0].message.content（OpenAI格式）
            JsonNode choicesNode = rootNode.get("choices");
            if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                JsonNode firstChoice = choicesNode.get(0);
                JsonNode messageNode = firstChoice.get("message");
                if (messageNode != null) {
                    JsonNode contentNode = messageNode.get("content");
                    if (contentNode != null && !contentNode.isNull()) {
                        String content = contentNode.asText().trim();
                        LOGGER.debug("Successfully extracted content from OpenAI format: {}", content);
                        return content;
                    }
                }
            }

            // 兼容格式：直接查找content字段
            JsonNode contentNode = rootNode.get("content");
            if (contentNode != null && !contentNode.isNull()) {
                String content = contentNode.asText().trim();
                LOGGER.debug("Found content in direct field: {}", content);
                return content;
            }

            LOGGER.warn("No content found in response structure, returning original response");
            return response.trim();

        }
        catch (Exception e) {
            LOGGER.error("Failed to parse JSON response: {}. Error: {}", response.substring(0, Math.min(response.length(), 100)), e.getMessage());
            // 回退到原始响应
            return response.trim();
        }
    }

    /**
     * 清理和验证识别结果
     * 
     * @param recognizedTitle
     *            AI返回的原始识别结果
     * @return 清理后的标题，如果验证失败返回null
     */
    public static String cleanAndValidateTitle(String recognizedTitle) {
        if (recognizedTitle == null || recognizedTitle.trim().isEmpty()) {
            LOGGER.warn("识别结果为空");
            return null;
        }

        String rawText = recognizedTitle.trim();

        // 1. 预处理：如果包含换行符，尝试提取最后一行非空内容
        // 这可以应对 Gemini Search 输出 'code_output' + JSON + 结果 的情况
        if (rawText.contains("\n")) {
            String[] lines = rawText.split("\n");
            // 倒序查找最后一行有意义的文本
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                // 跳过空行、代码块标记、JSON 括号
                if (line.isEmpty() || line.equals("```") || line.equals("]") || line.equals("}")) {
                    continue;
                }
                // 使用这一行作为潜在标题
                rawText = line;
                LOGGER.debug("从多行响应中提取候选行: {}", rawText);
                break;
            }
        }

        // 2. 检查提取后的文本是否是 JSON 格式（如果只是 JSON 而没有任何有效标题行，则视为失败）
        if (isJsonResponse(rawText)) {
            LOGGER.warn("AI 返回了 JSON 格式响应而不是标题: '{}'", rawText.length() > 100 ? rawText.substring(0, 100) + "..." : rawText);
            return null;
        }

        // 3. 清理逻辑 - 移除标点符号和规范化空格
        String cleaned = rawText.replaceAll("^[\\s\\p{Punct}]+", "") // 移除开头的标点符号
                .replaceAll("[\\s\\p{Punct}]+$", "") // 移除结尾的标点符号
                .replaceAll("\\s+", " ") // 规范化空格
                .trim();

        // 4. 检查是否包含错误提示词
        if (containsErrorIndicators(cleaned)) {
            LOGGER.warn("识别结果包含错误提示词: {}", cleaned);
            return null;
        }

        // 5. 检查是否是未知电影格式
        String lowerCleaned = cleaned.toLowerCase();
        if (lowerCleaned.equals("未知电影") || lowerCleaned.equals("unknown movie")) {
            LOGGER.warn("识别结果为未知电影");
            return "未知电影 1900"; // 返回标准未知电影格式
        }

        // 6. 验证是否符合"标题 年份"格式
        if (!validateTitleYearFormat(cleaned)) {
            return null;
        }

        LOGGER.debug("清理并验证后的标题: '{}' -> '{}'", recognizedTitle, cleaned);
        return cleaned;
    }

    /**
     * 检查是否是JSON格式响应
     */
    private static boolean isJsonResponse(String text) {
        return text.startsWith("{") || text.startsWith("[") || text.startsWith("code_output") || text.contains("\"url\":")
                || text.contains("\"title\":");
    }

    /**
     * 检查是否包含错误指示词
     */
    private static boolean containsErrorIndicators(String text) {
        String lower = text.toLowerCase();
        return lower.contains("error") || lower.contains("failed") || lower.contains("unable") || lower.contains("cannot")
                || lower.contains("unable to") || lower.contains("not possible") || lower.contains("i'm") || lower.contains("i am")
                || lower.contains("sorry") || lower.contains("apologize");
    }

    /**
     * 验证标题年份格式
     * 
     * @param cleaned
     *            清理后的标题
     * @return true如果格式有效
     */
    private static boolean validateTitleYearFormat(String cleaned) {
        // 提取年份（最后4个数字）
        int lastSpaceIndex = cleaned.lastIndexOf(' ');
        if (lastSpaceIndex <= 0 || lastSpaceIndex >= cleaned.length() - 4) {
            LOGGER.warn("识别结果不符合'标题 年份'格式: {}", cleaned);
            return false;
        }

        // 提取可能的年份部分
        String yearPart = cleaned.substring(lastSpaceIndex + 1);
        if (!yearPart.matches("\\d{4}")) {
            LOGGER.warn("年份格式不正确，必须是4位数字: {}", yearPart);
            return false;
        }

        // 验证年份范围
        try {
            int year = Integer.parseInt(yearPart);
            int maxYear = getMaxValidYear();
            if (year < MIN_VALID_YEAR || year > maxYear) {
                LOGGER.warn("年份超出有效范围(1888-{}): {}", maxYear, year);
                return false;
            }
        }
        catch (NumberFormatException e) {
            LOGGER.warn("无法解析年份: {}", yearPart);
            return false;
        }

        // 提取标题部分
        String titlePart = cleaned.substring(0, lastSpaceIndex).trim();
        if (titlePart.isEmpty() || titlePart.length() < 2 || titlePart.length() > 90) {
            LOGGER.warn("标题部分长度不合理: {}", titlePart);
            return false;
        }

        return true;
    }

    /**
     * 检查字符串是否包含有效年份
     * 
     * @param text
     *            待检查的文本
     * @return true如果包含有效年份
     */
    public static boolean containsValidYear(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        Matcher matcher = YEAR_PATTERN.matcher(text);
        int maxYear = getMaxValidYear();

        while (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                if (year >= MIN_VALID_YEAR && year <= maxYear) {
                    LOGGER.debug("Found valid year in text: {}", year);
                    return true;
                }
            }
            catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }

        LOGGER.debug("No valid year found in text: '{}'", text);
        return false;
    }

    /**
     * 解析批量响应为结果列表
     * 
     * @param content
     *            已提取的内容字符串
     * @return 有效结果列表
     */
    public static List<String> parseBatchContent(String content) {
        List<String> validResults = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            return validResults;
        }

        // 按行分割结果
        String[] lines = content.split("\n");

        // 过滤空行和无效结果
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("null")) {
                validResults.add(trimmed);
            }
            else {
                LOGGER.debug("Skipped invalid line: '{}'", line);
            }
        }

        LOGGER.debug("Parsed {} valid results from {} lines", validResults.size(), lines.length);
        return validResults;
    }

    /**
     * 安全解析年份字符串
     * 
     * @param yearStr
     *            年份字符串
     * @return 解析后的年份，解析失败返回null
     */
    public static Integer safeParseYear(String yearStr) {
        if (yearStr == null || yearStr.trim().isEmpty()) {
            return null;
        }

        try {
            int year = Integer.parseInt(yearStr.trim());
            int maxYear = getMaxValidYear();
            if (year >= MIN_VALID_YEAR && year <= maxYear) {
                return year;
            }
        }
        catch (NumberFormatException e) {
            LOGGER.debug("Failed to parse year: {}", yearStr);
        }

        return null;
    }

    // ==================== 索引匹配解析方法 ====================

    // 双尖括号索引匹配正则：<<数字>> 内容
    private static final Pattern INDEXED_RESULT_PATTERN = Pattern.compile("^<<(\\d+)>>\\s*(.+)$");
    // 兼容旧格式：1. 标题 或 1: 标题 或 1) 标题
    private static final Pattern LEGACY_INDEX_PATTERN   = Pattern.compile("^(\\d+)[.:\\)]\\s*(.+)$");
    // 中括号索引格式（备选）：[1] 标题
    private static final Pattern BRACKET_INDEX_PATTERN  = Pattern.compile("^\\[(\\d+)\\]\\s*(.+)$");

    /**
     * 解析带索引的AI响应（增强版） 支持多种索引格式： - <<1>> 标题 (推荐格式) - 1. 标题 - 1: 标题 - 1) 标题 - [1] 标题 同时支持 JSON 数组格式（隐式索引）
     * 
     * @param response
     *            AI响应内容
     * @return 索引到内容的映射
     */
    public static java.util.Map<Integer, String> parseIndexedResponse(String response) {
        java.util.Map<Integer, String> results = new java.util.LinkedHashMap<>();

        if (response == null || response.trim().isEmpty()) {
            LOGGER.debug("parseIndexedResponse: 输入为空");
            return results;
        }

        // 预处理：提取代码块内的内容（如果AI用代码块包裹），同时去除 'code_output' 等标记
        String content = extractFromCodeBlock(response);

        // 如果内容看起来像 JSON 数组，尝试按 JSON 数组解析 (隐式索引)
        if (content.trim().startsWith("[")) {
            try {
                JsonNode arrayNode = OBJECT_MAPPER.readTree(content);
                if (arrayNode.isArray()) {
                    for (int i = 0; i < arrayNode.size(); i++) {
                        JsonNode item = arrayNode.get(i);
                        String title = null;
                        if (item.isTextual()) {
                            title = item.asText();
                        }
                        else if (item.isObject() && item.has("title")) {
                            title = item.get("title").asText();
                        }

                        if (title != null && !title.trim().isEmpty()) {
                            // JSON 数组使用位置索引 (i + 1)
                            results.put(i + 1, title.trim());
                        }
                    }
                    LOGGER.debug("parseIndexedResponse: 解析出 {} 个 JSON 数组结果", results.size());
                    if (!results.isEmpty()) {
                        return results;
                    }
                }
            }
            catch (Exception e) {
                LOGGER.debug("尝试 JSON 解析失败，回退到行解析: {}", e.getMessage());
            }
        }

        String[] lines = content.split("\n");
        int parsedCount = 0;

        for (String line : lines) {
            line = line.trim();

            // 跳过空行和代码块标记
            if (line.isEmpty() || line.startsWith("```")) {
                continue;
            }

            // 跳过 'code_output' 标记 (可能是单独一行)
            if (line.equalsIgnoreCase("code_output")) {
                continue;
            }

            // 跳过常见的解释性文本
            if (isExplanatoryText(line)) {
                LOGGER.debug("跳过解释性文本: {}", line.length() > 50 ? line.substring(0, 50) + "..." : line);
                continue;
            }

            // 尝试多种索引格式匹配
            IndexParseResult parsed = tryParseIndexedLine(line);
            if (parsed != null) {
                // 只存储第一个匹配（避免重复索引覆盖）
                if (!results.containsKey(parsed.index)) {
                    results.put(parsed.index, parsed.content);
                    parsedCount++;
                }
                else {
                    LOGGER.debug("索引 {} 已存在，跳过重复: {}", parsed.index, parsed.content);
                }
            }
        }

        LOGGER.debug("parseIndexedResponse: 从 {} 行中解析出 {} 个结果", lines.length, parsedCount);
        return results;
    }

    /**
     * 索引解析结果
     */
    private static class IndexParseResult {
        final int    index;
        final String content;

        IndexParseResult(int index, String content) {
            this.index = index;
            this.content = content;
        }
    }

    /**
     * 尝试用多种格式解析索引行
     */
    private static IndexParseResult tryParseIndexedLine(String line) {
        // 1. 优先尝试双尖括号格式 <<1>> 标题
        Matcher matcher = INDEXED_RESULT_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseMatcherResult(matcher);
        }

        // 2. 尝试旧格式 1. 标题 / 1: 标题 / 1) 标题
        matcher = LEGACY_INDEX_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseMatcherResult(matcher);
        }

        // 3. 尝试中括号格式 [1] 标题
        matcher = BRACKET_INDEX_PATTERN.matcher(line);
        if (matcher.matches()) {
            return parseMatcherResult(matcher);
        }

        return null;
    }

    /**
     * 从正则匹配结果中提取索引和内容
     */
    private static IndexParseResult parseMatcherResult(Matcher matcher) {
        try {
            int index = Integer.parseInt(matcher.group(1));
            String content = matcher.group(2).trim();

            // 验证索引范围（1-1000 合理范围）
            if (index < 1 || index > 1000) {
                LOGGER.debug("索引超出合理范围: {}", index);
                return null;
            }

            // 验证内容非空
            if (content.isEmpty()) {
                return null;
            }

            return new IndexParseResult(index, content);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从代码块中提取内容
     */
    private static String extractFromCodeBlock(String response) {
        String trimmed = response.trim();

        // 检查是否被代码块包裹
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            // 移除开头的 ```xxx
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                String afterFirst = trimmed.substring(firstNewline + 1);
                // 移除结尾的 ```
                if (afterFirst.endsWith("```")) {
                    return afterFirst.substring(0, afterFirst.length() - 3).trim();
                }
            }
        }

        return response;
    }

    /**
     * 检查是否是解释性文本（非结果行）
     */
    private static boolean isExplanatoryText(String line) {
        String lower = line.toLowerCase();

        // 常见的解释性开头
        if (lower.startsWith("好的") || lower.startsWith("以下是") || lower.startsWith("这是") || lower.startsWith("识别结果") || lower.startsWith("结果如下")
                || lower.startsWith("ok") || lower.startsWith("here") || lower.startsWith("the result") || lower.startsWith("i ")
                || lower.startsWith("sure")) {
            return true;
        }

        // 常见的解释性结尾
        if (lower.endsWith("如下：") || lower.endsWith("如下:") || lower.endsWith("：") || lower.endsWith("as follows:")) {
            return true;
        }

        return false;
    }

    // ==================== 电视剧标题清理方法 ====================

    /**
     * 清理和验证电视剧标题
     * 
     * @param title
     *            AI返回的原始标题
     * @return 清理后的标题，验证失败返回null
     */
    public static String cleanAndValidateTvShowTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }

        String trimmed = title.trim();

        // 检查是否是 JSON/Code 格式
        if (isJsonResponse(trimmed)) {
            LOGGER.warn("AI returned JSON/Code format instead of title: '{}'", trimmed.length() > 50 ? trimmed.substring(0, 50) + "..." : trimmed);
            return null;
        }

        // 检查错误提示词
        if (containsErrorIndicators(trimmed)) {
            LOGGER.warn("AI result contains error message: {}", trimmed);
            return null;
        }

        // 检查未知结果
        String lowerCleaned = trimmed.toLowerCase();
        if (lowerCleaned.equals("未知电视剧") || lowerCleaned.equals("unknown tv show") || lowerCleaned.equals("unknown")
                || lowerCleaned.equals("unknown show")) {
            LOGGER.warn("AI returned unknown result");
            return "未知电视剧";
        }

        // 清理标点
        String cleaned = trimmed.replaceAll("^[\\s\\p{Punct}]+", "").replaceAll("[\\s\\p{Punct}]+$", "").replaceAll("\\s+", " ").trim();

        if (cleaned.length() >= 1 && cleaned.length() <= 100) {
            return cleaned;
        }

        LOGGER.warn("Invalid title length: {}", cleaned.length());
        return null;
    }

    /**
     * 解析剧集信息 (季数 集数)
     * 
     * @param content
     *            AI返回的剧集信息字符串
     * @return 包含季数和集数的数组 [season, episode]，失败返回null
     */
    public static int[] parseEpisodeInfo(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        String cleaned = content.trim();
        Pattern pattern = Pattern.compile("(\\d{1,3})\\s+(\\d{1,4})");
        Matcher matcher = pattern.matcher(cleaned);

        if (matcher.find()) {
            try {
                int season = Integer.parseInt(matcher.group(1));
                int episode = Integer.parseInt(matcher.group(2));
                return new int[] { season, episode };
            }
            catch (NumberFormatException e) {
                LOGGER.warn("Failed to parse season/episode numbers: {}", cleaned);
            }
        }

        LOGGER.warn("Could not match season/episode pattern in: {}", cleaned);
        return null;
    }
}
