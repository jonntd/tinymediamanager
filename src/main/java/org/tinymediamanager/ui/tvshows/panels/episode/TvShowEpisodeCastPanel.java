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
package org.tinymediamanager.ui.tvshows.panels.episode;

import java.beans.PropertyChangeListener;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.Person;
import org.tinymediamanager.ui.TmmUILayoutStore;
import org.tinymediamanager.ui.components.label.PersonImageLabel;
import org.tinymediamanager.ui.components.label.TmmLabel;
import org.tinymediamanager.ui.components.table.PersonTable;
import org.tinymediamanager.ui.components.table.TmmTable;
import org.tinymediamanager.ui.tvshows.TvShowEpisodeSelectionModel;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.ObservableElementList;
import net.miginfocom.swing.MigLayout;

/**
 * The Class TvShowEpisodeCastPanel.
 * 
 * @author Manuel Laggner
 */
public class TvShowEpisodeCastPanel extends JPanel {
  private final TvShowEpisodeSelectionModel selectionModel;
  private final EventList<Person>           crewEventList;
  private final EventList<Person>           actorEventList;

  /**
   * UI elements
   */
  private PersonImageLabel                  lblCrewThumb;
  private PersonImageLabel                  lblActorThumb;
  private TmmTable                          tableCrew;
  private TmmTable                          tableActor;

  /**
   * Instantiates a new tv show episode cast panel.
   * 
   * @param model
   *          the selection model
   */
  public TvShowEpisodeCastPanel(TvShowEpisodeSelectionModel model) {
    this.selectionModel = model;
    crewEventList = GlazedLists.threadSafeList(new ObservableElementList<>(new BasicEventList<>(), GlazedLists.beanConnector(Person.class)));
    actorEventList = GlazedLists.threadSafeList(new ObservableElementList<>(new BasicEventList<>(), GlazedLists.beanConnector(Person.class)));

    initComponents();

    lblActorThumb.setDesiredAspectRatio(2 / 3f);
    lblCrewThumb.setDesiredAspectRatio(2 / 3f);

    lblActorThumb.enableLightbox();
    lblCrewThumb.enableLightbox();

    lblActorThumb.setCacheUrl(true);
    lblCrewThumb.setCacheUrl(true);

    // install the propertychangelistener
    PropertyChangeListener propertyChangeListener = propertyChangeEvent -> {
      String property = propertyChangeEvent.getPropertyName();
      Object source = propertyChangeEvent.getSource();
      // react on selection/change of an episode

      if (source.getClass() != TvShowEpisodeSelectionModel.class) {
        return;
      }

      if ("selectedTvShowEpisode".equals(property)) {
        actorEventList.clear();
        actorEventList.addAll(selectionModel.getSelectedTvShowEpisode().getActors());
        if (!actorEventList.isEmpty()) {
          tableActor.getSelectionModel().setSelectionInterval(0, 0);
        }

        crewEventList.clear();
        crewEventList.addAll(selectionModel.getSelectedTvShowEpisode().getCrew());

        if (!crewEventList.isEmpty()) {
          tableCrew.getSelectionModel().setSelectionInterval(0, 0);
        }
      }
    };

    selectionModel.addPropertyChangeListener(propertyChangeListener);

    // selectionlistener for the selected actor
    tableActor.getSelectionModel().addListSelectionListener(arg0 -> {
      if (!arg0.getValueIsAdjusting()) {
        int selectedRow = tableActor.convertRowIndexToModel(tableActor.getSelectedRow());
        if (selectedRow >= 0 && selectedRow < actorEventList.size()) {
          Person actor = actorEventList.get(selectedRow);
          lblActorThumb.setPerson(selectionModel.getSelectedTvShowEpisode().getTvShow(), actor); // needs to use the TV show here
        }
        else {
          lblActorThumb.clearImage();
        }
      }
    });

    // selectionlistener for the selected crew member
    tableCrew.getSelectionModel().addListSelectionListener(arg0 -> {
      if (!arg0.getValueIsAdjusting()) {
        int selectedRow = tableCrew.convertRowIndexToModel(tableCrew.getSelectedRow());
        if (selectedRow >= 0 && selectedRow < crewEventList.size()) {
          Person crewMember = crewEventList.get(selectedRow);
          lblCrewThumb.setPerson(selectionModel.getSelectedTvShowEpisode().getTvShow(), crewMember); // needs to use the TV show here
        }
        else {
          lblCrewThumb.clearImage();
        }
      }
    });
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[][400lp,grow][150lp,grow]", "[150lp:200lp,grow][][150lp:200lp,grow]"));
    {
      JLabel lblProducersT = new TmmLabel(TmmResourceBundle.getString("metatag.crew"));
      add(lblProducersT, "cell 0 0,aligny top");

      tableCrew = new PersonTable(crewEventList) {
        @Override
        public void onPersonChanged(Person person) {
          super.onPersonChanged(person);
          TvShowEpisodeCastPanel.this.selectionModel.getSelectedTvShowEpisode().saveToDb();
          TvShowEpisodeCastPanel.this.selectionModel.getSelectedTvShowEpisode().writeNFO();
        }
      };
      tableCrew.setName(getName() + ".producerTable");
      TmmUILayoutStore.getInstance().install(tableCrew);
      JScrollPane scrollPanePerson = new JScrollPane();
      tableCrew.configureScrollPane(scrollPanePerson);
      add(scrollPanePerson, "cell 1 0,grow");
    }
    {
      lblCrewThumb = new PersonImageLabel();
      add(lblCrewThumb, "cell 2 0,growx");
    }
    {
      JSeparator separator = new JSeparator();
      add(separator, "cell 0 1 3 1,growx");
    }
    {
      JLabel lblActorsT = new TmmLabel(TmmResourceBundle.getString("metatag.actors"));
      add(lblActorsT, "cell 0 2,aligny top");

      tableActor = new PersonTable(actorEventList) {
        @Override
        public void onPersonChanged(Person person) {
          super.onPersonChanged(person);
          TvShowEpisodeCastPanel.this.selectionModel.getSelectedTvShowEpisode().saveToDb();
          TvShowEpisodeCastPanel.this.selectionModel.getSelectedTvShowEpisode().writeNFO();
        }
      };
      tableActor.setName(getName() + ".actorTable");
      TmmUILayoutStore.getInstance().install(tableActor);
      JScrollPane scrollPanePersons = new JScrollPane();
      tableActor.configureScrollPane(scrollPanePersons);
      add(scrollPanePersons, "cell 1 2,grow");
    }
    {
      lblActorThumb = new PersonImageLabel();
      add(lblActorThumb, "cell 2 2,growx");
    }
  }
}
