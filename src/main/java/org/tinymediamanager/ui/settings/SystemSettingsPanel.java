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
import static org.tinymediamanager.ui.TmmFontHelper.L2;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.apache.commons.lang3.SystemUtils;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.LauncherExtraConfig;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.core.movie.services.ChatGPTMovieRecognitionService;
import org.tinymediamanager.core.services.AIApiRateLimiter;


import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextArea;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextPane;

import net.miginfocom.swing.MigLayout;

/**
 * The Class MiscSettingsPanel.
 * 
 * @author Manuel Laggner
 */
class SystemSettingsPanel extends JPanel {
  private static final Logger  LOGGER           = LoggerFactory.getLogger(SystemSettingsPanel.class);

  private Timer                aiStatisticsTimer;
  private static final Pattern MEMORY_PATTERN   = Pattern.compile("-Xmx([0-9]*)(.)");
  private static final Pattern DIRECT3D_PATTERN = Pattern.compile("-Dsun.java2d.d3d=(true|false)");

  private final Settings       settings         = Settings.getInstance();

  private JTextField           tfProxyHost;
  private JTextField           tfProxyPort;
  private JTextField           tfProxyUsername;
  private JPasswordField       tfProxyPassword;

  private JSlider              sliderMemory;
  private JLabel               lblMemory;
  private JCheckBox            chckbxIgnoreSSLProblems;
  private JCheckBox            chckbxDisableD3d;
  private JSpinner             spMaximumDownloadThreads;
  private JTextField           tfHttpPort;
  private JTextField           tfHttpApiKey;
  private JCheckBox            chkbxEnableHttpServer;

  // OpenAI API settings
  private JTextField           tfOpenAiApiKey;
  private JTextField           tfOpenAiApiUrl;
  private JTextField           tfOpenAiModel;
  private JTextArea           taOpenAiExtractionPrompt;
  private JButton             btnTestOpenAiPrompt;
  private JTextField           tfOpenAiTestPath;

  // AI Rate Limiting settings
  private JCheckBox           chkAiRateLimitEnabled;
  private JSpinner            spAiMaxCallsPerMinute;
  private JSpinner            spAiMaxCallsPerHour;
  private JSpinner            spAiMinIntervalSeconds;
  private JCheckBox           chkAiIndividualFallbackEnabled;
  private JLabel              lblAiStatistics;
  private JButton             btnResetAiStatistics;

  /**
   * Instantiates a new general settings panel.
   */
  SystemSettingsPanel() {

    initComponents();

    initDataBindings();

    initMemorySlider();
    initDirect3d();

    // add a listener to write the actual memory state/direct3d setting to launcher-extra.yml
    addHierarchyListener(new HierarchyListener() {
      private boolean oldState = false;

      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if (oldState != isShowing()) {
          oldState = isShowing();
          if (!isShowing()) {
            writeLauncherExtraYml();
          }
        }
      }
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[600lp,grow]", "[][15lp!][][15lp!][][15lp!][][15lp!]"));
    {
      JPanel panelMemory = new JPanel(new MigLayout("hidemode 1, insets 0", "[20lp!][][300lp][grow]", ""));

      JLabel lblMemoryT = new TmmLabel(TmmResourceBundle.getString("Settings.memoryborder"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelMemory, lblMemoryT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/settings#memory-settings"));
      add(collapsiblePanel, "cell 0 0,growx,wmin 0");
      {
        lblMemoryT = new JLabel(TmmResourceBundle.getString("Settings.memory"));
        panelMemory.add(lblMemoryT, "cell 1 0,aligny top");

        sliderMemory = new JSlider();
        sliderMemory.setSnapToTicks(true);

        Dictionary<Integer, JLabel> labelTable = new Hashtable<>();
        labelTable.put(256, new JLabel("256"));
        labelTable.put(2048, new JLabel("2048"));
        labelTable.put(4096, new JLabel("4096"));
        labelTable.put(6144, new JLabel("6144"));
        labelTable.put(8192, new JLabel("8192"));
        sliderMemory.setLabelTable(labelTable);

        sliderMemory.setPaintTicks(true);
        sliderMemory.setPaintLabels(true);
        sliderMemory.setMinorTickSpacing(256);
        sliderMemory.setMinimum(256);
        sliderMemory.setMaximum(8192);
        sliderMemory.setValue(512);
        panelMemory.add(sliderMemory, "cell 2 0,growx,aligny top");

        lblMemory = new JLabel("512");
        panelMemory.add(lblMemory, "cell 3 0,aligny top");

        JLabel lblMb = new JLabel("MB");
        panelMemory.add(lblMb, "cell 3 0,aligny top");

        JTextArea tpMemoryHint = new ReadOnlyTextArea(TmmResourceBundle.getString("Settings.memory.hint"));
        panelMemory.add(tpMemoryHint, "cell 1 1 3 1,growx, wmin 0");
        TmmFontHelper.changeFont(tpMemoryHint, L2);
      }
    }
    {
      JPanel panelProxy = new JPanel();
      panelProxy.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][][grow]", "[][][][]")); // 16lp ~ width of the

      JLabel lblProxyT = new TmmLabel(TmmResourceBundle.getString("Settings.proxy"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelProxy, lblProxyT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/settings#proxy-settings"));
      add(collapsiblePanel, "cell 0 2,growx,wmin 0");
      {
        JLabel lblProxyHostT = new JLabel(TmmResourceBundle.getString("Settings.proxyhost"));
        panelProxy.add(lblProxyHostT, "cell 1 0,alignx right");

        tfProxyHost = new JTextField();
        panelProxy.add(tfProxyHost, "cell 2 0");
        tfProxyHost.setColumns(20);
        lblProxyHostT.setLabelFor(tfProxyHost);

        JLabel lblProxyPortT = new JLabel(TmmResourceBundle.getString("Settings.proxyport"));
        panelProxy.add(lblProxyPortT, "cell 1 1,alignx right");
        lblProxyPortT.setLabelFor(tfProxyPort);

        tfProxyPort = new JTextField();
        panelProxy.add(tfProxyPort, "cell 2 1");
        tfProxyPort.setColumns(20);

        JLabel lblProxyUserT = new JLabel(TmmResourceBundle.getString("Settings.proxyuser"));
        panelProxy.add(lblProxyUserT, "cell 1 2,alignx right");
        lblProxyUserT.setLabelFor(tfProxyUsername);

        tfProxyUsername = new JTextField();
        panelProxy.add(tfProxyUsername, "cell 2 2");
        tfProxyUsername.setColumns(20);

        JLabel lblProxyPasswordT = new JLabel(TmmResourceBundle.getString("Settings.proxypass"));
        panelProxy.add(lblProxyPasswordT, "cell 1 3,alignx right");
        lblProxyPasswordT.setLabelFor(tfProxyPassword);

        tfProxyPassword = new JPasswordField();
        tfProxyPassword.setColumns(20);
        panelProxy.add(tfProxyPassword, "cell 2 3");
      }
    }
    {
      JPanel panelMisc = new JPanel();
      panelMisc.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][][grow]", "[][][][grow]")); // 16lp ~ width of the

      JLabel lblMiscT = new TmmLabel(TmmResourceBundle.getString("Settings.api"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelMisc, lblMiscT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/http-api"));

      chkbxEnableHttpServer = new JCheckBox(TmmResourceBundle.getString("Settings.api.enable"));
      panelMisc.add(chkbxEnableHttpServer, "cell 1 0 2 1");

      JLabel lblHttpPortT = new JLabel(TmmResourceBundle.getString("Settings.api.port"));
      panelMisc.add(lblHttpPortT, "cell 1 1,alignx trailing");

      tfHttpPort = new JTextField();
      panelMisc.add(tfHttpPort, "cell 2 1");
      tfHttpPort.setColumns(10);
      tfHttpPort.addKeyListener(new KeyAdapter() {
        @Override
        public void keyTyped(KeyEvent e) {
          char c = e.getKeyChar();
          if (((c < '0') || (c > '9')) && (c != KeyEvent.VK_BACK_SPACE)) {
            e.consume(); // if it's not a number, ignore the event
          }
        }
      });

      JLabel lblHttpApiKey = new JLabel(TmmResourceBundle.getString("Settings.api.key"));
      panelMisc.add(lblHttpApiKey, "cell 1 2,alignx trailing");

      tfHttpApiKey = new JTextField();
      panelMisc.add(tfHttpApiKey, "cell 2 2");
      tfHttpApiKey.setColumns(30);

      add(collapsiblePanel, "cell 0 4,growx,wmin 0");
    }
    {
      JPanel panelOpenAI = new JPanel();
      panelOpenAI.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][][grow]", "[][][][][][grow]"));

      JLabel lblOpenAiT = new TmmLabel(TmmResourceBundle.getString("Settings.openai"), H3);
       CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelOpenAI, lblOpenAiT, true);

      JLabel lblOpenAiApiKey = new JLabel(TmmResourceBundle.getString("Settings.openai.apikey"));
      panelOpenAI.add(lblOpenAiApiKey, "cell 1 0,alignx trailing");

      tfOpenAiApiKey = new JTextField();
      panelOpenAI.add(tfOpenAiApiKey, "cell 2 0");
      tfOpenAiApiKey.setColumns(50);

      JLabel lblOpenAiApiUrl = new JLabel(TmmResourceBundle.getString("Settings.openai.apiurl"));
      panelOpenAI.add(lblOpenAiApiUrl, "cell 1 1,alignx trailing");

      tfOpenAiApiUrl = new JTextField();
      panelOpenAI.add(tfOpenAiApiUrl, "cell 2 1");
      tfOpenAiApiUrl.setColumns(50);

      JLabel lblOpenAiModel = new JLabel(TmmResourceBundle.getString("Settings.openai.model"));
      panelOpenAI.add(lblOpenAiModel, "cell 1 2,alignx trailing");

      tfOpenAiModel = new JTextField();
      panelOpenAI.add(tfOpenAiModel, "cell 2 2");
      tfOpenAiModel.setColumns(50);

      JLabel lblOpenAiExtractionPrompt = new JLabel(TmmResourceBundle.getString("Settings.openai.extractionprompt"));
      panelOpenAI.add(lblOpenAiExtractionPrompt, "cell 1 3,alignx trailing,aligny top");

      taOpenAiExtractionPrompt = new JTextArea();
      taOpenAiExtractionPrompt.setLineWrap(true);
      taOpenAiExtractionPrompt.setWrapStyleWord(true);
      TmmFontHelper.changeFont(taOpenAiExtractionPrompt, TmmFontHelper.L2);
      panelOpenAI.add(taOpenAiExtractionPrompt, "cell 2 3,grow");

      // Add test file path input for OpenAI testing
      JLabel lblOpenAiTestPath = new JLabel(TmmResourceBundle.getString("Settings.openai.testpath"));
      panelOpenAI.add(lblOpenAiTestPath, "cell 1 4,alignx trailing");

      tfOpenAiTestPath = new JTextField();
      tfOpenAiTestPath.setColumns(50);
      tfOpenAiTestPath.setText("/Users/jonntd/Movies/Test Movie/Test Movie.mkv");
      panelOpenAI.add(tfOpenAiTestPath, "cell 2 4,growx");

      // Add test button for OpenAI prompt testing (now includes batch testing)
      btnTestOpenAiPrompt = new JButton("Test OpenAI Recognition");
      btnTestOpenAiPrompt.addActionListener(e -> testOpenAiPrompt());
      panelOpenAI.add(btnTestOpenAiPrompt, "cell 2 5,alignx right");

      // AI Rate Limiting Controls
      JLabel lblAiRateLimit = new JLabel("AI Rate Limiting");
      lblAiRateLimit.setFont(lblAiRateLimit.getFont().deriveFont(Font.BOLD));
      panelOpenAI.add(lblAiRateLimit, "cell 1 6,spanx 2");

      chkAiRateLimitEnabled = new JCheckBox("Enable AI Rate Limiting");
      panelOpenAI.add(chkAiRateLimitEnabled, "cell 1 7,spanx 2");

      JLabel lblMaxCallsPerMinute = new JLabel("Max calls per minute:");
      panelOpenAI.add(lblMaxCallsPerMinute, "cell 1 8,alignx trailing");
      spAiMaxCallsPerMinute = new JSpinner(new SpinnerNumberModel(50, 1, 200, 1));
      panelOpenAI.add(spAiMaxCallsPerMinute, "cell 2 8");

      JLabel lblMaxCallsPerHour = new JLabel("Max calls per hour:");
      panelOpenAI.add(lblMaxCallsPerHour, "cell 1 9,alignx trailing");
      spAiMaxCallsPerHour = new JSpinner(new SpinnerNumberModel(1000, 10, 5000, 10));
      panelOpenAI.add(spAiMaxCallsPerHour, "cell 2 9");

      JLabel lblMinInterval = new JLabel("Min interval (seconds):");
      panelOpenAI.add(lblMinInterval, "cell 1 10,alignx trailing");
      spAiMinIntervalSeconds = new JSpinner(new SpinnerNumberModel(1, 0, 60, 1));
      panelOpenAI.add(spAiMinIntervalSeconds, "cell 2 10");

      chkAiIndividualFallbackEnabled = new JCheckBox("Enable individual AI fallback");
      panelOpenAI.add(chkAiIndividualFallbackEnabled, "cell 1 11,spanx 2");

      // AI Statistics Display
      JLabel lblAiStatsTitle = new JLabel("AI API Statistics:");
      panelOpenAI.add(lblAiStatsTitle, "cell 1 12,alignx trailing");
      lblAiStatistics = new JLabel("Loading...");
      panelOpenAI.add(lblAiStatistics, "cell 2 12");

      btnResetAiStatistics = new JButton("Reset Statistics");
      btnResetAiStatistics.addActionListener(e -> resetAiStatistics());
      panelOpenAI.add(btnResetAiStatistics, "cell 2 13,alignx right");

      add(collapsiblePanel, "cell 0 6,growx,wmin 0");
    }
    {
      JPanel panelMisc = new JPanel();
      panelMisc.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][][]")); // 16lp ~ width of the

      JLabel lblMiscT = new TmmLabel(TmmResourceBundle.getString("Settings.misc"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelMisc, lblMiscT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/settings#misc-settings-1"));

      add(collapsiblePanel, "cell 0 8,growx,wmin 0");
      {
        JLabel lblParallelDownloadCountT = new JLabel(TmmResourceBundle.getString("Settings.paralleldownload"));
        panelMisc.add(lblParallelDownloadCountT, "cell 1 0 2 1");

        spMaximumDownloadThreads = new JSpinner(new SpinnerNumberModel(settings.getMaximumDownloadThreads(), 1, 1024, 1));
        spMaximumDownloadThreads.setMinimumSize(new Dimension(60, 20));
        panelMisc.add(spMaximumDownloadThreads, "cell 1 0 2 1");

        chckbxIgnoreSSLProblems = new JCheckBox(TmmResourceBundle.getString("Settings.ignoressl"));
        panelMisc.add(chckbxIgnoreSSLProblems, "cell 1 1 2 1");

        JTextPane tpSSLHint = new ReadOnlyTextPane();
        tpSSLHint.setText(TmmResourceBundle.getString("Settings.ignoressl.desc"));
        TmmFontHelper.changeFont(tpSSLHint, L2);
        panelMisc.add(tpSSLHint, "cell 2 2,grow");

        chckbxDisableD3d = new JCheckBox(TmmResourceBundle.getString("Settings.disabled3d"));
        if (!SystemUtils.IS_OS_WINDOWS) {
          chckbxDisableD3d.setEnabled(false);
        }
        panelMisc.add(chckbxDisableD3d, "cell 1 3 2 1");

        JTextPane tpD3dHint = new ReadOnlyTextPane();
        tpD3dHint.setText(TmmResourceBundle.getString("Settings.disabled3d.desc"));
        TmmFontHelper.changeFont(tpD3dHint, L2);
        panelMisc.add(tpD3dHint, "cell 2 4,grow");
      }
    }
  }

  private void initMemorySlider() {
    Path file = Paths.get(Globals.CONTENT_FOLDER, LauncherExtraConfig.LAUNCHER_EXTRA_YML);
    int maxMemory = 512;
    if (Files.exists(file)) {
      // parse out memory option from extra.txt
      try {
        LauncherExtraConfig extraConfig = LauncherExtraConfig.readFile(file.toFile());
        Matcher matcher = MEMORY_PATTERN.matcher(String.join("\n", extraConfig.jvmOpts));
        if (matcher.find()) {
          maxMemory = Integer.parseInt(matcher.group(1));
          String dimension = matcher.group(2);
          if ("k".equalsIgnoreCase(dimension)) {
            maxMemory /= 1024;
          }
          if ("g".equalsIgnoreCase(dimension)) {
            maxMemory *= 1024;
          }
        }
      }
      catch (Exception e) {
        maxMemory = 512;
      }
    }

    sliderMemory.setValue(maxMemory);
  }

  private void writeLauncherExtraYml() {
    int memoryAmount = sliderMemory.getValue();

    Path file = Paths.get(Globals.CONTENT_FOLDER, LauncherExtraConfig.LAUNCHER_EXTRA_YML);
    try {
      LauncherExtraConfig extraConfig = LauncherExtraConfig.readFile(file.toFile());

      // delete any old memory setting
      extraConfig.jvmOpts.removeIf(option -> MEMORY_PATTERN.matcher(option).find());

      // set the new one if it differs from the default
      if (memoryAmount != 512) {
        extraConfig.jvmOpts.add("-Xmx" + memoryAmount + "m");
      }

      // delete any old direct3d setting
      extraConfig.jvmOpts.removeIf(option -> DIRECT3D_PATTERN.matcher(option).find());

      if (chckbxDisableD3d.isSelected()) {
        extraConfig.jvmOpts.add("-Dsun.java2d.d3d=false");
      }

      // and re-write the settings
      extraConfig.save();
    }
    catch (Exception e) {
      LOGGER.warn("Could not write memory settings - '{}'", e.getMessage());
    }
  }

  private void initDirect3d() {
    Path file = Paths.get(Globals.CONTENT_FOLDER, LauncherExtraConfig.LAUNCHER_EXTRA_YML);
    boolean disableDirect3d = false;

    if (Files.exists(file)) {
      // parse out memory option from extra.txt
      try {
        LauncherExtraConfig extraConfig = LauncherExtraConfig.readFile(file.toFile());
        Matcher matcher = DIRECT3D_PATTERN.matcher(String.join("\n", extraConfig.jvmOpts));
        if (matcher.find()) {
          boolean valueFromYml = Boolean.parseBoolean(matcher.group(1));
          disableDirect3d = !valueFromYml;
        }
      }
      catch (Exception e) {
        // ingored
      }
    }

    chckbxDisableD3d.setSelected(disableDirect3d);
  }

  protected void initDataBindings() {
    Property settingsBeanProperty = BeanProperty.create("proxyHost");
    Property jTextFieldBeanProperty = BeanProperty.create("text");
    AutoBinding autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty, tfProxyHost,
        jTextFieldBeanProperty);
    autoBinding.bind();
    //
    Property settingsBeanProperty_1 = BeanProperty.create("proxyPort");
    Property jTextFieldBeanProperty_1 = BeanProperty.create("text");
    AutoBinding autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_1, tfProxyPort,
        jTextFieldBeanProperty_1);
    autoBinding_1.bind();
    //
    Property settingsBeanProperty_2 = BeanProperty.create("proxyUsername");
    Property jTextFieldBeanProperty_2 = BeanProperty.create("text");
    AutoBinding autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_2, tfProxyUsername,
        jTextFieldBeanProperty_2);
    autoBinding_2.bind();
    //
    Property settingsBeanProperty_3 = BeanProperty.create("proxyPassword");
    Property jPasswordFieldBeanProperty = BeanProperty.create("text");
    AutoBinding autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_3, tfProxyPassword,
        jPasswordFieldBeanProperty);
    autoBinding_3.bind();

    //
    Property jSliderBeanProperty = BeanProperty.create("value");
    Property jLabelBeanProperty = BeanProperty.create("text");
    AutoBinding autoBinding_11 = Bindings.createAutoBinding(UpdateStrategy.READ, sliderMemory, jSliderBeanProperty, lblMemory, jLabelBeanProperty);
    autoBinding_11.bind();
    //
    Property settingsBeanProperty_4 = BeanProperty.create("ignoreSSLProblems");
    Property jCheckBoxBeanProperty = BeanProperty.create("selected");
    AutoBinding autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_4, chckbxIgnoreSSLProblems,
        jCheckBoxBeanProperty);
    autoBinding_4.bind();
    //
    Property settingsBeanProperty_5 = BeanProperty.create("maximumDownloadThreads");
    Property jSpinnerBeanProperty = BeanProperty.create("value");
    AutoBinding autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_5, spMaximumDownloadThreads,
        jSpinnerBeanProperty);
    autoBinding_5.bind();

    //
    Property settingsBeanProperty_9 = BeanProperty.create("enableHttpServer");
    AutoBinding autoBinding_7 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_9, chkbxEnableHttpServer,
        jCheckBoxBeanProperty);
    autoBinding_7.bind();
    //
    Property settingsBeanProperty_10 = BeanProperty.create("httpServerPort");
    Property jTextFieldBeanProperty_5 = BeanProperty.create("text");
    AutoBinding autoBinding_8 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_10, tfHttpPort,
        jTextFieldBeanProperty_5);
    autoBinding_8.bind();
    //
    Property settingsBeanProperty_11 = BeanProperty.create("httpApiKey");
    Property jTextFieldBeanProperty_6 = BeanProperty.create("text");
    AutoBinding autoBinding_12 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_11, tfHttpApiKey,
        jTextFieldBeanProperty_6);
    autoBinding_12.bind();

    // OpenAI API settings bindings
    Property settingsBeanProperty_12 = BeanProperty.create("openAiApiKey");
    Property jTextFieldBeanProperty_7 = BeanProperty.create("text");
    AutoBinding autoBinding_13 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_12, tfOpenAiApiKey,
        jTextFieldBeanProperty_7);
    autoBinding_13.bind();
    //
    Property settingsBeanProperty_13 = BeanProperty.create("openAiApiUrl");
    Property jTextFieldBeanProperty_8 = BeanProperty.create("text");
    AutoBinding autoBinding_14 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_13, tfOpenAiApiUrl,
        jTextFieldBeanProperty_8);
    autoBinding_14.bind();
    //
    Property settingsBeanProperty_14 = BeanProperty.create("openAiModel");
    Property jTextFieldBeanProperty_9 = BeanProperty.create("text");
    AutoBinding autoBinding_15 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_14, tfOpenAiModel,
        jTextFieldBeanProperty_9);
    autoBinding_15.bind();
    //
    Property settingsBeanProperty_15 = BeanProperty.create("openAiExtractionPrompt");
    Property jTextAreaBeanProperty = BeanProperty.create("text");
    AutoBinding autoBinding_16 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_15, taOpenAiExtractionPrompt,
        jTextAreaBeanProperty);
    autoBinding_16.bind();
    //
    Property settingsBeanProperty_16 = BeanProperty.create("openAiTestPath");
    Property jTextFieldBeanProperty_10 = BeanProperty.create("text");
    AutoBinding autoBinding_17 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_16, tfOpenAiTestPath,
        jTextFieldBeanProperty_10);
    autoBinding_17.bind();

    // AI Rate Limiting settings bindings
    Property settingsBeanProperty_17 = BeanProperty.create("aiRateLimitEnabled");
    Property jCheckBoxBeanProperty_1 = BeanProperty.create("selected");
    AutoBinding autoBinding_18 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_17, chkAiRateLimitEnabled,
        jCheckBoxBeanProperty_1);
    autoBinding_18.bind();

    Property settingsBeanProperty_18 = BeanProperty.create("aiMaxCallsPerMinute");
    Property jSpinnerBeanProperty_1 = BeanProperty.create("value");
    AutoBinding autoBinding_19 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_18, spAiMaxCallsPerMinute,
        jSpinnerBeanProperty_1);
    autoBinding_19.bind();

    Property settingsBeanProperty_19 = BeanProperty.create("aiMaxCallsPerHour");
    Property jSpinnerBeanProperty_2 = BeanProperty.create("value");
    AutoBinding autoBinding_20 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_19, spAiMaxCallsPerHour,
        jSpinnerBeanProperty_2);
    autoBinding_20.bind();

    Property settingsBeanProperty_20 = BeanProperty.create("aiMinIntervalSeconds");
    Property jSpinnerBeanProperty_3 = BeanProperty.create("value");
    AutoBinding autoBinding_21 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_20, spAiMinIntervalSeconds,
        jSpinnerBeanProperty_3);
    autoBinding_21.bind();

    Property settingsBeanProperty_21 = BeanProperty.create("aiIndividualFallbackEnabled");
    Property jCheckBoxBeanProperty_2 = BeanProperty.create("selected");
    AutoBinding autoBinding_22 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_21, chkAiIndividualFallbackEnabled,
        jCheckBoxBeanProperty_2);
    autoBinding_22.bind();

    // 启动统计更新定时器
    updateAiStatistics();
    startStatisticsTimer();

    // 添加面板关闭监听器，确保定时器被正确停止
    addHierarchyListener(new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
          if (!isShowing()) {
            stopStatisticsTimer();
          }
        }
      }
    });
  }

  /**
   * Test the OpenAI prompt by calling ChatGPTMovieRecognitionService with a test movie
   */
  private void testOpenAiPrompt() {
    // 禁用测试按钮，防止重复点击
    btnTestOpenAiPrompt.setEnabled(false);
    btnTestOpenAiPrompt.setText("Testing...");

    // 使用SwingWorker在后台线程中执行测试，避免阻塞UI
    javax.swing.SwingWorker<String, Void> testWorker = new javax.swing.SwingWorker<String, Void>() {
      @Override
      protected String doInBackground() throws Exception {
        // Create a test movie object to simulate recognition
        Movie testMovie = new Movie();
        testMovie.setTitle("Test Movie");

        // Use the user-provided test path from the text field
        String testPath = tfOpenAiTestPath.getText().trim();
        if (testPath.isEmpty()) {
          // 使用更通用的测试路径，或者提示用户输入
          testPath = System.getProperty("user.home") + "/Movies/Test Movie/Test Movie.mkv";
          // 设置回文本框以便用户可以看到和修改
          final String finalTestPath = testPath;
          javax.swing.SwingUtilities.invokeLater(() -> tfOpenAiTestPath.setText(finalTestPath));
        }

        // Add a mock media file path for testing
        org.tinymediamanager.core.entities.MediaFile mockFile = new org.tinymediamanager.core.entities.MediaFile();
        mockFile.setFile(java.nio.file.Paths.get(testPath));
        testMovie.addToMediaFiles(mockFile);

        // Test both individual and batch recognition
        StringBuilder resultText = new StringBuilder();

        // 1. Test individual recognition
        ChatGPTMovieRecognitionService individualService = new ChatGPTMovieRecognitionService();
        String individualResult = individualService.recognizeMovieTitle(testMovie);
        resultText.append("Individual Recognition: ").append(individualResult != null ? individualResult : "Failed").append("\n\n");

        // 2. Test batch recognition
        try {
          java.util.List<Movie> testMovies = new java.util.ArrayList<>();
          testMovies.add(testMovie);

          org.tinymediamanager.core.movie.services.BatchChatGPTMovieRecognitionService batchService =
              new org.tinymediamanager.core.movie.services.BatchChatGPTMovieRecognitionService();
          java.util.Map<String, String> batchResults = batchService.batchRecognizeMovieTitles(testMovies);

          if (batchResults.isEmpty()) {
            resultText.append("Batch Recognition: No results returned");
          } else {
            resultText.append("Batch Recognition: ");
            for (java.util.Map.Entry<String, String> entry : batchResults.entrySet()) {
              resultText.append(entry.getValue());
            }
          }
        } catch (Exception batchError) {
          resultText.append("Batch Recognition: Error - ").append(batchError.getMessage());
        }

        return resultText.toString();
      }

      @Override
      protected void done() {
        try {
          String resultText = get();

          // Show the result in a message dialog
          if (resultText.contains("Failed") && resultText.contains("No results")) {
            javax.swing.JOptionPane.showMessageDialog(SystemSettingsPanel.this,
                "Both individual and batch recognition failed.\n" +
                "Please check your API key and configuration.\n\n" + resultText,
                "OpenAI Test Results",
                javax.swing.JOptionPane.WARNING_MESSAGE);
          } else {
            javax.swing.JOptionPane.showMessageDialog(SystemSettingsPanel.this,
                "OpenAI Test Results:\n\n" + resultText,
                "OpenAI Test Results",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
          }
        } catch (Exception e) {
          org.slf4j.LoggerFactory.getLogger(SystemSettingsPanel.class).error("Error testing OpenAI prompt", e);
          javax.swing.JOptionPane.showMessageDialog(SystemSettingsPanel.this,
              "Error testing OpenAI:\n" + e.getMessage(),
              "OpenAI Test Error",
              javax.swing.JOptionPane.ERROR_MESSAGE);
        } finally {
          // 恢复测试按钮状态
          btnTestOpenAiPrompt.setEnabled(true);
          btnTestOpenAiPrompt.setText("Test OpenAI Recognition");
        }
      }
    };

    testWorker.execute();
  }

  /**
   * Test the batch OpenAI recognition service
   */
  private void testBatchOpenAiRecognition() {
    try {
      // Create test movies list
      java.util.List<Movie> testMovies = new java.util.ArrayList<>();

      // Create a test movie object
      Movie testMovie = new Movie();
      testMovie.setTitle("Test Movie");

      // Use the user-provided test path from the text field
      String testPath = tfOpenAiTestPath.getText().trim();
      if (testPath.isEmpty()) {
        testPath = System.getProperty("user.home") + "/Movies/Test Movie/Test Movie.mkv";
        tfOpenAiTestPath.setText(testPath);
      }

      // Add a mock media file path for testing
      org.tinymediamanager.core.entities.MediaFile mockFile = new org.tinymediamanager.core.entities.MediaFile();
      mockFile.setFile(java.nio.file.Paths.get(testPath));
      testMovie.addToMediaFiles(mockFile);

      testMovies.add(testMovie);

      // Create BatchChatGPTMovieRecognitionService instance
      org.tinymediamanager.core.movie.services.BatchChatGPTMovieRecognitionService batchService =
          new org.tinymediamanager.core.movie.services.BatchChatGPTMovieRecognitionService();

      // Test the batch recognition
      java.util.Map<String, String> results = batchService.batchRecognizeMovieTitles(testMovies);

      // Show the result in a message dialog
      if (results.isEmpty()) {
        JOptionPane.showMessageDialog(this,
            "Batch recognition test completed but no results returned.\n" +
            "Please check your API key and configuration.",
            "Batch OpenAI Test",
            JOptionPane.WARNING_MESSAGE);
      } else {
        StringBuilder resultText = new StringBuilder("Batch recognition test results:\n");
        for (java.util.Map.Entry<String, String> entry : results.entrySet()) {
          resultText.append("Movie ID: ").append(entry.getKey())
                   .append(" -> Title: ").append(entry.getValue()).append("\n");
        }
        JOptionPane.showMessageDialog(this,
            resultText.toString(),
            "Batch OpenAI Test",
            JOptionPane.INFORMATION_MESSAGE);
      }
    } catch (Exception e) {
      LOGGER.error("Error testing batch OpenAI recognition", e);
      JOptionPane.showMessageDialog(this,
          "Error testing batch OpenAI recognition:\n" + e.getMessage(),
          "Batch OpenAI Test",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * 更新AI统计信息显示
   */
  private void updateAiStatistics() {
    try {
      AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
      String statistics = rateLimiter.getStatistics();
      lblAiStatistics.setText(statistics);
    } catch (Exception e) {
      lblAiStatistics.setText("Statistics unavailable");
      LOGGER.warn("Failed to get AI statistics: {}", e.getMessage());
    }
  }

  /**
   * 启动统计更新定时器
   */
  private void startStatisticsTimer() {
    if (aiStatisticsTimer == null) {
      aiStatisticsTimer = new Timer(30000, e -> updateAiStatistics());
      aiStatisticsTimer.start();
    }
  }

  /**
   * 停止统计更新定时器
   */
  private void stopStatisticsTimer() {
    if (aiStatisticsTimer != null) {
      aiStatisticsTimer.stop();
      aiStatisticsTimer = null;
    }
  }

  /**
   * 重置AI统计信息
   */
  private void resetAiStatistics() {
    try {
      AIApiRateLimiter rateLimiter = AIApiRateLimiter.getInstance();
      rateLimiter.reset();
      updateAiStatistics();
      JOptionPane.showMessageDialog(this, "AI API statistics have been reset.", "Statistics Reset", JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      LOGGER.error("Failed to reset AI statistics: {}", e.getMessage());
      JOptionPane.showMessageDialog(this, "Failed to reset statistics: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }
}
