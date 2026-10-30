package com.zrlog.util;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeUnit;

/** A monotonic budget shared by the stages of one synchronous operation. */
public final class ExecutionDeadline {
    private static final long MAX_MILLIS = TimeUnit.DAYS.toMillis(1);
    private final long deadlineNanos;

    private ExecutionDeadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    public static ExecutionDeadline afterMillis(long millis) {
        if (millis < 0 || millis > MAX_MILLIS) throw new IllegalArgumentException("Invalid execution budget");
        return new ExecutionDeadline(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis));
    }

    public long remainingMillis() {
        long nanos = deadlineNanos - System.nanoTime();
        return nanos <= 0 ? 0 : Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos));
    }

    public long requireRemainingMillis() throws HttpTimeoutException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Execution interrupted");
        long remaining = remainingMillis();
        if (remaining == 0) throw new HttpTimeoutException("Execution time budget exhausted");
        return remaining;
    }

    public ExecutionDeadline capMillis(long maximum) {
        if (maximum < 0 || maximum > MAX_MILLIS) throw new IllegalArgumentException("Invalid execution budget");
        return remainingMillis() <= maximum ? this : afterMillis(maximum);
    }

    public ExecutionDeadline reserveMillis(long reserve) {
        if (reserve < 0) throw new IllegalArgumentException("Invalid reserve");
        return reserve >= remainingMillis() ? afterMillis(0)
                : new ExecutionDeadline(deadlineNanos - TimeUnit.MILLISECONDS.toNanos(reserve));
    }
}
