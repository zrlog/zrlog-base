package com.zrlog.data.service;

import com.zrlog.common.CacheService;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.data.cache.CacheServiceImpl;
import com.zrlog.data.support.InMemoryZrLogDatabase;
import com.zrlog.data.support.InMemoryZrLogDatabase.DatabaseType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class BaseDataDbServiceDatabaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    private final DatabaseType databaseType;

    public BaseDataDbServiceDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Test
    public void shouldBuildBaseDataInitFromRealTables() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            seedContent(db);

            BaseDataInitVO init = new BaseDataDbService().queryCacheInit(Runnable::run);

            assertEquals("ZrLog Test", init.getWebSite().getTitle());
            assertEquals(Long.valueOf(2), init.getStatistics().getTotalArticleSize());
            assertEquals(Long.valueOf(2), init.getStatistics().getTotalTagSize());
            assertEquals(Long.valueOf(1), init.getStatistics().getTotalTypeSize());
            assertEquals("ZrLog", init.getLinks().get(0).getLinkName());
            assertEquals("Home", init.getLogNavs().get(0).getNavName());
            assertEquals("Plugin", init.getPlugins().get(0).getPluginName());
            assertEquals("Second", init.getHotLogs().get(0).getTitle());
            assertEquals("2026_01", init.getArchiveList().get(0).getText());
            assertEquals(Long.valueOf(2), init.getArchiveList().get(0).getCount());
            assertEquals("tech", init.getTypeHotLogs().get(0).getTypeAlias());
            assertEquals(2, init.getTypeHotLogs().get(0).getLogs().size());
            assertEquals("admin", init.getUsers().get(0).getUserName());
            assertEquals("bar", init.getTemplateConfigCacheMap().get("default").get("foo"));
        }
    }

    @Test
    public void shouldRefreshAndPersistCacheInitDataThroughWebsiteTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            seedContent(db);

            CacheServiceImpl cacheService = new CacheServiceImpl();
            BaseDataInitVO init = cacheService.getInitData();
            Long firstVersion = init.getVersion();
            BaseDataInitVO refreshed = cacheService.refreshInitData();
            Map<String, Object> savedCache = db.queryOne("select value from website where name=?",
                    "base_data_init_cache_v3");

            assertEquals(42L, cacheService.getCurrentSqlVersion());
            assertTrue(cacheService.getWebSiteVersion() != 0);
            assertEquals("Tech", cacheService.getArticleTypes().get(0).getTypeName());
            assertTrue(cacheService.getTags().stream().map(TagDTO::getText).anyMatch("java"::equals));
            assertEquals("admin", cacheService.getUserInfoById(1L).getUserName());
            assertEquals("bar", cacheService.getTemplateConfigMapWithCache("default").get("foo"));
            assertNotNull(savedCache);
            assertNotNull(savedCache.get("value"));
            assertFalse(savedCache.get("value").toString().isEmpty());
            assertTrue(refreshed.getVersion() > firstVersion);
        }
    }

    @Test
    public void shouldRemoveEmptyDataPluginsFromCacheInit() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            insertPlugin(db, 1, "tags");
            insertPlugin(db, 2, "archives");
            insertPlugin(db, 3, "types");
            insertPlugin(db, 4, "links");
            insertPlugin(db, 5, "other");

            BaseDataInitVO init = new BaseDataDbService().queryCacheInit(Runnable::run);

            assertEquals(1, init.getPlugins().size());
            assertEquals("other", init.getPlugins().get(0).getPluginName());
            assertTrue(init.getTags().isEmpty());
            assertTrue(init.getArchives().isEmpty());
            assertTrue(init.getTypes().isEmpty());
            assertTrue(init.getLinks().isEmpty());
        }
    }

    @Test
    public void shouldReturnPartialInitWhenOptionalTablesFailToLoad() throws Exception {
        for (String table : List.of("link", "type", "lognav", "plugin", "user", "tag", "log")) {
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
                db.update("drop table " + table);

                BaseDataInitVO init = new BaseDataDbService().queryCacheInit(Runnable::run);

                assertNotNull(init);
                assertNotNull(init.getWebSite());
            }
        }
    }

    private static void seedContent(InMemoryZrLogDatabase db) throws Exception {
        db.update("insert into user(userId, email, password, userName, header) values(?, ?, ?, ?, ?)",
                1, "admin@example.com", "password", "admin", "/avatar.png");
        db.update("insert into type(typeId, alias, typeName, remark) values(?, ?, ?, ?)",
                1, "tech", "Tech", "Tech posts");
        db.update("insert into link(linkId, linkName, url, sort, alt, icon, status) values(?, ?, ?, ?, ?, ?, ?)",
                1, "ZrLog", "https://www.zrlog.com", 1, "ZrLog", "link", true);
        db.update("insert into lognav(navId, navName, url, sort, icon) values(?, ?, ?, ?, ?)",
                1, "Home", "/", 1, "home");
        insertPlugin(db, 1, "Plugin");
        insertArticle(db, 1, "first", "First", "First content", "java,zrlog",
                "2026-01-15 10:00:00", 5);
        insertArticle(db, 2, "second", "Second", "Second content", "java",
                "2026-01-20 11:00:00", 7);
        db.update("insert into website(name, value, remark) values(?, ?, ?)",
                "title", "ZrLog Test", "title");
        db.update("insert into website(name, value, remark) values(?, ?, ?)",
                "description", "A test site", "description");
        db.update("insert into website(name, value, remark) values(?, ?, ?)",
                "template", "default", "template");
        db.update("insert into website(name, value, remark) values(?, ?, ?)",
                "author", "Site Author", "author");
        db.update("insert into website(name, value, remark) values(?, ?, ?)",
                "default_setting", "{\"foo\":\"bar\"}", "template config");
        db.update("insert into website(name, value, remark) values(?, ?, ?)",
                CacheService.ZRLOG_SQL_VERSION_KEY, "42", "sql version");
    }

    private static void insertPlugin(InMemoryZrLogDatabase db, int id, String pluginName) throws Exception {
        db.update("insert into plugin(pluginId, content, isSystem, pTitle, sort, pluginName, level)"
                        + " values(?, ?, ?, ?, ?, ?, ?)",
                id, "{}", false, pluginName, id, pluginName, 1);
    }

    private static void insertArticle(InMemoryZrLogDatabase db, int id, String alias, String title, String content,
                                      String keywords, String releaseTime, int click) throws Exception {
        db.update("insert into log(logId, alias, canComment, click, version, content, plain_content, markdown,"
                        + " digest, keywords, recommended, releaseTime, last_update_date, title, typeId, userId,"
                        + " hot, rubbish, privacy, editor_type) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                        + " ?, ?, ?, ?, ?, ?)",
                id, alias, true, click, 1, content, content, content, "Digest", keywords, false,
                releaseTime, releaseTime, title, 1, 1, false, false, false, "markdown");
    }
}
