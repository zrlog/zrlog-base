package com.zrlog.data.service;

import com.zrlog.common.vo.LockVO;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.model.WebSite;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class DistributedLockDatabaseTest {

    private final DatabaseType databaseType;

    public DistributedLockDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    @Test
    public void shouldStoreListAndReleaseDistributedLocksUsingWebsiteTable() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            DistributedLock first = new DistributedLock(() -> new WebSite(db.dataSource()), "cache");
            DistributedLock second = new DistributedLock(() -> new WebSite(db.dataSource()), "cache");

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
            second.unlock();
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
}
