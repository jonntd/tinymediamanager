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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TmmUILayoutStore;

import net.miginfocom.swing.MigLayout;

/**
 * The class {@link TmmOptionDialog} is a replacement for the {@link JOptionPane} with more layout possibilities
 * 
 * @author Manuel Laggner
 */
public class TmmOptionDialog extends JDialog {

  private int result = JOptionPane.NO_OPTION;

  private TmmOptionDialog(Frame owner, Icon icon, String title, String message, JCheckBox chkbxOption) {
    super(owner, title, true);
    setName("message");
    setLayout(new BorderLayout());

    JPanel panelContent = new JPanel(new MigLayout("hidemode 3", "10lp[50lp]10lp[500lp, grow]10lp", "10lp[10lp, center][10lp, center]"));
    add(panelContent, BorderLayout.CENTER);

    if (icon != null) {
      panelContent.add(new JLabel(icon), "cell 0 0 1 2, center, grow");
    }
    else {
      panelContent.add(new JLabel(IconManager.QUESTION_BIG), "cell 0 0 1 2, center, grow");
    }

    String[] lines = message.split("\n");
    for (String line : lines) {
      JLabel lblMessage = new JLabel(line);
      panelContent.add(lblMessage, "cell 1 0, aligny center, flowy");
    }

    if (chkbxOption != null) {
      panelContent.add(chkbxOption, "cell 1 1, aligny center, grow, wmin 0");
    }

    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

    JButton btnOk = new JButton(TmmResourceBundle.getString("Button.yes"));
    btnOk.addActionListener(e -> {
      result = JOptionPane.OK_OPTION;
      setVisible(false);
    });

    JButton btnCancel = new JButton(TmmResourceBundle.getString("Button.no"));
    btnCancel.addActionListener(e -> {
      result = JOptionPane.NO_OPTION;
      setVisible(false);
    });

    buttonPanel.add(btnOk);
    buttonPanel.add(btnCancel);

    add(buttonPanel, BorderLayout.SOUTH);
    getRootPane().setDefaultButton(btnOk);

    setLocationRelativeTo(owner);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent e) {
        btnOk.requestFocusInWindow();
      }
    });
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible) {
      pack();
      TmmUILayoutStore.getInstance().loadSettings(this);
      super.setVisible(true);
      toFront();
    }
    else {
      TmmUILayoutStore.getInstance().saveSettings(this);
      super.setVisible(false);
      dispose();
    }
  }

  /**
   * Display an option dialog with the given owner (for placement), icon, title, message and a checkbox for options
   * 
   * @param owner
   *          the owner {@link Frame}
   * @param icon
   *          the {@link Icon} to be displayed on the left
   * @param title
   *          the title
   * @param message
   *          the message
   * @param chkbxOption
   *          a {@link JCheckBox} for an optional option
   * @return the result of the dialog - JOptionPane.YES_OPTION when clicked OK, JOptionPane.NO_OPTION otherwise
   */
  public static int showOptionDialog(Frame owner, Icon icon, String title, String message, JCheckBox chkbxOption) {
    TmmOptionDialog dialog = new TmmOptionDialog(owner, icon, title, message, chkbxOption);
    dialog.setVisible(true);
    return dialog.result;
  }

  /**
   * Display an option dialog with the given owner (for placement), icon, title and message
   * 
   * @param owner
   *          the owner {@link Frame}
   * @param icon
   *          the {@link Icon} to be displayed on the left
   * @param title
   *          the title
   * @param message
   *          the message
   * @return the result of the dialog - JOptionPane.YES_OPTION when clicked OK, JOptionPane.NO_OPTION otherwise
   */
  public static int showOptionDialog(Frame owner, Icon icon, String title, String message) {
    return showOptionDialog(owner, icon, title, message, null);
  }

  /**
   * Display an option dialog with the given owner (for placement), title and message
   * 
   * @param owner
   *          the owner {@link Frame}
   * @param title
   *          the title
   * @param message
   *          the message
   * @return the result of the dialog - JOptionPane.YES_OPTION when clicked OK, JOptionPane.NO_OPTION otherwise
   */
  public static int showOptionDialog(Frame owner, String title, String message) {
    return showOptionDialog(owner, null, title, message, null);
  }

  /**
   * Display an option dialog with the given owner (for placement), title, message and a checkbox for options
   * 
   * @param owner
   *          the owner {@link Frame}
   * @param title
   *          the title
   * @param message
   *          the message
   * @param chkbxOption
   *          a {@link JCheckBox} for an optional option
   * @return the result of the dialog - JOptionPane.YES_OPTION when clicked OK, JOptionPane.NO_OPTION otherwise
   */
  public static int showOptionDialog(Frame owner, String title, String message, JCheckBox chkbxOption) {
    return showOptionDialog(owner, null, title, message, chkbxOption);
  }
}
