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
package org.tinymediamanager.ui.dialogs;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.ui.TmmUILogAppender;
import org.tinymediamanager.ui.TmmUILogCollector;
import org.tinymediamanager.ui.components.NoBorderScrollPane;

import net.miginfocom.swing.MigLayout;

public class ActivityLogDialog extends TmmDialog {
  private final TmmUILogAppender.TmmLogTextPane tpLogs;

  public ActivityLogDialog() {
    super(TmmResourceBundle.getString("logwindow.title"), "log");
    setBounds(5, 5, 1000, 590);
    setModalityType(ModalityType.MODELESS);

    JPanel panelContent = new JPanel();
    getContentPane().add(panelContent, BorderLayout.CENTER);
    panelContent.setLayout(new MigLayout("", "[500lp:600lp:n,grow]", "[400lp:500lp:n,grow]"));

    JScrollPane scrollPane = new NoBorderScrollPane();
    panelContent.add(scrollPane, "cell 0 0,grow");

    tpLogs = new TmmUILogAppender.TmmLogTextPane();
    scrollPane.setViewportView(tpLogs);
    tpLogs.setEditable(false);
    // tpLogs.setWrapStyleWord(true);
    // tpLogs.setLineWrap(true);

    {
      JButton btnClose = new JButton(TmmResourceBundle.getString("Button.close"));
      btnClose.addActionListener(arg0 -> setVisible(false));
      addDefaultButton(btnClose);
    }
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible) {
      TmmUILogCollector.getInstance().getUiLogAppender().setTextPane(tpLogs);
    }
    else {
      TmmUILogCollector.getInstance().getUiLogAppender().setTextPane(null);
    }
    super.setVisible(visible);
  }
}
