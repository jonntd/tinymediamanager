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

package org.tinymediamanager.ui.renderer;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.TableCellRenderer;

/**
 * the class {@link MultilineTextareaCellRenderer} is used to render table cells with multiple lines; ATTENTION multiple lines will always be rendered
 * on top and not centered
 * 
 * @author Manuel Laggner
 */
public class MultilineTextareaCellRenderer extends JTextArea implements TableCellRenderer {
  public MultilineTextareaCellRenderer() {
    setLineWrap(false);// do not wrap long lines on our own; but newLines with \n ARE wrapped
    setOpaque(true);
    setAlignmentY(CENTER_ALIGNMENT);
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    setText(value != null ? value.toString() : "");
    setFont(table.getFont());

    if (isSelected) {
      setBackground(table.getSelectionBackground());
      setForeground(table.getSelectionForeground());
    }
    else {
      setBackground(table.getBackground());
      setForeground(table.getForeground());
    }

    return this;
  }
}
