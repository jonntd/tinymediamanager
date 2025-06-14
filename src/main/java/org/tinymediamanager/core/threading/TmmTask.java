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
package org.tinymediamanager.core.threading;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.time.StopWatch;
import org.tinymediamanager.core.TmmModuleManager;
import org.tinymediamanager.license.TmmFeature;

/**
 * The class TmmTask. The main class representing tasks in tinyMediaManager. Provides basic task lifecycle management, progress tracking, and listener
 * notification. Subclasses must implement the doInBackground() method to define the actual task logic.
 *
 * @author Manuel Laggner
 */
public abstract class TmmTask implements Runnable, TmmTaskHandle, TmmFeature {
  private final Set<TmmTaskListener> listeners = new CopyOnWriteArraySet<>();
  private final TaskType             type;
  private final long                 uniqueId;

  protected TaskState                state     = TaskState.CREATED;
  protected String                   taskName;
  protected String                   taskDescription;
  protected int                      workUnits;
  protected int                      progressDone;
  protected boolean                  cancel;
  protected Thread                   thread;
  protected StopWatch                stopWatch;

  /**
   * Constructs a new TmmTask.
   *
   * @param taskName
   *          the name of the task
   * @param workUnits
   *          the total number of work units for progress tracking
   * @param type
   *          the type of the task
   */
  protected TmmTask(String taskName, int workUnits, TaskType type) {
    this.taskName = taskName;
    this.workUnits = workUnits;
    this.taskDescription = "";
    this.progressDone = 0;
    this.type = type;
    uniqueId = TmmTaskManager.getInstance().GLOB_THRD_CNT.incrementAndGet();
    this.thread = null;
  }

  /**
   * Returns a string representation of this task.
   *
   * @return a string describing the task
   */
  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  /**
   * Gets the name of the task.
   *
   * @return the task name
   */
  @Override
  public final String getTaskName() {
    return taskName;
  }

  /**
   * Gets the total number of work units for this task.
   *
   * @return the number of work units
   */
  @Override
  public final int getWorkUnits() {
    return workUnits;
  }

  /**
   * Gets the number of work units completed so far.
   *
   * @return the number of completed work units
   */
  @Override
  public final int getProgressDone() {
    return progressDone;
  }

  /**
   * Gets the current description of the task.
   *
   * @return the task description
   */
  @Override
  public final String getTaskDescription() {
    return taskDescription;
  }

  /**
   * Gets the current state of the task.
   *
   * @return the task state
   */
  @Override
  public final TaskState getState() {
    return state;
  }

  /**
   * Sets the name of the task.
   *
   * @param taskName
   *          the new task name
   */
  protected final void setTaskName(String taskName) {
    this.taskName = taskName;
  }

  /**
   * Sets the total number of work units for this task.
   *
   * @param workUnits
   *          the number of work units
   */
  protected final void setWorkUnits(int workUnits) {
    this.workUnits = workUnits;
  }

  /**
   * Sets the number of completed work units.
   *
   * @param progressDone
   *          the number of completed work units
   */
  protected final void setProgressDone(int progressDone) {
    this.progressDone = progressDone;
  }

  /**
   * Sets the description of the task.
   *
   * @param taskDescription
   *          the new task description
   */
  protected final void setTaskDescription(String taskDescription) {
    this.taskDescription = taskDescription;
  }

  /**
   * Adds a listener to this task.
   *
   * @param listener
   *          the listener to add
   */
  public final void addListener(final TmmTaskListener listener) {
    listeners.add(listener);
  }

  /**
   * Sets the state of the task and notifies listeners.
   *
   * @param newState
   *          the new state
   */
  protected final void setState(TaskState newState) {
    this.state = newState;
    // System.out.println("Task '" + getTaskName() + "' set to " + newState);
    informListeners();
  }

  /**
   * Removes a listener from this task.
   *
   * @param listener
   *          the listener to remove
   */
  public final void removeListener(final TmmTaskListener listener) {
    listeners.remove(listener);
  }

  /**
   * Notifies all registered listeners about the current task state.
   */
  protected final void informListeners() {
    // inform the statistics timer that tmm is active
    TmmModuleManager.getInstance().setActive();

    for (TmmTaskListener listener : listeners) {
      listener.processTaskEvent(this);
    }
  }

  /**
   * Runs the task. Handles feature checks, cancellation, and lifecycle management.
   */
  @Override
  public final void run() {
    // is this task active at all?
    if (!isFeatureEnabled()) {
      return;
    }

    // the task has been cancelled before it is being executed
    if (cancel) {
      finish();
      return;
    }

    String name = Thread.currentThread().getName();
    if (!name.contains("-G")) {
      name = name + "-G0";
    }
    name = name.replaceAll("\\-G\\d+", "-G" + uniqueId);
    Thread.currentThread().setName(name);

    start();
    try {
      doInBackground();
    }
    finally {
      finish();
    }
  }

  /**
   * Cancels the task, stops the stopwatch, and sets the state to CANCELLED.
   */
  @Override
  public void cancel() {
    cancel = true;

    if (stopWatch != null && stopWatch.isStarted()) {
      stopWatch.stop();
    }

    setState(TaskState.CANCELLED);
    // thread = null; // nah, cancelled tasks can run till finished, and must not emty-ed immediately
  }

  /**
   * Starts the task, initializes the thread and stopwatch, and sets the state to STARTED.
   */
  protected void start() {
    thread = Thread.currentThread();
    stopWatch = new StopWatch();
    stopWatch.start();

    setState(TaskState.STARTED);
  }

  /**
   * Returns the elapsed running time of this task in milliseconds.
   *
   * @return the running time in milliseconds, or 0 if the stopwatch is not initialized
   */
  protected long getRuntime() {
    long runtime = 0;

    if (stopWatch != null && stopWatch.isStarted()) {
      stopWatch.split();
      runtime = stopWatch.getTime();
      stopWatch.unsplit();
    }

    return runtime;
  }

  /**
   * Publishes the current state with a description and progress value.
   *
   * @param taskDescription
   *          the current task description
   * @param progress
   *          the current progress value
   */
  protected void publishState(String taskDescription, int progress) {
    this.taskDescription = taskDescription;
    this.progressDone = progress;
    informListeners();
  }

  /**
   * Publishes the current state with a progress value.
   *
   * @param progress
   *          the current progress value
   */
  protected void publishState(int progress) {
    this.progressDone = progress;
    informListeners();
  }

  /**
   * Publishes the current state with the current description and progress.
   */
  protected void publishState(String taskDescription) {
    this.taskDescription = taskDescription;
    publishState();
  }

  /**
   * Publishes the current state with the current description and progress.
   */
  protected void publishState() {
    informListeners();
  }

  /**
   * Finishes the task, stops the stopwatch, sets the state to FINISHED (unless FAILED), and clears listeners.
   */
  protected void finish() {
    if (stopWatch != null && stopWatch.isStarted()) {
      stopWatch.stop();
    }

    if (state != TaskState.FAILED) {
      setState(TaskState.FINISHED);
    }

    thread = null;
    listeners.clear(); // avoid leak
  }

  /**
   * Gets the type of this task.
   *
   * @return the task type
   */
  @Override
  public final TaskType getType() {
    return type;
  }

  /**
   * The main logic of the task. Must be implemented by subclasses.
   */
  protected abstract void doInBackground();
}
