/*
 * Copyright 2012 - 2020 Manuel Laggner
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
package org.tinymediamanager.ui.components.textfield;

import java.awt.KeyboardFocusManager;

import javax.swing.JTextArea;

/**
 * The class {@link TmmTextArea} is an enhancement to the {@link JTextArea} which provides some enhancements like a better focus traversal and an undo manager
 *
 * @author Manuel Laggner
 */
public class TmmTextArea extends JTextArea {

  public TmmTextArea() {
    super();
    setLineWrap(true);
    setWrapStyleWord(true);

    setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
    setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
  }
}
