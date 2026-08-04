package utils.taskmanager;

import java.util.concurrent.atomic.AtomicLong;

public abstract class Task implements Runnable{

    public String taskName;
    public Boolean result;
    // Cumulative ops completed so far, updated by the running task and readable
    // without blocking on the task's Future (see TaskManager.getTaskProgress()).
    public AtomicLong completedOps = new AtomicLong(0);

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