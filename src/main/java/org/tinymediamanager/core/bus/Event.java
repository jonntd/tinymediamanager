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

import java.util.Objects;

/**
 * This class represents the event to be published via the EventBus
 *
 * @author Manuel Laggner
 */
public record Event(Object sender, String eventType) {
  public static String TYPE_SAVE = "save";
  public static String TYPE_ADD = "add";
  public static String TYPE_REMOVE = "remove";

  public static Event createSaveEvent(Object sender) {
    return new Event(sender, TYPE_SAVE);
  }

  public static Event createAddEvent(Object sender) {
    return new Event(sender, TYPE_ADD);
  }

  public static Event createRemoveEvent(Object sender) {
    return new Event(sender, TYPE_REMOVE);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Event event = (Event) o;
    return Objects.equals(sender, event.sender) && Objects.equals(eventType, event.eventType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sender, eventType);
  }
}
