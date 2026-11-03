package com.zrlog.data.service;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.repository.WebSiteRepository;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import java.sql.SQLException;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.function.BooleanSupplier;

/**
 * Non-reentrant database lock shared by JDBC and WebAPI, including before schema migration. The
 * timeout bounds waiting, not ownership: locks do not expire while protected work may still run.
 */
public class DistributedLock implements Lock, AutoCloseable {
    public static final String LOCK_PREFIX = "distributed_lock_";
    public static final String SCHEMA_UPGRADE_KEY = "schema_upgrade_lock";
    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

    private final String lockKey;
    private final jakarta.inject.Provider<WebSiteRepository> websites;
    private String owner;
    private Thread ownerThread;

    @AssistedInject
    public DistributedLock(
            jakarta.inject.Provider<WebSiteRepository> websites, @Assisted String lockKey) {
        this.websites = Objects.requireNonNull(websites);
        this.lockKey = storageKey(lockKey);
    }

    /** Preserve the migration key so older and newer application versions exclude each other. */
    public static String storageKey(String lockKey) {
        Objects.requireNonNull(lockKey);
        return SCHEMA_UPGRADE_KEY.equals(lockKey) ? lockKey : LOCK_PREFIX + lockKey;
    }

    private static boolean isDuplicateKey(SQLException failure) {
        String message = failure.getMessage();
        return "23505".equals(failure.getSQLState())
                || ("23000".equals(failure.getSQLState()) && failure.getErrorCode() == 1062)
                || (message != null
                        && (message.contains("SQLITE_CONSTRAINT_UNIQUE")
                                || message.contains("UNIQUE constraint failed: website.name")));
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock(time, unit, () -> false);
    }

    /** Cancellation stops waiting; it never releases another caller's lock. */
    public boolean tryLock(long time, TimeUnit unit, BooleanSupplier cancelled)
            throws InterruptedException {
        long waitNanos = unit.toNanos(time);
        if (waitNanos <= 0 || waitNanos > TimeUnit.MINUTES.toNanos(10)) {
            throw new IllegalArgumentException("waitTime must be positive and at most 10 minutes");
        }
        Objects.requireNonNull(cancelled);
        long started = System.nanoTime();
        boolean available = true;
        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException("Interrupted while waiting for database lock");
            }
            if (cancelled.getAsBoolean()) {
                return false;
            }
            if (available && tryLock()) {
                return true;
            }
            long remaining = waitNanos - (System.nanoTime() - started);
            if (remaining <= 0) {
                return false;
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(POLL_NANOS, remaining));
            if (cancelled.getAsBoolean()) {
                return false;
            }
            if (System.nanoTime() - started >= waitNanos) {
                return false;
            }
            // Poll with reads while another owner exists, avoiding repeated failed INSERTs on D1.
            synchronized (this) {
                try {
                    available = owner == null && currentOwner() == null;
                } catch (SQLException failure) {
                    throw new DAOException(failure);
                }
            }
            if (System.nanoTime() - started >= waitNanos) {
                return false;
            }
        }
    }

    @Override
    public synchronized boolean tryLock() {
        if (owner != null) {
            return false;
        }
        String token = UUID.randomUUID().toString();
        try {
            websites.get()
                    .insertLock(
                            lockKey,
                            token,
                            "Created at "
                                    + ResultValueConvertUtils.formatDate(
                                            new Date(), "yyyy-MM-dd HH:mm:ss"));
        } catch (SQLException failure) {
            String current;
            try {
                current = currentOwner();
            } catch (SQLException readFailure) {
                failure.addSuppressed(readFailure);
                throw new DAOException(failure);
            }
            // A lost D1 response can hide a committed INSERT. Only our own token proves ownership.
            if (!token.equals(current)) {
                if (isDuplicateKey(failure)) {
                    return false;
                }
                throw new DAOException(failure);
            }
        }
        owner = token;
        ownerThread = Thread.currentThread();
        return true;
    }

    private String currentOwner() throws SQLException {
        return websites.get().findValueByName(lockKey);
    }

    @Override
    public synchronized void unlock() {
        // A timed-out caller sharing this Lock (e.g. cache refresh) must not release the holder.
        if (ownerThread != Thread.currentThread()) {
            return;
        }
        try {
            close();
        } catch (SQLException failure) {
            throw new DAOException(failure);
        }
    }

    /**
     * Release this handle's token, also from a shutdown thread after protected work has stopped.
     * Failed releases retain the token so shutdown can retry; successful closes are idempotent.
     */
    @Override
    public synchronized void close() throws SQLException {
        if (owner == null) {
            return;
        }
        try {
            websites.get().deleteLock(lockKey, owner);
        } catch (SQLException failure) {
            String current;
            try {
                current = currentOwner();
            } catch (SQLException readFailure) {
                failure.addSuppressed(readFailure);
                throw failure;
            }
            // The DELETE may also have committed before the response was lost.
            if (owner.equals(current)) {
                throw failure;
            }
        }
        owner = null;
        ownerThread = null;
    }

    @Override
    public void lock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void lockInterruptibly() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    @AssistedFactory
    public interface Factory {
        DistributedLock create(String lockKey);
    }
}
