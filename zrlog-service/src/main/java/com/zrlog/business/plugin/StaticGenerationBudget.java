package com.zrlog.business.plugin;

import com.zrlog.util.ExecutionDeadline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/** Shared deadline and authority fence for synchronous static generation. */
public final class StaticGenerationBudget {
    public static final String REQUEST_ATTRIBUTE = StaticGenerationBudget.class.getName();
    private final ExecutionDeadline deadline;
    private final Runnable fence;
    private final String executionId;
    private final int actorId;

    public StaticGenerationBudget(ExecutionDeadline deadline, Runnable fence) {
        this(deadline, fence, null, 0);
    }

    public StaticGenerationBudget(ExecutionDeadline deadline, Runnable fence, String executionId, int actorId) {
        this.deadline = Objects.requireNonNull(deadline);
        this.fence = Objects.requireNonNull(fence);
        this.executionId = executionId;
        this.actorId = actorId;
    }

    public String executionId() { return executionId; }
    public int actorId() { return actorId; }

    public void check() {
        fence.run();
        remainingMillis();
    }

    public long remainingMillis() {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Static generation interrupted");
        long remaining = deadline.remainingMillis();
        if (remaining == 0) throw new IllegalStateException("Static generation time budget exhausted");
        return remaining;
    }

    public void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        while (true) {
            remainingMillis();
            int count = input.read(buffer);
            if (count < 0) break;
            remainingMillis();
            output.write(buffer, 0, count);
        }
        remainingMillis();
    }
}
