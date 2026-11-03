package com.zrlog.business.version;

import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.SqlConvertUtils;
import com.hibegin.common.dao.entity.EntityRepository;
import com.zrlog.business.service.DbUpgradeService;
import com.zrlog.common.CacheService;
import com.zrlog.model.OAuthGrant;
import org.junit.Assume;
import org.junit.Test;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * Runs only against CI's explicitly provisioned, empty MySQL database.
 */
public class V31MySqlMetadataUpgradeTest {
    private static List<Map<String, Object>> schema(InMemoryDatabase db) throws Exception {
        return db.queryList("SELECT table_name,column_name,column_type,is_nullable,column_default,datetime_precision "
                + "FROM information_schema.columns WHERE table_schema=database() ORDER BY table_name,column_name");
    }

    private static void load(InMemoryDatabase db, String resource) throws Exception {
        try (InputStream input = V31MySqlMetadataUpgradeTest.class.getResourceAsStream(resource)) {
            assertNotNull(resource, input);
            db.executeStatements(SqlConvertUtils.extractExecutableSqlByInputStream(input));
        }
    }

    private static void clear(InMemoryDatabase db) throws Exception {
        for (Map<String, Object> row : db.queryList("SELECT table_name AS name FROM information_schema.tables WHERE table_schema=database()")) {
            String name = row.get("name").toString();
            if (!name.matches("[a-z_]+")) throw new IllegalStateException("Unexpected table in disposable database");
            db.execute("DROP TABLE `" + name + "`");
        }
    }

    @Test
    public void historicalDataSurvivesMysqlDdlAndMatchesFreshInstallation() throws Exception {
        String url = System.getenv("ZRLOG_MYSQL_TEST_URL");
        Assume.assumeTrue("Disposable MySQL service was not provided", url != null && !url.isEmpty());
        assertTrue("Use only the disposable migration database", url.matches(
                "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/zrlog_entity_migration_test(?:\\?.*)?"));
        Properties properties = new Properties();
        properties.setProperty("driverClass", "com.mysql.cj.jdbc.Driver");
        properties.setProperty("jdbcUrl", url);
        properties.setProperty("user", System.getenv("ZRLOG_MYSQL_TEST_USER"));
        properties.setProperty("password", System.getenv("ZRLOG_MYSQL_TEST_PASSWORD"));
        try (InMemoryDatabase db = InMemoryDatabase.open(properties, false)) {
            assertEquals("Refuse to overwrite an existing schema", 0L, ((Number) db.scalar(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema=database() ")).longValue());
            try {
                load(db, "/schema/v30-install-e8d8c9d.sql");
                long epoch = Timestamp.valueOf("2024-02-29 13:14:15.123").getTime();
                for (int i = 0; i < 47; i++) {
                    db.update("INSERT INTO user_passkey_challenge(requestId,ceremony,requestJson,createdAt,expiresAt) VALUES(?,?,?,?,?)",
                            "request-" + i, "authentication", "{}", epoch + i, epoch + i + 300000);
                }
                db.update("INSERT INTO oauth_grant(id,userId,clientId,scope,resource,authVersion,createdAt) VALUES(?,?,?,?,?,?,?)",
                        "grant", 1, "client", "read", "resource", 7, epoch);
                db.update("INSERT INTO log(logId,title,version,last_update_date) VALUES(1,'historical',123,'2020-01-02 03:04:05')");
                db.update("INSERT INTO website(name,value) VALUES(?,?)", CacheService.ZRLOG_SQL_VERSION_KEY, "30");

                new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade();
                assertEquals("31", db.scalar("SELECT value FROM website WHERE name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
                for (Map<String, Object> row : db.queryList("SELECT id,createdAt,expiresAt FROM user_passkey_challenge ORDER BY id")) {
                    long expected = epoch + ((Number) row.get("id")).longValue() - 1;
                    assertEquals(expected, ResultValueConvertUtils.parseDate(row.get("createdAt")).longValue());
                    assertEquals(expected + 300000, ((Number) row.get("expiresAt")).longValue());
                }
                EntityRepository<OAuthGrant, String> grants = new EntityRepository<>(OAuthGrant.class, db.dataSource());
                OAuthGrant grant = grants.findById("grant").orElseThrow();
                assertEquals(epoch, grant.getCreatedAt().getTime());
                assertEquals(Integer.valueOf(7), grant.getAuthVersion());
                assertNull(grant.getUpdatedAt());
                grant.setRevoked(true);
                assertTrue(grants.update(grant));
                assertEquals(Long.valueOf(1), grants.findById("grant").orElseThrow().getVersion());
                assertEquals(123, ((Number) db.scalar("SELECT version FROM log WHERE logId=1")).intValue());
                assertNull(db.scalar("SELECT createdAt FROM log WHERE logId=1"));
                new V31EntityMetadataUpgrade(db.dataSource()).run();
                assertEquals(epoch, grants.findById("grant").orElseThrow().getCreatedAt().getTime());

                List<Map<String, Object>> upgradedSchema = schema(db);
                clear(db);
                load(db, "/init-table-structure.sql");
                assertEquals("Upgrade and fresh install must agree on columns, precision and defaults", upgradedSchema, schema(db));
            } finally {
                clear(db);
            }
        }
    }
}
