package com.fountainframework.core.server;

import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Lock-free backpressure coordinator between virtual threads (write side)
 * and the Netty I/O thread (read side).
 * <p>
 * Write side: virtual threads call {@link #awaitWritable(ChannelHandlerContext)}
 * before each chunk write. If the channel is not writable, the thread CAS-sets
 * the backpressure flag and self-parks via {@link LockSupport#park(Object)}.
 * <p>
 * Read side: the Netty I/O thread calls {@link #isBackpressure()} to decide
 * whether to queue incoming requests. Queued tasks are drained via
 * {@link #drainTo(ExecutorService)} when writability is restored.
 * <p>
 * Coordination: is called from
 * {@code channelWritabilityChanged} — it clears the flag, unparks all waiting
 * writers, drains the queue, and restores autoRead.
 */
final class BackpressureController {

    private final int maxQueuedRequests;

    /** CAS-updated: virtual threads set true, onWritable clears. */
    private final AtomicBoolean backpressure = new AtomicBoolean(false);

    /** Parked virtual threads waiting for writability. */
    private final ConcurrentLinkedQueue<Thread> parkedWriters = new ConcurrentLinkedQueue<>();

    /**
     * Bounded queue of request tasks that arrived while backpressure was active.
     * Accessed only from the Netty I/O thread — no synchronization needed.
     */
    private final Deque<Runnable> pendingRequests = new ArrayDeque<>();

    BackpressureController(int maxQueuedRequests) {
        this.maxQueuedRequests = maxQueuedRequests;
    }

    // ---- Write side (virtual thread) ----

    /**
     * Park the current virtual thread until the channel is writable.
     * Fast path (channel already writable): single volatile read, no park.
     */
    void awaitWritable(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            return;
        }

        backpressure.compareAndSet(false, true);

        Thread current = Thread.currentThread();
        parkedWriters.offer(current);

        while (!ctx.channel().isWritable() && ctx.channel().isActive()) {
            LockSupport.park(this);
        }

        parkedWriters.remove(current);
    }

    // ---- Read side (Netty I/O thread) ----

    boolean isBackpressure() {
        return backpressure.get();
    }

    /**
     * Try to queue a request task. Returns {@code false} if the queue is full.
     */
    boolean enqueue(ChannelHandlerContext ctx, Runnable task) {
        if (pendingRequests.size() >= maxQueuedRequests) {
            return false;
        }
        pendingRequests.offer(task);
        ctx.channel().config().setAutoRead(false);
        return true;
    }

    // ---- Writability restored (Netty I/O thread) ----

    /**
     * Called from {@code channelWritabilityChanged} when writable again.
     * Clears the flag, unparks all writers, drains queued requests, restores autoRead.
     */
    void onWritable(ChannelHandlerContext ctx, ExecutorService virtualThreadPool) {
        backpressure.set(false);

        Thread parked;
        while ((parked = parkedWriters.poll()) != null) {
            LockSupport.unpark(parked);
        }

        drainTo(virtualThreadPool);

        if (!ctx.channel().config().isAutoRead()) {
            ctx.channel().config().setAutoRead(true);
        }
    }

    // ---- Lifecycle ----

    void shutdown() {
        pendingRequests.clear();

        Thread parked;
        while ((parked = parkedWriters.poll()) != null) {
            LockSupport.unpark(parked);
        }
    }

    private void drainTo(ExecutorService virtualThreadPool) {
        Runnable task;
        while ((task = pendingRequests.poll()) != null) {
            virtualThreadPool.execute(task);
        }
    }
}
