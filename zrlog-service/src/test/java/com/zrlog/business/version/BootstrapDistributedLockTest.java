package com.zrlog.business.version;

import static org.junit.Assert.*;

import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.test.support.ZrLogTestDatabase;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BootstrapDistributedLockTest {
    private static final String V30 = "/schema/v30-install-e8d8c9d.sql";
    private final String backend;

    public BootstrapDistributedLockTest(String backend) {
        this.backend = backend;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Object[] backends() {
        return new Object[] {"H2", "SQLITE", "WEB_API"};
    }

    private static DistributedLock acquire(ZrLogTestDatabase db, long waitMillis)
            throws SQLException {
        return DaggerDataComponent.factory()
                .create(() -> db.dataSource(), () -> true)
                .locks()
                .acquire(
                        DistributedLock.SCHEMA_UPGRADE_KEY,
                        waitMillis,
                        TimeUnit.MILLISECONDS,
                        () -> false);
    }

    private ZrLogTestDatabase open() throws Exception {
        return backend.equals("WEB_API")
                ? ZrLogTestDatabase.openWebApi(V30)
                : ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.valueOf(backend), V30);
    }

    @Test
    public void managerCanInspectAndRecoverLocksBeforeSchemaMigration() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            db.putWebsite(DistributedLock.SCHEMA_UPGRADE_KEY, "previous-migration");
            db.putWebsite(DistributedLock.LOCK_PREFIX + "request", "locked");
            var manager =
                    DaggerDataComponent.factory()
                            .create(() -> db.dataSource(), () -> true)
                            .lockManager();
            var locks = manager.getLocks();
            assertEquals(2, locks.size());
            for (var lock : locks) {
                manager.releaseLock(lock.getName());
            }
            assertTrue(manager.getLocks().isEmpty());
            try (DistributedLock acquired = acquire(db, 30000)) {
                assertNotEquals(
                        "previous-migration",
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY));
            }
            assertTrue(manager.getLocks().isEmpty());
        }
    }

    @Test
    public void timeoutRetainsOldLockAndAllowsRecoveryOnlyAfterRelease() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            try (DistributedLock first = acquire(db, 30000)) {
                Object owner =
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY);
                db.update(
                        "UPDATE website SET remark=? WHERE name=?",
                        "Schema migration started at 2000-01-01",
                        DistributedLock.SCHEMA_UPGRADE_KEY);
                SQLException timeout = assertThrows(SQLException.class, () -> acquire(db, 1));
                assertTrue(timeout.getMessage().contains("Timed out waiting"));
                assertEquals(
                        owner,
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY));
            }
            try (DistributedLock next = acquire(db, 1)) {
                assertNotNull(
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY));
            }
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void closeDoesNotDeleteAnotherOwnersLock() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            try (DistributedLock first = acquire(db, 30000)) {
                db.update(
                        "UPDATE website SET value=? WHERE name=?",
                        "replacement-owner",
                        DistributedLock.SCHEMA_UPGRADE_KEY);
            }
            assertEquals(
                    "replacement-owner",
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void interruptedStartupDoesNotAcquireOrDeleteLock() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            try {
                Thread.currentThread().interrupt();
                SQLException interrupted =
                        assertThrows(SQLException.class, () -> acquire(db, 30000));
                assertTrue(interrupted.getMessage().contains("Interrupted"));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void committedInsertWithLostResponseStillReleasesItsOwnLock() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            db.beforeWebApiUpdate(
                    (sql, params) -> {
                        if (sql.startsWith("INSERT INTO website")) {
                            db.update(sql, params);
                            throw new SQLException("Connection closed before D1 response arrived");
                        }
                    });
            try (DistributedLock lock = acquire(db, 30000)) {
                assertNotNull(
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY));
            }
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void committedDeleteWithLostResponseDoesNotFailStartup() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            db.beforeWebApiUpdate(
                    (sql, params) -> {
                        if (sql.startsWith("DELETE FROM website")) {
                            db.update(sql, params);
                            throw new SQLException("Connection closed before D1 response arrived");
                        }
                    });
            acquire(db, 30000).close();
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void failedDeleteIsReportedAndRetainsTheLock() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            DistributedLock lock = acquire(db, 30000);
            SQLException unavailable = new SQLException("D1 unavailable");
            db.beforeWebApiUpdate(
                    (sql, params) -> {
                        throw unavailable;
                    });
            assertSame(unavailable, assertThrows(SQLException.class, lock::close));
            assertNotNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
            db.beforeWebApiUpdate((sql, params) -> {});
            lock.close();
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void d1UniqueConstraintIsReportedAsContentionWithoutTakingOver() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            db.putWebsite(DistributedLock.SCHEMA_UPGRADE_KEY, "other-instance");
            SQLException d1 =
                    new SQLException(
                            "Error: D1_ERROR: UNIQUE constraint failed: website.name: "
                                    + "SQLITE_CONSTRAINT (extended: SQLITE_CONSTRAINT_UNIQUE)");
            db.beforeWebApiUpdate(
                    (sql, params) -> {
                        throw d1;
                    });
            SQLException timeout = assertThrows(SQLException.class, () -> acquire(db, 1));
            assertTrue(timeout.getMessage().contains("Timed out waiting"));
            assertEquals(
                    "other-instance",
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void unrelatedDatabaseFailureIsNotRetriedAsLockContention() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            db.putWebsite(DistributedLock.SCHEMA_UPGRADE_KEY, "other-instance");
            SQLException denied = new SQLException("INSERT permission denied", "42000");
            db.beforeWebApiUpdate(
                    (sql, params) -> {
                        throw denied;
                    });
            assertSame(
                    denied, assertThrows(SQLException.class, () -> acquire(db, 30000)).getCause());
            assertEquals(
                    "other-instance",
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void interruptWhileWaitingPreservesOwnerAndInterruptStatus() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open();
                DistributedLock first = acquire(db, 30000)) {
            Object owner =
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY);
            CountDownLatch observedOwner = new CountDownLatch(1);
            db.afterWebApiQuery((sql, params) -> observedOwner.countDown());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread waiter =
                    new Thread(
                            () -> {
                                try (DistributedLock unexpected = acquire(db, 30000)) {
                                    failure.set(
                                            new AssertionError("Acquired another instance's lock"));
                                } catch (Throwable expected) {
                                    failure.set(expected);
                                    interrupted.set(Thread.currentThread().isInterrupted());
                                }
                            });
            waiter.start();
            try {
                assertTrue(observedOwner.await(5, TimeUnit.SECONDS));
                waiter.interrupt();
                waiter.join(5000);
                assertFalse(waiter.isAlive());
                assertTrue(failure.get() instanceof SQLException);
                assertTrue(failure.get().getMessage().contains("Interrupted"));
                assertTrue(interrupted.get());
                assertEquals(
                        owner,
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY));
            } finally {
                waiter.interrupt();
                waiter.join(5000);
            }
        }
    }
}
