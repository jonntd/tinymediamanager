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

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * The {@code TmmUILogAppender} class is a custom Logback appender that captures log messages from tinyMediaManager and forwards them to a custom
 * Swing-based UI component for display.
 * <p>
 * This appender supports filtering logs by level and formatting using a custom pattern. Messages are buffered and only a fixed number (100 by
 * default) are stored in memory.
 * </p>
 * <p>
 * It is designed to integrate with a {@link TmmLogTextPane} for visual log output.
 * </p>
 *
 * @author Manuel Laggner
 */
public class TmmUILogAppender extends AppenderBase<ILoggingEvent> {

  private static final int      MAX_LOG_SIZE = 100;

  private final Deque<LogEntry> logBuffer    = new LinkedList<>();
  private final List<Level>     levels;

  private TmmLogTextPane        textPane;

  /**
   * Creates a new {@code TmmUILogAppender} for a single log level.
   *
   * @param level
   *          the log level to capture (e.g., Level.INFO)
   */
  public TmmUILogAppender(Level level) {
    this(List.of(level));
  }

  /**
   * Creates a new {@code TmmUILogAppender} for multiple log levels.
   *
   * @param levels
   *          the list of log levels to capture
   */
  public TmmUILogAppender(List<Level> levels) {
    super();
    this.levels = new ArrayList<>(levels);
  }

  /**
   * Sets the UI text pane to which logs should be output. If a buffer of previous logs exists, they will be appended to the pane immediately.
   *
   * @param textPane
   *          the {@link TmmLogTextPane} to output logs
   */
  public void setTextPane(TmmLogTextPane textPane) {
    this.textPane = textPane;

    if (this.textPane != null) {
      synchronized (logBuffer) {
        this.textPane.setText("");
        for (LogEntry logEntry : logBuffer) {
          this.textPane.appendFormatted(logEntry);
        }
      }
    }
  }

  /**
   * Receives a logging event and processes it. Only logs originating from the {@code org.tinymediamanager} package and matching the configured levels
   * are processed.
   *
   * @param eventObject
   *          the logging event to append
   */
  @Override
  protected void append(ILoggingEvent eventObject) {
    if (!eventObject.getLoggerName().startsWith("org.tinymediamanager")) {
      // ignore all non tmm logs
      return;
    }
    if (!levels.contains(eventObject.getLevel())) {
      // ignore anything that's not in our levels list
      return;
    }

    LogEntry logEntry = new LogEntry(new SimpleDateFormat("HH:mm:ss").format(new Date(eventObject.getTimeStamp())), eventObject.getLevel().toString(),
        eventObject.getFormattedMessage());

    synchronized (logBuffer) {
      if (logBuffer.size() >= MAX_LOG_SIZE) {
        logBuffer.removeFirst();
      }
      logBuffer.addLast(logEntry);
    }

    if (textPane != null) {
      SwingUtilities.invokeLater(() -> textPane.appendFormatted(logEntry));
    }
  }

  /**
   * A lightweight record that holds a single log entry including its timestamp, log level, and message content.
   *
   * @param timestamp
   *          the time of the log (formatted as HH:mm:ss)
   * @param level
   *          the log level (e.g., INFO, WARN)
   * @param message
   *          the message content
   */
  private record LogEntry(String timestamp, String level, String message) {
  }

  /**
   * {@code TmmLogTextPane} is a custom {@link JTextPane} component used for displaying log messages in a styled format. It color-codes messages based
   * on log level and includes timestamps.
   * <p>
   * Supported log levels include INFO, WARN, and ERROR, with each level assigned a specific color/style. Messages are displayed in a monospace font
   * for readability.
   * </p>
   */
  public static class TmmLogTextPane extends JTextPane {

    private final StyledDocument     doc;
    private final Style              defaultStyle;
    private final Style              dateStyle;
    private final Map<String, Style> levelStyles = new HashMap<>();

    /**
     * Constructs a new {@code TmmLogTextPane}, initializing the text styles for default messages, timestamps, and log levels (INFO, WARN, ERROR). The
     * pane is set to be non-editable.
     */
    public TmmLogTextPane() {
      this.doc = getStyledDocument();
      setEditable(false);

      // Default (black)
      defaultStyle = doc.addStyle("DEFAULT", null);
      StyleConstants.setForeground(defaultStyle, getForeground());

      // Date style
      dateStyle = doc.addStyle("DATE", null);
      StyleConstants.setFontFamily(dateStyle, "Courier New");

      // Level styles
      levelStyles.put("INFO", dateStyle);
      levelStyles.put("WARN", createStyle(new Color(255, 125, 0)));
      levelStyles.put("ERROR", createStyle(Color.RED));
    }

    /**
     * Creates a new {@link Style} object with the specified foreground color. The created style uses a bold, monospace ("Courier New") font.
     *
     * @param color
     *          the foreground color for the style
     * @return a new {@link Style} configured with the given color
     */
    private Style createStyle(Color color) {
      Style style = doc.addStyle(color.toString(), null);
      StyleConstants.setForeground(style, color);
      StyleConstants.setBold(style, true);
      StyleConstants.setFontFamily(style, "Courier New");
      return style;
    }

    /**
     * Appends a log entry to the text pane in a styled format. Includes the timestamp (in monospace), the log level (color-coded), and the log
     * message.
     *
     * @param logEntry
     *          the log entry to be appended to the pane
     */
    private void appendFormatted(LogEntry logEntry) {
      try {
        doc.insertString(doc.getLength(), "[" + logEntry.timestamp + "] ", dateStyle);
        doc.insertString(doc.getLength(), String.format("%-5s ", logEntry.level), levelStyles.getOrDefault(logEntry.level, defaultStyle));
        doc.insertString(doc.getLength(), logEntry.message + "\n", defaultStyle);
        setCaretPosition(doc.getLength());
      }
      catch (BadLocationException ignored) {
        // ignore
      }
    }
  }
}
