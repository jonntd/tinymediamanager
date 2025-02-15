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

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.tinymediamanager.core.threading.TmmTask;

/**
 * The class {@link QueueTask} can be used to queue different tasks in a specified order
 * 
 * @author Manuel Laggner
 */
public class QueueTask extends TmmTask {
  private final Queue<TmmTask> taskQueue  = new LinkedBlockingQueue<>();

  private TmmTask              activeTask = null;

  public QueueTask(String taskName) {
    super(taskName, 0, TaskType.BACKGROUND_TASK);
  }

  public void addTask(TmmTask task) {
    taskQueue.add(task);
  }

  @Override
  protected void doInBackground() {
    setWorkUnits(taskQueue.size() + 1);
    int counter = 1; // start optically with a little progress - looks better to the user ;)

    while (true) {
      if (cancel) {
        break;
      }

      activeTask = taskQueue.poll();
      if (activeTask == null) {
        break;
      }

      setTaskDescription(activeTask.getTaskName());

      activeTask.run();
      setProgressDone(++counter);
    }
  }

  @Override
  public void cancel() {
    super.cancel();

    // copy to avoid the 1 in a million chance to get the activeTask set to null while we try to accedd
    TmmTask task = activeTask;
    if (task != null) {
      task.cancel();
    }
  }
}
