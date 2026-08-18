package utils.taskmanager;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class TaskManager {
    private int workers;
    private RankedThreadPoolExecutor poolExecutor;
    private ConcurrentHashMap<String, Future> tasks = new ConcurrentHashMap<String, Future>();

    public TaskManager(int workers) {
        this.workers = workers;
        // Same fixed pool size as before; only the dequeue order changes, so that a
        // load's Nth worker never outranks another load's 1st. See
        // RankedThreadPoolExecutor for why FIFO starves later loads.
        this.poolExecutor = new RankedThreadPoolExecutor(this.workers);
    }

    public int getWorkerCount() {
        return this.workers;
    }

    // Pool visibility for callers/diagnostics: how many tasks are executing right now
    // versus waiting for a thread.
    public int getActiveTaskCount() {
        return this.poolExecutor.getActiveCount();
    }

    public int getQueuedTaskCount() {
        return this.poolExecutor.getQueue().size();
    }

    public void shutdown() {
        this.poolExecutor.shutdownNow();
    }

    public void submit(Task task) {
        Future future = this.poolExecutor.submit(task);
        this.tasks.put(task.taskName, future);
    }

    public void getAllTaskResult() {
        for (String taskName : this.tasks.keySet()) {
            try {
                this.tasks.get(taskName).get();
                this.tasks.remove(taskName);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean check_if_task_with_name_exists(String task_name) {
        for (String taskName : this.tasks.keySet()) {
            if (task_name.equals(taskName)) {
                return true;
            }
        }
        return false;
    }

    public boolean getTaskResult(Task task) {
        Future future = this.tasks.get(task.taskName);
        if (future == null) {
            System.out.println("Task '" + task.taskName + "' not found in task manager. "
                    + "It may not have been submitted or was already consumed.");
            task.result = false;
            return false;
        }
        try {
            future.get();
            this.tasks.remove(task.taskName);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            this.tasks.remove(task.taskName);
        }
        return task.result;
    }

    // Non-blocking progress read: does not touch the Future, so it is safe to
    // call while another thread is blocked inside getTaskResult()'s future.get().
    public long getTaskProgress(Task task) {
        return task.completedOps.get();
    }

    public boolean isTaskRunning(Task task) {
        Future future = this.tasks.get(task.taskName);
        return future != null && !future.isDone();
    }

    public void abortTask(Task task) {
        Future future = this.tasks.get(task.taskName);
        if (future != null) {
            future.cancel(true);
        } else {
            System.out.println("Task '" + task.taskName + "' not found during abort. "
                    + "It may not have been submitted or was already consumed.");
        }
    }

    public void abortAllTasks() {
        for (Entry<String, Future> task : this.tasks.entrySet()) {
            this.tasks.get(task.getKey()).cancel(true);
        }
    }
}
