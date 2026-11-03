package com.zrlog.business.version;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.LoggerUtil;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/** A bootstrap lock shared by JDBC and WebAPI; uses only the historical website columns. */
public final class SchemaUpgradeLock implements AutoCloseable {
    private static final Logger LOGGER = LoggerUtil.getLogger(SchemaUpgradeLock.class);
    private static final long WAIT_MILLIS = 30_000;
    private static final long POLL_MILLIS = 250;
    public static final String KEY = "schema_upgrade_lock";
    private final DAO dao;
    private final String owner = UUID.randomUUID().toString();
    private boolean released;

    private SchemaUpgradeLock(DataSourceWrapper source) {
        dao = new DAO(source);
    }

    public static SchemaUpgradeLock acquire(DataSourceWrapper source) throws SQLException {
        return acquire(source, () -> false);
    }

    public static SchemaUpgradeLock acquire(DataSourceWrapper source, BooleanSupplier cancelled) throws SQLException {
        return acquire(source, WAIT_MILLIS, cancelled);
    }

    static SchemaUpgradeLock acquire(DataSourceWrapper source, long waitMillis) throws SQLException {
        return acquire(source, waitMillis, () -> false);
    }

    private static SchemaUpgradeLock acquire(DataSourceWrapper source, long waitMillis,
                                             BooleanSupplier cancelled) throws SQLException {
        SchemaUpgradeLock lock = new SchemaUpgradeLock(source);
        long started = System.nanoTime();
        boolean logged = false;
        while (true) {
            checkRunning(cancelled);
            if (Thread.currentThread().isInterrupted()) {
                throw new SQLException("Interrupted while waiting for schema migration lock");
            }
            SQLException conflict;
            Object currentOwner;
            try {
                lock.dao.execute("INSERT INTO website(name,value,remark) VALUES(?,?,?)", KEY, lock.owner,
                        "Schema migration started at " + new Timestamp(System.currentTimeMillis()));
                return lock;
            } catch (SQLException failure) {
                try {
                    currentOwner = lock.currentOwner();
                } catch (SQLException readFailure) {
                    failure.addSuppressed(readFailure);
                    throw failure;
                }
                // D1 may commit the INSERT even when the response is lost. Reconcile our own token.
                if (lock.owner.equals(currentOwner)) {
                    return lock;
                }
                if (!isDuplicateKey(failure)) {
                    throw failure;
                }
                conflict = failure;
            }
            if (!logged) {
                LOGGER.info("Another instance holds the schema migration lock; waiting up to " + waitMillis + "ms");
                logged = true;
            }
            do {
                checkRunning(cancelled);
                long remaining = TimeUnit.MILLISECONDS.toNanos(waitMillis) - (System.nanoTime() - started);
                if (remaining <= 0) {
                    throw new SQLException("Timed out waiting for schema migration lock after " + waitMillis
                            + "ms. Another instance may still be upgrading. After an abrupt termination, "
                            + "confirm all previous instances have stopped before removing website row '"
                            + KEY + "' and restarting.", conflict);
                }
                try {
                    TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS), remaining));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for schema migration lock", interrupted);
                }
                currentOwner = lock.currentOwner();
            } while (currentOwner != null);
        }
    }

    private static void checkRunning(BooleanSupplier cancelled) throws SQLException {
        if (cancelled.getAsBoolean()) {
            throw new SQLException("Schema migration cancelled because the application is stopping");
        }
    }

    private Object currentOwner() throws SQLException {
        return dao.queryFirstObj("SELECT value FROM website WHERE name=?", KEY);
    }

    private static boolean isDuplicateKey(SQLException failure) {
        String message = failure.getMessage();
        return "23505".equals(failure.getSQLState())
                || ("23000".equals(failure.getSQLState()) && failure.getErrorCode() == 1062)
                || (message != null && (message.contains("SQLITE_CONSTRAINT_UNIQUE")
                || message.contains("UNIQUE constraint failed: website.name")));
    }

    @Override
    public synchronized void close() throws SQLException {
        if (released) { return; }
        // No expiry/takeover: a paused old process must never resume DDL after another instance took over.
        try {
            dao.execute("DELETE FROM website WHERE name=? AND value=?", KEY, owner);
        } catch (SQLException failure) {
            Object currentOwner;
            try {
                currentOwner = currentOwner();
            } catch (SQLException readFailure) {
                failure.addSuppressed(readFailure);
                throw failure;
            }
            // As with INSERT, a missing D1 response does not mean the DELETE failed.
            if (owner.equals(currentOwner)) {
                throw failure;
            }
        }
        released = true;
    }
}
