/*
 * Copyright 2012 - 2025 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.core.tvshow;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Utils;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.scraper.util.ParserUtils;
import org.tinymediamanager.scraper.util.StrgUtils;
import org.tinymediamanager.core.tvshow.services.ChatGPTEpisodeRecognitionService;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.Message.MessageLevel;
import org.tinymediamanager.core.MessageManager;

/**
 * The Class TvShowEpisodeAndSeasonParser.
 * 
 * @author Manuel Laggner
 */
public class TvShowEpisodeAndSeasonParser {

  private static final Logger  LOGGER              = LoggerFactory.getLogger(TvShowEpisodeAndSeasonParser.class);

  // 智能解析结果缓存，支持LRU和TTL
  private static final ConcurrentHashMap<String, SmartCachedEpisodeResult> PARSING_CACHE = new ConcurrentHashMap<>();
  private static final int MAX_CACHE_SIZE = 10000; // 最大缓存条目数
  private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24小时TTL

  // 缓存性能统计
  private static final java.util.concurrent.atomic.AtomicLong cacheEvictions = new java.util.concurrent.atomic.AtomicLong(0);
  private static final java.util.concurrent.atomic.AtomicLong hotDataHits = new java.util.concurrent.atomic.AtomicLong(0);

  // 缓存统计 - 使用原子操作保证线程安全
  private static final java.util.concurrent.atomic.AtomicLong cacheHits = new java.util.concurrent.atomic.AtomicLong(0);
  private static final java.util.concurrent.atomic.AtomicLong cacheMisses = new java.util.concurrent.atomic.AtomicLong(0);

  /**
   * 智能缓存条目（支持LRU和TTL）
   */
  private static class SmartCachedEpisodeResult {
    final EpisodeMatchingResult result;
    final long timestamp;
    volatile long lastAccessTime;
    volatile int accessCount;

    SmartCachedEpisodeResult(EpisodeMatchingResult result) {
      this.result = result;
      this.timestamp = System.currentTimeMillis();
      this.lastAccessTime = this.timestamp;
      this.accessCount = 1;
    }

    boolean isExpired() {
      return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
    }

    void recordAccess() {
      this.lastAccessTime = System.currentTimeMillis();
      this.accessCount++;
    }

    double getAccessFrequency() {
      long age = System.currentTimeMillis() - timestamp;
      return age > 0 ? (accessCount * 1000.0 / age) : accessCount;
    }

    boolean isHotData() {
      // 访问频率高或最近访问过的数据被认为是热点数据
      long timeSinceLastAccess = System.currentTimeMillis() - lastAccessTime;
      return accessCount >= 3 || timeSinceLastAccess < 60000; // 1分钟内访问过
    }
  }

  /**
   * 生成安全的缓存键，避免冲突
   */
  private static String generateCacheKey(String prefix, String filename, String showname) {
    try {
      // 标准化文件名，处理特殊字符和编码问题
      String normalizedFilename = normalizeForCache(filename);
      String normalizedShowname = showname != null ? normalizeForCache(showname) : "null";

      // 使用SHA-256哈希生成固定长度的安全键
      String combined = prefix + ":" + normalizedFilename + ":" + normalizedShowname;
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));

      // 转换为十六进制字符串
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }

      return prefix + "_" + hexString.toString();

    } catch (Exception e) {
      // 降级到简单的Base64编码
      LOGGER.warn("Failed to generate SHA-256 cache key, falling back to Base64: {}", e.getMessage());
      String safeFilename = java.util.Base64.getEncoder().encodeToString(filename.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      String safeShowname = showname != null ? java.util.Base64.getEncoder().encodeToString(showname.getBytes(java.nio.charset.StandardCharsets.UTF_8)) : "null";
      return prefix + ":" + safeFilename + ":" + safeShowname;
    }
  }

  /**
   * 标准化字符串用于缓存键生成
   */
  private static String normalizeForCache(String input) {
    if (input == null || input.isEmpty()) {
      return "";
    }

    // 移除控制字符和不可见字符
    String normalized = input.replaceAll("[\\p{Cntrl}\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]", "");

    // 标准化Unicode字符（NFC标准化）
    normalized = java.text.Normalizer.normalize(normalized, java.text.Normalizer.Form.NFC);

    // 限制长度，避免过长的键
    if (normalized.length() > 500) {
      normalized = normalized.substring(0, 500);
    }

    return normalized;
  }

  /**
   * 清理解析缓存，防止内存泄漏
   */
  public static void clearParsingCache() {
    PARSING_CACHE.clear();
    LOGGER.debug("Parsing cache cleared");
  }

  /**
   * 获取缓存大小，用于监控
   */
  public static int getCacheSize() {
    return PARSING_CACHE.size();
  }

  /**
   * 获取缓存统计信息
   */
  public static String getCacheStatistics() {
    long hits = cacheHits.get();
    long misses = cacheMisses.get();
    long total = hits + misses;
    double hitRate = total > 0 ? (hits * 100.0 / total) : 0.0;
    return String.format("Cache: %d entries, Hits: %d, Misses: %d, Hit rate: %.1f%%",
                        PARSING_CACHE.size(), hits, misses, hitRate);
  }

  /**
   * 重置缓存统计
   */
  public static void resetCacheStatistics() {
    cacheHits.set(0);
    cacheMisses.set(0);
  }

  /**
   * 获取缓存命中数
   */
  public static long getCacheHits() {
    return cacheHits.get();
  }

  /**
   * 获取缓存未命中数
   */
  public static long getCacheMisses() {
    return cacheMisses.get();
  }



  /**
   * 获取热点数据命中数
   */
  public static long getHotDataHits() {
    return hotDataHits.get();
  }

  /**
   * 获取缓存驱逐次数
   */
  public static long getCacheEvictions() {
    return cacheEvictions.get();
  }

  /**
   * 清理AI相关的缓存条目
   */
  public static void clearAICache() {
    int removedCount = 0;
    java.util.Iterator<java.util.Map.Entry<String, SmartCachedEpisodeResult>> iterator = PARSING_CACHE.entrySet().iterator();

    while (iterator.hasNext()) {
      java.util.Map.Entry<String, SmartCachedEpisodeResult> entry = iterator.next();
      if (entry.getKey().startsWith("ai_") || entry.getKey().startsWith("hybrid_")) {
        iterator.remove();
        removedCount++;
      }
    }

    LOGGER.info("Cleared {} AI-related cache entries", removedCount);
  }

  /**
   * 智能缓存存储（LRU + 热点数据保护）
   */
  private static void smartCacheStore(String cacheKey, EpisodeMatchingResult result) {
    // 检查缓存大小，如果超出限制则进行LRU清理
    if (PARSING_CACHE.size() >= MAX_CACHE_SIZE) {
      performLRUEviction();
    }

    // 存储新的缓存条目
    PARSING_CACHE.put(cacheKey, new SmartCachedEpisodeResult(result));
  }

  /**
   * 执行LRU清理策略
   */
  private static void performLRUEviction() {
    int targetSize = (int) (MAX_CACHE_SIZE * 0.8); // 清理到80%容量
    int toRemove = PARSING_CACHE.size() - targetSize;

    if (toRemove <= 0) return;

    // 收集所有缓存条目并按LRU排序
    java.util.List<java.util.Map.Entry<String, SmartCachedEpisodeResult>> entries =
        new java.util.ArrayList<>(PARSING_CACHE.entrySet());

    // 按最后访问时间排序（最久未访问的在前面）
    entries.sort((e1, e2) -> {
      SmartCachedEpisodeResult r1 = e1.getValue();
      SmartCachedEpisodeResult r2 = e2.getValue();

      // 保护热点数据：热点数据排在后面，不会被清理
      if (r1.isHotData() && !r2.isHotData()) return 1;
      if (!r1.isHotData() && r2.isHotData()) return -1;

      // 按最后访问时间排序
      return Long.compare(r1.lastAccessTime, r2.lastAccessTime);
    });

    // 移除最久未访问的条目（保护热点数据）
    int removed = 0;
    for (java.util.Map.Entry<String, SmartCachedEpisodeResult> entry : entries) {
      if (removed >= toRemove) break;

      // 不移除热点数据
      if (!entry.getValue().isHotData()) {
        PARSING_CACHE.remove(entry.getKey());
        removed++;
        cacheEvictions.incrementAndGet();
      }
    }

    LOGGER.debug("LRU eviction completed: removed {} entries, protected hot data", removed);
  }

  /**
   * 智能AI调用决策器
   */
  private static class SmartAIDecisionMaker {
    // 文件类型权重（基于历史成功率）
    private static final java.util.Map<String, Double> FILE_TYPE_WEIGHTS = new java.util.HashMap<>();

    static {
      // 基于经验的文件类型AI成功率权重
      FILE_TYPE_WEIGHTS.put("mkv", 0.9);   // 高成功率
      FILE_TYPE_WEIGHTS.put("mp4", 0.85);
      FILE_TYPE_WEIGHTS.put("avi", 0.8);
      FILE_TYPE_WEIGHTS.put("wmv", 0.7);
      FILE_TYPE_WEIGHTS.put("flv", 0.6);   // 低成功率
    }

    /**
     * 基础AI资格检查
     */
    private static boolean isBasicAIEligible(String filename, String tvShowTitle) {
      return TvShowEpisodeAndSeasonParser.shouldUseAI(filename, tvShowTitle);
    }

    /**
     * 判断是否应该使用AI识别
     */
    static boolean shouldUseAI(String filename, String tvShowTitle, EpisodeMatchingResult traditionalResult) {
      // 基础检查（调用原有的shouldUseAI方法）
      if (!isBasicAIEligible(filename, tvShowTitle)) {
        return false;
      }

      // 智能决策因子
      double aiScore = calculateAIScore(filename, tvShowTitle, traditionalResult);

      // 阈值：0.5以上才使用AI
      boolean shouldUse = aiScore > 0.5;

      LOGGER.debug("AI decision for {}: score={:.2f}, decision={}", filename, aiScore, shouldUse);
      return shouldUse;
    }

    /**
     * 计算AI调用评分
     */
    private static double calculateAIScore(String filename, String tvShowTitle, EpisodeMatchingResult traditionalResult) {
      double score = 0.0;

      // 因子1：文件类型权重 (30%)
      String extension = getFileExtension(filename).toLowerCase();
      double typeWeight = FILE_TYPE_WEIGHTS.getOrDefault(extension, 0.75); // 默认权重
      score += typeWeight * 0.3;

      // 因子2：传统解析失败程度 (40%)
      double failureScore = calculateFailureScore(traditionalResult);
      score += failureScore * 0.4;

      // 因子3：文件名复杂度 (20%)
      double complexityScore = calculateComplexityScore(filename);
      score += complexityScore * 0.2;

      // 因子4：剧集名称匹配度 (10%)
      double titleMatchScore = calculateTitleMatchScore(filename, tvShowTitle);
      score += titleMatchScore * 0.1;

      return Math.min(1.0, Math.max(0.0, score)); // 限制在0-1范围
    }

    private static String getFileExtension(String filename) {
      int lastDot = filename.lastIndexOf('.');
      return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    private static double calculateFailureScore(EpisodeMatchingResult result) {
      if (result.season > 0 && !result.episodes.isEmpty()) {
        return 0.2; // 传统解析成功，AI价值较低
      } else if (result.season > 0 || !result.episodes.isEmpty()) {
        return 0.6; // 部分成功，AI可能有帮助
      } else {
        return 1.0; // 完全失败，AI价值最高
      }
    }

    private static double calculateComplexityScore(String filename) {
      // 文件名越复杂，AI识别价值越高
      int specialChars = filename.replaceAll("[a-zA-Z0-9\\s]", "").length();
      int totalLength = filename.length();

      if (totalLength == 0) return 0.5;

      double complexity = (double) specialChars / totalLength;
      return Math.min(1.0, complexity * 2); // 特殊字符比例越高，复杂度越高
    }

    private static double calculateTitleMatchScore(String filename, String tvShowTitle) {
      if (tvShowTitle == null || tvShowTitle.isEmpty()) {
        return 0.5; // 无法判断，给中等分
      }

      String normalizedFilename = filename.toLowerCase();
      String normalizedTitle = tvShowTitle.toLowerCase();

      if (normalizedFilename.contains(normalizedTitle)) {
        return 0.3; // 包含剧集名，传统解析可能足够
      } else {
        return 0.8; // 不包含剧集名，AI可能更有帮助
      }
    }
  }

  /**
   * 检查并清理缓存，防止内存溢出
   * 使用智能清理策略，只清理部分缓存而非全部
   */
  private static void checkAndCleanCache() {
    if (PARSING_CACHE.size() > MAX_CACHE_SIZE) {
      // 清理25%的缓存条目，而不是全部清空
      int targetSize = (int) (MAX_CACHE_SIZE * 0.75);
      int toRemove = PARSING_CACHE.size() - targetSize;

      LOGGER.warn("Parsing cache size exceeded limit ({}), removing {} oldest entries",
                  MAX_CACHE_SIZE, toRemove);

      // 简单的清理策略：移除一些条目（在实际应用中可以实现LRU）
      java.util.Iterator<String> iterator = PARSING_CACHE.keySet().iterator();
      int removed = 0;
      while (iterator.hasNext() && removed < toRemove) {
        iterator.next();
        iterator.remove();
        removed++;
      }

      LOGGER.debug("Cache cleanup completed, current size: {}", PARSING_CACHE.size());
    }
  }

  // foo.yyyy.mm.dd.*
  private static final Pattern DATE_1              = Pattern.compile("([0-9]{4})[.-]([0-9]{2})[.-]([0-9]{2})", Pattern.CASE_INSENSITIVE);

  // foo.mm.dd.yyyy.*
  private static final Pattern DATE_2              = Pattern.compile("([0-9]{2})[.-]([0-9]{2})[.-]([0-9]{4})", Pattern.CASE_INSENSITIVE);

  // old parsing logic
  // public static final Pattern SEASON_LONG = Pattern.compile("(staffel|season|saison|series|temporada)[\\s_.-]?(\\d{1,4})",
  // Pattern.CASE_INSENSITIVE);
  //
  // SAME WITH OUR TRANSLATIONS
  // cat messages* | grep "metatag.season=" | cut -d "=" -f2
  // lowercase, add "series"
  public static final String[] SEASON_TRANSLATIONS = { "series", "season", "الموسم", "sezóna", "sæson", "staffel", "σεζόν", "temporada", "فصل",
      "kausi", "saison", "sezona", "évad", "þáttaröð", "stagione", "시즌", "seizoen", "sesong", "sezon", "сезон", "сезона", "säsong", "சீசன்" };
  public static final Pattern  SEASON_LONG;
  static {
    String regex = Arrays.stream(TvShowEpisodeAndSeasonParser.SEASON_TRANSLATIONS).collect(Collectors.joining("|"));
    regex = "(" + regex + ")[\\s_.-]?(\\d{1,4})";
    SEASON_LONG = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }

  // must start with a delimiter!
  public static final Pattern  SEASON_ONLY        = Pattern.compile("[\\s_.-]s[\\s_.-]?(\\d{1,4})", Pattern.CASE_INSENSITIVE);
  public static final Pattern  EPISODE_ONLY       = Pattern.compile("[\\s_.-]ep?[\\s_.-]?(\\d{1,4})", Pattern.CASE_INSENSITIVE);
  private static final Pattern EPISODE_PATTERN    = Pattern.compile("[epx_-]+(\\d{1,4})", Pattern.CASE_INSENSITIVE);
  private static final Pattern EPISODE_PATTERN_2  = Pattern.compile("(?:episode|ep)[\\. _-]*(\\d{1,4})", Pattern.CASE_INSENSITIVE);

  // 中文剧集解析模式
  private static final Pattern CHINESE_EPISODE_PATTERN = Pattern.compile("第(\\d{1,4})集", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHINESE_SEASON_PATTERN = Pattern.compile("第([一二三四五六七八九十\\d{1,2}])季", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHINESE_SEASON_EPISODE_PATTERN = Pattern.compile("第([一二三四五六七八九十\\d{1,2}])季第(\\d{1,4})集", Pattern.CASE_INSENSITIVE);

  // 扩展的中文解析模式
  private static final Pattern CHINESE_EPISODE_EXTENDED = Pattern.compile("(\\d{1,4})集", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHINESE_PART_PATTERN = Pattern.compile("第([一二三四五六七八九十\\d{1,2}])部分?", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHINESE_CHAPTER_PATTERN = Pattern.compile("第([一二三四五六七八九十\\d{1,2}])章", Pattern.CASE_INSENSITIVE);

  // 特殊格式模式
  private static final Pattern ROMAN_NUMERAL_PATTERN = Pattern.compile("([IVX]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern DOCUMENTARY_PATTERN = Pattern.compile("(\\d{1,2})(?:of|/)(\\d{1,2})", Pattern.CASE_INSENSITIVE);
  // (1/6) with normal or unicode slash!
  private static final Pattern EPISODE_PATTERN_NR = Pattern.compile("(\\d{1,2})[⧸/](\\d{1,2})", Pattern.CASE_INSENSITIVE);
  private static final Pattern ROMAN_PATTERN      = Pattern.compile("(part|pt)[\\._\\s]+([MDCLXVI]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SEASON_MULTI_EP    = Pattern.compile("s(\\d{1,4})[ _]?((?:([epx.-]+\\d{1,4})+))", Pattern.CASE_INSENSITIVE);
  private static final Pattern SEASON_MULTI_EP_2  = Pattern.compile("(\\d{1,4})(?=x)((?:([epx]+\\d{1,4})+))", Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMBERS_2_PATTERN  = Pattern.compile("([0-9]{2})", Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMBERS_3_PATTERN  = Pattern.compile("([0-9])([0-9]{2})", Pattern.CASE_INSENSITIVE);

  // https://kodi.wiki/view/Anime - PREP should run before any default Kodi regex
  private static final Pattern ANIME_PREPEND1     = Pattern.compile(
      "(Special|SP|OVA|OAV|Picture Drama)(?:[ _.-]*(?:ep?[ .]?)?(\\d{1,4})(?:[_ ]?v\\d+)?)+(?=\\b|_)[^])}]*?(?:[\\[({][^])}]+[\\])}][ _.-]*)*?(?:[\\[({][\\da-f]{8}[\\])}])",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ANIME_PREPEND2     = Pattern.compile(
      "(?:S(?:eason)?\\s*(?=\\d))?(Specials|\\d{1,3})[\\/](?:[^\\/]+[\\/])*[^\\/]+(?:\\b|_)(?:[ _.-]*(?:ep?[ .]?)?(\\d{1,4})(?:[_ ]?v\\d+)?)+(?=\\b|_)[^])}]*?(?:[\\[({][^])}]+[\\])}][ _.-]*)*?(?:[\\[({][\\da-f]{8}[\\])}])",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ANIME_PREPEND3     = Pattern.compile(
      "[-._ ]+S(?:eason ?)?(\\d{1,3})(?:[ _.-]*(?:ep?[ .]?)?(\\d{1,4})(?:[_ ]?v\\d+)?)+(?=\\b|_)[^])}]*?(?:[\\[({][^])}]+[\\])}][ _.-]*)*?(?:[\\[({][\\da-f]{8}[\\])}])",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ANIME_PREPEND4     = Pattern.compile(
      "((?=\\b|_))(?:[ _.-]*(?:ep?[ .]?)?(\\d{1,4})(?:-(\\d{1,3}))?(?:[_ ]?v\\d+)?)+(?=\\b|_)[^])}]*?(?:[\\[({][^])}]+[\\])}][ _.-]*)*?(?:[\\[({][\\da-f]{8}[\\])}])",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ANIME_PREPEND4_2   = Pattern.compile("((\\d{1,3})(?:-(\\d{1,3})){1,10})");

  private static final Pattern ANIME_APPEND1      = Pattern.compile(
      "(Special|SP|OVA|OAV|Picture Drama)(?:[ _.-]*(?:ep?[ .]?)?(\\d{1,4})(?:[_ ]?v\\d+)?)+(?=\\b|_)[^\\])}]*?(?:[\\[({][^\\])}]+[\\])}][ _.-]*)*?[^\\]\\[)(}{\\\\/]*$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ANIME_APPEND2      = Pattern.compile(
      "(?:S(?:eason)?\\s*(?=\\d))?(Specials|\\d{1,3})[\\\\/](?:[^\\\\/]+[\\\\/])*[^\\\\/]+(?:\\b|_)[ _.-]*(?:ep?[ .]?)?(\\d{1,4})(?:[_ ]?v\\d+)?(?:\\b|_)[^\\])}]*?(?:[\\[({][^\\])}]+[\\])}][ _.-]*)*?[^\\]\\[)(}{\\\\/]*?$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ANIME_APPEND3      = Pattern.compile(
      "[-._ ]+S(?:eason ?)?(\\d{1,3})(?:[ _.-]*(?:ep?[ .]?)?(\\d{1,4})(?:[_ ]?v\\d+)?)+(?=\\b|_)[^\\])}]*?(?:[\\[({][^\\])}]+[\\])}][ _.-]*)*?[^\\]\\[)(}{\\\\/]*$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern ANIME_APPEND4      = Pattern.compile(
      "((?=\\b|_))(?:[ _.-]*(?:ep?[ .]?)?(\\d{1,4})(?:[_ ]?v\\d+)?)+(?=\\b|_)[^\\])}]*?(?:[\\[({][^\\])}]+[\\])}][ _.-]*)*?[^\\]\\[)(}{\\\\/]*$",
      Pattern.CASE_INSENSITIVE);

  private TvShowEpisodeAndSeasonParser() {
    throw new IllegalAccessError();
  }

  public static String cleanEpisodeTitle(String titleToClean, String tvShowName) {
    String basename = ParserUtils.removeStopwordsAndBadwordsFromTvEpisodeName(titleToClean.replaceAll("([\":<>|?*])", ""));
    basename = Utils.cleanFolderStackingMarkers(basename);// i know, but this needs no extension ;)

    // parse foldername
    Pattern regex = Pattern.compile("(.*[\\/\\\\])");
    Matcher m = regex.matcher(basename);
    if (m.find()) {
      basename = basename.replaceAll(regex.pattern(), "");
    }
    basename = basename + " ";

    // remove show name
    if (tvShowName != null && !tvShowName.isEmpty()) {
      // remove string like tvshow name (440, 24, ...)
      basename = basename.replaceAll("(?i)^[^ES]" + Pattern.quote(tvShowName), ""); // with our added space, but not prefixed with S/E
      // "some fine show" would match with "some.fine-show"
      String delimited = tvShowName.replaceAll("[ _.-]", "[ _.-]"); // replace all delimiters, with delimiters pattern ;)
      basename = basename.replaceAll("(?i)^" + delimited, "");
    }

    basename = StrgUtils.replaceUnicodeCharactersInverse(basename);

    basename = basename.replaceFirst("\\.\\w{1,4}$", ""); // remove extension if 1-4 chars
    basename = basename.replaceFirst("[\\(\\[]\\d{4}[\\)\\]]", ""); // remove (xxxx) or [xxxx] as year
    basename = basename.replaceFirst("[\\(\\[][A-Fa-f0-9]{8}[\\)\\]]", ""); // remove (xxxxxxxx) or [xxxxxxxx] as 8 byte crc

    return removeEpisodeVariantsFromTitle(basename);
  }

  private static String removeEpisodeVariantsFromTitle(String title) {
    StringBuilder backup = new StringBuilder(title);
    StringBuilder ret = new StringBuilder();

    // quite same patters as above, minus the last ()
    title = title.replaceAll("[Ss]([0-9]+)[\\]\\[ _.-]*[Ee]([0-9]+)", "");
    title = title.replaceAll("[ _.-]()[Ee][Pp]?_?([0-9]+)", "");
    title = title.replaceAll("([0-9]{4})[.-]([0-9]{2})[.-]([0-9]{2})", "");
    title = title.replaceAll("([0-9]{2})[.-]([0-9]{2})[.-]([0-9]{4})", "");
    title = title.replaceAll("[\\\\/\\._ \\[\\(-]([0-9]+)x([0-9]+)", "");
    title = title.replaceAll("[\\/ _.-]p(?:ar)?t[ _.-]()([ivx]+)", "");
    title = title.replaceAll("[epx_-]+(\\d{1,3})", "");
    title = title.replaceAll("episode[\\. _-]*(\\d{1,3})", "");
    title = title.replaceAll("(part|pt)[\\._\\s]+([MDCLXVI]+)", "");
    title = title.replaceAll(SEASON_LONG.toString(), "");
    title = title.replaceAll("s(\\d{1,4})[ ]?((?:([epx_.-]+\\d{1,3})+))", "");
    title = title.replaceAll("(\\d{1,4})(?=x)((?:([epx]+\\d{1,3})+))", "");

    // split and reassemble
    String[] splitted = StringUtils.split(title, "[\\[\\]() _,.-]");
    for (String s : splitted) {
      if (MediaIdUtil.isValidImdbId(s)) {
        s = ""; // remove IMDB ID from title
      }
      ret.append(" ").append(s);
    }
    ret = new StringBuilder(ret.toString().strip());

    // uh-oh - we removed too much
    // also split and reassemble backup
    if (StringUtils.isEmpty(ret.toString())) {
      String[] b = StringUtils.split(backup.toString(), "[\\[\\]() _,.-]");
      backup = new StringBuilder();
      for (String s : b) {
        backup.append(" ").append(s);
      }
      ret = new StringBuilder(backup.toString().strip());
    }
    return ret.toString();
  }

  /**
   * 中文数字转阿拉伯数字
   */
  private static int chineseNumberToInt(String chineseNumber) {
    if (chineseNumber.matches("\\d+")) {
      return Integer.parseInt(chineseNumber);
    }

    switch (chineseNumber) {
      case "一": return 1;
      case "二": return 2;
      case "三": return 3;
      case "四": return 4;
      case "五": return 5;
      case "六": return 6;
      case "七": return 7;
      case "八": return 8;
      case "九": return 9;
      case "十": return 10;
      default: return -1;
    }
  }

  /**
   * 罗马数字转阿拉伯数字
   */
  private static int romanToInt(String roman) {
    if (roman == null || roman.isEmpty()) {
      return -1;
    }

    roman = roman.toUpperCase();
    switch (roman) {
      case "I": return 1;
      case "II": return 2;
      case "III": return 3;
      case "IV": return 4;
      case "V": return 5;
      case "VI": return 6;
      case "VII": return 7;
      case "VIII": return 8;
      case "IX": return 9;
      case "X": return 10;
      case "XI": return 11;
      case "XII": return 12;
      case "XIII": return 13;
      case "XIV": return 14;
      case "XV": return 15;
      case "XVI": return 16;
      case "XVII": return 17;
      case "XVIII": return 18;
      case "XIX": return 19;
      case "XX": return 20;
      default: return -1;
    }
  }

  /**
   * 解析中文剧集格式（增强版）
   */
  private static EpisodeMatchingResult parseChineseEpisodeFormat(EpisodeMatchingResult result, String name) {
    Matcher m;

    // 先尝试完整的"第X季第Y集"格式
    m = CHINESE_SEASON_EPISODE_PATTERN.matcher(name);
    if (m.find()) {
      try {
        int season = chineseNumberToInt(m.group(1));
        int episode = Integer.parseInt(m.group(2));
        if (season > 0 && episode > 0) {
          result.season = season;
          result.episodes.add(episode);
          LOGGER.debug("Parsed Chinese season-episode format: Season {}, Episode {}", season, episode);
          return result;
        }
      } catch (NumberFormatException e) {
        // 忽略解析错误
      }
    }

    // 尝试单独的"第X集"格式
    m = CHINESE_EPISODE_PATTERN.matcher(name);
    if (m.find()) {
      try {
        int episode = Integer.parseInt(m.group(1));
        if (episode > 0) {
          result.episodes.add(episode);
          LOGGER.debug("Parsed Chinese episode format: Episode {}", episode);
        }
      } catch (NumberFormatException e) {
        // 忽略解析错误
      }
    }

    // 尝试扩展的"X集"格式（没有"第"字）
    if (result.episodes.isEmpty()) {
      m = CHINESE_EPISODE_EXTENDED.matcher(name);
      if (m.find()) {
        try {
          int episode = Integer.parseInt(m.group(1));
          if (episode > 0 && episode <= 999) { // 合理范围内
            result.episodes.add(episode);
            LOGGER.debug("Parsed Chinese extended episode format: Episode {}", episode);
          }
        } catch (NumberFormatException e) {
          // 忽略解析错误
        }
      }
    }

    // 尝试"第X部分"格式
    if (result.episodes.isEmpty()) {
      m = CHINESE_PART_PATTERN.matcher(name);
      if (m.find()) {
        try {
          int part = chineseNumberToInt(m.group(1));
          if (part > 0) {
            result.episodes.add(part);
            LOGGER.debug("Parsed Chinese part format: Part {}", part);
          }
        } catch (Exception e) {
          // 忽略解析错误
        }
      }
    }

    // 尝试"第X章"格式
    if (result.episodes.isEmpty()) {
      m = CHINESE_CHAPTER_PATTERN.matcher(name);
      if (m.find()) {
        try {
          int chapter = chineseNumberToInt(m.group(1));
          if (chapter > 0) {
            result.episodes.add(chapter);
            LOGGER.debug("Parsed Chinese chapter format: Chapter {}", chapter);
          }
        } catch (Exception e) {
          // 忽略解析错误
        }
      }
    }

    // 尝试罗马数字格式（如"大海战II"）
    if (result.episodes.isEmpty()) {
      m = ROMAN_NUMERAL_PATTERN.matcher(name);
      if (m.find()) {
        try {
          int romanNum = romanToInt(m.group(1));
          if (romanNum > 0) {
            result.episodes.add(romanNum);
            LOGGER.debug("Parsed Roman numeral format: Episode {}", romanNum);
          }
        } catch (Exception e) {
          // 忽略解析错误
        }
      }
    }

    // 尝试单独的"第X季"格式
    if (result.season == -1) {
      m = CHINESE_SEASON_PATTERN.matcher(name);
      if (m.find()) {
        try {
          int season = chineseNumberToInt(m.group(1));
          if (season > 0) {
            result.season = season;
            LOGGER.debug("Parsed Chinese season format: Season {}", season);
          }
        } catch (Exception e) {
          // 忽略解析错误
        }
      }
    }

    return result;
  }

  /**
   * 检查文件是否适合AI识别
   */
  private static boolean shouldUseAI(String filename, String tvShowTitle) {
    // 如果文件名过长且包含大量描述性文字，可能不是标准剧集
    if (filename.length() > 100) {
      LOGGER.debug("Filename too long for AI recognition: {}", filename);
      return false;
    }

    // 如果文件名包含明显的非剧集关键词，跳过AI
    String lowerFilename = filename.toLowerCase();
    String[] nonEpisodeKeywords = {
      "trailer", "预告", "花絮", "幕后", "making", "behind",
      "interview", "访谈", "documentary", "纪录片", "special", "特辑",
      "opening", "ending", "op", "ed", "主题曲", "片头", "片尾"
    };

    for (String keyword : nonEpisodeKeywords) {
      if (lowerFilename.contains(keyword)) {
        LOGGER.debug("Filename contains non-episode keyword '{}', skipping AI: {}", keyword, filename);
        return false;
      }
    }

    // 如果电视剧标题和文件名完全不匹配，可能是错误分类
    if (tvShowTitle != null && !tvShowTitle.isEmpty()) {
      String cleanTitle = tvShowTitle.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
      String cleanFilename = filename.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();

      // 检查是否有任何共同字符
      boolean hasCommonChars = false;
      for (char c : cleanTitle.toCharArray()) {
        if (cleanFilename.indexOf(c) >= 0) {
          hasCommonChars = true;
          break;
        }
      }

      if (!hasCommonChars && cleanTitle.length() > 2) {
        LOGGER.debug("Filename has no common characters with TV show title, skipping AI: {} vs {}", filename, tvShowTitle);
        return false;
      }
    }

    return true;
  }

  /**
   * 使用AI辅助识别剧集文件名（当传统解析失败时的补充方案）
   *
   * @param filename 剧集文件名
   * @param tvShowTitle 电视剧标题
   * @return EpisodeMatchingResult AI识别结果
   */
  public static EpisodeMatchingResult detectEpisodeWithAI(String filename, String tvShowTitle) {
    // 创建AI专用缓存键
    String aiCacheKey = generateCacheKey("ai", filename, tvShowTitle);

    // 检查AI缓存（带TTL验证）
    SmartCachedEpisodeResult cachedAiEntry = PARSING_CACHE.get(aiCacheKey);
    if (cachedAiEntry != null) {
      if (!cachedAiEntry.isExpired()) {
        long hits = cacheHits.incrementAndGet();
        LOGGER.debug("Using cached AI recognition result for: {} (Cache hits: {})", filename, hits);
        return cachedAiEntry.result;
      } else {
        // 缓存已过期，移除
        PARSING_CACHE.remove(aiCacheKey);
        LOGGER.debug("AI cache entry expired for: {}", filename);
      }
    }
    cacheMisses.incrementAndGet();

    // 检查是否适合使用AI
    if (!shouldUseAI(filename, tvShowTitle)) {
      LOGGER.info("Skipping AI recognition for: {}", filename);
      EpisodeMatchingResult emptyResult = new EpisodeMatchingResult();
      // 缓存空结果，避免重复检查
      smartCacheStore(aiCacheKey, emptyResult);
      return emptyResult;
    }

    LOGGER.info("Attempting AI-assisted episode recognition for: {}", filename);
    EpisodeMatchingResult aiResult = ChatGPTEpisodeRecognitionService.recognizeEpisode(filename, tvShowTitle);

    // 缓存AI识别结果（无论成功还是失败）
    smartCacheStore(aiCacheKey, aiResult);

    return aiResult;
  }

  /**
   * 混合识别方法：先尝试传统解析（包括中文），失败时使用AI辅助
   *
   * @param filename 剧集文件名
   * @param tvShowTitle 电视剧标题
   * @return EpisodeMatchingResult 识别结果
   */
  public static EpisodeMatchingResult detectEpisodeHybrid(String filename, String tvShowTitle) {
    return detectEpisodeHybrid(filename, tvShowTitle, true);
  }

  /**
   * 混合剧集解析方法（传统解析 + AI识别）
   * @param filename 文件名
   * @param tvShowTitle 电视剧标题
   * @param enableAI 是否启用AI识别（false时只做传统解析）
   * @return 解析结果
   */
  public static EpisodeMatchingResult detectEpisodeHybrid(String filename, String tvShowTitle, boolean enableAI) {
    // 创建缓存键，包含文件名和剧集名
    String cacheKey = generateCacheKey("hybrid", filename, tvShowTitle);

    // 检查缓存（带TTL验证）
    SmartCachedEpisodeResult cachedEntry = PARSING_CACHE.get(cacheKey);
    if (cachedEntry != null) {
      if (!cachedEntry.isExpired()) {
        long hits = cacheHits.incrementAndGet();
        LOGGER.debug("Using cached hybrid parsing result for: {} (Cache hits: {})", filename, hits);
        return cachedEntry.result;
      } else {
        // 缓存已过期，移除
        PARSING_CACHE.remove(cacheKey);
        LOGGER.debug("Hybrid cache entry expired for: {}", filename);
      }
    }
    cacheMisses.incrementAndGet();

    // 首先尝试传统解析
    EpisodeMatchingResult traditionalResult = detectEpisodeFromFilename(filename, tvShowTitle);

    // 检查传统解析是否成功
    if (traditionalResult.season != -1 && !traditionalResult.episodes.isEmpty()) {
      LOGGER.debug("Traditional parsing successful for: {}", filename);
      // 缓存成功的传统解析结果
      smartCacheStore(cacheKey, traditionalResult);
      return traditionalResult;
    }

    // 传统解析失败，尝试中文格式解析
    LOGGER.debug("Traditional parsing failed, trying Chinese format parsing for: {}", filename);
    EpisodeMatchingResult chineseResult = new EpisodeMatchingResult();
    chineseResult = parseChineseEpisodeFormat(chineseResult, filename);

    // 检查中文解析是否成功
    if (!chineseResult.episodes.isEmpty()) {
      LOGGER.info("Chinese format parsing successful for: {}", filename);

      // 如果没有季数，默认为第1季
      if (chineseResult.season == -1) {
        chineseResult.season = 1;
      }

      // 发送中文解析成功消息
      String successMsg = String.format("中文格式解析: %s → S%02dE%02d",
          filename, chineseResult.season, chineseResult.episodes.get(0));
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.INFO, "中文格式解析", successMsg));

      return chineseResult;
    }

    // 如果禁用AI，直接返回中文解析结果
    if (!enableAI) {
      LOGGER.debug("AI recognition disabled, returning Chinese parsing result for: {}", filename);
      // 缓存中文解析结果
      smartCacheStore(cacheKey, chineseResult);
      return chineseResult;
    }

    // 智能AI调用决策
    if (!SmartAIDecisionMaker.shouldUseAI(filename, tvShowTitle, chineseResult)) {
      LOGGER.debug("Smart AI decision: skipping AI for {} (low value)", filename);
      // 缓存中文解析结果
      smartCacheStore(cacheKey, chineseResult);
      return chineseResult;
    }

    // 中文解析也失败，智能决策使用AI识别
    LOGGER.info("Smart AI decision: using AI for {} (high value)", filename);
    EpisodeMatchingResult aiResult = detectEpisodeWithAI(filename, tvShowTitle);

    // 如果AI识别成功，使用AI结果
    if (aiResult.season != -1 && !aiResult.episodes.isEmpty()) {
      LOGGER.info("AI recognition successful for: {}", filename);

      // 发送AI识别成功消息到Message history
      String successMsg = String.format("自动AI识别: %s → S%02dE%02d",
          filename, aiResult.season, aiResult.episodes.get(0));
      MessageManager.getInstance().pushMessage(
          new Message(MessageLevel.INFO, "自动AI识别", successMsg));

      // 缓存成功的AI识别结果
      smartCacheStore(cacheKey, aiResult);
      return aiResult;
    }

    // 所有方法都失败，返回传统解析结果（可能包含部分信息）
    LOGGER.warn("All parsing methods failed for: {}", filename);
    // 缓存失败的解析结果，避免重复尝试
    smartCacheStore(cacheKey, traditionalResult);
    return traditionalResult;
  }

  /**
   * Does all the season/episode detection
   * 
   * @param name
   *          the RELATIVE filename (like /dir2/seas1/fname.ext) from the TvShowRoot
   * @param showname
   *          the show name
   * @return result the calculated result
   */
  public static EpisodeMatchingResult detectEpisodeFromFilename(String name, String showname) {
    // 创建缓存键，包含文件名和剧集名
    String cacheKey = generateCacheKey("filename", name, showname);

    // 检查智能缓存（TTL + LRU + 热点数据检测）
    SmartCachedEpisodeResult cachedEntry = PARSING_CACHE.get(cacheKey);
    if (cachedEntry != null) {
      if (!cachedEntry.isExpired()) {
        // 记录访问，更新LRU信息
        cachedEntry.recordAccess();
        long hits = cacheHits.incrementAndGet();

        // 检查是否为热点数据
        if (cachedEntry.isHotData()) {
          hotDataHits.incrementAndGet();
          LOGGER.debug("Using hot cached result for: {} (Cache hits: {}, Hot hits: {})",
                      name, hits, hotDataHits.get());
        } else {
          LOGGER.debug("Using cached result for: {} (Cache hits: {})", name, hits);
        }

        return cachedEntry.result;
      } else {
        // 缓存已过期，移除
        PARSING_CACHE.remove(cacheKey);
        LOGGER.debug("Smart cache entry expired for: {}", name);
      }
    }
    cacheMisses.incrementAndGet();

    // parse ANIME exclusively in front, unmodified
    EpisodeMatchingResult result = new EpisodeMatchingResult();
    String nameNoExt = name.replaceFirst("\\.\\w{1,4}$", ""); // remove extension if 1-4 chars
    result = parseAnimeExclusive(result, nameNoExt);
    if (!result.episodes.isEmpty()) {
      // ALWAYS parse date if we have none (but do not use year as season in this case)
      if (result.date == null) {
        EpisodeMatchingResult add = new EpisodeMatchingResult();
        parseDatePattern(add, name);
        if (add.date != null) {
          result.date = add.date;
        }
      }
      return result;
    }

    // first check ONLY filename!
    result = detect(FilenameUtils.getName(name), showname);

    // only EPs found, but no season
    if (!result.episodes.isEmpty() && result.season == -1) {
      // try parsing whole string,
      EpisodeMatchingResult result2 = detect(name, showname);
      // and IF the detected episodes (from filename) are same AMOUNT, take it
      // (so a multifile folder pattern wont override a single file in there)
      if (result2.season != -1 && result2.episodes.size() == result.episodes.size()) {
        result = result2;
      }
      else {
        // take season only - rely on former filename only detection
        result.season = result2.season; // could also be -1, so here's a good point for checking Anime2
        // parse ANIME in workflow - w/o hashes regex
        result = parseAnimeNoHash(result, name);
      }
    }
    else if (result.episodes.isEmpty() && result.date == null) {
      // nothing found - check whole string as such
      result = detect(name, showname);
    }

    // ALWAYS parse date if we have none (but do not use year as season in this case)
    if (result.date == null) {
      EpisodeMatchingResult add = new EpisodeMatchingResult();
      parseDatePattern(add, name);
      if (add.date != null) {
        result.date = add.date;
      }
    }

    // we have found some valid episodes, but w/o season -> upgrade them for season 1
    if (!result.episodes.isEmpty() && !result.episodes.contains(-1) && result.season == -1) {
      result.season = 1;
    }

    // 智能缓存存储（LRU + 热点数据保护）
    smartCacheStore(cacheKey, result);

    return result;
  }

  /**
   * Does all the season/episode detection
   *
   * @param name
   *          the RELATIVE filename (like /dir2/seas1/fname.ext) from the TvShowRoot
   * @param showname
   *          the show name
   * @return result the calculated result
   */
  private static EpisodeMatchingResult detect(String name, String showname) {
    LOGGER.debug("parsing '{}'", name);
    EpisodeMatchingResult result = new EpisodeMatchingResult();
    Pattern regex;
    Matcher m;

    // remove problematic strings from name
    String filename = FilenameUtils.getName(name);

    // check for disc files and remove!!
    if (filename.toLowerCase(Locale.ROOT).matches("(video_ts|vts_\\d\\d_\\d)\\.(vob|bup|ifo)") || // dvd
        filename.toLowerCase(Locale.ROOT).matches("(index\\.bdmv|movieobject\\.bdmv|\\d{5}\\.m2ts)")) { // bluray
      name = FilenameUtils.getPath(name);
    }

    String basename = ParserUtils.removeStopwordsAndBadwordsFromTvEpisodeName(name);
    String foldername = "";

    // parse foldername
    regex = Pattern.compile("(.*[\\/\\\\])");
    m = regex.matcher(basename);
    if (m.find()) {
      foldername = m.group(1);
      basename = basename.replaceAll(regex.pattern(), "");
    }

    // happens, when we only parse filename, but it completely gets stripped out.
    if (basename.isEmpty() && foldername.isEmpty()) {
      return result;
    }

    basename = basename.replaceFirst("\\.\\w{1,4}$", ""); // remove extension if 1-4 chars
    basename = basename.replaceFirst("[\\(\\[]\\d{4}[\\)\\]]", ""); // remove (xxxx) or [xxxx] as year
    basename = basename.replaceFirst("[\\(\\[][A-Fa-f0-9]{8}[\\)\\]]", ""); // remove (xxxxxxxx) or [xxxxxxxx] as 8 byte crc

    basename = " " + basename + " "; // ease regex parsing w/o ^$
    foldername = " " + foldername + " "; // ease regex parsing w/o ^$

    result.stackingMarkerFound = !Utils.getStackingMarker(filename).isEmpty();
    result.name = basename.strip();

    // parse all long named season names, and remove
    result = parseSeasonLong(result, basename + foldername);
    if (result.season != -1) {
      basename = basename.replaceAll("(?i)" + SEASON_LONG.toString(), "");
      foldername = foldername.replaceAll("(?i)" + SEASON_LONG.toString(), "");
    }
    result = parseSeasonMultiEP(result, basename + foldername);
    result = parseSeasonMultiEP2(result, basename + foldername);
    result = parseEpisodePattern(result, basename);

    if (result.season == -1 && !StringUtils.isBlank(foldername)) {
      // we couldn't find a season while using the long season pattern.
      // But IF we have a foldername (in second run only) we can parse the short one here too
      result = parseSeasonOnly(result, foldername);
    }

    if (!result.episodes.isEmpty()) {
      return postClean(result);
    }

    // since we parsed all long variants, now it is a good time to remove the show name, even something like "24"
    if (showname != null && !showname.isEmpty()) {
      // remove string like tvshow name (440, 24, ...)
      basename = basename.replaceAll("(?i)[^ES]" + Pattern.quote(showname), ""); // with our added space, but not prefixed with S/E
      foldername = foldername.replaceAll("(?i)[^ES]" + Pattern.quote(showname), ""); // with our added space, but not prefixed with S/E
      try {
        // Since this is the title, change all spaces to delimiter pattern!
        // "some fine show" would match with "some.fine-show"
        // since we generate a dynamic pattern, guard that with try/catch - the quote() from above would not work
        showname = showname.replaceAll("[ _.-]", "[ _.-]"); // replace all delimiters, with delimiters pattern ;)
        foldername = foldername.replaceAll("(?i)" + showname, "");
      }
      catch (Exception e) {
        // ignore
      }
    }

    // ======================================================================
    // After here are some weird detections
    // run them only, when we have NO result!!!
    // so we step out here...
    // ======================================================================
    result = parseRoman(result, basename);
    if (!result.episodes.isEmpty()) {
      return postClean(result);
    }
    result = parseDatePattern(result, basename);
    if (result.date != null) {
      // since we have a matching date, we wont find episodes solely by number
      return postClean(result);
    }

    // ======================================================================
    // After here are some really, REALLY generic detections.
    // Just take 3, 2 or 1 single number for episode
    // strip as many as we know... it's the last change to do some detection!
    // ======================================================================

    // ignore disc files
    MediaFile mf = new MediaFile();
    mf.setFilename(filename); // cant use Paths.get()
    if (mf.isDiscFile()) {
      return postClean(result);
    }

    // parse season short (S 01), but only if we do not have already one!
    if (result.season == -1) {
      result = parseSeasonOnly(result, basename + foldername);
      if (result.season != -1) {
        foldername = foldername.replaceAll("(?i)" + SEASON_ONLY.toString(), "");
        basename = basename.replaceAll("(?i)" + SEASON_ONLY.toString(), "");
      }
    }

    // parse episode short (EP 01), but only if we do not have already one!
    if (result.episodes.isEmpty()) {
      result = parseEpisodeOnly(result, basename);
    }
    if (!result.episodes.isEmpty()) {
      return postClean(result);
    }

    List<String> numbersOnly = new ArrayList<>();
    // strip all [optionals]!
    String optionals = "[\\[\\{](.*?)[\\]\\}]";
    String woOptionals = basename.replaceAll(optionals, "");
    numbersOnly.addAll(Arrays.asList(woOptionals.split("[\\s\\|_.-]"))); // split on our delimiters
    // now we should have numbers only in there - if we find something different - remove
    for (int i = numbersOnly.size() - 1; i >= 0; i--) {
      if (numbersOnly.get(i).isEmpty() || numbersOnly.get(i).matches(".*?\\D.*?")) {
        numbersOnly.remove(i);
      }
    }

    // nothing found removing []? try with optionals...
    if (numbersOnly.size() == 0) {
      regex = Pattern.compile(optionals); // only optionals
      m = regex.matcher(basename);
      while (m.find()) {
        String delimitedNumbers = " " + m.group(1) + " "; // ease regex
        numbersOnly.addAll(Arrays.asList(delimitedNumbers.split("[\\s\\|_.-]"))); // split on our delimiters
      }
    }
    // now we should have numbers only in there - if we find something different - remove
    for (int i = numbersOnly.size() - 1; i >= 0; i--) {
      if (numbersOnly.get(i).isEmpty() || numbersOnly.get(i).matches(".*?\\D.*?")) {
        numbersOnly.remove(i);
      }
    }

    // reverse array, so that the latter number is more significant (episode numbers are mostly at end)
    // (when having 2 or more equal length numbers)
    Collections.reverse(numbersOnly);

    result = parseNumbers4(result, numbersOnly);
    if (!result.episodes.isEmpty()) {
      return postClean(result);
    }
    result = parseNumbers3(result, numbersOnly);
    if (!result.episodes.isEmpty()) {
      return postClean(result);
    }
    result = parseNumbers2(result, numbersOnly);
    if (!result.episodes.isEmpty()) {
      return postClean(result);
    }
    result = parseNumbers1(result, numbersOnly);
    return postClean(result);
  }

  private static EpisodeMatchingResult parseNumbers1(EpisodeMatchingResult result, List<String> numbersOnly) {
    for (String num : numbersOnly) {
      if (num.length() == 1) {
        int ep = Integer.parseInt(num); // just one :P
        if (ep > 0 && !result.episodes.contains(ep)) {
          result.episodes.add(ep);
          LOGGER.trace("add found EP '{}'", ep);
        }
        return result;
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseNumbers2(EpisodeMatchingResult result, List<String> numbersOnly) {
    for (String num : numbersOnly) {
      if (num.length() == 2) {
        // Filename contains only 2 subsequent numbers; parse this as EE
        int ep = Integer.parseInt(num);
        if (ep > 0 && !result.episodes.contains(ep)) {
          result.episodes.add(ep);
          LOGGER.trace("add found EP '{}'", ep);
        }
        return result;
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseNumbers3(EpisodeMatchingResult result, List<String> numbersOnly) {
    for (String num : numbersOnly) {
      if (num.length() == 3) {
        // Filename contains only 3 subsequent numbers; parse this as SEE
        int s = Integer.parseInt(num.substring(0, 1));
        int ep = Integer.parseInt(num.substring(1));
        if (result.season == -1 || result.season == s) {
          if (ep > 0 && !result.episodes.contains(ep)) {
            result.episodes.add(ep);
            LOGGER.trace("add found EP '{}'", ep);
          }
          LOGGER.trace("add found season '{}'", s);
          result.season = s;
        }
        // for 3 character numbers, we iterate multiple times (with same season)!
        // do not stop on first one"
        // return result;
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseNumbers4(EpisodeMatchingResult result, List<String> numbersOnly) {
    for (String num : numbersOnly) {
      if (num.length() == 4) {
        // Filename contains only 4 subsequent numbers; parse this as SSEE
        int s = Integer.parseInt(num.substring(0, 2));
        int ep = Integer.parseInt(num.substring(2));
        if (result.season == s) { // we NEED to have a season set, else every year would be a valid SSEE number!!
          if (ep > 0 && !result.episodes.contains(ep)) {
            result.episodes.add(ep);
            LOGGER.trace("add found EP '{}'", ep);
          }
        }
        // for 4 character numbers, we iterate multiple times (with same season)!
        // do not stop on first one"
        // return result;
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseDatePattern(EpisodeMatchingResult result, String name) {
    Matcher m;
    // Date1 pattern yyyy-mm-dd
    m = DATE_1.matcher(name);
    if (m.find()) {
      int s = result.season;
      try {
        s = Integer.parseInt(m.group(1));
        result.date = new SimpleDateFormat("yyyy-MM-dd").parse(m.group(1) + "-" + m.group(2) + "-" + m.group(3));
      }
      catch (NumberFormatException | ParseException nfe) {
        // can not happen from regex since we only come here with max 2 numeric chars
      }
      result.season = s;
      LOGGER.trace("add found year as season '{}', date: '{}'", s, result.date);
      return result; // since we have a matching year, we wont find episodes solely by number
    }
    // Date2 pattern dd-mm-yyyy
    m = DATE_2.matcher(name);
    if (m.find()) {
      int s = result.season;
      try {
        s = Integer.parseInt(m.group(3));
        result.date = new SimpleDateFormat("dd-MM-yyyy").parse(m.group(1) + "-" + m.group(2) + "-" + m.group(3));
      }
      catch (NumberFormatException | ParseException nfe) {
        // can not happen from regex since we only come here with max 2 numeric chars
      }
      result.season = s;
      LOGGER.trace("add found year as season '{}', date: '{}'", s, result.date);
      return result; // since we have a matching year, we wont find episodes solely by number
    }

    return result;
  }

  private static EpisodeMatchingResult parseRoman(EpisodeMatchingResult result, String name) {
    Pattern regex;
    Matcher m;
    // parse Roman only when not found anything else!!
    if (result.episodes.isEmpty()) {
      regex = ROMAN_PATTERN;
      m = regex.matcher(name);
      while (m.find()) {
        int ep = 0;
        ep = decodeRoman(m.group(2));
        if (ep > 0 && !result.episodes.contains(ep)) {
          result.episodes.add(ep);
          LOGGER.trace("add found EP '{}'", ep);
        }
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseEpisodePattern(EpisodeMatchingResult result, String name) {
    Pattern regex;
    Matcher m;
    // EpisodeNR-only parsing, when previous styles didn't find anything! (even with unicode slash / stacking style)
    if (result.episodes.isEmpty()) {
      regex = EPISODE_PATTERN_NR;
      m = regex.matcher(name);
      while (m.find()) {
        int ep = 0;
        try {
          ep = Integer.parseInt(m.group(1));
        }
        catch (NumberFormatException nfe) {
          // can not happen from regex since we only come here with a numeric chars
        }
        int max = 0;
        try {
          max = Integer.parseInt(m.group(2));
        }
        catch (NumberFormatException nfe) {
          // can not happen from regex since we only come here with a numeric chars
        }
        if (ep > 0 && !result.episodes.contains(ep) && ep <= max) {
          result.episodes.add(ep);
          LOGGER.trace("add found EP '{}'", ep);
        }
      }
    }

    // Episode-only parsing, when previous styles didn't find anything!
    if (result.episodes.isEmpty()) {
      regex = EPISODE_PATTERN_2;
      m = regex.matcher(name);
      while (m.find()) {
        int ep = 0;
        try {
          ep = Integer.parseInt(m.group(1));
        }
        catch (NumberFormatException nfe) {
          // can not happen from regex since we only come here with max 2 numeric chars
        }
        if (ep > 0 && !result.episodes.contains(ep)) {
          result.episodes.add(ep);
          LOGGER.trace("add found EP '{}'", ep);
        }
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseSeasonMultiEP2(EpisodeMatchingResult result, String name) {
    Pattern regex;
    Matcher m;
    // parse XYY or XX_YY 1-N
    regex = SEASON_MULTI_EP_2;
    m = regex.matcher(name);
    while (m.find()) {
      int s = -1;
      try {
        // for the case of name.1x02x03.ext
        s = Integer.parseInt(m.group(1));
        if (m.group(2) != null && result.season < 0) {
          result.season = s;
          LOGGER.trace("add found season '{}", s);
        }
        // multiSE pattern MUST have always same (first) season - mixing not possible!
        if (result.season == s) {
          String eps = m.group(2); // name.s01"ep02-02-04".ext
          // now we have a string of 1-N episodes - parse them
          Pattern regex2 = EPISODE_PATTERN; // episode fixed to 1-2 chars
          Matcher m2 = regex2.matcher(eps);
          while (m2.find()) {
            int ep = 0;
            try {
              ep = Integer.parseInt(m2.group(1));
            }
            catch (NumberFormatException nfe) {
              // can not happen from regex since we only come here with max 2 numeric chars
            }
            if (ep > 0 && !result.episodes.contains(ep)) {
              result.episodes.add(ep);
              LOGGER.trace("add found EP '{}'", ep);
            }
          }
        }
        else {
          LOGGER.trace("also found season {}, but we already have a season {} - ignoring", s, result.season);
        }
      }
      catch (NumberFormatException nfe) {
        // can not happen from regex since we only come here with max 2 numeric chars
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseSeasonMultiEP(EpisodeMatchingResult result, String name) {
    Pattern regex;
    Matcher m;
    // parse SxxEPyy 1-N
    regex = SEASON_MULTI_EP;
    m = regex.matcher(name);
    while (m.find()) {
      int s = -1;
      try {
        s = Integer.parseInt(m.group(1));
        if (result.season < 0) {
          result.season = s;
          LOGGER.trace("add found season '{}", s);
        }

        // multiSE pattern MUST have always same (first) season - mixing not possible!
        if (result.season == s) {
          String eps = m.group(2); // name.s01"ep02-02-04".ext
          // now we have a string of 1-N episodes - parse them
          Pattern regex2 = EPISODE_PATTERN; // episode fixed to 1-2 chars
          Matcher m2 = regex2.matcher(eps);
          while (m2.find()) {
            int ep = -1;
            try {
              ep = Integer.parseInt(m2.group(1));
            }
            catch (NumberFormatException nfe) {
              // can not happen from regex since we only come here with max 2 numeric chars
            }
            // check if the found episode is not -1 (0 allowed!), not already in the list and if multi episode
            if (ep > -1 && !result.episodes.contains(ep)) {
              result.episodes.add(ep);
              LOGGER.trace("add found EP '{}'", ep);
            }
          }
        }
        else {
          LOGGER.trace("also found season {}, but we already have a season {} - ignoring", s, result.season);
        }
      }
      catch (NumberFormatException nfe) {
        // can not happen from regex since we only come here with max 2 numeric chars
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseEpisodeOnly(EpisodeMatchingResult result, String name) {
    Matcher m;
    m = EPISODE_ONLY.matcher(name);
    if (m.find()) {
      try {
        int e = Integer.parseInt(m.group(1));
        result.episodes.add(e);
        LOGGER.trace("add found episode '{}'", e);
      }
      catch (NumberFormatException nfe) {
        // ignore
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseSeasonLong(EpisodeMatchingResult result, String name) {
    Matcher m;
    // season detection
    if (result.season == -1) {
      m = SEASON_LONG.matcher(name);
      if (m.find()) {
        int s = result.season;
        try {
          s = Integer.parseInt(m.group(2));
        }
        catch (NumberFormatException nfe) {
          // can not happen from regex since we only come here with max 2 numeric chars
        }
        result.season = s;
        LOGGER.trace("add found season '{}'", s);
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseSeasonOnly(EpisodeMatchingResult result, String name) {
    Matcher m;
    // season detection
    if (result.season == -1) {
      m = SEASON_ONLY.matcher(name);
      if (m.find()) {
        int s = result.season;
        try {
          s = Integer.parseInt(m.group(1));
        }
        catch (NumberFormatException nfe) {
          // can not happen from regex since we only come here with max 2 numeric chars
        }
        result.season = s;
        LOGGER.trace("add found season '{}'", s);
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseAnimeExclusive(EpisodeMatchingResult result, String name) {
    Matcher m;
    // Anything with the filename marked as Special/OVA/OAV/etc goes to season 0, regardless of what the directory may say
    m = ANIME_PREPEND1.matcher(name);
    if (m.find()) {
      try {
        LOGGER.debug("parsed as Anime PREPEND1 '{}'", name);
        int ep = Integer.parseInt(m.group(2));
        result.episodes.add(ep);
        result.season = 0;
      }
      catch (NumberFormatException nfe) {
      }
    }

    if (result.episodes.isEmpty()) {
      // Inside a directory that specifies the season. May include any number of subdirectories. Doesn't try to find season markers in the file name
      m = ANIME_PREPEND2.matcher(name);
      if (m.find()) {
        LOGGER.debug("parsed as Anime PREPEND2 '{}'", name);
        try {
          int ep = Integer.parseInt(m.group(2));
          result.episodes.add(ep);
          result.season = Integer.parseInt(m.group(1));
        }
        catch (NumberFormatException nfe) {
        }
      }
    }

    if (result.episodes.isEmpty()) {
      // Include season marker in the filename
      m = ANIME_PREPEND3.matcher(name);
      if (m.find()) {
        LOGGER.debug("parsed as Anime PREPEND3 '{}'", name);
        try {
          int ep = Integer.parseInt(m.group(2));
          result.episodes.add(ep);
          result.season = Integer.parseInt(m.group(1));
        }
        catch (NumberFormatException nfe) {
        }
      }
    }

    if (result.episodes.isEmpty()) {
      // Anything else gets the default blank first capture, which sets the file to season 1
      m = ANIME_PREPEND4.matcher(name);
      if (m.find()) {
        LOGGER.debug("parsed as Anime PREPEND4 '{}'", name);
        try {
          int ep = Integer.parseInt(m.group(2));
          result.episodes.add(ep);
          result.season = 1;

          // ok, we matched the nice long pattern...
          // But, in case of multiple episodes, regex cannot repeat unlimited, so we get only first and last number
          // do a second regex here, to split it manually
          m = ANIME_PREPEND4_2.matcher(name);
          if (m.find()) {
            LOGGER.debug("parsed as Anime PREPEND4_2 '{}'", name);
            String[] nums = m.group(1).split("-");
            for (String num : nums) {
              ep = Integer.parseInt(num);
              if (!result.episodes.contains(ep)) {
                result.episodes.add(ep);
              }
            }
            // yes, we added already the "last" number above
            // since we ONLY enter that part on 2+ numbers, we should sort it here...
            Collections.sort(result.episodes);
          }
        }
        catch (NumberFormatException nfe) {
        }
      }
    }
    return result;
  }

  private static EpisodeMatchingResult parseAnimeNoHash(EpisodeMatchingResult result, String name) {
    Matcher m;
    // Anything with the filename marked as Special/OVA/OAV/etc goes to season 0, regardless of what the directory may say
    m = ANIME_APPEND1.matcher(name);
    if (m.find()) {
      LOGGER.debug("parsed as Anime APPEND1 '{}'", name);
      try {
        int ep = Integer.parseInt(m.group(2));
        if (!result.episodes.contains(ep)) {
          result.episodes.add(ep);
        }
        result.season = 0;
      }
      catch (NumberFormatException nfe) {
      }
    }

    if (result.episodes.isEmpty() || result.season == -1) {
      // Inside a directory that specifies the season. May include any number of subdirectories. Doesn't try to find season markers in the file name
      m = ANIME_APPEND2.matcher(name);
      if (m.find()) {
        LOGGER.debug("parsed as Anime APPEND2 '{}'", name);
        try {
          int ep = Integer.parseInt(m.group(2));
          if (!result.episodes.contains(ep)) {
            result.episodes.add(ep);
          }
          result.season = Integer.parseInt(m.group(1));
        }
        catch (NumberFormatException nfe) {
        }
      }
    }

    if (result.episodes.isEmpty() || result.season == -1) {
      // Include season marker in the filename
      m = ANIME_APPEND3.matcher(name);
      if (m.find()) {
        LOGGER.debug("parsed as Anime APPEND3 '{}'", name);
        try {
          int ep = Integer.parseInt(m.group(2));
          if (!result.episodes.contains(ep)) {
            result.episodes.add(ep);
          }
          result.season = Integer.parseInt(m.group(1));
        }
        catch (NumberFormatException nfe) {
        }
      }
    }

    // as ANINE it would set episode 1 on undetectable
    // but that interferes with our -1 approach
    // so we must not use the append4 pattern!
    // changed as of 20240803
    if (result.episodes.isEmpty() || result.season == -1) {
      // Anything else gets the default blank first capture, which sets the file to season 1
      m = ANIME_APPEND4.matcher(name);
      if (m.find()) {
        LOGGER.debug("parsed as Anime APPEND4 '{}'", name);
        try {
          int ep = Integer.parseInt(m.group(2));
          if (ep > 0 && !result.episodes.contains(ep)) {
            result.episodes.add(ep);
          }
          result.season = 1;
        }
        catch (NumberFormatException nfe) {
        }
      }
    }
    return result;
  }

  private static EpisodeMatchingResult postClean(EpisodeMatchingResult emr) {
    // try to clean the filename
    emr.cleanedName = cleanFilename(emr.name, new Pattern[] { SEASON_LONG, SEASON_MULTI_EP, SEASON_MULTI_EP_2, EPISODE_PATTERN, EPISODE_PATTERN_2,
        NUMBERS_3_PATTERN, NUMBERS_2_PATTERN, ROMAN_PATTERN, DATE_1, DATE_2, SEASON_ONLY });
    Collections.sort(emr.episodes);
    LOGGER.trace("returning result '{}'", emr);
    return emr;
  }

  private static String cleanFilename(String name, Pattern[] patterns) {
    String result = name;
    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(result);
      if (matcher.find()) {
        result = matcher.replaceFirst("");
      }
    }

    // last but not least, clean all leading/trailing separators
    result = result.replaceAll("^[ \\.\\-_]+", "");
    result = result.replaceAll("[ \\.\\-_]+$", "");

    return result;
  }

  /**
   * Decode single roman.
   * 
   * @param letter
   *          the letter
   * @return the int
   */
  private static int decodeSingleRoman(char letter) {
    switch (letter) {
      case 'M':
        return 1000;

      case 'D':
        return 500;

      case 'C':
        return 100;

      case 'L':
        return 50;

      case 'X':
        return 10;

      case 'V':
        return 5;

      case 'I':
        return 1;

      default:
        return 0;
    }
  }

  /**
   * Decode roman.
   * 
   * @param roman
   *          the roman
   * @return the int
   */
  public static int decodeRoman(String roman) {
    int result = 0;
    String uRoman = roman.toUpperCase(Locale.ROOT); // case-insensitive
    for (int i = 0; i < uRoman.length() - 1; i++) {// loop over all but the last
                                                   // character
      // if this character has a lower value than the next character
      if (decodeSingleRoman(uRoman.charAt(i)) < decodeSingleRoman(uRoman.charAt(i + 1))) {
        // subtract it
        result -= decodeSingleRoman(uRoman.charAt(i));
      }
      else {
        // add it
        result += decodeSingleRoman(uRoman.charAt(i));
      }
    }
    // decode the last character, which is always added
    result += decodeSingleRoman(uRoman.charAt(uRoman.length() - 1));
    return result;
  }

  /******************************************************************************************
   * helper classes
   ******************************************************************************************/
  public static class EpisodeMatchingResult {

    public int           season              = -1;
    public List<Integer> episodes            = new ArrayList<>();
    public String        name                = "";
    public String        cleanedName         = "";
    public Date          date                = null;
    public boolean       stackingMarkerFound = false;

    @Override
    public String toString() {
      return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }
  }
}
