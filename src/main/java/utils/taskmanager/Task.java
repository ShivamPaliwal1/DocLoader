package utils.taskmanager;

import java.util.concurrent.atomic.AtomicLong;

public abstract class Task implements Runnable{

    public String taskName;
    public Boolean result;
    // Cumulative ops completed so far, updated by the running task and readable
    // without blocking on the task's Future (see TaskManager.getTaskProgress()).
    public AtomicLong completedOps = new AtomicLong(0);
    // Position of this task within the group of workers created for one load
    // request (0 for the first worker, 1 for the second, ...). TaskManager
    // schedules low indexes first, so every load gets its first worker running
    // before any load gets its second. Left at 0 for standalone tasks, which
    // makes their scheduling plain FIFO exactly as before.
    public int workerIndex = 0;

    public Task(String taskName) {
        super();
        this.taskName = taskName;
        this.result = false;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public abstract void run() throws RuntimeException;

}