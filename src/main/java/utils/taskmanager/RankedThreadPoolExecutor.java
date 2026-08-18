package utils.taskmanager;

import java.util.Comparator;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-size pool that dequeues by {@link Task#workerIndex} instead of FIFO.
 *
 * A load request creates N workers (Task_x_0 .. Task_x_N-1) that all pull from one
 * shared generator, and the caller waits for every one of them. With a FIFO queue and
 * more requests than threads, the whole first request runs before the second request's
 * first worker gets a thread, so callers further back observe zero progress for as long
 * as it takes the requests ahead of them to finish - which is what trips their stall
 * detectors even though the pool is perfectly healthy.
 *
 * Ordering by worker index instead gives: every load gets its 1st worker before any load
 * gets its 2nd. Concurrency per load then becomes whatever the pool can spare
 * (pool_size / active_loads), capped by the N the caller asked for, with no caller-side
 * arithmetic. Ties are broken by submission order, so tasks at the same index keep
 * running FIFO and nothing is starved.
 *
 * Tasks that never set workerIndex all sit at index 0 and therefore stay pure FIFO,
 * making this a no-op for every existing single-load caller.
 */
public class RankedThreadPoolExecutor extends ThreadPoolExecutor {

    private static final AtomicLong SUBMISSION_SEQ = new AtomicLong(0);

    public RankedThreadPoolExecutor(int workers) {
        super(workers, workers, 0L, TimeUnit.MILLISECONDS,
              new PriorityBlockingQueue<Runnable>(64, new RankComparator()));
    }

    // submit() wraps the Runnable in a FutureTask before it reaches the queue, so the
    // rank has to be captured here or the comparator would have nothing to read.
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new RankedFutureTask<>(runnable, value, rankOf(runnable),
                                      SUBMISSION_SEQ.incrementAndGet());
    }

    private static int rankOf(Runnable runnable) {
        if (runnable instanceof RankedFutureTask) {
            return ((RankedFutureTask<?>) runnable).rank;
        }
        if (runnable instanceof Task) {
            return ((Task) runnable).workerIndex;
        }
        return 0;
    }

    private static long seqOf(Runnable runnable) {
        if (runnable instanceof RankedFutureTask) {
            return ((RankedFutureTask<?>) runnable).seq;
        }
        // Anything submitted through execute() rather than submit() is unranked;
        // treat it as index 0 / earliest so it keeps FIFO semantics.
        return 0;
    }

    static class RankedFutureTask<T> extends FutureTask<T> {
        final int rank;
        final long seq;

        RankedFutureTask(Runnable runnable, T result, int rank, long seq) {
            super(runnable, result);
            this.rank = rank;
            this.seq = seq;
        }
    }

    static class RankComparator implements Comparator<Runnable> {
        @Override
        public int compare(Runnable left, Runnable right) {
            int byRank = Integer.compare(rankOf(left), rankOf(right));
            if (byRank != 0) {
                return byRank;
            }
            return Long.compare(seqOf(left), seqOf(right));
        }
    }
}
