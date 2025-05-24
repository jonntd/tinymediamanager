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

package org.tinymediamanager.core.jmte;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import com.floreysoft.jmte.Renderer;

public class PathRenderer implements Renderer<Path> {
  @Override
  public String render(Path o, Locale locale, Map<String, Object> model) {
    if (o != null) {
      return o.toString();
    }
    return "";
  }
}
