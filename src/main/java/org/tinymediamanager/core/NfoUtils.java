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

package org.tinymediamanager.core;

import java.util.Locale;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaFileAudioStream;
import org.tinymediamanager.core.entities.Person;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.util.LanguageUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Utility class for handling NFO-related operations in TinyMediaManager.
 * <p>
 * Provides static methods for mapping media metadata to NFO-compatible formats, creating XML elements for video and audio streams, and extracting or
 * setting well-known IDs for media entities and persons. Includes helpers for XML transformation and element retrieval.
 * 
 * @author Manuel Laggner
 */
public final class NfoUtils {

  private static final String ORACLE_IS_STANDALONE = "http://www.oracle.com/xml/is-standalone";

  /**
   * Utility class - do not instantiate
   */
  private NfoUtils() {
    throw new IllegalAccessError();
  }

  /**
   * add all well known ids for the given {@link Person} as XML children
   *
   * @param element
   *          the NFO {@link Element} to add the ids to
   * @param person
   *          the {@link Person} to get the ids from
   */
  public static void addPersonIdsAsChildren(Element element, Person person) {
    // TMDB id
    int tmdbId = person.getIdAsInt(MediaMetadata.TMDB);
    if (tmdbId > 0) {
      Element id = element.getOwnerDocument().createElement("tmdbid");
      id.setTextContent(String.valueOf(tmdbId));
      element.appendChild(id);
    }

    // IMDB id
    String imdbId = person.getIdAsString(MediaMetadata.IMDB);
    if (StringUtils.isNotBlank(imdbId)) {
      Element id = element.getOwnerDocument().createElement("imdbid");
      id.setTextContent(imdbId);
      element.appendChild(id);
    }

    // TVDB id
    int tvdbId = person.getIdAsInt(MediaMetadata.TVDB);
    if (tvdbId > 0) {
      Element id = element.getOwnerDocument().createElement("tvdbid");
      id.setTextContent(String.valueOf(tvdbId));
      element.appendChild(id);
    }
  }

  /**
   * add all well known ids for the given {@link Person} as XML attributes
   *
   * @param element
   *          the NFO {@link Element} to add the ids to
   * @param person
   *          the {@link Person} to get the ids from
   */
  public static void addPersonIdsAsAttributes(Element element, Person person) {
    // TMDB id
    int tmdbId = person.getIdAsInt(MediaMetadata.TMDB);
    if (tmdbId > 0) {
      element.setAttribute("tmdbid", String.valueOf(tmdbId));
    }

    // IMDB id
    String imdbId = person.getIdAsString(MediaMetadata.IMDB);
    if (StringUtils.isNotBlank(imdbId)) {
      element.setAttribute("imdbid", imdbId);
    }

    // TVDB id
    int tvdbId = person.getIdAsInt(MediaMetadata.TVDB);
    if (tvdbId > 0) {
      element.setAttribute("tvdbid", String.valueOf(tvdbId));
    }
  }

  /**
   * try to detect the default scraper by the given ids
   *
   * @return the scraper where the default should be set
   */
  public static String detectDefaultScraper(MediaEntity mediaEntity) {
    // IMDB first
    if (mediaEntity.getIds().containsKey(MediaMetadata.IMDB)) {
      return MediaMetadata.IMDB;
    }

    // TVDB second
    if (mediaEntity.getIds().containsKey(MediaMetadata.TVDB)) {
      return MediaMetadata.TVDB;
    }

    // TMDB third
    if (mediaEntity.getIds().containsKey(MediaMetadata.TMDB)) {
      return MediaMetadata.TMDB;
    }

    // the first found as fallback
    return mediaEntity.getIds().keySet().stream().findFirst().orElse("");
  }

  /**
   * get the transformer for XML output
   *
   * @return the transformer
   * @throws Exception
   *           any Exception that has been thrown
   */
  public static Transformer getTransformer() throws Exception {
    Transformer transformer = TransformerFactory.newInstance().newTransformer(); // NOSONAR

    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");
    // not supported in all JVMs
    try {
      transformer.setOutputProperty(ORACLE_IS_STANDALONE, "yes");
    }
    catch (Exception ignored) {
      // okay, seems we're not on OracleJDK, OpenJDK or AdoptOpenJDK
    }
    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

    return transformer;
  }

  /**
   * get any single element by the tag name
   *
   * @param tag
   *          the tag name
   * @return an element or null
   */
  public static Element getSingleElementByTag(Document document, String tag) {
    NodeList nodeList = document.getElementsByTagName(tag);
    for (int i = 0; i < nodeList.getLength(); ++i) {
      Node node = nodeList.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        return (Element) node;
      }
    }
    return null;
  }

  /**
   * Maps a given video codec string to its NFO-compatible representation. Converts common codec names to their standardized NFO values (e.g., "avc"
   * to "h264"). If the codec is blank, returns an empty string. If the codec is not recognized, returns the lower-case version of the input.
   *
   * @param codec
   *          the video codec string to map
   * @return the NFO-compatible codec string
   */
  private static String mapVideoCodecToNfo(String codec) {
    if (StringUtils.isBlank(codec)) {
      return "";
    }

    return switch (codec.toLowerCase()) {
      case "avc" -> "h264";
      case "h265" -> "hevc";
      case "mpeg-1" -> "mpeg1";
      case "mpeg-2" -> "mpeg2";
      case "mpeg-4" -> "mpeg4";
      default -> codec.toLowerCase();
    };
  }

  /**
   * Maps a given audio codec string to its NFO-compatible representation. Converts common codec names to standardized NFO values (e\.g\., "ac3ex" to
   * "ac3"\, "dts-x" to "dtshd_ma_x"\)\. If the codec is blank\, returns an empty string\. If the codec is not recognized\, returns the lower-case
   * version of the input\.
   *
   * @param codec
   *          the audio codec string to map
   * @return the NFO-compatible codec string
   */
  private static String mapAudioCodecToNfo(String codec) {
    if (StringUtils.isBlank(codec)) {
      return "";
    }

    return switch (codec.toLowerCase()) {
      case "ac3ex" -> "ac3";
      case "adpcm_ms" -> "pcm";
      case "dts", "dts-es", "dts-96/24" -> "dca";
      case "truehd/atmos" -> "truehd_atmos";
      case "dtshd-hra" -> "dtshd_hra";
      case "dtshd-ma" -> "dtshd_ma";
      case "dts-x" -> "dtshd_ma_x";
      case "dts-x-imax" -> "dtshd_ma_x_imax";
      case "eac3/atmos" -> "eac3_ddp_atmos";
      default -> codec.toLowerCase();
    };
  }

  /**
   * Create the correct stereo mode for 3D videos.<br>
   * Mapped to Kodi values from the <a href="https://github.com/xbmc/xbmc/blob/master/xbmc/guilib/StereoscopicsManager.cpp#L69">Kodi wiki</a>
   *
   * @param stereomode
   *          the stereomode to map
   * @return the correct Kodi stereomode
   */
  private static String mapStereomode(String stereomode) {
    if (StringUtils.isBlank(stereomode)) {
      return "";
    }

    // old style till TMM 5.1.4
    if (stereomode.equals(MediaFileHelper.VIDEO_3D_SBS) || stereomode.equals(MediaFileHelper.VIDEO_3D_HSBS)) {
      return "split_vertical";
    }
    else if (stereomode.equals(MediaFileHelper.VIDEO_3D_TAB) || stereomode.equals(MediaFileHelper.VIDEO_3D_HTAB)) {
      return "split_horizontal";
    }

    // new style as of TMM 5.1.5
    return switch (stereomode.toLowerCase()) {
      case "left_right", "right_left" -> "split_vertical";
      case "checkerboard_rl", "checkerboard_lr" -> "checkerboard";
      case "top_bottom", "bottom_top" -> "split_horizontal";
      case "row_interleaved_rl", "row_interleaved_lr" -> "row_interleaved";
      case "col_interleaved_rl", "col_interleaved_lr", "block_lr", "block_rl" -> "off"; // unsupported in Kodi
      default -> stereomode.toLowerCase();
    };
  }

  /**
   * Maps a given video resolution string to its NFO-compatible representation. Converts "2160p" to "4K" and "4320p" to "8K". For other values,
   * removes the "p" suffix.
   *
   * @param videoFormat
   *          the video resolution string to map (e.g., "1080p", "2160p")
   * @return the NFO-compatible resolution string (e.g., "1080", "4K", "8K"), or an empty string if input is blank
   */
  private static String mapVideoResolutionToNfo(String videoFormat) {
    if (StringUtils.isBlank(videoFormat)) {
      return "";
    }

    return switch (videoFormat.toLowerCase()) {
      case "2160p" -> "4K";
      case "4320p" -> "8K";
      default -> videoFormat.replace("p", "");
    };
  }

  /**
   * Creates an XML element representing video stream details for NFO files.
   * <p>
   * The returned element contains child elements for codec, aspect ratio, width, height, resolution, HDR type (if available), and stereomode (if 3D
   * format is present).
   * <p>
   * Codec and resolution values are mapped to NFO-compatible formats. HDR type is mapped to Kodi skin values if possible.
   *
   * @param parent
   *          the parent XML element to use for document context
   * @param mediaFile
   *          the {@link MediaFile} containing video stream information
   * @return an XML {@link Element} named "video" with stream details as children
   */
  public static Element createStreamdetailsVideoTag(Element parent, MediaFile mediaFile) {
    Document document = parent.getOwnerDocument();

    Element video = document.createElement("video");

    Element codec = document.createElement("codec");
    codec.setTextContent(mapVideoCodecToNfo(mediaFile.getVideoCodec()));
    video.appendChild(codec);

    Element aspect = document.createElement("aspect");
    aspect.setTextContent(String.format(Locale.US, "%.2f", mediaFile.getAspectRatio()));
    video.appendChild(aspect);

    Element width = document.createElement("width");
    width.setTextContent(Integer.toString(mediaFile.getVideoWidth()));
    video.appendChild(width);

    Element height = document.createElement("height");
    height.setTextContent(Integer.toString(mediaFile.getVideoHeight()));
    video.appendChild(height);

    Element resolution = document.createElement("resolution");
    resolution.setTextContent(mapVideoResolutionToNfo(mediaFile.getVideoFormat()));
    video.appendChild(resolution);

    if (StringUtils.isNotEmpty(mediaFile.getHdrFormat())) {
      String hdrFormat = mediaFile.getHdrFormat().toLowerCase();

      // basically a TMM string to Kodi skin mapping, but only one
      Element hdrtype = document.createElement("hdrtype");
      if (hdrFormat.contains("dolby vision")) {
        hdrtype.setTextContent("dolbyvision");
      }
      else if (hdrFormat.contains("hdr10+")) {
        hdrtype.setTextContent("hdr10plus");
      }
      else {
        hdrtype.setTextContent(hdrFormat);
      }
      video.appendChild(hdrtype);
    }

    if (!mediaFile.getVideo3DFormat().isEmpty()) {
      Element stereomode = document.createElement("stereomode");
      stereomode.setTextContent(mapStereomode(mediaFile.getVideo3DFormat()));
      video.appendChild(stereomode);
    }

    return video;
  }

  /**
   * Creates an XML element representing audio stream details for NFO files.
   * <p>
   * The returned element contains child elements for codec, language, and channels. Codec values are mapped to NFO-compatible formats.
   *
   * @param parent
   *          the parent XML element to use for document context
   * @param audioStream
   *          the {@link MediaFileAudioStream} containing audio stream information
   * @return an XML {@link Element} named "audio" with stream details as children
   */
  public static Element createStreamdetailsAudioTag(Element parent, MediaFileAudioStream audioStream) {
    Document document = parent.getOwnerDocument();

    Element audio = document.createElement("audio");

    Element codec = document.createElement("codec");
    codec.setTextContent(mapAudioCodecToNfo(audioStream.getCodec()));
    audio.appendChild(codec);

    Element language = document.createElement("language");
    language.setTextContent(LanguageUtils.parseLanguageFromString(audioStream.getLanguage()));
    audio.appendChild(language);

    Element channels = document.createElement("channels");
    channels.setTextContent(Integer.toString(audioStream.getAudioChannels()));
    audio.appendChild(channels);

    return audio;
  }
}
