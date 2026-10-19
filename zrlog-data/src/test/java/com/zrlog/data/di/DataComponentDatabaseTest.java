package com.zrlog.data.di;

import static org.junit.Assert.*;

import com.hibegin.common.dao.DAO;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.test.support.ZrLogTestDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DataComponentDatabaseTest {
    @Parameterized.Parameters(name = "{0}")
    public static ZrLogTestDatabase.DatabaseType[] databases() {
        return ZrLogTestDatabase.DatabaseType.values();
    }

    private final ZrLogTestDatabase.DatabaseType type;

    public DataComponentDatabaseTest(ZrLogTestDatabase.DatabaseType type) {
        this.type = type;
    }

    @Test
    public void graphsOwnTheirDatabaseCacheAndLocksWithoutADefaultSource() throws Exception {
        try (ZrLogTestDatabase first = ZrLogTestDatabase.open(type);
                ZrLogTestDatabase second = ZrLogTestDatabase.open(type)) {
            DataComponent a = DaggerDataComponent.factory().create(first::dataSource, () -> true);
            DataComponent b = DaggerDataComponent.factory().create(second::dataSource, () -> true);
            DAO.setDs(null);
            a.website().updateByKV("title", "first");
            b.website().updateByKV("title", "second");

            assertEquals("first", a.cache().getPublicWebSiteInfo().getTitle());
            assertEquals("second", b.cache().getPublicWebSiteInfo().getTitle());
            assertSame(a.cache(), a.cache());
            assertNotSame(a.cache(), b.cache());
            a.log().set("title", "uncommitted query state");
            assertTrue(a.log().getAttrs().isEmpty());
            assertNotSame(a.website(), a.website());

            DistributedLock firstLock = a.locks().create("same-key");
            DistributedLock secondLock = b.locks().create("same-key");
            assertTrue(firstLock.tryLock());
            assertTrue(secondLock.tryLock());
            a.lockManager().releaseLock("same-key");
            assertTrue(a.lockManager().getLocks().isEmpty());
            assertEquals(1, b.lockManager().getLocks().size());
            secondLock.unlock();

            a.cache().refreshInitData();
            b.cache().refreshInitData();
            assertEquals("first", a.cache().getPublicWebSiteInfo().getTitle());
            assertEquals("second", b.cache().getPublicWebSiteInfo().getTitle());
        }
    }
}
