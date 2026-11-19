package com.zrlog.data.cache;

import static org.junit.Assert.*;

import com.google.gson.reflect.TypeToken;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class WebsiteCacheServiceDatabaseTest {
    private final DatabaseType databaseType;

    public WebsiteCacheServiceDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    @Test
    public void usesOnlyTheVersionedCachePrefix() throws Exception {
        try (var db = ZrLogTestDatabase.open(databaseType)) {
            var cache =
                    DaggerDataComponent.factory().create(db::dataSource, () -> true).websiteCache();
            db.putWebsite("admin_cache:legacy", "\"old\"");
            db.putWebsite("cacheXv1:unrelated", "\"other\"");

            assertNull(cache.getJson("legacy", String.class));
            assertTrue(cache.putJson("value", "one"));
            assertTrue(cache.putJson("cache_v1:list", List.of("two")));
            assertEquals("one", cache.getJson("cache_v1:value", String.class));
            Type listType = new TypeToken<List<String>>() {}.getType();
            assertEquals(List.of("two"), cache.getJson("list", listType));
            assertEquals(
                    List.of("cache_v1:list", "cache_v1:value"),
                    cache.listEntries().stream().map(row -> row.name).collect(Collectors.toList()));
            var entry = cache.listEntries().get(1);
            assertEquals("\"one\"", entry.value);
            assertEquals(5L, entry.size);
            assertTrue(cache.putString("bad", "{"));
            assertNull(cache.getJson("bad", String.class));
            assertTrue(cache.remove("value"));
            assertNull(cache.getJson("value", String.class));
        }
    }
}
