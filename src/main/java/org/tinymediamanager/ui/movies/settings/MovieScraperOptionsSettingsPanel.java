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
package org.tinymediamanager.ui.movies.settings;

import static org.tinymediamanager.ui.TmmFontHelper.H3;
import static org.tinymediamanager.ui.TmmFontHelper.L2;

import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextArea;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Property;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.MovieSettings;
import org.tinymediamanager.scraper.entities.CountryCode;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.rating.RatingProvider;
import org.tinymediamanager.ui.TmmFontHelper;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.button.JHintCheckBox;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextArea;
import org.tinymediamanager.ui.movies.panels.MovieScraperMetadataPanel;

import net.miginfocom.swing.MigLayout;

/**
 * The class {@link MovieScraperSettingsPanel} shows scraper options for the meta data scraper.
 *
 * @author Manuel Laggner
 */
class MovieScraperOptionsSettingsPanel extends JPanel {
  private final MovieSettings       settings         = MovieModuleManager.getInstance().getSettings();

  private JSlider                   sliderThreshold;
  private JComboBox<MediaLanguages> cbScraperLanguage;
  private JComboBox<CountryCode>    cbCertificationCountry;
  private JComboBox<CountryItem>    cbReleaseCountry;
  private JCheckBox                 chckbxScraperFallback;
  private JCheckBox                 chckbxCapitalizeWords;
  private JCheckBox                 chckbxDoNotOverwrite;
  private JCheckBox                 chckbxFetchAllRatings;
  private JCheckBox                 chckbxRatingImdb;
  private JCheckBox                 chckbxRatingRtTomatometer;
  private JCheckBox                 chckbxRatingRtAudienceScore;
  private JCheckBox                 chckbxRatingTmdb;
  private JCheckBox                 chckbxRatingMcMetascore;
  private JCheckBox                 chckbxRatingMcUserscore;
  private JCheckBox                 chckbxRatingMyAnimeList;
  private JCheckBox                 chckbxRatingRogerEbert;
  private JCheckBox                 chckbxRatingTraktTv;
  private JCheckBox                 chckbxRatingLetterboxd;

  /**
   * Instantiates a new movie scraper settings panel.
   */
  MovieScraperOptionsSettingsPanel() {
    ItemListener checkBoxListener = e -> checkChanges();

    // UI init
    initComponents();
    initDataBindings();

    // data init
    for (String country : Locale.getISOCountries()) {
      CountryItem item = new CountryItem(new Locale("", country));
      cbReleaseCountry.addItem(item);
      if (item.locale.getCountry().equalsIgnoreCase(settings.getReleaseDateCountry())) {
        cbReleaseCountry.setSelectedItem(item);
      }
    }
    cbReleaseCountry.addItemListener(l -> settings.setReleaseDateCountry(((CountryItem) cbReleaseCountry.getSelectedItem()).locale.getCountry()));

    chckbxRatingImdb.addItemListener(checkBoxListener);
    chckbxRatingTmdb.addItemListener(checkBoxListener);
    chckbxRatingMcMetascore.addItemListener(checkBoxListener);
    chckbxRatingMcUserscore.addItemListener(checkBoxListener);
    chckbxRatingRtTomatometer.addItemListener(checkBoxListener);
    chckbxRatingRtAudienceScore.addItemListener(checkBoxListener);
    chckbxRatingTraktTv.addItemListener(checkBoxListener);
    chckbxRatingLetterboxd.addItemListener(checkBoxListener);
    chckbxRatingMyAnimeList.addItemListener(checkBoxListener);
    chckbxRatingRogerEbert.addItemListener(checkBoxListener);

    // threshold slider
    Dictionary<Integer, JLabel> labelTable = new Hashtable<>();
    labelTable.put(100, new JLabel("100"));
    labelTable.put(75, new JLabel("75"));
    labelTable.put(50, new JLabel("50"));
    labelTable.put(25, new JLabel("25"));
    labelTable.put(0, new JLabel("0"));
    sliderThreshold.setLabelTable(labelTable);
    sliderThreshold.setValue((int) (settings.getScraperThreshold() * 100));
    sliderThreshold.addChangeListener(arg0 -> settings.setScraperThreshold(sliderThreshold.getValue() / 100.0));
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[700lp,grow]", "[][]15lp![][15lp!][]"));
    {
      JPanel panelOptions = new JPanel();
      panelOptions.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][10lp!][][][][10lp!][][]")); // 16lp ~ width of the

      JLabel lblOptions = new TmmLabel(TmmResourceBundle.getString("Settings.advancedoptions"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelOptions, lblOptions, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#advanced-options"));
      add(collapsiblePanel, "cell 0 0,growx, wmin 0");
      {
        JLabel lblScraperLanguage = new JLabel(TmmResourceBundle.getString("Settings.preferredLanguage"));
        panelOptions.add(lblScraperLanguage, "cell 1 0 2 1");

        cbScraperLanguage = new JComboBox(MediaLanguages.valuesSorted());
        panelOptions.add(cbScraperLanguage, "cell 1 0 2 1");

        JLabel lblCountry = new JLabel(TmmResourceBundle.getString("Settings.certificationCountry"));
        panelOptions.add(lblCountry, "cell 1 1 2 1");

        cbCertificationCountry = new JComboBox(CountryCode.values());
        panelOptions.add(cbCertificationCountry, "cell 1 1 2 1");

        JLabel label = new JLabel(TmmResourceBundle.getString("Settings.releaseDateCountry"));
        panelOptions.add(label, "flowx,cell 1 2 2 1");

        cbReleaseCountry = new JComboBox();
        panelOptions.add(cbReleaseCountry, "cell 1 2 2 1");

        chckbxFetchAllRatings = new JHintCheckBox(TmmResourceBundle.getString("Settings.fetchallratings"));
        chckbxFetchAllRatings.setToolTipText(TmmResourceBundle.getString("Settings.fetchallratings.desc"));
        panelOptions.add(chckbxFetchAllRatings, "cell 1 4 2 1");

        chckbxRatingImdb = new JCheckBox("IMDb");
        panelOptions.add(chckbxRatingImdb, "flowx,cell 2 5");

        chckbxRatingTmdb = new JCheckBox("TMDB");
        panelOptions.add(chckbxRatingTmdb, "cell 2 5");

        chckbxRatingMcMetascore = new JCheckBox("Metacritic Metascore");
        panelOptions.add(chckbxRatingMcMetascore, "cell 2 5");

        chckbxRatingMcUserscore = new JCheckBox("Metacritic Userscore");
        panelOptions.add(chckbxRatingMcUserscore, "cell 2 5");

        chckbxRatingMyAnimeList = new JCheckBox("MyAnimeList");
        panelOptions.add(chckbxRatingMyAnimeList, "cell 2 6");

        chckbxRatingRogerEbert = new JCheckBox("RogerEbert.com");
        panelOptions.add(chckbxRatingRogerEbert, "cell 2 6");

        chckbxRatingTraktTv = new JCheckBox("Trakt.tv");
        panelOptions.add(chckbxRatingTraktTv, "cell 2 5");

        chckbxRatingLetterboxd = new JCheckBox("Letterboxd");
        panelOptions.add(chckbxRatingLetterboxd, "cell 2 5");

        chckbxRatingRtTomatometer = new JCheckBox("Rotten Tomatoes - Tomatometer");
        panelOptions.add(chckbxRatingRtTomatometer, "flowx,cell 2 6");

        chckbxRatingRtAudienceScore = new JCheckBox("RottenTomatoes - Audience Score");
        panelOptions.add(chckbxRatingRtAudienceScore, "cell 2 6");

        chckbxScraperFallback = new JCheckBox(TmmResourceBundle.getString("Settings.scraperfallback"));
        panelOptions.add(chckbxScraperFallback, "cell 1 8 2 1");

        chckbxCapitalizeWords = new JCheckBox((TmmResourceBundle.getString("Settings.scraper.capitalizeWords")));
        panelOptions.add(chckbxCapitalizeWords, "cell 1 9 2 1");
      }
    }
    {
      JPanel panelDefaults = new JPanel();
      panelDefaults.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][]")); // 16lp ~ width of the

      JLabel lblDefaultsT = new TmmLabel(TmmResourceBundle.getString("scraper.metadata.defaults"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelDefaults, lblDefaultsT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#metadata-scrape-defaults"));
      add(collapsiblePanel, "cell 0 2,growx");
      {
        MovieScraperMetadataPanel movieScraperMetadataPanel = new MovieScraperMetadataPanel();
        panelDefaults.add(movieScraperMetadataPanel, "cell 1 0 2 1");
      }

      chckbxDoNotOverwrite = new JCheckBox(TmmResourceBundle.getString("message.scrape.donotoverwrite"));
      chckbxDoNotOverwrite.setToolTipText(TmmResourceBundle.getString("message.scrape.donotoverwrite.desc"));
      panelDefaults.add(chckbxDoNotOverwrite, "cell 1 1 2 1");
    }
    {
      JPanel panelAutomaticScrape = new JPanel(new MigLayout("hidemode 1, insets 0", "[20lp!][][300lp][grow]", ""));

      JLabel lblAutomaticScrapeT = new TmmLabel(TmmResourceBundle.getString("Settings.automaticscraper"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelAutomaticScrape, lblAutomaticScrapeT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/movies/settings#automatic-scraper"));
      add(collapsiblePanel, "cell 0 4,growx,wmin 0");
      {
        JLabel lblScraperThreshold = new JLabel(TmmResourceBundle.getString("Settings.scraperTreshold"));
        panelAutomaticScrape.add(lblScraperThreshold, "cell 1 0,aligny top");

        sliderThreshold = new JSlider();
        sliderThreshold.setMinorTickSpacing(5);
        sliderThreshold.setMajorTickSpacing(10);
        sliderThreshold.setSnapToTicks(true);
        sliderThreshold.setPaintTicks(true);
        sliderThreshold.setPaintLabels(true);
        panelAutomaticScrape.add(sliderThreshold, "cell 2 0,growx,aligny top");

        JTextArea tpScraperThresholdHint = new ReadOnlyTextArea(TmmResourceBundle.getString("Settings.scraperTreshold.hint"));
        TmmFontHelper.changeFont(tpScraperThresholdHint, L2);
        panelAutomaticScrape.add(tpScraperThresholdHint, "cell 1 1 3 1, growx, wmin 0");
      }
    }
  }

  private void checkChanges() {
    List<RatingProvider.RatingSource> fetchRatingSources = new ArrayList<>();

    if (chckbxRatingImdb.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.IMDB);
    }
    if (chckbxRatingTmdb.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.TMDB);
    }
    if (chckbxRatingMcMetascore.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.METACRITIC);
    }
    if (chckbxRatingMcUserscore.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.METACRITIC_USER);
    }
    if (chckbxRatingRtTomatometer.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.ROTTEN_TOMATOES_TOMATOMETER);
    }
    if (chckbxRatingRtAudienceScore.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.ROTTEN_TOMATOES_AVG_RATING);
    }
    if (chckbxRatingMyAnimeList.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.MAL);
    }
    if (chckbxRatingRogerEbert.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.ROGER_EBERT);
    }
    if (chckbxRatingTraktTv.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.TRAKT_TV);
    }
    if (chckbxRatingLetterboxd.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.LETTERBOXD);
    }

    settings.setFetchRatingSources(fetchRatingSources);
  }

  private static class CountryItem {
    private final Locale locale;

    public CountryItem(Locale locale) {
      this.locale = locale;
    }

    @Override
    public String toString() {
      return locale.getCountry() + " - " + locale.getDisplayCountry();
    }
  }

  protected void initDataBindings() {
    Property settingsBeanProperty_8 = BeanProperty.create("scraperLanguage");
    Property jComboBoxBeanProperty = BeanProperty.create("selectedItem");
    AutoBinding autoBinding_7 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_8, cbScraperLanguage,
        jComboBoxBeanProperty);
    autoBinding_7.bind();
    //
    Property settingsBeanProperty_9 = BeanProperty.create("certificationCountry");
    AutoBinding autoBinding_8 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_9, cbCertificationCountry,
        jComboBoxBeanProperty);
    autoBinding_8.bind();
    //
    Property settingsBeanProperty_1 = BeanProperty.create("scraperFallback");
    Property jCheckBoxBeanProperty_2 = BeanProperty.create("selected");
    AutoBinding autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_1, chckbxScraperFallback,
        jCheckBoxBeanProperty_2);
    autoBinding_1.bind();
    //
    Property settingsBeanProperty_2 = BeanProperty.create("capitalWordsInTitles");
    Property jCheckBoxBeanProperty_3 = BeanProperty.create("selected");
    AutoBinding autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_2, chckbxCapitalizeWords,
        jCheckBoxBeanProperty_3);
    autoBinding_2.bind();
    //
    Property movieSettingsBeanProperty = BeanProperty.create("doNotOverwriteExistingData");
    Property jCheckBoxBeanProperty = BeanProperty.create("selected");
    AutoBinding autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty, chckbxDoNotOverwrite,
        jCheckBoxBeanProperty);
    autoBinding_3.bind();
    //
    Property movieSettingsBeanProperty_1 = BeanProperty.create("fetchAllRatings");
    AutoBinding autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, movieSettingsBeanProperty_1, chckbxFetchAllRatings,
        jCheckBoxBeanProperty);
    autoBinding_4.bind();
    //
    Property jCheckBoxBeanProperty_1 = BeanProperty.create("enabled");
    AutoBinding autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2, chckbxRatingImdb,
        jCheckBoxBeanProperty_1);
    autoBinding.bind();
    //
    AutoBinding autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2,
        chckbxRatingRtTomatometer, jCheckBoxBeanProperty_1);
    autoBinding_5.bind();
    //
    AutoBinding autoBinding_6 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2,
        chckbxRatingRtAudienceScore, jCheckBoxBeanProperty_1);
    autoBinding_6.bind();
    //
    AutoBinding autoBinding_9 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2, chckbxRatingTmdb,
        jCheckBoxBeanProperty_1);
    autoBinding_9.bind();
    //
    AutoBinding autoBinding_10 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2,
        chckbxRatingMcMetascore, jCheckBoxBeanProperty_1);
    autoBinding_10.bind();
    //
    AutoBinding autoBinding_11 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2,
        chckbxRatingMcUserscore, jCheckBoxBeanProperty_1);
    autoBinding_11.bind();
    //
    AutoBinding autoBinding_12 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2,
        chckbxRatingMyAnimeList, jCheckBoxBeanProperty_1);
    autoBinding_12.bind();
    //
    AutoBinding autoBinding_13 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2,
        chckbxRatingRogerEbert, jCheckBoxBeanProperty_1);
    autoBinding_13.bind();
    //
    AutoBinding autoBinding_14 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2, chckbxRatingTraktTv,
        jCheckBoxBeanProperty_1);
    autoBinding_14.bind();
    //
    AutoBinding autoBinding_15 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_2,
        chckbxRatingLetterboxd, jCheckBoxBeanProperty_1);
    autoBinding_15.bind();
  }
}
