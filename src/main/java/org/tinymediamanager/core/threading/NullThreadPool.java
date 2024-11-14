package org.tinymediamanager.core.threading;

/**
 * This class creates a threadpool of X parallel workers, and adds Y tasks to it (sleeping each for 10 secs)<br>
 * useful for debugging threads - please keep
 * 
 * @author Myron Boyle
 *
 */
public class NullThreadPool extends TmmThreadPool {
  private final int poolsize;
  private final int workunits;
  private final int sleep;

  public NullThreadPool(String taskName, int poolsize, int workunits, int sleep) {
    super(taskName);
    this.poolsize = poolsize;
    this.workunits = workunits;
    this.sleep = sleep;
  }

  @Override
  public void callback(Object obj) {
    publishState(progressDone);
  }

  @Override
  protected void doInBackground() {
    initThreadPool(poolsize, taskName);
    setTaskName(taskName);
    publishState();

    for (int i = 0; i < workunits; i++) {
      submitTask(new Runnable() {
        public void run() {
          ThreadUtils.sleep(sleep);
        }
      });
      ThreadUtils.sleep(50); // delay task creation a bit ;)
    }
    publishState();
    waitForCompletionOrCancel();
  }
}
