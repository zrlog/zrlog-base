package com.zrlog.data.service;

import com.zrlog.common.vo.LockVO;
import com.zrlog.data.support.InMemoryZrLogDatabase;
import com.zrlog.data.support.InMemoryZrLogDatabase.DatabaseType;
import com.zrlog.data.util.DistributedLockManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class DistributedLockDatabaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    private final DatabaseType databaseType;

    public DistributedLockDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Test
    public void shouldStoreListAndReleaseDistributedLocksUsingWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            DistributedLock first = new DistributedLock("cache");
            DistributedLock second = new DistributedLock("cache");

            assertTrue(first.tryLock());
            assertFalse(second.tryLock());
            assertFalse(second.tryLock(1, TimeUnit.MILLISECONDS));

            List<LockVO> locks = DistributedLockManager.getInstance().getLocks();

            assertEquals(1, locks.size());
            assertEquals("cache", locks.get(0).getName());
            assertTrue(locks.get(0).getRemark().startsWith("Created at "));

            DistributedLockManager.getInstance().releaseLock("cache");

            assertEquals(0L, ((Number) db.scalar("select count(1) from website where name=?",
                    DistributedLock.LOCK_PREFIX + "cache")).longValue());
            assertTrue(second.tryLock());
            second.unlock();
        }
    }

    @Test
    public void shouldRejectUnsupportedOrUnsafeLockOperations() {
        DistributedLock lock = new DistributedLock("cache");

        assertThrows(UnsupportedOperationException.class, lock::lock);
        assertThrows(UnsupportedOperationException.class, lock::lockInterruptibly);
        assertThrows(UnsupportedOperationException.class, lock::newCondition);
        assertThrows(IllegalArgumentException.class, () -> lock.tryLock(0, TimeUnit.MILLISECONDS));
        assertThrows(IllegalArgumentException.class, () -> lock.tryLock(601, TimeUnit.SECONDS));
    }
}
