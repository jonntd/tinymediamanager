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
package org.tinymediamanager.thirdparty.yt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.Globals;
import org.tinymediamanager.addon.YtDlpAddon;
import org.tinymediamanager.core.Settings;
import org.tinymediamanager.core.TmmProperties;
import org.tinymediamanager.core.threading.TmmTask;
import org.tinymediamanager.core.threading.TmmTaskHandle.TaskType;
import org.tinymediamanager.thirdparty.FFmpeg;

/**
 * The {@link YtDlp} class provides functionality for managing and using yt-dlp within tinyMediaManager. It handles downloading trailers from YouTube,
 * managing the yt-dlp executable, and performing automatic updates. This is a utility class with only static methods.
 * 
 * @author Manuel Laggner
 */
public class YtDlp {
  private static final Logger LOGGER = LoggerFactory.getLogger(YtDlp.class);

  /**
   * Private constructor to prevent instantiation.
   *
   * @throws IllegalAccessError
   *           as this is a utility class
   */
  private YtDlp() {
    throw new IllegalAccessError();
  }

  /**
   * Initializes yt-dlp.
   */
  public static void init() {
    YtDlpAddon ytDlpAddon = new YtDlpAddon();
    if (ytDlpAddon.isAvailable()) {
      LOGGER.info("yt-dlp is available at '{}'", ytDlpAddon.getExecutablePath());
    }
    else {
      LOGGER.warn("yt-dlp is not available");
    }
  }

  /**
   * Downloads a trailer from a given URL using yt-dlp. Performs an update check before downloading and uses FFmpeg if available.
   *
   * @param url
   *          the URL of the trailer to download
   * @param height
   *          desired video height in pixels; use 0 for original quality
   * @param trailerFile
   *          target path for the downloaded trailer (without extension)
   * @throws IOException
   *           if there are issues with file operations or yt-dlp execution
   * @throws InterruptedException
   *           if the download process is interrupted
   */
  public static void downloadTrailer(String url, int height, Path trailerFile) throws IOException, InterruptedException {
    selfUpdateIfAvailable();
    executeCommand(createCommandForDownload(url, height, trailerFile));
  }

  /**
   * Constructs the yt-dlp command for downloading a video. Includes configuration for cookies, video quality, and download parameters.
   *
   * @param url
   *          URL of the video to download
   * @param height
   *          desired video height; affects quality selection
   * @param trailerFile
   *          target file path for the download
   * @return list of command arguments for yt-dlp
   * @throws IOException
   *           if yt-dlp executable cannot be found
   */
  private static List<String> createCommandForDownload(String url, int height, Path trailerFile) throws IOException {
    List<String> cmdList = new ArrayList<>();
    cmdList.add(getYtDlpExecutable());

    if (FFmpeg.isAvailable()) {
      cmdList.add("--ffmpeg-location");
      cmdList.add(FFmpeg.getFfmpegExecutable());
    }

    Path cookieFile = Paths.get(Globals.DATA_FOLDER, "yt-dlp-cookies.txt");
    if (Files.exists(cookieFile)) {
      cmdList.add("--cookies");
      cmdList.add(cookieFile.toAbsolutePath().toString());
    }

    cmdList.add("-f");
    cmdList.add("bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4] / bv*+ba/b");

    if (height > 0) {
      cmdList.add("-S");
      cmdList.add("res:" + height);
    }

    cmdList.add("--concurrent-fragments");
    cmdList.add("4");
    cmdList.add("--abort-on-unavailable-fragment");
    cmdList.add("--fragment-retries");
    cmdList.add("99");

    if (Settings.getInstance().isIgnoreSSLProblems()) {
      cmdList.add("--no-check-certificates");
    }

    cmdList.add(url);
    cmdList.add("-o");
    cmdList.add(trailerFile.toAbsolutePath().toString());

    return cmdList;
  }

  /**
   * Executes a command using yt-dlp and captures its output.
   * 
   * @param cmdline
   *          the command line arguments to execute
   * @return the output of the command as a String
   * @throws IOException
   *           if there are issues with executing the command or reading its output
   * @throws InterruptedException
   *           if the command execution is interrupted
   */
  private static String executeCommand(List<String> cmdline) throws IOException, InterruptedException {
    LOGGER.debug("Running command: {}", String.join(" ", cmdline));

    ProcessBuilder pb = new ProcessBuilder(cmdline.toArray(new String[0])).redirectErrorStream(true);
    final Process process = pb.start();

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      new Thread(() -> {
        try {
          IOUtils.copy(process.getInputStream(), outputStream);
        }
        catch (IOException e) {
          LOGGER.debug("could not get output from the process", e);
        }
      }).start();

      int processValue = process.waitFor();
      String response = outputStream.toString(StandardCharsets.UTF_8);
      if (processValue != 0) {
        LOGGER.warn("Error calling yt-dlp - '{}'", response);
        throw new IOException("error running yt-dlp - code '" + processValue + "' / '" + response + "'");
      }
      return response;
    }
    finally {
      process.destroy();
      // Process must be destroyed before closing streams, can't use try-with-resources,
      // as resources are closing when leaving try block, before finally
      IOUtils.close(process.getErrorStream());
    }
  }

  /**
   * Checks if yt-dlp is available and usable.
   *
   * @return true if yt-dlp is available and executable
   */
  public static boolean isAvailable() {
    YtDlpAddon ytDlpAddon = new YtDlpAddon();
    return ytDlpAddon.isAvailable();
  }

  /**
   * Retrieves the path to the yt-dlp executable.
   *
   * @return absolute path to yt-dlp executable
   * @throws IOException
   *           if yt-dlp is not available
   */
  private static String getYtDlpExecutable() throws IOException {
    YtDlpAddon ytDlpAddon = new YtDlpAddon();

    if (!Settings.getInstance().isUseInternalYtDlp() && StringUtils.isNotBlank(Settings.getInstance().getExternalYtDlpPath())
        && Files.isExecutable(Paths.get(Settings.getInstance().getExternalYtDlpPath()))) {
      // external yt-dlp chosen and filled
      return Settings.getInstance().getExternalYtDlpPath();
    }
    else if (ytDlpAddon.isAvailable()) {
      return ytDlpAddon.getExecutablePath();
    }
    else {
      throw new IOException("yt-dlp is not available");
    }
  }

  /**
   * Checks for and performs yt-dlp updates if needed. Updates are limited to once every 2 days to avoid rate limiting. The update process runs as a
   * background task.
   */
  public static void selfUpdateIfAvailable() {
    YtDlpAddon ytDlpAddon = new YtDlpAddon();
    if (ytDlpAddon.isAvailable()) {
      // we need an own logic here - just every 2 days is ok
      // (got blocked executing 3 updates in a row)
      String lastUpdateCheck = TmmProperties.getInstance().getProperty("lastYtDlpUpdateCheck", "0");
      long old = Long.parseLong(lastUpdateCheck);
      long now = new Date().getTime();

      if (now > old + 2 * 1000 * 3600 * 24F) {
        TmmTask upd = new TmmTask("YT-DLP", 1, TaskType.BACKGROUND_TASK) {
          @Override
          protected void doInBackground() {
            setTaskDescription("self updating YT-DLP...");
            TmmProperties.getInstance().putProperty("lastYtDlpUpdateCheck", Long.toString(new Date().getTime()));
            TmmProperties.getInstance().writeProperties();
            try {
              String response = executeCommand(List.of(ytDlpAddon.getExecutablePath(), "--update"));
              LOGGER.debug(response);
            }
            catch (Exception e) {
              LOGGER.warn("Error self-updating yt-dlp - '{}'", e.getMessage());
            }
          }
        };
        // run as blocking task, since we DO come from a DL BG task,
        // where only one can run - so it should be able to update before :)
        upd.run();
        // TmmTaskManager.getInstance().addDownloadTask(upd);
      }
    }
  }
}
