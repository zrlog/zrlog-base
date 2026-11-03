package com.zrlog.business.version;

import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SchemaUpgradeLockTest {
    private static final String V30 = "/schema/v30-install-e8d8c9d.sql";
    private final String backend;

    public SchemaUpgradeLockTest(String backend) { this.backend = backend; }

    @Parameterized.Parameters(name = "{0}")
    public static Object[] backends() { return new Object[]{"H2", "SQLITE", "WEB_API"}; }

    private ZrLogTestDatabase open() throws Exception {
        return backend.equals("WEB_API") ? ZrLogTestDatabase.openWebApi(V30)
                : ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.valueOf(backend), V30);
    }

    @Test
    public void timeoutRetainsOldLockAndAllowsRecoveryOnlyAfterRelease() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            try (SchemaUpgradeLock first = SchemaUpgradeLock.acquire(db.dataSource())) {
                Object owner = db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY);
                db.update("UPDATE website SET remark=? WHERE name=?", "Schema migration started at 2000-01-01", SchemaUpgradeLock.KEY);
                SQLException timeout = assertThrows(SQLException.class, () -> SchemaUpgradeLock.acquire(db.dataSource(), 0));
                assertTrue(timeout.getMessage().contains("Timed out waiting"));
                assertNotNull(timeout.getCause());
                assertEquals(owner, db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
            }
            try (SchemaUpgradeLock next = SchemaUpgradeLock.acquire(db.dataSource(), 0)) {
                assertNotNull(db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
            }
            assertNull(db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
        }
    }

    @Test
    public void closeDoesNotDeleteAnotherOwnersLock() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            try (SchemaUpgradeLock first = SchemaUpgradeLock.acquire(db.dataSource())) {
                db.update("UPDATE website SET value=? WHERE name=?", "replacement-owner", SchemaUpgradeLock.KEY);
            }
            assertEquals("replacement-owner", db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
        }
    }

    @Test
    public void interruptedStartupDoesNotAcquireOrDeleteLock() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            try {
                Thread.currentThread().interrupt();
                SQLException interrupted = assertThrows(SQLException.class, () -> SchemaUpgradeLock.acquire(db.dataSource()));
                assertTrue(interrupted.getMessage().contains("Interrupted"));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertNull(db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
        }
    }

    @Test
    public void committedInsertWithLostResponseStillReleasesItsOwnLock() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            db.beforeWebApiUpdate((sql, params) -> {
                if (sql.startsWith("INSERT INTO website")) {
                    db.update(sql, params);
                    throw new SQLException("Connection closed before D1 response arrived");
                }
            });
            try (SchemaUpgradeLock lock = SchemaUpgradeLock.acquire(db.dataSource())) {
                assertNotNull(db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
            }
            assertNull(db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
        }
    }

    @Test
    public void committedDeleteWithLostResponseDoesNotFailStartup() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            db.beforeWebApiUpdate((sql, params) -> {
                if (sql.startsWith("DELETE FROM website")) {
                    db.update(sql, params);
                    throw new SQLException("Connection closed before D1 response arrived");
                }
            });
            SchemaUpgradeLock.acquire(db.dataSource()).close();
            assertNull(db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
        }
    }

    @Test
    public void failedDeleteIsReportedAndRetainsTheLock() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            SchemaUpgradeLock lock = SchemaUpgradeLock.acquire(db.dataSource());
            SQLException unavailable = new SQLException("D1 unavailable");
            db.beforeWebApiUpdate((sql, params) -> { throw unavailable; });
            assertSame(unavailable, assertThrows(SQLException.class, lock::close));
            assertNotNull(db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
            db.beforeWebApiUpdate((sql, params) -> {});
            lock.close();
            assertNull(db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
        }
    }

    @Test
    public void d1UniqueConstraintIsReportedAsContentionWithoutTakingOver() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            db.putWebsite(SchemaUpgradeLock.KEY, "other-instance");
            SQLException d1 = new SQLException("Error: D1_ERROR: UNIQUE constraint failed: website.name: "
                    + "SQLITE_CONSTRAINT (extended: SQLITE_CONSTRAINT_UNIQUE)");
            db.beforeWebApiUpdate((sql, params) -> { throw d1; });
            SQLException timeout = assertThrows(SQLException.class, () -> SchemaUpgradeLock.acquire(db.dataSource(), 0));
            assertSame(d1, timeout.getCause());
            assertEquals("other-instance", db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
        }
    }

    @Test
    public void unrelatedDatabaseFailureIsNotRetriedAsLockContention() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            db.putWebsite(SchemaUpgradeLock.KEY, "other-instance");
            SQLException denied = new SQLException("INSERT permission denied", "42000");
            db.beforeWebApiUpdate((sql, params) -> { throw denied; });
            assertSame(denied, assertThrows(SQLException.class, () -> SchemaUpgradeLock.acquire(db.dataSource())));
            assertEquals("other-instance", db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
        }
    }

    @Test
    public void interruptWhileWaitingPreservesOwnerAndInterruptStatus() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open(); SchemaUpgradeLock first = SchemaUpgradeLock.acquire(db.dataSource())) {
            Object owner = db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY);
            CountDownLatch observedOwner = new CountDownLatch(1);
            db.afterWebApiQuery((sql, params) -> observedOwner.countDown());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread waiter = new Thread(() -> {
                try (SchemaUpgradeLock unexpected = SchemaUpgradeLock.acquire(db.dataSource())) {
                    failure.set(new AssertionError("Acquired another instance's lock"));
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
                assertEquals(owner, db.scalar("SELECT value FROM website WHERE name=?", SchemaUpgradeLock.KEY));
            } finally {
                waiter.interrupt();
                waiter.join(5000);
            }
        }
    }
}
