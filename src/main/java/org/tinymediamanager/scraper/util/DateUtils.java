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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * The class {@link DateUtils} contains some helpers for {@link java.util.Date} objects
 *
 * @author Manuel Laggner
 */
public class DateUtils {
  private static final Map<String, String> DATE_FORMAT_REGEXPS = new HashMap<>(30);

  static {
    DATE_FORMAT_REGEXPS.put("^\\d{8}$", "yyyyMMdd");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}-\\d{1,2}-\\d{4}$", "dd-MM-yyyy");
    DATE_FORMAT_REGEXPS.put("^\\d{4}-\\d{1,2}-\\d{1,2}$", "yyyy-MM-dd");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}/\\d{1,2}/\\d{4}$", "MM/dd/yyyy");
    DATE_FORMAT_REGEXPS.put("^\\d{4}/\\d{1,2}/\\d{1,2}$", "yyyy/MM/dd");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\.\\d{1,2}\\.\\d{4}$", "dd.MM.yyyy");
    DATE_FORMAT_REGEXPS.put("^\\d{4}\\.\\d{1,2}\\.\\d{1,2}$", "yyyy.MM.dd");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\s[a-z]{3}\\s\\d{4}$", "dd MMM yyyy");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\s[a-z]{3}\\.\\s\\d{4}$", "dd MMM. yyyy");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\s[a-z]{4,}\\s\\d{4}$", "dd MMMM yyyy");
    DATE_FORMAT_REGEXPS.put("^[a-z]{3,}\\s\\d{1,2},\\s\\d{4}$", "MMMMM dd, yyyy"); // IMDB
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\.\\s[a-z]{3,}\\s\\d{4}$", "dd. MMMMM yyyy"); // IMDB
    DATE_FORMAT_REGEXPS.put("^\\d{12}$", "yyyyMMddHHmm");
    DATE_FORMAT_REGEXPS.put("^\\d{8}\\s\\d{4}$", "yyyyMMdd HHmm");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}-\\d{1,2}-\\d{4}\\s\\d{1,2}:\\d{2}$", "dd-MM-yyyy HH:mm");
    DATE_FORMAT_REGEXPS.put("^\\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}$", "yyyy-MM-dd HH:mm");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2}$", "MM/dd/yyyy HH:mm");
    DATE_FORMAT_REGEXPS.put("^\\d{4}/\\d{1,2}/\\d{1,2}\\s\\d{1,2}:\\d{2}$", "yyyy/MM/dd HH:mm");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\s[a-z]{3}\\s\\d{4}\\s\\d{1,2}:\\d{2}$", "dd MMM yyyy HH:mm");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\s[a-z]{4,}\\s\\d{4}\\s\\d{1,2}:\\d{2}$", "dd MMMM yyyy HH:mm");
    DATE_FORMAT_REGEXPS.put("^\\d{14}$", "yyyyMMddHHmmss");
    DATE_FORMAT_REGEXPS.put("^\\d{8}\\s\\d{6}$", "yyyyMMdd HHmmss");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}-\\d{1,2}-\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$", "dd-MM-yyyy HH:mm:ss");
    DATE_FORMAT_REGEXPS.put("^\\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}$", "yyyy-MM-dd HH:mm:ss");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$", "MM/dd/yyyy HH:mm:ss");
    DATE_FORMAT_REGEXPS.put("^\\d{4}/\\d{1,2}/\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}$", "yyyy/MM/dd HH:mm:ss");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}-\\d{1,2}-\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}\\.\\d{1,3}$", "dd-MM-yyyy HH:mm:ss.S");
    DATE_FORMAT_REGEXPS.put("^\\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}\\.\\d{1,3}$", "yyyy-MM-dd HH:mm:ss.S");
    DATE_FORMAT_REGEXPS.put("(?i)^\\d{4}-\\d{1,2}-\\d{1,2}T\\s\\d{1,2}:\\d{2}:\\d{2}\\.\\d+$", "yyyy-MM-dd'T' HH:mm:ss.S"); // nextpvr
    DATE_FORMAT_REGEXPS.put("(?i)^\\d{4}-\\d{1,2}-\\d{1,2}T\\d{1,2}:\\d{2}:\\d{2}\\.\\d+$", "yyyy-MM-dd'T'HH:mm:ss.S"); // nextpvr
    DATE_FORMAT_REGEXPS.put("(?i)^\\d{4}-\\d{1,2}-\\d{1,2}T\\d{1,2}:\\d{2}:\\d{2}Z$", "yyyy-MM-dd'T'HH:mm:ss'Z'"); // parsed trailer
    DATE_FORMAT_REGEXPS.put("(?i)^\\d{4}-\\d{1,2}-\\d{1,2}T\\d{1,2}:\\d{2}:\\d{2}\\.\\d+Z$", "yyyy-MM-dd'T'HH:mm:ss.S'Z'"); // parsed trailer
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}\\.\\d{1,3}$", "MM/dd/yyyy HH:mm:ss.S");
    DATE_FORMAT_REGEXPS.put("^\\d{4}/\\d{1,2}/\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}\\.\\d{1,3}$", "yyyy/MM/dd HH:mm:ss.S");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\s[a-z]{3}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$", "dd MMM yyyy HH:mm:ss");
    DATE_FORMAT_REGEXPS.put("^\\d{1,2}\\s[a-z]{4,}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$", "dd MMMM yyyy HH:mm:ss");
    DATE_FORMAT_REGEXPS.put("^\\w{3} \\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}$", "z yyyy-MM-dd HH:mm:ss"); // MediaInfo
    DATE_FORMAT_REGEXPS.put("^\\w{3} \\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}\\.\\d{1,3}$", "z yyyy-MM-dd HH:mm:ss.S"); // MediaInfo
  }

  private DateUtils() {
    throw new IllegalAccessError();
  }

  /**
   * Gets the higher {@link Date} of the given two {@link Date} objects. Nullsafe!
   * 
   * @param date1
   *          the first {@link Date} object
   * @param date2
   *          the second {@link Date} object
   * @return the higher {@link Date} object or null if both are null
   */
  public static Date getHigherDate(Date date1, Date date2) {
    // If both dates are null, return null
    if (date1 == null && date2 == null) {
      return null;
    }

    // If one date is null, return the non-null one
    if (date1 == null) {
      return date2;
    }

    if (date2 == null) {
      return date1;
    }

    // Both dates are non-null, return the latest one
    return (date1.after(date2)) ? date1 : date2;
  }

  /**
   * Determine SimpleDateFormat pattern matching with the given date string. Returns null if format is unknown. You can simply extend DateUtil with
   * more formats if needed.<br>
   * https://stackoverflow.com/a/3390252
   * 
   * @param dateString
   *          The date string to determine the SimpleDateFormat pattern for.
   * @return The matching SimpleDateFormat pattern, or null if format is unknown.
   * @see SimpleDateFormat
   */
  public static String determineDateFormat(String dateString) {
    if (StringUtils.isBlank(dateString)) {
      return null;
    }

    for (String regexp : DATE_FORMAT_REGEXPS.keySet()) {
      if (dateString.toLowerCase(Locale.ROOT).matches(regexp)) {
        return DATE_FORMAT_REGEXPS.get(regexp);
      }
    }
    return null; // Unknown format.
  }

  /**
   * Parses the date.
   * 
   * @param dateAsString
   *          the date as string
   * @return the date
   * @throws ParseException
   *           the parse exception
   */
  public static Date parseDate(String dateAsString) throws ParseException {
    if (StringUtils.isBlank(dateAsString)) {
      return null;
    }

    Date date = null;
    String format = determineDateFormat(dateAsString);
    try {
      // try localized
      date = new SimpleDateFormat(format).parse(dateAsString);
    }
    catch (Exception e) {
      // try US style
      try {
        date = new SimpleDateFormat(format, Locale.US).parse(dateAsString);
      }
      catch (Exception e2) {
        // FALLBACK:
        // German and Canadian have month names with dot added!
        // see https://stackoverflow.com/q/69860992
        // so we try to remove them, to normalize the string again for parsing...

        for (String mon : LanguageUtils.MONTH_REGIONAL_TO_NUM.keySet()) {
          if (dateAsString.matches(".*\\W" + mon + "\\W.*")) { // non-word must be around!
            // we have a match to replace!
            dateAsString = dateAsString.replaceAll(mon, String.valueOf(LanguageUtils.MONTH_REGIONAL_TO_NUM.get(mon)));
            format = determineDateFormat(dateAsString); // do we now get a known format?
            if (format == null) {
              dateAsString = dateAsString.replaceAll(" ", "."); // add delimiters
              dateAsString = dateAsString.replaceAll("\\.+", "."); // remove dupes
              format = determineDateFormat(dateAsString); // do we now get a known format?
            }
            if (format != null) {
              date = new SimpleDateFormat(format).parse(dateAsString);
            }
            return date; // we found a match, lets return this (in)valid date...
          }
        }
      }
    }

    if (date == null) {
      throw new ParseException("could not parse date from: \"" + dateAsString + "\"", 0);
    }

    return date;
  }
}
