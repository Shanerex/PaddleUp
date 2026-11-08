package com.paddleup.auction.util;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Executes tasks one at a time, in submission order, on threads borrowed from a shared pool. It
 * gives one auction its single writer without a thread of its own: no lock is held between tasks,
 * and arrival order is queue order.
 */
public final class SerialExecutor {

  private final Executor pool;
  private final Queue<Step> queue = new ArrayDeque<>();
  private boolean running;

  /** One queued unit of work. */
  private interface Step {
    void apply();
  }

  public SerialExecutor(Executor pool) {
    this.pool = pool;
  }

  /** Queues {@code task}; the future completes with its result or its exception. */
  public <T> CompletableFuture<T> submit(Callable<T> task) {
    var future = new CompletableFuture<T>();
    enqueue(
        () -> {
          try {
            future.complete(task.call());
          } catch (Throwable t) {
            future.completeExceptionally(t);
          }
        });
    return future;
  }

  private synchronized void enqueue(Step task) {
    queue.add(task);
    if (!running) {
      running = true;
      pool.execute(this::drain);
    }
  }

  private void drain() {
    while (true) {
      Step next;
      synchronized (this) {
        next = queue.poll();
        if (next == null) {
          running = false;
          return;
        }
      }
      next.apply();
    }
  }
}
