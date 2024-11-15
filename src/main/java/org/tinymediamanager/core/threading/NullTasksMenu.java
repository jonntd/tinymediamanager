package org.tinymediamanager.core.threading;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.tinymediamanager.core.threading.TmmTaskHandle.TaskType;
import org.tinymediamanager.ui.IconManager;

public class NullTasksMenu {

  public static JMenu createTaskManagerTestMenu() {
    JMenu menu = new JMenu("Tasks Debug");
    menu.setIcon(IconManager.FEEDBACK);

    JMenuItem i = new JMenuItem("ThreadPool with 1T 100WU");
    i.addActionListener(new AddTask(i.getText(), 1, 100, 1000, "pool"));
    menu.add(i);
    i = new JMenuItem("ThreadPool with 5T 100WU");
    i.addActionListener(new AddTask(i.getText(), 5, 100, 1000, "pool"));
    menu.add(i);
    menu.addSeparator();

    i = new JMenuItem("1 Main Task á 10WU");
    i.addActionListener(new AddTask(i.getText(), 1, 10, 1000, "main"));
    menu.add(i);
    i = new JMenuItem("1 Download Task á 10WU");
    i.addActionListener(new AddTask(i.getText(), 1, 10, 1000, "dl"));
    menu.add(i);
    i = new JMenuItem("1 ImageCache Task á 10WU");
    i.addActionListener(new AddTask(i.getText(), 1, 10, 1000, "img"));
    menu.add(i);
    i = new JMenuItem("1 Unknown Task á 10WU");
    i.addActionListener(new AddTask(i.getText(), 1, 10, 1000, "anon"));
    menu.add(i);
    menu.addSeparator();

    i = new JMenuItem("10 Main Tasks á 10WU");
    i.addActionListener(new AddTask(i.getText(), 10, 10, 1000, "main"));
    menu.add(i);
    i = new JMenuItem("10 Download Tasks á 10WU");
    i.addActionListener(new AddTask(i.getText(), 10, 10, 1000, "dl"));
    menu.add(i);
    i = new JMenuItem("10 ImageCache Tasks á 10WU");
    i.addActionListener(new AddTask(i.getText(), 10, 10, 1000, "img"));
    menu.add(i);
    i = new JMenuItem("10 Unknown Tasks á 10WU");
    i.addActionListener(new AddTask(i.getText(), 10, 10, 1000, "anon"));
    menu.add(i);
    return menu;
  }

  private static class AddTask implements ActionListener {
    private final int    sleep;
    private final int    amount;
    private final int    workunits;
    private final String type;
    private final String name;

    public AddTask(String name, int amount, int workunits, int sleepForEachWU, String type) {
      this.name = name;
      this.amount = amount;
      this.workunits = workunits;
      this.type = type;
      this.sleep = sleepForEachWU;
    }

    public void actionPerformed(ActionEvent e) {
      if (type.equals("pool")) {
        long rnd = Math.round(Math.random() * 100 + 1);
        TmmTaskManager.getInstance().addMainTask(new NullThreadPool(name + "-" + rnd, amount, workunits, sleep));
      }
      else {
        for (int i = 0; i < amount; i++) {
          switch (type) {
            case "main": {
              TmmTaskManager.getInstance().addUnnamedTask(new NullTask(name + "-" + i, workunits, sleep, TaskType.MAIN_TASK));
              break;
            }

            case "dl": {
              TmmTaskManager.getInstance().addDownloadTask(new NullTask(name + "-" + i, workunits, sleep, TaskType.BACKGROUND_TASK));
              break;
            }

            case "img": {
              TmmTaskManager.getInstance().addImageCacheTask(new NullTask(name + "-" + i, workunits, sleep, TaskType.BACKGROUND_TASK));
              break;
            }

            case "anon": {
              TmmTaskManager.getInstance().addUnnamedTask(new NullTask(name + "-" + i, workunits, sleep, TaskType.BACKGROUND_TASK));
              break;
            }

            default:
              break;
          }
          ThreadUtils.sleep(100); // delay task creation a bit ;)
        }
      } // end else
    }
  }
}
