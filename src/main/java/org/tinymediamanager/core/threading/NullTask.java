package org.tinymediamanager.core.threading;

/**
 * This class creates a dummy tasks with X workunits, where every unit sleeps for a sec <br>
 * useful for debugging threads - please keep
 * 
 * @author Myron Boyle
 *
 */
public class NullTask extends TmmTask {
  private final int sleep;

  public NullTask(String taskName, int workUnits, int sleep, TaskType type) {
    super(taskName, workUnits, type);
    this.sleep = sleep;
  }

  @Override
  protected void doInBackground() {
    for (int i = 0; i < workUnits; i++) {
      ThreadUtils.sleep(sleep);
      setProgressDone(i);
      publishState();
      if (cancel) {
        break;
      }
    }
  }
}
