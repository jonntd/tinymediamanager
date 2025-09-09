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
package org.tinymediamanager.ui.tvshows.settings;

import static org.tinymediamanager.ui.TmmFontHelper.H3;

import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.Property;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.tvshow.TvShowModuleManager;
import org.tinymediamanager.core.tvshow.TvShowSettings;
import org.tinymediamanager.scraper.entities.CountryCode;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.rating.RatingProvider;
import org.tinymediamanager.ui.components.button.DocsButton;
import org.tinymediamanager.ui.components.button.JHintCheckBox;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.CollapsiblePanel;
import org.tinymediamanager.ui.tvshows.panels.TvShowScraperMetadataPanel;

import net.miginfocom.swing.MigLayout;

/**
 * The class {@link TvShowScraperSettingsPanel} shows scraper options for the meta data scraper.
 * 
 * @author Manuel Laggner
 */
class TvShowScraperOptionsSettingsPanel extends JPanel {
  private final TvShowSettings      settings = TvShowModuleManager.getInstance().getSettings();

  private JComboBox<MediaLanguages> cbScraperLanguage;
  private JComboBox<CountryCode>    cbCertificationCountry;
  private JComboBox<CountryItem>    cbReleaseCountry;
  private JCheckBox                 chckbxCapitalizeWords;
  private JCheckBox                 chckbxDoNotOverwrite;
  private JCheckBox                 chckbxFetchAllRatings;
  private JCheckBox                 chckbxRatingImdb;
  private JCheckBox                 chckbxRatingTmdb;
  private JCheckBox                 chckbxRatingMcUserscore;
  private JCheckBox                 chckbxRatingMyAnimeList;
  private JCheckBox                 chckbxRatingRogerEbert;
  private JCheckBox                 chckbxRatingTraktTv;
  private JCheckBox                 chckbxRatingLetterboxd;

  /**
   * Instantiates a new movie scraper settings panel.
   */
  TvShowScraperOptionsSettingsPanel() {
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
    chckbxRatingMcUserscore.addItemListener(checkBoxListener);
    chckbxRatingTraktTv.addItemListener(checkBoxListener);
    chckbxRatingLetterboxd.addItemListener(checkBoxListener);
    chckbxRatingMyAnimeList.addItemListener(checkBoxListener);
    chckbxRatingRogerEbert.addItemListener(checkBoxListener);
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[600lp,grow]", "[][]15lp![][15lp!][][15lp!][]"));
    {
      JPanel panelOptions = new JPanel();
      panelOptions.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][][][10lp!][][][][10lp!][]")); // 16lp ~ width of the

      JLabel lblOptions = new TmmLabel(TmmResourceBundle.getString("Settings.advancedoptions"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelOptions, lblOptions, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/settings#advanced-options"));
      add(collapsiblePanel, "cell 0 0,growx, wmin 0");
      {
        JLabel lblScraperLanguage = new JLabel(TmmResourceBundle.getString("Settings.preferredLanguage")); // $NON-NLS-1$
        panelOptions.add(lblScraperLanguage, "cell 1 0 2 1");

        cbScraperLanguage = new JComboBox(MediaLanguages.valuesSorted());
        panelOptions.add(cbScraperLanguage, "cell 1 0 2 1");

        JLabel lblCountry = new JLabel(TmmResourceBundle.getString("Settings.certificationCountry")); // $NON-NLS-1$
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

        chckbxCapitalizeWords = new JCheckBox(TmmResourceBundle.getString("Settings.scraper.capitalizeWords"));
        panelOptions.add(chckbxCapitalizeWords, "cell 1 8");
      }
    }
    {
      JPanel panelDefaults = new JPanel();
      panelDefaults.setLayout(new MigLayout("hidemode 1, insets 0", "[20lp!][16lp!][grow]", "[][]")); // 16lp ~ width of the

      JLabel lblDefaultsT = new TmmLabel(TmmResourceBundle.getString("scraper.metadata.defaults"), H3);
      CollapsiblePanel collapsiblePanel = new CollapsiblePanel(panelDefaults, lblDefaultsT, true);
      collapsiblePanel.addExtraTitleComponent(new DocsButton("/tvshows/settings#metadata-scrape-defaults"));
      add(collapsiblePanel, "cell 0 2,growx, wmin 0");
      {
        TvShowScraperMetadataPanel scraperMetadataPanel = new TvShowScraperMetadataPanel();
        panelDefaults.add(scraperMetadataPanel, "cell 1 0 2 1");
      }

      chckbxDoNotOverwrite = new JCheckBox(TmmResourceBundle.getString("message.scrape.donotoverwrite"));
      chckbxDoNotOverwrite.setToolTipText(TmmResourceBundle.getString("message.scrape.donotoverwrite.desc"));
      panelDefaults.add(chckbxDoNotOverwrite, "cell 1 1 2 1");
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
    if (chckbxRatingMcUserscore.isSelected()) {
      fetchRatingSources.add(RatingProvider.RatingSource.METACRITIC_USER);
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
    Property settingsBeanProperty_10 = BeanProperty.create("capitalWordsInTitles");
    Property jCheckBoxBeanProperty_1 = BeanProperty.create("selected");
    AutoBinding autoBinding_9 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, settingsBeanProperty_10, chckbxCapitalizeWords,
        jCheckBoxBeanProperty_1);
    autoBinding_9.bind();
    //
    Property tvShowSettingsBeanProperty = BeanProperty.create("doNotOverwriteExistingData");
    Property jCheckBoxBeanProperty = BeanProperty.create("selected");
    AutoBinding autoBinding_1 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, tvShowSettingsBeanProperty, chckbxDoNotOverwrite,
        jCheckBoxBeanProperty);
    autoBinding_1.bind();
    //
    Property tvShowSettingsBeanProperty_1 = BeanProperty.create("fetchAllRatings");
    AutoBinding autoBinding_2 = Bindings.createAutoBinding(UpdateStrategy.READ_WRITE, settings, tvShowSettingsBeanProperty_1, chckbxFetchAllRatings,
        jCheckBoxBeanProperty);
    autoBinding_2.bind();
    //
    Property jCheckBoxBeanProperty_2 = BeanProperty.create("enabled");
    AutoBinding autoBinding = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_1, chckbxRatingImdb,
        jCheckBoxBeanProperty_2);
    autoBinding.bind();
    //
    AutoBinding autoBinding_3 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_1, chckbxRatingTmdb,
        jCheckBoxBeanProperty_2);
    autoBinding_3.bind();
    //
    AutoBinding autoBinding_4 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_1,
        chckbxRatingMcUserscore, jCheckBoxBeanProperty_2);
    autoBinding_4.bind();
    //
    AutoBinding autoBinding_5 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_1,
        chckbxRatingMyAnimeList, jCheckBoxBeanProperty_2);
    autoBinding_5.bind();
    //
    AutoBinding autoBinding_6 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_1,
        chckbxRatingRogerEbert, jCheckBoxBeanProperty_2);
    autoBinding_6.bind();
    //
    AutoBinding autoBinding_10 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_1, chckbxRatingTraktTv,
        jCheckBoxBeanProperty_2);
    autoBinding_10.bind();
    //
    AutoBinding autoBinding_11 = Bindings.createAutoBinding(UpdateStrategy.READ, chckbxFetchAllRatings, jCheckBoxBeanProperty_1,
        chckbxRatingLetterboxd, jCheckBoxBeanProperty_2);
    autoBinding_11.bind();
  }
}
