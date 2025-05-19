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
package org.tinymediamanager.scraper.util;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

/**
 * Utility class providing various string manipulation and conversion methods. Includes functionality for hex conversion, character replacement,
 * Unicode handling, string normalization, and various string formatting operations.
 *
 * @author Manuel Laggner, Myron Boyle
 * @since 1.0
 */
public class StrgUtils {
  private static final Map<Integer, Replacement> REPLACEMENTS          = new HashMap<>(20);
  private static final Map<Integer, String>      INVALID_CHARACTERS    = new HashMap<>(7);
  private static final Map<Integer, String>      SEPARATOR_CHARACTERS  = new HashMap<>(2);
  private static final String[]                  COMMON_TITLE_PREFIXES = buildCommonTitlePrefixes();
  private static final char[]                    HEX_ARRAY             = "0123456789ABCDEF".toCharArray();
  private static final byte[]                    DIGITS_LOWER          = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
      'f' };
  private static final Pattern                   NORMALIZE_PATTERN     = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
  private static final char[]                    CAP_DELIMS            = new char[] { ' ', '-', '_', '.', '\'', '(', '[', '*' };
  private static final String[]                  NON_CAP               = new String[] { "'S", "'M", "'Ll", "'T", "'D", "'Ve", "'Re" };

  static {
    REPLACEMENTS.put(0xc6, new Replacement("AE", "Ae"));
    REPLACEMENTS.put(0xe6, new Replacement("ae"));
    REPLACEMENTS.put(0xd0, new Replacement("D"));
    REPLACEMENTS.put(0x111, new Replacement("d"));
    REPLACEMENTS.put(0xd8, new Replacement("O"));
    REPLACEMENTS.put(0xf8, new Replacement("o"));
    REPLACEMENTS.put(0x152, new Replacement("OE", "Oe"));
    REPLACEMENTS.put(0x153, new Replacement("oe"));
    REPLACEMENTS.put(0x166, new Replacement("T"));
    REPLACEMENTS.put(0x167, new Replacement("t"));
    REPLACEMENTS.put(0x141, new Replacement("L"));
    REPLACEMENTS.put(0x142, new Replacement("l"));

    // various apostrophes https://www.compart.com/de/unicode/U+2019
    REPLACEMENTS.put(0x2018, new Replacement("'"));
    REPLACEMENTS.put(0x2019, new Replacement("'"));
    REPLACEMENTS.put(0x2032, new Replacement("'"));
    REPLACEMENTS.put(0x02BC, new Replacement("'"));
    REPLACEMENTS.put(0x05F3, new Replacement("'"));
    REPLACEMENTS.put(0xA78C, new Replacement("'"));

    REPLACEMENTS.put(0x201C, new Replacement("\""));
    REPLACEMENTS.put(0x201D, new Replacement("\""));
    REPLACEMENTS.put(0x02DD, new Replacement("\""));
    REPLACEMENTS.put(0x05F4, new Replacement("\""));
    REPLACEMENTS.put(0x2033, new Replacement("\"")); // interestingly, this results in 2x 0x2032?!?
    REPLACEMENTS.put(0x3003, new Replacement("\""));

    // invalid characters - the first one will be taken as replacement
    INVALID_CHARACTERS.put(0x0022, "″＂“”״ʺ˝ˮ〃"); // "
    INVALID_CHARACTERS.put(0x0027, "ˈ′’‘‛＇ʹʼ׳ꞌ"); // '
    INVALID_CHARACTERS.put(0x002A, "⚹⁎✲✱＊﹡٭※⁂⁑∗꙳\uD83D\uDFB6"); // *
    INVALID_CHARACTERS.put(0x003A, "∶：﹕ː˸։፡፥⁚⁝꞉︰"); // :
    INVALID_CHARACTERS.put(0x003C, "‹＜﹤〈⟨〈˂"); // <
    INVALID_CHARACTERS.put(0x003E, "›＞﹥〉⟩〉˃"); // >
    INVALID_CHARACTERS.put(0x003F, "❓？﹖︖¿؟‽⯑⸮�"); // ?
    SEPARATOR_CHARACTERS.put(0x002F, "⁄∕⟋⧸"); // /
    SEPARATOR_CHARACTERS.put(0x005C, "∖⟍⧹"); // \
  }

  private static String[] buildCommonTitlePrefixes() {
    // @formatter:off
    return new String[] { "A", "An", "The", // english
        "Der", "Die", "Das", "Ein", "Eine", "Eines", "Einer", "Einem", "Einen", // german
        "Le", "La", "Une", "Des", // french
        "El", "Los", "La", "Las", "Un", "Unos", "Una", "Unas" // spanish
    };
    // @formatter:on
  }

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private StrgUtils() {
    throw new IllegalAccessError();
  }

  /**
   * Converts a byte array to a hexadecimal string representation. Each byte is converted to two hexadecimal digits.
   *
   * @param bytes
   *          the byte array to convert
   * @return the hexadecimal string representation
   */
  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  /**
   * Converts a byte array to a lowercase hexadecimal string.
   *
   * @param data
   *          the byte array to encode
   * @return the hexadecimal string in lowercase
   */
  public static String encodeHex(byte[] data) {
    final int dataLength = data.length;
    final int outLength = dataLength << 1;

    final byte[] out = new byte[outLength];
    for (int i = 0, j = 0; i < dataLength; i++) {
      out[(j++)] = DIGITS_LOWER[((0xf0 & data[i]) >>> 4)];
      out[(j++)] = DIGITS_LOWER[(0x0f & data[i])];
    }
    return new String(out, 0, outLength, StandardCharsets.ISO_8859_1);
  }

  /**
   * Converts an integer to an 8-character lowercase hexadecimal string.
   *
   * @param data
   *          the integer to encode
   * @return the 8-character hexadecimal string
   */
  public static String encodeHex(int data) {
    byte[] out = new byte[8];

    out[0] = DIGITS_LOWER[(data >>> 28) & 0x0f];
    out[1] = DIGITS_LOWER[(data >>> 24) & 0x0f];
    out[2] = DIGITS_LOWER[(data >>> 20) & 0x0f];
    out[3] = DIGITS_LOWER[(data >>> 16) & 0x0f];
    out[4] = DIGITS_LOWER[(data >>> 12) & 0x0f];
    out[5] = DIGITS_LOWER[(data >>> 8) & 0x0f];
    out[6] = DIGITS_LOWER[(data >>> 4) & 0x0f];
    out[7] = DIGITS_LOWER[(data >>> 0) & 0x0f];

    return new String(out, 0, 8, StandardCharsets.ISO_8859_1);
  }

  /**
   * Converts a long value to a 16-character lowercase hexadecimal string.
   *
   * @param data
   *          the long value to encode
   * @return the 16-character hexadecimal string
   */
  public static String encodeHex(long data) {
    byte[] out = new byte[16];

    out[0] = DIGITS_LOWER[(int) ((data >>> 60) & 0x0f)];
    out[1] = DIGITS_LOWER[(int) ((data >>> 56) & 0x0f)];
    out[2] = DIGITS_LOWER[(int) ((data >>> 52) & 0x0f)];
    out[3] = DIGITS_LOWER[(int) ((data >>> 48) & 0x0f)];
    out[4] = DIGITS_LOWER[(int) ((data >>> 44) & 0x0f)];
    out[5] = DIGITS_LOWER[(int) ((data >>> 40) & 0x0f)];
    out[6] = DIGITS_LOWER[(int) ((data >>> 36) & 0x0f)];
    out[7] = DIGITS_LOWER[(int) ((data >>> 32) & 0x0f)];
    out[8] = DIGITS_LOWER[(int) ((data >>> 28) & 0x0f)];
    out[9] = DIGITS_LOWER[(int) ((data >>> 24) & 0x0f)];
    out[10] = DIGITS_LOWER[(int) ((data >>> 20) & 0x0f)];
    out[11] = DIGITS_LOWER[(int) ((data >>> 16) & 0x0f)];
    out[12] = DIGITS_LOWER[(int) ((data >>> 12) & 0x0f)];
    out[13] = DIGITS_LOWER[(int) ((data >>> 8) & 0x0f)];
    out[14] = DIGITS_LOWER[(int) ((data >>> 4) & 0x0f)];
    out[15] = DIGITS_LOWER[(int) ((data >>> 0) & 0x0f)];

    return new String(out, 0, 16, StandardCharsets.ISO_8859_1);
  }

  /**
   * Removes HTML tags from a string.
   *
   * @param html
   *          the string containing HTML
   * @return the string with all HTML tags removed, or null if input was null
   */
  public static String removeHtml(String html) {
    if (html == null) {
      return null;
    }
    return html.replaceAll("<[^>]+>", "");
  }

  /**
   * Removes surrounding double quotes from a string.
   *
   * @param str
   *          the string to process
   * @return the string without surrounding quotes, or null if input was null
   */
  public static String unquote(String str) {
    if (str == null) {
      return null;
    }
    return str.replaceFirst("^\\\"(.*)\\\"$", "$1");
  }

  /**
   * Creates a string representation of a Map in "key: value" format.
   *
   * @param map
   *          the map to convert to string
   * @return "null" if map is null, "empty" if map is empty, or key-value pairs as string
   */
  public static String mapToString(Map<?, ?> map) {
    if (map == null) {
      return "null";
    }
    if (map.isEmpty()) {
      return "empty";
    }

    StringBuilder sb = new StringBuilder();
    for (Map.Entry<?, ?> me : map.entrySet()) {
      sb.append(me.getKey()).append(": ").append(me.getValue()).append(",");
    }
    return sb.toString();
  }

  /**
   * Pads a numeric string with leading zeros to achieve specified length.
   *
   * @param encodeString
   *          the string to pad
   * @param padding
   *          the desired total length after padding
   * @return the padded string, or original string if not numeric
   */
  public static String zeroPad(String encodeString, int padding) {
    if (StringUtils.isBlank(encodeString)) {
      return encodeString;
    }

    try {
      int v = Integer.parseInt(encodeString);
      String format = "%0" + padding + "d";
      return String.format(format, v);
    }
    catch (Exception e) {
      return encodeString;
    }
  }

  /**
   * Extracts a substring matching a regular expression pattern with one capturing group.
   *
   * @param str
   *          the string to search in
   * @param pattern
   *          the regex pattern with one capturing group
   * @return the matched substring from the capturing group, or empty string if no match
   */
  public static String substr(String str, String pattern) {
    if (StringUtils.isBlank(str)) {
      return "";
    }

    Pattern regex = Pattern.compile(pattern);
    Matcher m = regex.matcher(str);
    if (m.find()) {
      return m.group(1);
    }
    else {
      return "";
    }
  }

  /**
   * Removes duplicate whitespace characters and replaces all whitespace with single spaces.
   *
   * @param s
   *          the string to process
   * @return the string with normalized whitespace, or empty string if input was blank
   */
  public static String removeDuplicateWhitespace(String s) {
    if (StringUtils.isBlank(s)) {
      return "";
    }

    StringBuilder result = new StringBuilder();
    int length = s.length();
    boolean isPreviousWhiteSpace = false;
    for (int i = 0; i < length; i++) {
      char c = s.charAt(i);
      boolean thisCharWhiteSpace = Character.isWhitespace(c);
      if (!(isPreviousWhiteSpace && thisCharWhiteSpace)) {
        result.append(c);
      }
      isPreviousWhiteSpace = thisCharWhiteSpace;
    }
    return result.toString();
  }

  /**
   * Replaces filesystem-forbidden characters with their Unicode equivalents.
   *
   * @param input
   *          the string to process
   * @return the string with forbidden characters replaced, or null if input was null
   */
  public static String replaceForbiddenFilesystemCharacters(String input) {
    if (input == null) {
      return null;
    }

    StringBuilder result = new StringBuilder();

    for (char c : input.toCharArray()) {
      // is this char in our map?
      String replacement = INVALID_CHARACTERS.get(Integer.valueOf(c));

      // yes -> append the replacement
      if (replacement != null) {
        result.append(replacement.charAt(0));
      }
      else {
        result.append(c);
      }
    }

    return result.toString();
  }

  /**
   * Replaces separator characters from the file system with their Unicode counterparts
   *
   * @param input
   *          the {@link String} to replace characters in
   * @return a _cleaned_ version of the {@link String}
   */
  public static String replaceFilesystemSeparatorCharacters(String input) {
    if (input == null) {
      return null;
    }

    StringBuilder result = new StringBuilder();

    for (char c : input.toCharArray()) {
      // is this char in our map?
      String replacement = SEPARATOR_CHARACTERS.get(Integer.valueOf(c));

      // yes -> append the replacement
      if (replacement != null) {
        result.append(replacement.charAt(0));
      }
      else {
        result.append(c);
      }
    }

    return result.toString();
  }

  /**
   * Converts Unicode characters back to their common ASCII equivalents.
   *
   * @param input
   *          the string to process
   * @return the string with Unicode characters converted, or null if input was null
   */
  public static String replaceUnicodeCharactersInverse(String input) {
    if (input == null) {
      return null;
    }

    StringBuilder result = new StringBuilder();

    for (char c : input.toCharArray()) {
      // System.out.println(c + " - " + encodeHex(c));

      String replacement = null;

      // is this char in our map?
      for (Entry<Integer, String> entry : INVALID_CHARACTERS.entrySet()) {
        if (entry.getValue().indexOf(c) >= 0) {
          replacement = String.valueOf((char) entry.getKey().intValue());
          break;
        }
      }

      if (replacement == null) {
        for (Entry<Integer, String> entry : SEPARATOR_CHARACTERS.entrySet()) {
          if (entry.getValue().indexOf(c) >= 0) {
            replacement = String.valueOf((char) entry.getKey().intValue());
            break;
          }
        }
      }

      // yes -> append the original one
      if (replacement != null) {
        result.append(replacement.charAt(0));
      }
      else {
        result.append(c);
      }

    }

    return result.toString();
  }

  /**
   * Converts special characters (umlauts, accented letters, etc.) to their ASCII equivalents.
   *
   * @param input
   *          the string to convert
   * @param replaceAllCapitalLetters
   *          if true, uses uppercase for all replacement characters
   * @return the ASCII-safe string, or null if input was null
   */
  public static String convertToAscii(String input, boolean replaceAllCapitalLetters) {
    String result = null;
    if (input != null) {
      String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
      // https://stackoverflow.com/questions/9376621/folding-normalizing-ligatures-e-g-%C3%86-to-ae-using-corefoundation

      int len = normalized.length();
      result = processSpecialChars(normalized.toCharArray(), 0, len, replaceAllCapitalLetters);
    }

    return result;
  }

  /**
   * Internal helper method to process special characters during ASCII conversion.
   *
   * @param target
   *          character array to process
   * @param offset
   *          starting position
   * @param len
   *          length to process
   * @param uppercase
   *          whether to use uppercase replacements
   * @return the processed string
   */
  private static String processSpecialChars(char[] target, int offset, int len, boolean uppercase) {
    StringBuilder result = new StringBuilder();
    boolean skip = false;

    for (int i = 0; i < len; i++) {
      if (skip) {
        skip = false;
      }
      else {
        char c = target[i];
        // System.out.println(c + " - " + encodeHex(c));
        // https://www.compart.com/de/unicode/block/U+0000
        // https://www.compart.com/de/unicode/block/U+0080
        // TODO: 2 IFs are kinda overlapping, and clarify why we need control chars?!
        if ((c > 0x20 && c < 0x40) || (c > 0x7a && c < 0xc0) || (c > 0x5a && c < 0x61) || c == 0xd7 || c == 0xf7) {
          result.append(c);
        }
        else if (Character.isDigit(c) || Character.isISOControl(c)) {
          result.append(c);
        }
        else if (Character.isWhitespace(c) || Character.isLetter(c)) {
          boolean isUpper = false;

          switch (c) {
            case '\u00df':
              result.append("ss");
              break;

            /* Handling of capital and lowercase umlauts */
            case 'A':
            case 'O':
            case 'U':
              isUpper = true;
            case 'a':
            case 'o':
            case 'u':
              result.append(c);
              if (i + 1 < target.length && target[i + 1] == 0x308) {
                result.append(isUpper && uppercase ? 'E' : 'e');
                skip = true;
              }
              break;

            default:
              Replacement rep = REPLACEMENTS.get(Integer.valueOf(c));
              if (rep != null) {
                result.append(uppercase ? rep.UPPER : rep.LOWER);
              }
              else {
                result.append(c);
              }
          }
        }
        else {
          // None of our expected blocks? check replacer and add if found
          Replacement rep = REPLACEMENTS.get(Integer.valueOf(c));
          if (rep != null) {
            result.append(uppercase ? rep.UPPER : rep.LOWER);
          }
        }
      }
    }
    return result.toString();
  }

  /**
   * Internal class for handling character replacements with case sensitivity. Stores uppercase and lowercase versions of replacement characters.
   */
  private static class Replacement {
    private final String UPPER;
    private final String LOWER;

    /**
     * Creates a replacement with different uppercase and lowercase versions.
     *
     * @param ucReplacement
     *          uppercase replacement
     * @param lcReplacement
     *          lowercase replacement
     */
    Replacement(String ucReplacement, String lcReplacement) {
      UPPER = ucReplacement;
      LOWER = lcReplacement;
    }

    /**
     * Creates a replacement with the same string for both cases.
     *
     * @param caseInsensitiveReplacement
     *          the replacement to use for both cases
     */
    Replacement(String caseInsensitiveReplacement) {
      this(caseInsensitiveReplacement, caseInsensitiveReplacement);
    }
  }

  /**
   * Converts a sortable title back to its natural form (e.g., "Bourne Legacy, The" to "The Bourne Legacy").
   *
   * @param title
   *          the sortable title
   * @return the natural title form, or empty string if input was null/empty
   */
  public static String removeCommonSortableName(String title) {
    if (title == null || title.isEmpty()) {
      return "";
    }
    for (String prfx : COMMON_TITLE_PREFIXES) {
      String delim = " "; // one spaces as delim
      if (prfx.matches(".*['`´]$")) { // ends with hand-picked delim, so no
                                      // space between prefix and title
        delim = "";
      }
      title = title.replaceAll("(?i)(.*), " + prfx, prfx + delim + "$1");
    }
    return title.strip();
  }

  /**
   * Compares two version strings, handling special cases for SNAPSHOT, SVN and GIT versions.
   *
   * @param v1
   *          first version string
   * @param v2
   *          second version string
   * @return negative if v1 < v2, positive if v1 > v2, zero if equal
   */
  public static int compareVersion(String v1, String v2) {
    if (v1.contains("-SNAPSHOT") && v1.equals(v2) || v1.equals("SVN") || v1.equals("GIT")) {
      // we have the same snapshot version - consider as potential lower (for nightly)
      // same for GIT - always "lower" to trigger update scripts!
      return -1;
    }
    String s1 = normalisedVersion(v1);
    String s2 = normalisedVersion(v2);
    return s1.compareTo(s2);
  }

  /**
   * Normalizes a version string using default separator and width.
   *
   * @param version
   *          the version string to normalize
   * @return the normalized version string
   */
  private static String normalisedVersion(String version) {
    return normalisedVersion(version, ".", 4);
  }

  /**
   * Normalizes a version string using specified separator and width.
   *
   * @param version
   *          the version string to normalize
   * @param sep
   *          the separator to use
   * @param maxWidth
   *          the maximum width for padding
   * @return the normalized version string
   */
  private static String normalisedVersion(String version, String sep, int maxWidth) {
    // SNAPSHOT should be considered as lower version
    // so just removing does not work
    // add micromicro version to other
    if (!version.contains("-SNAPSHOT")) {
      version += ".0.0.1";
    }
    else {
      version = version.replace("-SNAPSHOT", "");
    }

    String[] split = Pattern.compile(sep, Pattern.LITERAL).split(version);
    StringBuilder sb = new StringBuilder();
    for (String s : split) {
      sb.append(String.format("%" + maxWidth + 's', s));
    }
    return sb.toString();
  }

  /**
   * Finds the longest string in an array of strings.
   *
   * @param array
   *          the array of strings to search
   * @return the longest string, or null if array is empty
   */
  public static String getLongestString(String[] array) {
    int maxLength = 0;
    String longestString = null;
    for (String s : array) {
      if (s.length() > maxLength) {
        maxLength = s.length();
        longestString = s;
      }
    }
    return longestString;
  }

  /**
   * Returns a non-null string, converting null to empty string.
   *
   * @param originalString
   *          the string to check
   * @return the original string or empty string if null
   */
  public static String getNonNullString(String originalString) {
    if (originalString == null) {
      return "";
    }
    return originalString;
  }

  /**
   * Normalizes a string by removing diacritical marks and duplicate whitespace.
   *
   * @param original
   *          the string to normalize
   * @return the normalized string
   */
  public static String normalizeString(String original) {
    String nfdNormalizedString = Normalizer.normalize(original, Normalizer.Form.NFD);
    return removeDuplicateWhitespace(NORMALIZE_PATTERN.matcher(nfdNormalizedString).replaceAll(""));
  }

  /**
   * Converts a string in array notation to a string array.
   *
   * @param source
   *          the string in array notation (e.g., "[1,2,3,4,5]")
   * @return the converted string array
   */
  public static String[] convertStringToArray(String source) {
    if (StringUtils.isBlank(source)) {
      return new String[] {};
    }
    return source.replace("[", "").replace("]", "").replaceAll("\\s", "").split(",");
  }

  /**
   * Capitalizes text according to tMM's custom rules.
   *
   * @param text
   *          the text to capitalize
   * @return the capitalized string
   */
  public static String capitalize(String text) {
    String ret = WordUtils.capitalize(text, CAP_DELIMS);
    for (String n : NON_CAP) {
      ret = ret.replaceAll(n + "\s", n.toLowerCase(Locale.ROOT) + " "); // String needs to end or have a whitespace after!
      ret = ret.replaceAll(n + "$", n.toLowerCase(Locale.ROOT)); // but not at end!
    }
    return ret;
  }

  /**
   * Strips whitespace from a string, handling null input.
   *
   * @param source
   *          the string to strip
   * @return the stripped string, or empty string if input was null
   */
  public static String strip(String source) {
    return source == null ? "" : source.strip();
  }
}
