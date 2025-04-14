/*
 * Copyright 2012 - 2024 Manuel Laggner
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

package org.tinymediamanager.core.bus;

import java.util.EventListener;

import org.tinymediamanager.core.entities.MediaEntity;

import ca.odell.glazedlists.ObservableElementChangeHandler;
import ca.odell.glazedlists.ObservableElementList;

/**
 * The class {@link EventBusConnector} is used to connect our {@link EventBus} to an {@link ObservableElementList} from Glazedlists
 * 
 * @param <E>
 *          the {@link MediaEntity} type to bind
 * 
 * @author Manuel Laggner
 */
public class EventBusConnector<E extends MediaEntity> implements IEventListener, ObservableElementList.Connector<E>, EventListener {

  private ObservableElementChangeHandler<? extends E> list;

  public EventBusConnector(String topic) {
    EventBus.registerListener(topic, this);
  }

  @Override
  public void processEvent(Event event) {
    list.elementChanged(event.sender());
  }

  @Override
  public EventListener installListener(E element) {
    return this;
  }

  @Override
  public void uninstallListener(E element, EventListener listener) {
    // do nothing
  }

  @Override
  public void setObservableElementList(ObservableElementChangeHandler<? extends E> list) {
    this.list = list;
  }
}
