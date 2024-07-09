/*
 * Copyright 2012 - 2024 Manuel Laggner
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

package org.tinymediamanager.core.jmte;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.util.MetadataUtil;

import com.floreysoft.jmte.NamedRenderer;
import com.floreysoft.jmte.RenderFormatInfo;

/**
 * this renderer tries to comma-separate a String, and returns just one entry (param = index)<br>
 * <br>
 * <b>Usage:</b> ${token;split(2)}<br>
 * <b>Input:</b> a,b,c,d,e<br>
 * <b>Output:</b> c
 * 
 * @author Myron Boyle
 */
public class NamedSplitRenderer implements NamedRenderer {
  private static final Logger LOGGER = LoggerFactory.getLogger(NamedSplitRenderer.class);

  @Override
  public String render(Object o, String s, Locale locale, Map<String, Object> map) {
    if (o == null) {
      return "";
    }

    if (o instanceof String) {
      String[] split = ((String) o).split(",");

      if (split.length == 1) {
        LOGGER.debug("there was nothing to split: {}", o);
        return o.toString(); // unmodified
      }

      int idx = MetadataUtil.parseInt(s, -1);
      if (idx >= split.length) {
        LOGGER.debug("Wanted entry {} greater than what we have splitted: {}", idx, split);
        return o.toString();// unmodified
      }

      if (idx >= 0) {
        return split[idx].strip();
      }
    }

    LOGGER.debug("there was nothing to split for index: {} - {}", s, o);
    return o.toString();// unmodified
  }

  @Override
  public String getName() {
    return "split";
  }

  @Override
  public RenderFormatInfo getFormatInfo() {
    return null;
  }

  @Override
  public Class<?>[] getSupportedClasses() {
    return new Class[] { String.class };
  }
}
