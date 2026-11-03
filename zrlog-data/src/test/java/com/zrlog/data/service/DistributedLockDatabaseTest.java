package com.zrlog.data.service;

import com.zrlog.common.vo.LockVO;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.data.util.DistributedLockManager;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class DistributedLockDatabaseTest {

    private final String backend;

    public DistributedLockDatabaseTest(String backend) {
        this.backend = backend;
    }

    @Parameterized.Parameters(name = "{0}")
    public static String[] databases() {
        return new String[]{"H2", "SQLITE", "WEB_API"};
    }

    private ZrLogTestDatabase open() throws Exception {
        return backend.equals("WEB_API") ? ZrLogTestDatabase.openWebApi()
                : ZrLogTestDatabase.open(DatabaseType.valueOf(backend));
    }

    @Test
    public void shouldStoreListAndReleaseDistributedLocksUsingWebsiteTable() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            DistributedLock first = new DistributedLock(() -> new WebSiteRepository(db.dataSource()), "cache");
            DistributedLock second = new DistributedLock(() -> new WebSiteRepository(db.dataSource()), "cache");

            assertTrue(first.tryLock());
            assertFalse(second.tryLock());
            assertFalse(second.tryLock(1, TimeUnit.MILLISECONDS));

            List<LockVO> locks = DaggerDataComponent.factory().create(() -> db.dataSource(), () -> true).lockManager().getLocks();

            assertEquals(1, locks.size());
            assertEquals("cache", locks.get(0).getName());
            assertTrue(locks.get(0).getRemark().startsWith("Created at "));

            DaggerDataComponent.factory().create(() -> db.dataSource(), () -> true).lockManager().releaseLock("cache");

            assertEquals(0L, ((Number) db.scalar("select count(1) from website where name=?",
                    DistributedLock.LOCK_PREFIX + "cache")).longValue());
            assertTrue(second.tryLock());
            // The old handle cannot delete the new owner's lock.
            Object owner = db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.LOCK_PREFIX + "cache");
            first.unlock();
            assertEquals(owner, db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.LOCK_PREFIX + "cache"));
            second.unlock();
            second.close();
            assertTrue(first.tryLock());
            first.unlock();
        }
    }

    @Test
    public void shouldRejectUnsupportedOrUnsafeLockOperations() {
        DistributedLock lock = new DistributedLock(() -> {
            throw new IllegalStateException("Database is unavailable in this test");
        }, "cache");

        assertThrows(UnsupportedOperationException.class, lock::lock);
        assertThrows(UnsupportedOperationException.class, lock::lockInterruptibly);
        assertThrows(UnsupportedOperationException.class, lock::newCondition);
        assertThrows(IllegalArgumentException.class, () -> lock.tryLock(0, TimeUnit.MILLISECONDS));
        assertThrows(IllegalArgumentException.class, () -> lock.tryLock(601, TimeUnit.SECONDS));
    }

    @Test
    public void timedOutThreadSharingTheHandleCannotReleaseTheHolder() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            DistributedLock lock = new DistributedLock(() -> new WebSiteRepository(db.dataSource()), "shared");
            assertTrue(lock.tryLock());
            assertFalse(lock.tryLock()); // Non-reentrant, even on the same thread.
            Object owner = db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.LOCK_PREFIX + "shared");
            ExecutorService waiter = Executors.newSingleThreadExecutor();
            try {
                assertFalse(waiter.submit(() -> {
                    try {
                        return lock.tryLock(1, TimeUnit.MILLISECONDS);
                    } finally {
                        lock.unlock();
                    }
                }).get(5, TimeUnit.SECONDS));
                assertEquals(owner, db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.LOCK_PREFIX + "shared"));
                lock.unlock();
                assertTrue(waiter.submit(() -> {
                    try {
                        return lock.tryLock(1, TimeUnit.SECONDS);
                    } finally {
                        lock.unlock();
                    }
                }).get(5, TimeUnit.SECONDS));
                assertNull(db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.LOCK_PREFIX + "shared"));
            } finally {
                lock.close();
                waiter.shutdownNow();
                assertTrue(waiter.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void managerListsAndReleasesMigrationAndRequestLocksWithoutTouchingSettings() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            DistributedLockManager manager = DaggerDataComponent.factory().create(() -> db.dataSource(), () -> true).lockManager();
            db.putWebsite("distributedXlockYsetting", "keep");
            try (DistributedLock schema = manager.acquire(DistributedLock.SCHEMA_UPGRADE_KEY, 1, TimeUnit.SECONDS, () -> false);
                 DistributedLock request = manager.acquire("request", 1, TimeUnit.SECONDS, () -> false)) {
                List<LockVO> locks = manager.getLocks();
                assertEquals(2, locks.size());
                assertTrue(locks.stream().anyMatch(lock -> lock.getName().equals(DistributedLock.SCHEMA_UPGRADE_KEY)));
                assertTrue(locks.stream().anyMatch(lock -> lock.getName().equals("request")));
                assertNotNull(db.scalar("SELECT value FROM website WHERE name=?", "schema_upgrade_lock"));
                assertNull(db.scalar("SELECT value FROM website WHERE name=?", "distributed_lock_schema_upgrade_lock"));
                manager.releaseLock(DistributedLock.SCHEMA_UPGRADE_KEY);
                manager.releaseLock("request");
                assertTrue(manager.getLocks().isEmpty());
                assertEquals("keep", db.scalar("SELECT value FROM website WHERE name=?", "distributedXlockYsetting"));
            }
        }
    }

    @Test
    public void administrativeReleaseDoesNotDeleteAReplacementCreatedAfterItsRead() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            String key = DistributedLock.LOCK_PREFIX + "replace";
            db.putWebsite(key, "old-owner");
            db.afterWebApiQuery((sql, params) -> db.update("UPDATE website SET value=? WHERE name=?", "new-owner", key));
            DaggerDataComponent.factory().create(() -> db.dataSource(), () -> true).lockManager().releaseLock("replace");
            assertEquals("new-owner", db.scalar("SELECT value FROM website WHERE name=?", key));
        }
    }

}
