package br.com.balancecontrol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs tasks in parallel, all released at the same instant, and collects outcomes. */
public final class Concurrently {

    private Concurrently() {
    }

    /** Outcome of one task: its result or the exception it threw. */
    public record Outcome<T>(T value, Throwable error) {
        public boolean ok() {
            return error == null;
        }
    }

    public static <T> List<Outcome<T>> run(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> f : futures) {
                try {
                    outcomes.add(new Outcome<>(f.get(60, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.ExecutionException e) {
                    outcomes.add(new Outcome<>(null, e.getCause()));
                } catch (java.util.concurrent.TimeoutException e) {
                    throw new AssertionError("Task did not finish in 60s (deadlock?)", e);
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    public static <T> List<Callable<T>> times(int n, Callable<T> task) {
        List<Callable<T>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tasks.add(task);
        }
        return tasks;
    }
}
