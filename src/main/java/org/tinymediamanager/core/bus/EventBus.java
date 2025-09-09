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

package org.tinymediamanager.core.bus;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The class {@link EventBus} is used to deliver messages/events inside the whole application
 *
 * @author Manuel Laggner
 */
public class EventBus {
  public static String                           TOPIC_MOVIES     = "movies";
  public static String                           TOPIC_MOVIE_SETS = "movieSets";
  public static String                           TOPIC_TV_SHOWS   = "tvShows";

  private static final EventBus                  INSTANCE         = new EventBus();

  private final ReentrantReadWriteLock           readWriteLock;
  private final Map<String, Set<IEventListener>> listeners;
  private final Map<String, Set<Event>>          events;
  private final ScheduledExecutorService         executor;

  private EventBus() {
    readWriteLock = new ReentrantReadWriteLock();

    listeners = new HashMap<>();
    events = new HashMap<>();

    executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "event-bus"));
  }

  /**
   * Register a new listener for the given topic
   * 
   * @param topic
   *          the topic to register the listener for
   * @param listener
   *          the {@link IEventListener} to add for this topic
   */
  public static void registerListener(String topic, IEventListener listener) {
    try {
      INSTANCE.readWriteLock.writeLock().lock();
      Set<IEventListener> listeners = INSTANCE.listeners.computeIfAbsent(topic, k -> new HashSet<>());
      listeners.add(listener);
    }
    finally {
      INSTANCE.readWriteLock.writeLock().unlock();
    }
  }

  /**
   * Remove the given {@link IEventListener} from the topic
   * 
   * @param topic
   *          the topic to remove the {@link IEventListener} from
   * @param listener
   *          the {@link IEventListener} to be removed
   */
  public synchronized static void removeListener(String topic, IEventListener listener) {
    try {
      INSTANCE.readWriteLock.writeLock().lock();
      Set<IEventListener> listeners = INSTANCE.listeners.get(topic);
      if (listeners != null) {
        listeners.remove(listener);
      }
    }
    finally {
      INSTANCE.readWriteLock.writeLock().unlock();
    }
  }

  /**
   * Send an {@link Event} for the chosen topic to all listeners
   * 
   * @param topic
   *          the topic to send the event for
   * @param event
   *          the {@link Event} to be sent
   */
  public static void publishEvent(String topic, Event event) {
    try {
      INSTANCE.readWriteLock.writeLock().lock();
      INSTANCE.events.computeIfAbsent(topic, k -> new LinkedHashSet<>()).add(event);

      // publish all open events to the listeners
      Runnable runnable = () -> {
        Set<Event> events = new LinkedHashSet<>();
        Set<IEventListener> listeners = new HashSet<>();

        try {
          INSTANCE.readWriteLock.writeLock().lock();
          Set<Event> eventsForTopic = Objects.requireNonNullElse(INSTANCE.events.get(topic), Collections.emptySet());
          if (!eventsForTopic.isEmpty()) {
            events.addAll(eventsForTopic);
            eventsForTopic.clear();
          }
          listeners.addAll(Objects.requireNonNullElse(INSTANCE.listeners.get(topic), Collections.emptySet()));
        }
        finally {
          INSTANCE.readWriteLock.writeLock().unlock();
        }

        for (Event e : events) {
          for (IEventListener listener : listeners) {
            listener.processEvent(e);
          }
        }
      };

      // fire the event after 250 ms to collect some subsequent events for the same sender
      INSTANCE.executor.schedule(runnable, 250, TimeUnit.MILLISECONDS);
    }
    finally {
      INSTANCE.readWriteLock.writeLock().unlock();
    }
  }
}
