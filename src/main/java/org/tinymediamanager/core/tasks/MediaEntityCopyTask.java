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

package org.tinymediamanager.core.tasks;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.Message;
import org.tinymediamanager.core.MessageManager;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.core.entities.MediaEntity;
import org.tinymediamanager.core.entities.MediaFile;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.scraper.util.ListUtils;

/**
 * Task for copying media entities and their associated files to a specified directory.
 * <p>
 * This task processes a list of {@link MediaEntity} objects, copying all their media files to the target directory. It supports progress reporting,
 * cancellation, and handles both single files and directory structures (e.g., DVD/BR folders).
 * 
 * @author Myron Boyle, Manuel Laggner
 */
public class MediaEntityCopyTask extends TmmTask {
  private static final Logger               LOGGER     = LoggerFactory.getLogger(MediaEntityCopyTask.class);
  private static final long                 CHUNK_SIZE = 1024 * 1024;                                       // 1 MB

  private final List<? extends MediaEntity> entities;
  private final Path                        directory;
  private final boolean                     fileSizeBase10;
  private final String                      messagePrefix;

  private long                              totalSize;
  private long                              timestamp;
  private long                              bytesDone;
  private MediaEntity                       currentEntity;
  private int                               currentEntityIndex;

  /**
   * Constructs a new MediaEntityCopyTask.
   *
   * @param entities
   *          the list of media entities to copy
   * @param directory
   *          the target directory to copy the files to
   */
  public MediaEntityCopyTask(List<? extends MediaEntity> entities, Path directory) {
    super(TmmResourceBundle.getString("task.copying.title"), 100, TaskType.BACKGROUND_TASK);
    this.entities = new ArrayList<>(entities);
    this.directory = directory;
    this.fileSizeBase10 = Settings.getInstance().isFileSizeBase10();
    this.messagePrefix = TmmResourceBundle.getString("task.copying");
  }

  /**
   * Calculates the total size of all files to be copied for progress reporting.
   *
   * @return the total size in bytes
   */
  private long calculateSize() {
    long size = 0;

    for (MediaEntity me : entities) {
      size += me.getTotalFilesize();
    }

    return size;
  }

  /**
   * Executes the copy operation in the background. Handles both single files and directory structures, supports cancellation and error reporting.
   */
  @Override
  protected void doInBackground() {
    if (ListUtils.isEmpty(entities)) {
      return;
    }

    // calculate the total size of all media entities
    totalSize = calculateSize();
    timestamp = System.nanoTime();

    if (totalSize == 0) {
      setWorkUnits(0);
    }

    try {
      // process all media entities
      for (MediaEntity me : entities) {
        currentEntity = me;
        currentEntityIndex++;

        Path newFolder = directory.resolve(Paths.get(me.getDataSource()).relativize(me.getPathNIO()));
        LOGGER.info("Copying files of '{}' to '{}'", me.getTitle(), newFolder);
        Files.createDirectories(newFolder);

        // copy all media files of the entity
        for (MediaFile mf : me.getMediaFiles()) {
          if (cancel) {
            break;
          }

          Path mfPath = mf.getFileAsPath();

          if (Files.isDirectory(mfPath)) {
            Path mePath = me.getPathNIO();

            // fake media file (DVD/BR disc folders) - copy the whole directory
            try (Stream<Path> stream = Files.walk(mfPath)) {
              stream.forEach(path -> {
                if (cancel) {
                  // to step out of the stream
                  Thread.currentThread().interrupt();
                }

                Path relative = mePath.relativize(path);
                Path targetPath = newFolder.resolve(relative);

                try {
                  if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath);
                  }
                  else {
                    Files.createDirectories(targetPath.getParent());
                    copy(path, targetPath);
                  }
                }
                catch (IOException e) {
                  // cancel the stream if an error occurs
                  throw new RuntimeException(e);
                }
              });
            }
          }
          else {
            // single media file - copy the file
            Path rel = me.getPathNIO().relativize(mfPath);
            if (rel.getNameCount() > 1) {
              Files.createDirectories(newFolder.resolve(rel.getParent()));
            }
            copy(mfPath, newFolder.resolve(rel));
          }
        }

        // cancel the task if requested
        if (cancel) {
          break;
        }
      }
    }
    catch (InterruptedIOException e) {
      // do not swallow these Exceptions
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LOGGER.error("Could not copy files - '{}'", e.getMessage());
      MessageManager.getInstance()
          .pushMessage(
              new Message(Message.MessageLevel.ERROR, "MediaEntityCopyTask", "message.threadcrashed", new String[] { ":", e.getLocalizedMessage() }));
      setState(TaskState.FAILED);
    }
  }

  /**
   * Copies a file from source to target using a file channel, supporting chunked transfer and cancellation.
   *
   * @param source
   *          the source file path
   * @param target
   *          the target file path
   * @throws IOException
   *           if an I/O error occurs
   */
  private void copy(Path source, Path target) throws IOException {
    long totalSize = Files.size(source);
    long position = 0;

    try (FileChannel inChannel = FileChannel.open(source, StandardOpenOption.READ);
        FileChannel outChannel = FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {

      while (position < totalSize && !cancel) {
        long bytesToTransfer = Math.min(CHUNK_SIZE, totalSize - position);
        long transferred = inChannel.transferTo(position, bytesToTransfer, outChannel);
        position += transferred;

        bytesDone += transferred;
        publishStateThrottled();
      }
    }

    // clean up if the task was cancelled
    if (cancel) {
      Files.deleteIfExists(target);
    }
  }

  /**
   * Publishes the current progress state, throttled to reduce UI updates.
   */
  private void publishStateThrottled() {
    // we push the progress only once per 250ms (to use less resources for updating the UI)
    long timestamp2 = System.nanoTime();
    if (timestamp2 - timestamp > 250000000) {
      int progress = 0;

      String taskDescription = messagePrefix + " '" + currentEntity.getTitle() + "' (" + currentEntityIndex + " / " + entities.size() + " - ";
      if (totalSize > 0) {
        taskDescription += formatBytesForOutput(bytesDone) + " / " + formatBytesForOutput(totalSize) + ")";
        progress = (int) (bytesDone * 100 / totalSize);
      }
      else {
        taskDescription += formatBytesForOutput(bytesDone) + ")";
      }

      publishState(taskDescription, progress);
    }
  }

  /**
   * Cancels the copy task and interrupts the current thread.
   */
  @Override
  public void cancel() {
    super.cancel();
    Thread.currentThread().interrupt(); // ensure the thread is interrupted
  }

  /**
   * Formats a byte value for output, using either base 10 or base 2 units.
   *
   * @param bytes
   *          the number of bytes
   * @return a human-readable string representation
   */
  private String formatBytesForOutput(long bytes) {
    double factor = fileSizeBase10 ? 1000d : 1024d;

    if (bytes < factor) {
      return String.format("%d B", bytes);
    }
    else if (bytes < factor * factor) {
      return String.format("%.2f kB", (double) bytes / factor);
    }
    else if (bytes < factor * factor * factor) {
      return String.format("%.2f M", (double) bytes / (factor * factor));
    }
    else if (bytes < factor * factor * factor * factor) {
      return String.format("%.2f G", (double) bytes / (factor * factor * factor));
    }

    // fallback
    return String.format("%.2f M", (double) bytes / (factor * factor));
  }
}
