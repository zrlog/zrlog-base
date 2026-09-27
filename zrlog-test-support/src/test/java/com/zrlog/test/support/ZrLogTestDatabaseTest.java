package com.zrlog.test.support;

import com.hibegin.common.dao.DAO;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ZrLogTestDatabaseTest {

    @Test public void schemaFixturesAreIsolatedAndRestoreThePreviousDao() throws Exception {
        for (ZrLogTestDatabase.DatabaseType type : ZrLogTestDatabase.DatabaseType.values()) {
            try (ZrLogTestDatabase outer = ZrLogTestDatabase.open(type)) {
                outer.putWebsite("title", "outer");
                try (ZrLogTestDatabase inner = ZrLogTestDatabase.open(type)) {
                    assertEquals(0L, ((Number) inner.scalar("select count(*) from website")).longValue());
                    inner.putWebsite("title", "inner");
                    assertEquals("inner", new DAO().queryFirstObj("select value from website where name='title'"));
                    // Use the installer's real schema; no test-only column patches.
                    inner.queryList("select sticky, extensions from log");
                }
                assertEquals("outer", new DAO().queryFirstObj("select value from website where name='title'"));
                assertEquals("outer", outer.scalar("select value from website where name=?", "title"));
            }
        }
    }

    @Test public void sqliteFilesAreRemovedAndPropertiesCannotMutateTheFixture() throws Exception {
        Path file;
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.SQLITE)) {
            String jdbcUrl = db.properties().getProperty("jdbcUrl");
            file = Path.of(jdbcUrl.substring("jdbc:sqlite:".length(), jdbcUrl.indexOf('?')));
            assertTrue(Files.isRegularFile(file));
            db.properties().setProperty("jdbcUrl", "changed");
            assertEquals(jdbcUrl, db.properties().getProperty("jdbcUrl"));
            db.putWebsite("title", "one");
            db.putWebsite("title", "two");
            assertEquals("two", db.scalar("select value from website where name='title'"));
        }
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(Path.of(file + "-wal")));
        assertFalse(Files.exists(Path.of(file + "-shm")));
    }

    @Test public void webApiAdapterRejectsTransactionsAndExposesControlledSqlHooks() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.openWebApi()) {
            assertTrue(db.dataSource().isWebApi());
            assertThrows(AssertionError.class, () -> db.dataSource().getConnection());
            AtomicInteger updates = new AtomicInteger();
            db.beforeWebApiUpdate((sql, params) -> updates.incrementAndGet());
            db.dataSource().getQueryRunner().update("insert into website(name,value) values(?,?)", "title", "web-api");
            assertEquals(1, updates.get());
            assertEquals("web-api", db.scalar("select value from website where name='title'"));
            db.beforeWebApiUpdate((sql, params) -> { throw new java.sql.SQLException("controlled failure"); });
            assertThrows(java.sql.SQLException.class,
                    () -> db.dataSource().getQueryRunner().update("delete from website"));
            assertEquals(1L, ((Number) db.scalar("select count(*) from website")).longValue());
        }
    }
}
