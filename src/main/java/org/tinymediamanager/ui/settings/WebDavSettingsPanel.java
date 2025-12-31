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
package org.tinymediamanager.ui.settings;

import static org.tinymediamanager.ui.TmmFontHelper.H3;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.swingbinding.JTableBinding;
import org.jdesktop.swingbinding.SwingBindings;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.webdav.WebDavSource;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.table.TmmTable;
import org.tinymediamanager.ui.panels.IModalPopupPanelProvider;
import org.tinymediamanager.ui.panels.ModalPopupPanel;
import org.tinymediamanager.ui.panels.WebDavSourcePanel;

import net.miginfocom.swing.MigLayout;

/**
 * The {@link WebDavSettingsPanel} - a panel to configure WebDAV sources
 * 
 * @author Manuel Laggner
 */
public class WebDavSettingsPanel extends JPanel {
  private final Settings settings = Settings.getInstance();

  private TmmTable       tableWebDavSources;
  private JButton        btnAddWebDavSource;
  private JButton        btnEditWebDavSource;
  private JButton        btnRemoveWebDavSource;

  public WebDavSettingsPanel() {
    initComponents();
    initDataBindings();

    // Add button listener
    btnAddWebDavSource.addActionListener(e -> {
      IModalPopupPanelProvider provider = IModalPopupPanelProvider.findModalProvider(this);
      if (provider == null) {
        return;
      }

      ModalPopupPanel popupPanel = provider.createModalPopupPanel();
      popupPanel.setTitle(TmmResourceBundle.getString("webdav.source.add"));

      WebDavSource webDavSource = new WebDavSource();
      WebDavSourcePanel sourcePanel = new WebDavSourcePanel(webDavSource);

      popupPanel.setOnCloseHandler(() -> settings.addWebDavSource(webDavSource));

      popupPanel.setContent(sourcePanel);
      provider.showModalPopupPanel(popupPanel);
    });

    // Remove button listener
    btnRemoveWebDavSource.addActionListener(e -> {
      int[] indexRows = TmmUIHelper.getSelectedRowsAsModelRows(tableWebDavSources);

      for (int indexRow : indexRows) {
        try {
          WebDavSource source = settings.getWebDavSources().get(indexRow);
          settings.removeWebDavSource(source);
        }
        catch (Exception ex) {
          // do nothing
        }
      }
    });

    // Edit button listener
    btnEditWebDavSource.addActionListener(e -> {
      int row = tableWebDavSources.getSelectedRow();
      row = tableWebDavSources.convertRowIndexToModel(row);
      if (row != -1) {
        WebDavSource source = settings.getWebDavSources().get(row);
        if (source != null) {
          IModalPopupPanelProvider provider = IModalPopupPanelProvider.findModalProvider(this);
          if (provider == null) {
            return;
          }

          ModalPopupPanel popupPanel = provider.createModalPopupPanel();
          popupPanel.setTitle(TmmResourceBundle.getString("webdav.source.edit"));

          WebDavSource editSource = new WebDavSource(source);
          WebDavSourcePanel sourcePanel = new WebDavSourcePanel(editSource);

          popupPanel.setOnCloseHandler(() -> {
            org.slf4j.LoggerFactory.getLogger(WebDavSettingsPanel.class).info("!!! WebDavSettingsPanel CloseHandler TRIGGERED !!!");
            source.setName(editSource.getName());
            source.setUrl(editSource.getUrl());
            source.setUsername(editSource.getUsername());
            source.setPassword(editSource.getPassword());
            org.slf4j.LoggerFactory.getLogger(WebDavSettingsPanel.class).info("!!! calling forceSaveSettings !!!");
            settings.forceSaveSettings();
          });

          popupPanel.setContent(sourcePanel);
          provider.showModalPopupPanel(popupPanel);
        }
      }
    });

    // Set column titles
    tableWebDavSources.getColumnModel().getColumn(0).setHeaderValue(TmmResourceBundle.getString("webdav.name"));
    tableWebDavSources.getColumnModel().getColumn(1).setHeaderValue(TmmResourceBundle.getString("webdav.url"));
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[600lp,grow]", "[]"));
    {
      JPanel panelWebDav = new JPanel(new MigLayout("hidemode 1, insets 0", "[20lp!][400lp][]", "[150lp]"));

      JLabel lblWebDavT = new TmmLabel(TmmResourceBundle.getString("webdav.sources"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelWebDav, lblWebDavT, true);
      add(collapsiblePanel, "growx, wmin 0");
      {
        JScrollPane spWebDavSources = new JScrollPane();
        panelWebDav.add(spWebDavSources, "cell 1 0, grow");

        tableWebDavSources = new TmmTable();
        spWebDavSources.setViewportView(tableWebDavSources);

        btnAddWebDavSource = new JButton(TmmResourceBundle.getString("Button.add"));
        panelWebDav.add(btnAddWebDavSource, "flowy, cell 2 0, growx, aligny top");

        btnEditWebDavSource = new JButton(TmmResourceBundle.getString("Button.edit"));
        panelWebDav.add(btnEditWebDavSource, "cell 2 0, growx");

        btnRemoveWebDavSource = new JButton(TmmResourceBundle.getString("Button.remove"));
        panelWebDav.add(btnRemoveWebDavSource, "cell 2 0, growx");
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void initDataBindings() {
    BeanProperty<Settings, java.util.List<WebDavSource>> settingsBeanProperty = BeanProperty.create("webDavSources");
    JTableBinding<WebDavSource, Settings, JTable> jTableBinding = SwingBindings.createJTableBinding(UpdateStrategy.READ_WRITE, settings,
        settingsBeanProperty, tableWebDavSources);

    BeanProperty<WebDavSource, String> nameBeanProperty = BeanProperty.create("name");
    jTableBinding.addColumnBinding(nameBeanProperty);

    BeanProperty<WebDavSource, String> urlBeanProperty = BeanProperty.create("url");
    jTableBinding.addColumnBinding(urlBeanProperty);

    jTableBinding.setEditable(false);
    jTableBinding.bind();
  }
}
