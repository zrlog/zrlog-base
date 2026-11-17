package com.zrlog.data.cache;

import com.zrlog.common.CacheService;
import com.zrlog.data.repository.WebSiteRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.sql.SQLException;

/** Shares one database read between schema validation and this application's cache construction. */
@Singleton
public final class StartupData {
    public static final String CACHE_KEY = "base_data_init_cache_v3";
    private final Provider<WebSiteRepository> websites;
    private Snapshot snapshot;

    @Inject
    public StartupData(Provider<WebSiteRepository> websites) {
        this.websites = websites;
    }

    public synchronized long readSqlVersion() throws SQLException {
        String version = read().sqlVersion;
        return version == null ? -1 : Long.parseLong(version);
    }

    private Snapshot read() throws SQLException {
        if (snapshot == null) {
            String sqlVersion = null;
            String cache = null;
            // These columns exist before Entity metadata migrations have run.
            for (Property row :
                    websites.get()
                            .queryList(
                                    "SELECT name,value FROM website WHERE name IN (?,?)",
                                    Property.class,
                                    CacheService.ZRLOG_SQL_VERSION_KEY,
                                    CACHE_KEY)) {
                if (CacheService.ZRLOG_SQL_VERSION_KEY.equals(row.name)) sqlVersion = row.value;
                else if (CACHE_KEY.equals(row.name)) cache = row.value;
            }
            snapshot = new Snapshot(sqlVersion, cache);
        }
        return snapshot;
    }

    /** Do not retain the serialized cache after CacheService has consumed it. */
    synchronized String takeCache() throws SQLException {
        String value = read().cache;
        snapshot = null;
        return value;
    }

    /** A migration may change both values; the cache must read them again afterwards. */
    public synchronized void invalidate() {
        snapshot = null;
    }

    private static final class Snapshot {
        private final String sqlVersion;
        private final String cache;

        private Snapshot(String sqlVersion, String cache) {
            this.sqlVersion = sqlVersion;
            this.cache = cache;
        }
    }

    public static final class Property {
        public String name;
        public String value;
    }
}
