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
package org.tinymediamanager.ui.movies.panels;

import java.awt.Component;
import java.awt.FlowLayout;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.MediaFileHelper;
import org.tinymediamanager.core.MediaFileType;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.movie.MovieModuleManager;
import org.tinymediamanager.core.movie.entities.Movie;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.ui.ColumnLayout;
import org.tinymediamanager.ui.IconManager;
import org.tinymediamanager.ui.TmmUIHelper;
import org.tinymediamanager.ui.WrapLayout;
import org.tinymediamanager.ui.components.NoBorderScrollPane;
import org.tinymediamanager.ui.components.button.FlatButton;
import org.tinymediamanager.ui.components.label.LinkLabel;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.panel.IdLinkPanel;
import org.tinymediamanager.ui.components.textfield.LinkTextArea;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextPane;
import org.tinymediamanager.ui.components.textfield.ReadOnlyTextPaneHTML;
import org.tinymediamanager.ui.movies.MovieSelectionModel;
import org.tinymediamanager.ui.panels.InformationPanel;
import org.tinymediamanager.ui.panels.MediaInformationLogosPanel;
import org.tinymediamanager.ui.panels.RatingPanel;

import net.miginfocom.swing.MigLayout;

/**
 * The Class MovieInformationPanel.
 * 
 * @author Manuel Laggner
 */
public class MovieInformationPanel extends InformationPanel {
  private static final Logger        LOGGER                 = LoggerFactory.getLogger(MovieInformationPanel.class);
  private static final String        LAYOUT_ARTWORK_VISIBLE = "[n:100lp:20%, grow][300lp:300lp,grow 350]";
  private static final String        LAYOUT_ARTWORK_HIDDEN  = "[][300lp:300lp,grow 350]";

  /** UI components */
  private RatingPanel                ratingPanel;
  private JLabel                     lblMovieName;
  private JLabel                     lblTagline;
  private JLabel                     lblYear;
  private LinkLabel                  lblImdbid;
  private JLabel                     lblRunningTime;
  private LinkLabel                  lblTmdbid;
  private JTextPane                  taGenres;
  private JTextPane                  taPlot;
  private JLabel                     lblCertification;
  private JPanel                     panelOtherIds;
  private MediaInformationLogosPanel panelLogos;
  private JLabel                     lblOriginalTitle;
  private JButton                    btnPlay;
  private JScrollPane                scrollPane;
  private JTextPane                  taProduction;
  private JTextPane                  taTags;
  private JLabel                     lblEdition;
  private LinkTextArea               lblMoviePath;
  private JLabel                     lblMovieSet;
  private JLabel                     lblSpokenLanguages;
  private JLabel                     lblCountry;
  private JLabel                     lblReleaseDate;
  private JTextPane                  taNote;
  private JLabel                     lblCertificationLogo;
  private JLabel                     lblShowlink;
  private JLabel                     lblDirector;

  /**
   * Instantiates a new movie information panel.
   * 
   * @param movieSelectionModel
   *          the movie selection model
   */
  public MovieInformationPanel(MovieSelectionModel movieSelectionModel) {
    initComponents();

    // action listeners
    lblTmdbid.addActionListener(arg0 -> {
      String url = "https://www.themoviedb.org/movie/" + lblTmdbid.getText();
      try {
        TmmUIHelper.browseUrl(url);
      }
      catch (Exception e) {
        LOGGER.error("browse to tmdbid", e);
        MessageManager.instance
            .pushMessage(new Message(Message.MessageLevel.ERROR, url, "message.erroropenurl", new String[] { ":", e.getLocalizedMessage() }));
      }
    });

    lblImdbid.addActionListener(arg0 -> {
      String url = "https://www.imdb.com/title/" + lblImdbid.getText();
      try {
        TmmUIHelper.browseUrl(url);
      }
      catch (Exception e) {
        LOGGER.error("browse to imdbid", e);
        MessageManager.instance
            .pushMessage(new Message(Message.MessageLevel.ERROR, url, "message.erroropenurl", new String[] { ":", e.getLocalizedMessage() }));
      }
    });

    lblMoviePath.addActionListener(arg0 -> {
      if (StringUtils.isNotEmpty(lblMoviePath.getLink())) {
        // get the location from the label
        Path path = Paths.get(lblMoviePath.getLink());
        TmmUIHelper.openFolder(path);
      }
    });

    // UI binding
    PropertyChangeListener propertyChangeListener = propertyChangeEvent -> {
      String property = propertyChangeEvent.getPropertyName();
      Object source = propertyChangeEvent.getSource();
      // react on selection of a movie and change of a movie

      if (source.getClass() != MovieSelectionModel.class) {
        return;
      }

      MovieSelectionModel selectionModel = (MovieSelectionModel) source;
      Movie movie = selectionModel.getSelectedMovie();

      if ("selectedMovie".equals(property)) {
        changeMovie(movie);
      }
    };

    movieSelectionModel.addPropertyChangeListener(propertyChangeListener);

    btnPlay.addActionListener(e -> {
      MediaFile mf = movieSelectionModel.getSelectedMovie().getMainVideoFile();
      if (StringUtils.isNotBlank(mf.getFilename())) {
        try {
          TmmUIHelper.openFile(MediaFileHelper.getMainVideoFile(mf));
        }
        catch (Exception ex) {
          LOGGER.error("open file", ex);
          MessageManager.instance
              .pushMessage(new Message(Message.MessageLevel.ERROR, mf, "message.erroropenfile", new String[] { ":", ex.getLocalizedMessage() }));
        }
      }
    });
  }

  private void changeMovie(Movie movie) {
    lblMovieName.setText(movie.getTitle());
    lblMovieName.setIcon(movie.isLocked() ? IconManager.LOCK_BLUE : null);
    lblOriginalTitle.setText(movie.getOriginalTitle());
    lblYear.setText(getIntegerAsStringWoZero(movie.getYear()));
    lblReleaseDate.setText(movie.getReleaseDateAsString());
    lblCertification.setText(movie.getCertification().getLocalizedName());
    lblRunningTime.setText(convertRuntime(movie.getRuntime()));
    taGenres.setText(movie.getGenresAsString());
    lblDirector.setText(movie.getDirectorsAsString());
    taProduction.setText(movie.getProductionCompany());
    lblCountry.setText(movie.getCountry());
    lblSpokenLanguages.setText(movie.getLocalizedSpokenLanguages());

    lblCertificationLogo.setIcon(getCertificationIcon(movie.getCertification()));
    lblImdbid.setText(movie.getImdbId());
    lblTmdbid.setText(getIntegerAsStringWoZero(movie.getTmdbId()));
    // other IDs
    panelOtherIds.removeAll();
    for (String key : movie.getIds().keySet()) {
      // all but IMDB and TMDB
      if (MediaMetadata.IMDB.equals(key) || MediaMetadata.TMDB.equals(key)) {
        continue;
      }

      panelOtherIds.add(new IdLinkPanel(key, movie));
    }
    panelOtherIds.invalidate();
    panelOtherIds.repaint();

    lblTagline.setText(movie.getTagline());
    taPlot.setText(movie.getPlot());

    lblMovieSet.setText(movie.getMovieSetTitle());
    lblShowlink.setText(movie.getShowlinksAsString());
    lblEdition.setText(movie.getEdition().getTitle());
    taTags.setText(movie.getTagsAsString());
    lblMoviePath.setText(movie.getPath());
    taNote.setText(movie.getNote());

    setArtwork(movie, MediaFileType.POSTER);
    setArtwork(movie, MediaFileType.FANART);
    setArtwork(movie, MediaFileType.BANNER);
    setArtwork(movie, MediaFileType.THUMB);
    setArtwork(movie, MediaFileType.CLEARLOGO);

    panelLogos.setMediaInformationSource(movie);

    setRating(movie);

    // scroll everything up
    SwingUtilities.invokeLater(() -> {
      scrollPane.getVerticalScrollBar().setValue(0);
      scrollPane.getHorizontalScrollBar().setValue(0);
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("hidemode 3", LAYOUT_ARTWORK_VISIBLE, "[][grow]"));

    {
      JPanel panelLeft = new JPanel();
      panelLeft.setLayout(new ColumnLayout());
      add(panelLeft, "cell 0 0 1 2, grow");

      for (Component component : generateArtworkComponents(MediaFileType.POSTER)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.FANART)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.BANNER)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.THUMB)) {
        panelLeft.add(component);
      }

      for (Component component : generateArtworkComponents(MediaFileType.CLEARLOGO)) {
        panelLeft.add(component);
      }
    }
    {
      JPanel panelTitle = new JPanel();
      add(panelTitle, "cell 1 0,grow");
      panelTitle.setLayout(new MigLayout("insets 0 0 n n", "[grow][]", "[][][shrink 0]"));

      {
        lblMovieName = new TmmLabel("", 1.33);
        panelTitle.add(lblMovieName, "flowx,cell 0 0,wmin 0,growx");
      }
      {
        btnPlay = new FlatButton(IconManager.PLAY_LARGE);
        panelTitle.add(btnPlay, "cell 1 0 1 2,aligny top");
      }
      {
        lblOriginalTitle = new JLabel("");
        panelTitle.add(lblOriginalTitle, "cell 0 1,growx,wmin 0");
      }
      {
        panelTitle.add(new JSeparator(), "cell 0 2 2 1,growx");
      }
    }
    {
      JPanel panelRight = new JPanel();
      panelRight
          .setLayout(new MigLayout("insets n 0 n n, hidemode 3", "[100lp,grow]", "[shrink 0][][shrink 0][][][][][shrink 0][][grow,top][shrink 0][]"));

      scrollPane = new NoBorderScrollPane(panelRight);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.getVerticalScrollBar().setUnitIncrement(8);
      add(scrollPane, "cell 1 1,grow, wmin 0");

      {
        JPanel panelTopDetails = new JPanel();
        panelRight.add(panelTopDetails, "cell 0 0,grow");
        panelTopDetails.setLayout(new MigLayout("insets 0", "[][][40lp!][][grow][]", "[]2lp[]2lp[grow]2lp[]2lp[]2lp[]2lp[]2lp[]2lp[]"));

        {
          JLabel lblYearT = new TmmLabel(TmmResourceBundle.getString("metatag.year"));
          panelTopDetails.add(lblYearT, "cell 0 0");

          lblYear = new JLabel("");
          panelTopDetails.add(lblYear, "cell 1 0,growx");
        }
        {
          JLabel lblImdbIdT = new TmmLabel("IMDb:");
          panelTopDetails.add(lblImdbIdT, "cell 3 0");

          lblImdbid = new LinkLabel("");
          panelTopDetails.add(lblImdbid, "cell 3 0");
        }
        {
          lblCertificationLogo = new JLabel("");
          panelTopDetails.add(lblCertificationLogo, "cell 5 0 1 3, top");
        }
        {
          JLabel lblReleaseDateT = new TmmLabel(TmmResourceBundle.getString("metatag.releasedate"));
          panelTopDetails.add(lblReleaseDateT, "cell 0 1");

          lblReleaseDate = new JLabel("");
          panelTopDetails.add(lblReleaseDate, "cell 1 1");
        }
        {
          JLabel lblTmdbIdT = new TmmLabel("TMDB:");
          panelTopDetails.add(lblTmdbIdT, "cell 3 1");

          lblTmdbid = new LinkLabel("");
          panelTopDetails.add(lblTmdbid, "cell 3 1");
        }
        {
          JLabel lblCertificationT = new TmmLabel(TmmResourceBundle.getString("metatag.certification"));
          panelTopDetails.add(lblCertificationT, "cell 0 2");

          lblCertification = new JLabel("");
          panelTopDetails.add(lblCertification, "cell 1 2,growx");
        }
        {
          panelOtherIds = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, 0));
          panelTopDetails.add(panelOtherIds, "cell 3 2 3 2,growx,top,wmin 0");
        }
        {
          JLabel lblRunningTimeT = new TmmLabel(TmmResourceBundle.getString("metatag.runtime"));
          panelTopDetails.add(lblRunningTimeT, "cell 0 3,aligny top");

          lblRunningTime = new JLabel("");
          panelTopDetails.add(lblRunningTime, "cell 1 3,aligny top");
        }
        {
          JLabel lblGenresT = new TmmLabel(TmmResourceBundle.getString("metatag.genre"));
          panelTopDetails.add(lblGenresT, "cell 0 4");

          taGenres = new ReadOnlyTextPane();
          panelTopDetails.add(taGenres, "cell 1 4 5 1,growx,wmin 0");
        }
        {
          JLabel lblDirectorT = new TmmLabel(TmmResourceBundle.getString("metatag.director"));
          panelTopDetails.add(lblDirectorT, "cell 0 5");

          lblDirector = new JLabel("");
          panelTopDetails.add(lblDirector, "cell 1 5 5 1,growx,wmin 0");
        }
        {
          JLabel lblProductionT = new TmmLabel(TmmResourceBundle.getString("metatag.production"));
          panelTopDetails.add(lblProductionT, "cell 0 6");

          taProduction = new ReadOnlyTextPane();
          panelTopDetails.add(taProduction, "cell 1 6 5 1,growx,wmin 0");
        }
        {
          JLabel lblCountryT = new TmmLabel(TmmResourceBundle.getString("metatag.country"));
          panelTopDetails.add(lblCountryT, "cell 0 7");

          lblCountry = new JLabel("");
          panelTopDetails.add(lblCountry, "cell 1 7 5 1,wmin 0");
        }
        {
          JLabel lblSpokenLanguagesT = new TmmLabel(TmmResourceBundle.getString("metatag.spokenlanguages"));
          panelTopDetails.add(lblSpokenLanguagesT, "cell 0 8");

          lblSpokenLanguages = new JLabel("");
          panelTopDetails.add(lblSpokenLanguages, "cell 1 8 5 1,wmin 0");
        }
      }

      {
        panelRight.add(new JSeparator(), "cell 0 1,growx");
      }

      {
        ratingPanel = new RatingPanel();
        panelRight.add(ratingPanel, "flowx,cell 0 2,aligny center");
      }

      {
        JSeparator sepLogos = new JSeparator();
        panelRight.add(sepLogos, "cell 0 3,growx");
      }

      {
        panelLogos = new MediaInformationLogosPanel();
        panelRight.add(panelLogos, "cell 0 4,growx, wmin 0");
      }

      {
        panelRight.add(new JSeparator(), "cell 0 5,growx");
      }

      {
        JLabel lblTaglineT = new TmmLabel(TmmResourceBundle.getString("metatag.tagline"));
        panelRight.add(lblTaglineT, "cell 0 6,alignx left,aligny top");

        lblTagline = new JLabel();
        panelRight.add(lblTagline, "cell 0 7,growx,wmin 0,aligny top");
      }

      {
        JLabel lblPlotT = new TmmLabel(TmmResourceBundle.getString("metatag.plot"));
        panelRight.add(lblPlotT, "cell 0 8,alignx left,aligny top");

        taPlot = new ReadOnlyTextPaneHTML();
        panelRight.add(taPlot, "cell 0 9,growx,wmin 0,aligny top");
      }
      {
        panelRight.add(new JSeparator(), "cell 0 10,growx");
      }
      {
        JPanel panelBottomDetails = new JPanel();
        panelRight.add(panelBottomDetails, "cell 0 11,grow");
        panelBottomDetails.setLayout(new MigLayout("insets 0", "[][200lp,grow]", "[]2lp[]2lp[]2lp[]2lp[]2lp[]"));
        {
          JLabel lblMoviesetT = new TmmLabel(TmmResourceBundle.getString("metatag.movieset"));
          panelBottomDetails.add(lblMoviesetT, "cell 0 0");

          lblMovieSet = new JLabel("");
          panelBottomDetails.add(lblMovieSet, "cell 1 0,growx,wmin 0");
        }
        {
          JLabel lblShowlinkT = new TmmLabel(TmmResourceBundle.getString("metatag.showlink"));
          panelBottomDetails.add(lblShowlinkT, "cell 0 1");

          lblShowlink = new JLabel("");
          panelBottomDetails.add(lblShowlink, "cell 1 1");
        }
        {
          JLabel lblEditionT = new TmmLabel(TmmResourceBundle.getString("metatag.edition"));
          panelBottomDetails.add(lblEditionT, "cell 0 2");

          lblEdition = new JLabel("");
          panelBottomDetails.add(lblEdition, "cell 1 2,growx,wmin 0");
        }
        {
          JLabel lblTagsT = new TmmLabel(TmmResourceBundle.getString("metatag.tags"));
          panelBottomDetails.add(lblTagsT, "cell 0 3");

          taTags = new ReadOnlyTextPane();
          panelBottomDetails.add(taTags, "cell 1 3,growx,wmin 0");
        }
        {
          JLabel lblMoviePathT = new TmmLabel(TmmResourceBundle.getString("metatag.path"));
          panelBottomDetails.add(lblMoviePathT, "cell 0 4");

          lblMoviePath = new LinkTextArea("");
          panelBottomDetails.add(lblMoviePath, "cell 1 4,growx,wmin 0");
        }
        {
          JLabel lblNoteT = new TmmLabel(TmmResourceBundle.getString("metatag.note"));
          panelBottomDetails.add(lblNoteT, "cell 0 5");

          taNote = new ReadOnlyTextPaneHTML();
          panelBottomDetails.add(taNote, "cell 1 5,growx,wmin 0");
        }
      }
    }
  }

  @Override
  protected List<MediaFileType> getShowArtworkFromSettings() {
    return MovieModuleManager.getInstance().getSettings().getShowArtworkTypes();
  }

  @Override
  protected void setColumnLayout(boolean artworkVisible) {
    if (artworkVisible) {
      ((MigLayout) getLayout()).setColumnConstraints(LAYOUT_ARTWORK_VISIBLE);
    }
    else {
      ((MigLayout) getLayout()).setColumnConstraints(LAYOUT_ARTWORK_HIDDEN);
    }
  }

  private void setRating(Movie movie) {
    Map<String, MediaRating> ratings = new HashMap<>(movie.getRatings());
    MediaRating customRating = movie.getRating();
    if (customRating != MediaMetadata.EMPTY_RATING) {
      ratings.put("custom", customRating);
    }

    ratingPanel.setRatings(ratings);
  }
}
