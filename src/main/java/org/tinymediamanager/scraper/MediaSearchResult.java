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
package org.tinymediamanager.scraper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.util.MediaIdUtil;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.StrgUtils;

/**
 * The Class MediaSearchResult.
 *
 * @author Manuel Laggner
 * @since 1.0
 */
public class MediaSearchResult implements Comparable<MediaSearchResult> {
  private static final Logger       LOGGER           = LoggerFactory.getLogger(MediaSearchResult.class);

  private final Map<String, Object> ids              = new HashMap<>();

  private MediaType                 type;
  private String                    providerId;
  private String                    url              = "";
  private String                    title            = "";
  private String                    overview         = "";
  private int                       year             = 0;
  private String                    originalTitle    = "";
  private String                    originalLanguage = "";
  private String                    englishTitle     = "";
  private float                     score            = 0;
  private MediaMetadata             metadata         = null;
  private String                    posterUrl        = "";
  private final List<String>        aliases          = new ArrayList<>();                               // 别名列表，用于多语言匹配

  public MediaSearchResult(String providerId, MediaType type) {
    this.providerId = providerId;
    this.type = type;
  }

  public MediaSearchResult(String providerId, MediaType type, float score) {
    this.providerId = providerId;
    this.type = type;
    this.score = score;
  }

  public MediaSearchResult(String providerId, MediaType type, String id, String title, int year, float score) {
    this.providerId = providerId;
    this.type = type;
    this.ids.put(providerId, StrgUtils.getNonNullString(id));
    this.title = StrgUtils.getNonNullString(title);
    this.year = year;
    this.score = score;
  }

  private MediaSearchResult(Builder builder) {
    type = builder.type;
    setProviderId(builder.providerId);
    setId(builder.id);
    setUrl(builder.url);
    setTitle(builder.title);
    setOverview(builder.overview);
    setYear(builder.year);
    setOriginalTitle(builder.originalTitle);
    setOriginalLanguage(builder.originalLanguage);
    setScore(builder.score);
    setMetadata(builder.metadata);
    setPosterUrl(builder.posterUrl);
  }

  /**
   * merges all entries from other MSR into ours, IF VALUES ARE EMPTY<br>
   * <b>needs testing!</b>
   *
   * @param msr
   *          other MediaSerachResult
   */
  public void mergeFrom(MediaSearchResult msr) {
    if (msr == null || !StringUtils.equals(providerId, msr.providerId) || type != msr.getMediaType()) {
      return;
    }

    url = StringUtils.isEmpty(url) ? msr.getUrl() : url;
    title = StringUtils.isEmpty(title) ? msr.getTitle() : title;
    year = year == 0 ? msr.getYear() : year;
    originalTitle = StringUtils.isEmpty(originalTitle) ? msr.getOriginalTitle() : originalTitle;
    originalLanguage = StringUtils.isEmpty(originalLanguage) ? msr.getOriginalLanguage() : originalLanguage;
    posterUrl = StringUtils.isEmpty(posterUrl) ? msr.getPosterUrl() : posterUrl;

    for (String key : msr.getIds().keySet()) {
      if (!ids.containsKey(key)) {
        ids.put(key, msr.getIds().get(key));
      }
    }

    if (metadata == null) {
      metadata = msr.getMediaMetadata();
    }
    else {
      metadata.mergeFrom(msr.getMediaMetadata());
    }
  }

  public void mergeFrom(MediaMetadata mediaMetadata) {
    if (mediaMetadata == null) {
      return;
    }

    this.metadata = mediaMetadata;

    ids.putAll(mediaMetadata.getIds());
    setTitle(mediaMetadata.getTitle());
    setOriginalTitle(mediaMetadata.getOriginalTitle());
    setYear(mediaMetadata.getYear());
    setOverview(mediaMetadata.getPlot());

    if (!mediaMetadata.getMediaArt(MediaArtwork.MediaArtworkType.POSTER).isEmpty()) {
      MediaArtwork poster = mediaMetadata.getMediaArt(MediaArtwork.MediaArtworkType.POSTER).get(0);
      setPosterUrl(poster.getPreviewUrl());
    }
  }

  /**
   * Get the original title of this search result
   *
   * @return the original title
   */
  public String getOriginalTitle() {
    return originalTitle;
  }

  /**
   * Set the original title for this search result
   *
   * @param originalTitle
   *          the original title
   */
  public void setOriginalTitle(String originalTitle) {
    this.originalTitle = StrgUtils.getNonNullString(originalTitle);
  }

  /**
   * Get the original language of this search result
   *
   * @return the original language
   */
  public String getOriginalLanguage() {
    return originalLanguage;
  }

  /**
   * Set the original language for this search result
   *
   * @param originalLanguage
   *          the original language
   */
  public void setOriginalLanguage(String originalLanguage) {
    this.originalLanguage = StrgUtils.getNonNullString(originalLanguage);
  }

  /**
   * Get the provider id
   *
   * @return the provider id
   */
  public String getProviderId() {
    return providerId;
  }

  /**
   * Set the provider id
   *
   * @param providerId
   *          the provider id
   */
  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  /**
   * Get the title of this search result
   *
   * @return the title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Set the title of this search result
   *
   * @param title
   *          the title
   */
  public void setTitle(String title) {
    this.title = StrgUtils.getNonNullString(title);
  }

  public String getOverview() {
    return overview;
  }

  public void setOverview(String overview) {
    this.overview = overview;
  }

  /**
   * Get the year of this search result
   *
   * @return the year
   */
  public int getYear() {
    return year;
  }

  /**
   * Set the year of this search result
   *
   * @param year
   *          the year
   */
  public void setYear(int year) {
    this.year = year;
  }

  /**
   * Set the year of this search result (nullsafe)
   *
   * @param year
   *          the year
   */
  public void setYear(Integer year) {
    if (year != null) {
      setYear(year.intValue());
    }
  }

  /**
   * Get the score of this search result. 1.0 is perfect match
   *
   * @return the score
   */
  public float getScore() {
    return score;
  }

  /**
   * Set the score of this result
   *
   * @param score
   *          the result
   */
  public void setScore(float score) {
    this.score = score;
  }

  /**
   * Set the score of this result (nullsafe)
   *
   * @param score
   *          the result
   */
  public void setScore(Float score) {
    if (score != null) {
      setScore(score.floatValue());
    }
  }

  /**
   * Get the url to this search result
   *
   * @return the url
   */
  public String getUrl() {
    return url;
  }

  /**
   * Set the url to this search result
   *
   * @param url
   *          the url
   */
  public void setUrl(String url) {
    this.url = StrgUtils.getNonNullString(url);
  }

  /**
   * Get the media type this search result is for
   *
   * @return the media type
   */
  public MediaType getMediaType() {
    return type;
  }

  /**
   * sets the MediaType (used for filtering search results)
   */
  public void setMediaType(MediaType type) {
    this.type = type;
  }

  /**
   * Get the id of this search result
   *
   * @return the id
   */
  public Object getId() {
    return ids.get(providerId);
  }

  /**
   * Set the id of this search result
   *
   * @param id
   *          the search result id
   */
  public void setId(String id) {
    this.ids.put(providerId, StrgUtils.getNonNullString(id));
  }

  /**
   * Set an media id for a provider id
   *
   * @param providerId
   *          the provider id
   * @param id
   *          the media id
   */
  public void setId(String providerId, String id) {
    ids.put(providerId, StrgUtils.getNonNullString(id));
  }

  /**
   * set all given ids
   *
   * @param ids
   *          the ids to set
   */
  public void setIds(Map<String, Object> ids) {
    if (ids == null) {
      return;
    }

    for (Map.Entry<String, Object> entry : ids.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }

      setId(entry.getKey(), entry.getValue().toString());
    }
  }

  /**
   * Get the IMDB id
   *
   * @return the IMDB id
   */
  public String getIMDBId() {
    String imdbId = "";

    // via imdb
    Object obj = ids.get(MediaMetadata.IMDB);
    if (obj != null) {
      imdbId = obj.toString();
    }

    // legacy ID
    if (MediaIdUtil.isValidImdbId(imdbId)) {
      return imdbId;
    }

    return "";
  }

  /**
   * any ID as int or 0
   *
   * @return the ID-value as int or an empty string
   */
  public int getIdAsInt(String key) {
    int id = 0;
    try {
      id = Integer.parseInt(String.valueOf(ids.get(key)));
    }
    catch (Exception e) {
      return 0;
    }
    return id;
  }

  /**
   * any ID as String or empty
   *
   * @return the ID-value as String or an empty string
   */
  public String getIdAsString(String key) {
    Object obj = ids.get(key);
    if (obj == null) {
      return "";
    }
    return String.valueOf(obj);
  }

  /**
   * get all given ids
   *
   * @return a map full of ids
   */
  public Map<String, Object> getIds() {
    return ids;
  }

  /**
   * Set the IMDB id
   *
   * @param imdbid
   *          the IMDB id
   */
  public void setIMDBId(String imdbid) {
    if (MediaIdUtil.isValidImdbId(imdbid)) {
      ids.put(MediaMetadata.IMDB, imdbid);
    }
  }

  /**
   * Get the MediaMetadata
   *
   * @return the MediaMetadata
   */
  public MediaMetadata getMediaMetadata() {
    return metadata;
  }

  /**
   * Set the MediaMetadata if you already got the whole meta data while searching (for buffering)
   *
   * @param md
   *          the MediaMetadata
   */
  public void setMetadata(MediaMetadata md) {
    metadata = md;
  }

  /**
   * Get the English title
   *
   * @return the English title
   */
  public String getEnglishTitle() {
    return englishTitle;
  }

  /**
   * Set the English title
   *
   * @param englishTitle
   *          the English title
   */
  public void setEnglishTitle(String englishTitle) {
    this.englishTitle = StrgUtils.getNonNullString(englishTitle);
  }

  /**
   * Get the poster url
   *
   * @return the poster url
   */
  public String getPosterUrl() {
    return posterUrl;
  }

  /**
   * calculate the search score by comparing the available result with the search options
   * 
   * 使用分层加权设计： - 标题相似度：60% 权重 - 年份匹配：25% 权重 - 其他因素：15% 权重（海报、包含匹配等）
   *
   * @param options
   *          the search options which have been used for searching
   */
  public void calculateScore(MediaSearchAndScrapeOptions options) {
    String searchQuery = options.getSearchQuery();
    int searchYear = options.getSearchYear();

    // 权重配置
    final float TITLE_WEIGHT = 0.60f;
    final float YEAR_WEIGHT = 0.25f;
    final float OTHER_WEIGHT = 0.15f;

    // ========== 1. 标题相似度得分 (0-1) ==========
    float titleScore = calculateTitleScore(searchQuery);

    // ========== 2. 年份匹配得分 (0-1) ==========
    float yearScore = calculateYearScore(searchYear, year);

    // ========== 3. 其他因素得分 (0-1) ==========
    float otherScore = calculateOtherScore(searchQuery);

    // ========== 4. 加权计算最终分数 ==========
    float calculatedScore = titleScore * TITLE_WEIGHT + yearScore * YEAR_WEIGHT + otherScore * OTHER_WEIGHT;

    // ========== 5. 部分ID匹配加分（不钳制，允许超过1.0以确保排序优先）==========
    float idBonus = calculateIdBonus(options);
    if (idBonus > 0) {
      calculatedScore += idBonus;
      LOGGER.trace("ID bonus applied: +{:.2f}, score now: {:.2f}", idBonus, calculatedScore);
    }

    // ========== 6. 边界保护（下限0，上限不限制以保持ID匹配优先级）==========
    calculatedScore = Math.max(0, calculatedScore);
    // 注意：不再将分数钳制在1.0以下，允许ID匹配的结果超过100%以确保排序优先

    LOGGER.debug(
        "Score calculation: query='{}' title='{}' year={}/{} => title={:.2f}*{:.0f}% + year={:.2f}*{:.0f}% + other={:.2f}*{:.0f}% + idBonus={:.2f} = {:.2f}",
        searchQuery, title, year, searchYear, titleScore, TITLE_WEIGHT * 100, yearScore, YEAR_WEIGHT * 100, otherScore, OTHER_WEIGHT * 100, idBonus,
        calculatedScore);

    setScore(calculatedScore);
  }

  /**
   * 计算标题相似度得分
   * 
   * @param searchQuery
   *          搜索词
   * @return 相似度得分 [0, 1]
   */
  private float calculateTitleScore(String searchQuery) {
    // 比较搜索词与各种标题的相似度，取最高分
    float score = Math.max(Math.max(MetadataUtil.calculateScore(searchQuery, title), MetadataUtil.calculateScore(searchQuery, originalTitle)),
        MetadataUtil.calculateScore(searchQuery, englishTitle));

    // 遍历别名列表，取最高相似度分数
    for (String alias : aliases) {
      if (StringUtils.isNotBlank(alias)) {
        score = Math.max(score, MetadataUtil.calculateScore(searchQuery, alias));
      }
    }

    // 如果搜索词完全包含在标题中，确保至少有合理的基础分
    if (isSearchContainedInTitle(searchQuery)) {
      score = Math.max(score, 0.70f);
      LOGGER.trace("search '{}' contained in title '{}' - ensuring minimum score 0.70", searchQuery, title);
    }

    return score;
  }

  /**
   * 检查搜索词是否完全包含在标题中
   */
  private boolean isSearchContainedInTitle(String searchQuery) {
    if (StringUtils.isBlank(searchQuery)) {
      return false;
    }

    String searchLower = searchQuery.replaceAll("[\\s\\p{Punct}]", "").toLowerCase();
    if (searchLower.isEmpty()) {
      return false;
    }

    String titleLower = title.replaceAll("[\\s\\p{Punct}]", "").toLowerCase();
    String originalTitleLower = StringUtils.isNotBlank(originalTitle) ? originalTitle.replaceAll("[\\s\\p{Punct}]", "").toLowerCase() : "";

    return titleLower.contains(searchLower) || originalTitleLower.contains(searchLower);
  }

  /**
   * 计算年份匹配得分
   * 
   * @param searchYear
   *          搜索年份
   * @param resultYear
   *          结果年份
   * @return 年份得分 [0, 1]
   */
  private float calculateYearScore(int searchYear, int resultYear) {
    // 无搜索年份 - 返回中性分数
    if (searchYear <= 1900) {
      return 0.5f;
    }

    // 结果无年份 - 返回较低分数
    if (resultYear == 0) {
      return 0.3f;
    }

    int diff = Math.abs(searchYear - resultYear);

    // 根据年份差异返回得分（针对电视剧优化，跨年播出常见）
    if (diff == 0) {
      return 1.0f; // 精确匹配
    }
    else if (diff == 1) {
      return 0.90f; // ±1年容差（电视剧跨年播出常见）
    }
    else if (diff == 2) {
      return 0.80f; // ±2年（季度延续）
    }
    else if (diff == 3) {
      return 0.65f; // ±3年
    }
    else if (diff <= 5) {
      return 0.45f; // ±4-5年
    }
    else {
      return Math.max(0.1f, 0.45f - (diff - 5) * 0.05f); // 渐进衰减，最低0.1
    }
  }

  /**
   * 计算其他因素得分
   * 
   * @param searchQuery
   *          搜索词
   * @return 其他因素得分 [0, 1]
   */
  private float calculateOtherScore(String searchQuery) {
    float score = 0.5f; // 基础分

    // 有海报加分
    if (StringUtils.isNotBlank(posterUrl)) {
      score += 0.25f;
    }

    // 搜索词包含在标题中额外加分
    if (isSearchContainedInTitle(searchQuery)) {
      score += 0.25f;
    }

    return Math.min(1.0f, score);
  }

  /**
   * 计算部分ID匹配加分
   * 
   * @param options
   *          搜索选项
   * @return ID匹配加分 [0, 0.3]
   */
  private float calculateIdBonus(MediaSearchAndScrapeOptions options) {
    float idBonus = 0f;

    // TVDB ID 匹配加分
    int optionTvdbId = options.getIdAsIntOrDefault("tvdb", 0);
    int resultTvdbId = getIdAsInt("tvdb");
    if (optionTvdbId > 0 && optionTvdbId == resultTvdbId) {
      idBonus = Math.max(idBonus, 0.15f);
      LOGGER.trace("TVDB ID match bonus applied: +0.15");
    }

    // IMDB ID 匹配加分
    String optionImdbId = options.getImdbId();
    String resultImdbId = getIMDBId();
    if (StringUtils.isNotBlank(optionImdbId) && optionImdbId.equals(resultImdbId)) {
      idBonus = Math.max(idBonus, 0.20f);
      LOGGER.trace("IMDB ID match bonus applied: +0.20");
    }

    // TMDB ID 匹配加分（同时检查 tmdb 和 tmdb_score_only 两个键）
    int optionTmdbId = options.getIdAsIntOrDefault("tmdb", 0);
    if (optionTmdbId == 0) {
      // 如果 tmdb 键没有值，尝试获取 tmdb_score_only 键（仅用于评分的路径 ID）
      optionTmdbId = options.getIdAsIntOrDefault("tmdb_score_only", 0);
    }
    int resultTmdbId = getIdAsInt("tmdb");
    if (optionTmdbId > 0 && optionTmdbId == resultTmdbId) {
      idBonus = Math.max(idBonus, 0.25f);
      LOGGER.trace("TMDB ID match bonus applied: +0.25");
    }

    return idBonus;
  }

  /**
   * 获取别名列表
   * 
   * @return 别名列表
   */
  public List<String> getAliases() {
    return aliases;
  }

  /**
   * 设置别名列表
   * 
   * @param aliases
   *          别名列表
   */
  public void setAliases(List<String> aliases) {
    this.aliases.clear();
    if (aliases != null) {
      this.aliases.addAll(aliases);
    }
  }

  /**
   * 添加单个别名
   * 
   * @param alias
   *          别名
   */
  public void addAlias(String alias) {
    if (StringUtils.isNotBlank(alias) && !this.aliases.contains(alias)) {
      this.aliases.add(alias);
    }
  }

  /**
   * Set the poster url
   *
   * @param posterUrl
   *          the poster url
   */
  public void setPosterUrl(String posterUrl) {
    this.posterUrl = StrgUtils.getNonNullString(posterUrl);
  }

  @Override
  public int compareTo(MediaSearchResult arg0) {
    if (getScore() < arg0.getScore()) {
      return 1;
    }
    else if (getScore() == arg0.getScore()) {
      // same score - rank on year
      if (year == arg0.getYear()) {
        // same year too? we just need to sort by _anything_
        return Integer.compare(hashCode(), arg0.hashCode());
      }
      else {
        return Integer.compare(getYear(), arg0.getYear());
      }
    }
    else {
      return -1;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    MediaSearchResult result = (MediaSearchResult) o;
    return Objects.equals(providerId, result.providerId) && Objects.equals(type, result.type) && Objects.equals(title, result.title)
        && ids.equals(result.ids);
  }

  @Override
  public int hashCode() {
    return Objects.hash(providerId, type, title, ids);
  }

  /**
   * <p>
   * Uses <code>ReflectionToStringBuilder</code> to generate a <code>toString</code> for the specified object.
   * </p>
   *
   * @return the String result
   * @see ReflectionToStringBuilder#toString(Object)
   */
  @Override
  public String toString() {
    return (new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE) {
      @Override
      protected boolean accept(Field f) {
        return super.accept(f) && !f.getName().equals("metadata");
      }
    }).toString();
  }

  public static final class Builder {

    private final MediaType type;

    private String          id;
    private String          providerId;
    private String          url;
    private String          title;
    private String          overview;
    private int             year;
    private String          originalTitle;
    private String          originalLanguage;
    private float           score;
    private MediaMetadata   metadata;
    private String          posterUrl;

    public Builder(@Nonnull MediaType type) {
      this.type = type;
    }

    @Nonnull
    public Builder providerId(@Nonnull String val) {
      providerId = val;
      return this;
    }

    @Nonnull
    public Builder id(@Nonnull String val) {
      id = val;
      return this;
    }

    @Nonnull
    public Builder url(@Nonnull String val) {
      url = val;
      return this;
    }

    @Nonnull
    public Builder title(@Nonnull String val) {
      title = val;
      return this;
    }

    @Nonnull
    public Builder overview(@Nonnull String val) {
      overview = val;
      return this;
    }

    @Nonnull
    public Builder year(int val) {
      year = val;
      return this;
    }

    @Nonnull
    public Builder originalTitle(@Nonnull String val) {
      originalTitle = val;
      return this;
    }

    @Nonnull
    public Builder originalLanguage(@Nonnull String val) {
      originalLanguage = val;
      return this;
    }

    @Nonnull
    public Builder score(float val) {
      score = val;
      return this;
    }

    @Nonnull
    public Builder metadata(@Nonnull MediaMetadata val) {
      metadata = val;
      return this;
    }

    @Nonnull
    public Builder posterUrl(@Nonnull String val) {
      posterUrl = val;
      return this;
    }

    @Nonnull
    public MediaSearchResult build() {
      return new MediaSearchResult(this);
    }
  }
}
