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
package org.tinymediamanager.ui.movies.actions;

import java.awt.event.ActionEvent;

import org.tinymediamanager.core.movie.tasks.MovieUpdateDatasourceTask;
import org.tinymediamanager.core.threading.TmmTaskManager;
import org.tinymediamanager.core.threading.TmmThreadPool;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.actions.TmmAction;

/**
 * MovieUpdateSingleDatasourceAction - update all movies from a single data source
 * 
 * @author Manuel Laggner
 */
public class MovieUpdateSingleDatasourceAction extends TmmAction {
  private final String datasource;

  public MovieUpdateSingleDatasourceAction(String datasource) {
    this.datasource = datasource;

    // 解码 WebDAV 路径用于显示
    String displayName = decodeWebDavPath(datasource);
    putValue(NAME, displayName);
    putValue(SMALL_ICON, IconManager.REFRESH);
    putValue(LARGE_ICON_KEY, IconManager.REFRESH);
  }

  /**
   * 解码 WebDAV 路径中的 URL 编码字符用于显示
   */
  private String decodeWebDavPath(String path) {
    if (path != null && path.startsWith("webdav://")) {
      try {
        // 找到 webdav://[id]/ 之后的路径部分进行解码
        int firstSlash = path.indexOf('/', 9); // 9 = length of "webdav://"
        if (firstSlash != -1) {
          String prefix = path.substring(0, firstSlash + 1);
          String remotePath = path.substring(firstSlash + 1);
          String decodedPath = java.net.URLDecoder.decode(remotePath, "UTF-8");
          return prefix + decodedPath;
        }
      }
      catch (Exception e) {
        // 解码失败，返回原始值
      }
    }
    return path;
  }

  @Override
  protected void processAction(ActionEvent e) {
    TmmThreadPool task = new MovieUpdateDatasourceTask(datasource);
    TmmTaskManager.getInstance().addMainTask(task);
  }
}
