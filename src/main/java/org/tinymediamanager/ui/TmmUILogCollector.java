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
package org.tinymediamanager.ui;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * The {@code TmmUILogCollector} is a singleton utility class responsible for initializing and managing a {@link TmmUILogAppender} instance that
 * captures log messages for the tinyMediaManager UI.
 * <p>
 * This class attaches a custom appender to the root SLF4J/Logback logger at runtime to intercept logs of levels INFO, WARN, and ERROR, format them,
 * and forward them to a UI component.
 * </p>
 * <p>
 * The collector is initialized statically and can be accessed through the {@link #instance} field.
 * </p>
 *
 * @author Manuel Laggner
 */
public class TmmUILogCollector {
  private static final TmmUILogCollector instance = new TmmUILogCollector();

  private final TmmUILogAppender         uiLogAppender;

  /**
   * Initializes the singleton instance of {@code TmmUILogCollector}.
   * <p>
   * Calling this method ensures the collector is loaded and the appender is registered.
   * </p>
   */
  public static void init() {
    // just to trigger class loading and initializing
  }

  /**
   * Returns the singleton instance of {@link TmmUILogCollector}.
   * <p>
   * This method provides global access to the {@code TmmUILogCollector}, ensuring that only one instance is used throughout the application.
   * </p>
   *
   * @return the singleton {@code TmmUILogCollector} instance
   */
  public static TmmUILogCollector getInstance() {
    return instance;
  }

  /**
   * Constructs a new {@code TmmUILogCollector}, creates a {@link TmmUILogAppender} for UI logging, and attaches it to the root Logback logger.
   * <p>
   * This constructor is private to enforce singleton behavior.
   * </p>
   */
  private TmmUILogCollector() {
    Logger rootLogger = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);

    // INFO logs - progress info
    // create a new TmmUILogAppender - so we don't need to put it in the logback.xml:
    uiLogAppender = new TmmUILogAppender(List.of(Level.ERROR, Level.WARN, Level.INFO));
    uiLogAppender.setContext(rootLogger.getLoggerContext());
    uiLogAppender.start();

    rootLogger.addAppender(uiLogAppender);
  }

  /**
   * Returns the configured {@link TmmUILogAppender} instance used to capture and forward logs to the UI.
   *
   * @return the UI log appender
   */
  public TmmUILogAppender getUiLogAppender() {
    return uiLogAppender;
  }
}
