package utils.taskmanager;

import java.util.concurrent.atomic.AtomicBoolean;
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
    // True when this task was cancelled before it ever ran because the work it
    // would have done was already finished by a sibling. Not a failure.
    public volatile boolean skipped = false;
    // Set only for workers that share one document generator with their siblings.
    public TaskGroup group = null;
    // Won by exactly one of: the thread about to execute this task, or skipTask()
    // deciding the task has nothing left to do. Whoever loses stands down.
    // Future.cancel(false) alone is not enough to decide this: a FutureTask stays in
    // state NEW while its runnable executes, so cancelling a *running* task succeeds
    // and makes get() throw immediately while the work is still in flight.
    private final AtomicBoolean claimed = new AtomicBoolean(false);

    /**
     * @return true if the caller now owns this task. A worker that loses the claim must
     *         return without doing any work; skipTask() that loses it must leave the
     *         running task alone.
     */
    public boolean claimForExecution() {
        return this.claimed.compareAndSet(false, true);
    }

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

    /**
     * Called by a worker that finds the shared generator empty, so that siblings still
     * waiting for a thread are not left to be scheduled just to observe the same thing.
     * No-op for tasks that do not share a generator.
     */
    protected void notifyWorkExhausted() {
        TaskGroup taskGroup = this.group;
        if (taskGroup != null) {
            taskGroup.markWorkExhausted();
        }
    }

    @Override
    public abstract void run() throws RuntimeException;

}