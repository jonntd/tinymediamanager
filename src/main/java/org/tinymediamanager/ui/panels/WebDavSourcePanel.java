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
package org.tinymediamanager.ui.panels;

import java.awt.Cursor;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.webdav.WebDavClient;
import org.tinymediamanager.core.webdav.WebDavSource;

import net.miginfocom.swing.MigLayout;

/**
 * The class {@link WebDavSourcePanel} is used to add/edit WebDAV source configurations
 * 
 * @author Manuel Laggner
 */
public class WebDavSourcePanel extends AbstractModalInputPanel {
  private final WebDavSource   source;

  private final JTextField     tfName;
  private final JTextField     tfUrl;
  private final JTextField     tfUsername;
  private final JPasswordField tfPassword;
  private final JButton        btnTestConnection;
  private final JLabel         lblStatus;

  public WebDavSourcePanel(WebDavSource source) {
    super();
    this.source = source;

    setLayout(new MigLayout("", "[][grow][]", "[][][][][]"));

    // Name
    JLabel lblName = new JLabel(TmmResourceBundle.getString("webdav.name"));
    add(lblName, "cell 0 0, alignx right");

    tfName = new JTextField();
    tfName.setColumns(30);
    add(tfName, "cell 1 0 2 1, growx");

    // URL
    JLabel lblUrl = new JLabel(TmmResourceBundle.getString("webdav.url"));
    add(lblUrl, "cell 0 1, alignx right");

    tfUrl = new JTextField();
    tfUrl.setColumns(30);
    add(tfUrl, "cell 1 1 2 1, growx");

    // Username
    JLabel lblUsername = new JLabel(TmmResourceBundle.getString("webdav.username"));
    add(lblUsername, "cell 0 2, alignx right");

    tfUsername = new JTextField();
    tfUsername.setColumns(20);
    add(tfUsername, "cell 1 2 2 1, growx");

    // Password
    JLabel lblPassword = new JLabel(TmmResourceBundle.getString("webdav.password"));
    add(lblPassword, "cell 0 3, alignx right");

    tfPassword = new JPasswordField();
    tfPassword.setColumns(20);
    add(tfPassword, "cell 1 3 2 1, growx");

    // Test Connection button
    btnTestConnection = new JButton(TmmResourceBundle.getString("webdav.testconnection"));
    btnTestConnection.addActionListener(e -> testConnection());
    add(btnTestConnection, "cell 1 4");

    // Status label
    lblStatus = new JLabel("");
    add(lblStatus, "cell 2 4");

    // Initialize fields with existing values
    tfName.setText(source.getName());
    tfUrl.setText(source.getUrl());
    tfUsername.setText(source.getUsername());
    tfPassword.setText(source.getPassword());
  }

  private void testConnection() {
    if (StringUtils.isBlank(tfUrl.getText())) {
      JOptionPane.showMessageDialog(this, TmmResourceBundle.getString("webdav.error.urlrequired"));
      return;
    }

    btnTestConnection.setEnabled(false);
    lblStatus.setText(TmmResourceBundle.getString("webdav.testing"));
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    // Create a temporary source for testing
    WebDavSource testSource = new WebDavSource();
    testSource.setUrl(tfUrl.getText().trim());
    testSource.setUsername(tfUsername.getText().trim());
    testSource.setPassword(new String(tfPassword.getPassword()));

    SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
      @Override
      protected Boolean doInBackground() {
        WebDavClient client = new WebDavClient(testSource);
        return client.testConnection();
      }

      @Override
      protected void done() {
        try {
          boolean success = get();
          if (success) {
            lblStatus.setText(TmmResourceBundle.getString("webdav.connection.success"));
          }
          else {
            lblStatus.setText(TmmResourceBundle.getString("webdav.connection.failed"));
          }
        }
        catch (Exception e) {
          lblStatus.setText(TmmResourceBundle.getString("webdav.connection.failed"));
        }
        finally {
          btnTestConnection.setEnabled(true);
          setCursor(Cursor.getDefaultCursor());
        }
      }
    };
    worker.execute();
  }

  @Override
  protected void onClose() {
    // Validate required fields
    if (StringUtils.isBlank(tfUrl.getText())) {
      JOptionPane.showMessageDialog(this, TmmResourceBundle.getString("webdav.error.urlrequired"));
      return;
    }

    // Update the source object
    source.setName(tfName.getText().trim());
    source.setUrl(tfUrl.getText().trim());
    source.setUsername(tfUsername.getText().trim());
    source.setPassword(new String(tfPassword.getPassword()));

    setVisible(false);
  }
}

