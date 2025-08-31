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

package org.tinymediamanager.core.mediainfo;

import org.tinymediamanager.core.Settings;

/**
 * common helpers for Mediainfo
 *
 * @author Manuel Laggner
 */
public class MediaInfoUtils {
  private static final boolean USE_LIBMEDIAINFO = Boolean.parseBoolean(System.getProperty("tmm.uselibmediainfo", "true"));

  private MediaInfoUtils() {
    throw new IllegalAccessError();
  }

  /**
   * checks if we should use libMediaInfo
   *
   * @return true/false
   */
  public static boolean useMediaInfo() {
    // 首先检查系统属性设置（命令行参数优先级最高）
    if (!USE_LIBMEDIAINFO) {
      return false;
    }

    // 然后检查 GUI 设置
    try {
      if (!Settings.getInstance().isEnableMediaInfo()) {
        return false;
      }
    }
    catch (Exception e) {
      // 如果设置加载失败，使用默认值 true
    }

    return true;
  }
}
