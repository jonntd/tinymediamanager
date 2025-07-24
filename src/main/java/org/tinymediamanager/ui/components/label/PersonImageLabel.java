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
package org.tinymediamanager.ui.components.label;

import java.awt.Graphics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.SwingWorker;

import org.apache.commons.lang3.StringUtils;
import org.tinymediamanager.core.ImageCache;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.Person;

/**
 * The Class PersonImageLabel.
 * 
 * @author Manuel Laggner
 */
public class PersonImageLabel extends ImageLabel {
  private SwingWorker<Void, Void> personWorker = null;
  private Person                  person       = null;

  public void setPerson(MediaEntity mediaEntity, Person person) {
    clearImage();

    if (mediaEntity != null && person != null && person != this.person) {
      if (personWorker != null && !personWorker.isDone()) {
        personWorker.cancel(true);
      }
      this.person = person;
      personWorker = new ActorImageLoader(person, mediaEntity);
      personWorker.execute();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    // refetch the image if its visible now
    if (isShowing() && !isLoading() && scaledImage == null) {
      if (StringUtils.isNotBlank(imagePath)) {
        if (worker != null && !worker.isDone()) {
          worker.cancel(true);
        }
        worker = new ImageLoader(this.imagePath, this.getSize());
        worker.execute();
        return;
      }
      else if (StringUtils.isNotBlank(imageUrl)) {
        worker = new ImageFetcher(imageUrl, this.getSize());
        worker.execute();
        return;
      }
    }

    super.paintComponent(g);
  }

  @Override
  protected boolean isLoading() {
    return (worker != null && !worker.isDone()) || (personWorker != null && !personWorker.isDone());
  }

  /*
   * inner class for loading the actor images
   */
  protected class ActorImageLoader extends SwingWorker<Void, Void> {
    private final Person      actor;
    private final MediaEntity mediaEntity;
    private Path              imagePath = null;

    private ActorImageLoader(Person actor, MediaEntity mediaEntity) {
      this.actor = actor;
      this.mediaEntity = mediaEntity;
    }

    @Override
    protected Void doInBackground() {
      // set file (or cached one) if existent
      String actorImageFilename = actor.getNameForStorage();
      if (StringUtils.isNotBlank(actorImageFilename)) {
        Path file = null;

        // we prefer reading it from the cache
        if (preferCache) {
          file = ImageCache.getCachedFile(Paths.get(mediaEntity.getPath(), Person.ACTOR_DIR, actorImageFilename));
        }

        // not in the cache - read it from the path
        if (file == null) {
          file = Paths.get(mediaEntity.getPath(), Person.ACTOR_DIR, actorImageFilename);
        }

        // not available in the path and not preferred from the cache..
        // well just try to read it from the cache
        if (!Files.exists(file) && !preferCache) {
          file = ImageCache.getCachedFile(Paths.get(mediaEntity.getPath(), Person.ACTOR_DIR, actorImageFilename));
        }

        if (file != null && Files.exists(file)) {
          imagePath = file;
          return null;
        }
      }

      // no file found, try to cache url (if visible, otherwise load on demand in paintComponent)
      if (isShowing()) {
        Path p = ImageCache.getCachedFile(actor.getThumbUrl());
        if (p != null) {
          imagePath = p;
        }
      }

      return null;
    }

    @Override
    protected void done() {
      if (isCancelled()) {
        return;
      }

      if (imagePath != null) {
        setImagePath(imagePath.toString());
      }
      else {
        clearImage();
      }
    }
  }
}
