package com.zrlog.data.service;

import static org.junit.Assert.*;

import com.zrlog.common.CacheService;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.data.cache.CacheServiceImpl;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.repository.*;
import com.zrlog.model.*;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BaseDataDbServiceDatabaseTest {

    private final DatabaseType databaseType;

    public BaseDataDbServiceDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    private static void seedContent(ZrLogTestDatabase db) throws Exception {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setPassword("password");
        user.setUserName("admin");
        user.setHeader("/avatar.png");
        new UserRepository(db.dataSource()).insert(user);
        Type type = new Type();
        type.setAlias("tech");
        type.setTypeName("Tech");
        type.setRemark("Tech posts");
        new TypeRepository(db.dataSource()).insert(type);
        Link link = new Link();
        link.setLinkName("ZrLog");
        link.setUrl("https://www.zrlog.com");
        link.setSort(1);
        link.setAlt("ZrLog");
        link.setIcon("link");
        link.setStatus(true);
        new LinkRepository(db.dataSource()).insert(link);
        LogNav nav = new LogNav();
        nav.setNavName("Home");
        nav.setUrl("/");
        nav.setSort(1);
        nav.setIcon("home");
        new LogNavRepository(db.dataSource()).insert(nav);
        insertPlugin(db, 1, "Plugin");
        insertArticle(
                db, 1, "first", "First", "First content", "java,zrlog", "2026-01-15 10:00:00", 5);
        insertArticle(
                db, 2, "second", "Second", "Second content", "java", "2026-01-20 11:00:00", 7);
        WebSiteRepository websites = new WebSiteRepository(db.dataSource());
        websites.insert(WebSiteRepository.entry("title", "ZrLog Test", "title"));
        websites.insert(WebSiteRepository.entry("description", "A test site", "description"));
        websites.insert(WebSiteRepository.entry("template", "default", "template"));
        websites.insert(WebSiteRepository.entry("author", "Site Author", "author"));
        websites.insert(
                WebSiteRepository.entry("default_setting", "{\"foo\":\"bar\"}", "template config"));
        websites.insert(
                WebSiteRepository.entry(CacheService.ZRLOG_SQL_VERSION_KEY, "42", "sql version"));
    }

    private static void insertPlugin(ZrLogTestDatabase db, int sort, String pluginName)
            throws Exception {
        Plugin plugin = new Plugin();
        plugin.setContent("{}");
        plugin.setIsSystem(false);
        plugin.setPTitle(pluginName);
        plugin.setSort(sort);
        plugin.setPluginName(pluginName);
        plugin.setLevel(1);
        new PluginRepository(db.dataSource()).insert(plugin);
    }

    private static void insertArticle(
            ZrLogTestDatabase db,
            int id,
            String alias,
            String title,
            String content,
            String keywords,
            String releaseTime,
            int click)
            throws Exception {
        Log article = new Log();
        article.setId((long) id);
        article.setAlias(alias);
        article.setCanComment(true);
        article.setClick(click);
        article.setContent(content);
        article.setPlainContent(content);
        article.setMarkdown(content);
        article.setDigest("Digest");
        article.setKeywords(keywords);
        article.setRecommended(false);
        article.setReleaseTime(Timestamp.valueOf(releaseTime));
        article.setTitle(title);
        article.setTypeId(1);
        article.setUserId(1);
        article.setHot(false);
        article.setRubbish(false);
        article.setPrivacy(false);
        article.setEditorType("markdown");
        new LogRepository(db.dataSource()).insert(article);
    }

    @Test
    public void shouldBuildBaseDataInitFromRealTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);

            BaseDataInitVO init =
                    DaggerDataComponent.factory()
                            .create(() -> db.dataSource(), () -> true)
                            .baseData()
                            .queryCacheInit(Runnable::run);

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
    public void shouldWaitForCategoryArticlesBeforeReturningSharedCache() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            Type empty = new Type();
            empty.setAlias("empty");
            empty.setTypeName("Empty category");
            empty.setRemark("No articles");
            new TypeRepository(db.dataSource()).insert(empty);
            var data = DaggerDataComponent.factory().create(db::dataSource, () -> true);
            ExecutorService caller = Executors.newSingleThreadExecutor();
            ExecutorService workers = Executors.newFixedThreadPool(4);
            CountDownLatch releaseInitialLoads = new CountDownLatch(1);
            CountDownLatch initialLoadQueued = new CountDownLatch(1);
            CountDownLatch categoryLoadsQueued = new CountDownLatch(2);
            BlockingQueue<Runnable> deferredLoads = new LinkedBlockingQueue<>();
            AtomicReference<Thread> loadingThread = new AtomicReference<>();
            Executor executor =
                    task -> {
                        if (Thread.currentThread() == loadingThread.get()) {
                            initialLoadQueued.countDown();
                            workers.execute(
                                    () -> {
                                        try {
                                            if (!releaseInitialLoads.await(5, TimeUnit.SECONDS)) {
                                                throw new AssertionError(
                                                        "Initial cache loads were not released");
                                            }
                                            task.run();
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new AssertionError(e);
                                        }
                                    });
                        } else {
                            deferredLoads.add(task);
                            categoryLoadsQueued.countDown();
                        }
                    };
            try {
                Future<BaseDataInitVO> refresh =
                        caller.submit(
                                () -> {
                                    loadingThread.set(Thread.currentThread());
                                    return data.baseData().queryCacheInit(executor);
                                });
                assertTrue(initialLoadQueued.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> refresh.get(200, TimeUnit.MILLISECONDS));
                releaseInitialLoads.countDown();
                assertTrue(categoryLoadsQueued.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> refresh.get(200, TimeUnit.MILLISECONDS));
                Runnable task;
                while ((task = deferredLoads.poll()) != null) {
                    workers.execute(task);
                }
                BaseDataInitVO init = refresh.get(5, TimeUnit.SECONDS);
                assertEquals(2, init.getTags().size());
                assertEquals(2, init.getTypeHotLogs().size());
                assertEquals("tech", init.getTypeHotLogs().get(0).getTypeAlias());
                assertEquals(2, init.getTypeHotLogs().get(0).getLogs().size());
                assertEquals("empty", init.getTypeHotLogs().get(1).getTypeAlias());
                assertTrue(init.getTypeHotLogs().get(1).getLogs().isEmpty());
            } finally {
                releaseInitialLoads.countDown();
                caller.shutdownNow();
                workers.shutdownNow();
                assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS));
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void shouldRefreshAndPersistCacheInitDataThroughWebsiteTable() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);

            CacheServiceImpl cacheService =
                    ((CacheServiceImpl)
                            DaggerDataComponent.factory()
                                    .create(() -> db.dataSource(), () -> true)
                                    .cache());
            BaseDataInitVO init = cacheService.getInitData();
            Long firstVersion = init.getVersion();
            BaseDataInitVO refreshed = cacheService.refreshInitData();
            Map<String, Object> savedCache =
                    db.queryOne(
                            "select value from website where name=?", "base_data_init_cache_v3");

            assertEquals(42L, cacheService.getCurrentSqlVersion());
            assertTrue(cacheService.getWebSiteVersion() != 0);
            assertEquals("Tech", cacheService.getArticleTypes().get(0).getTypeName());
            assertTrue(
                    cacheService.getTags().stream().map(TagDTO::getText).anyMatch("java"::equals));
            assertEquals("admin", cacheService.getUserInfoById(1L).getUserName());
            assertEquals("bar", cacheService.getTemplateConfigMapWithCache("default").get("foo"));
            assertNotNull(savedCache);
            assertNotNull(savedCache.get("value"));
            assertFalse(savedCache.get("value").toString().isEmpty());
            assertTrue(refreshed.getVersion() > firstVersion);
        }
    }

    @Test
    public void shouldMergeTagCaseVariantsAndCountEachPublicArticleOnce() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            LogRepository articles = new LogRepository(db.dataSource());
            Log first = articles.loadById(1L);
            first.setKeywords("Silent Hill,SILENT HILL,silent hill");
            articles.update(first);
            Log second = articles.loadById(2L);
            second.setKeywords("SILENT HILL");
            articles.update(second);
            CacheService cache =
                    DaggerDataComponent.factory().create(() -> db.dataSource(), () -> true).cache();

            BaseDataInitVO refreshed = cache.refreshInitData();

            assertEquals(1, refreshed.getTags().size());
            TagDTO tag = refreshed.getTags().get(0);
            assertTrue("Silent Hill".equalsIgnoreCase(tag.getText()));
            assertTrue(
                    List.of("Silent Hill", "SILENT HILL", "silent hill").contains(tag.getText()));
            assertEquals(Long.valueOf(2), tag.getCount());
            assertEquals(Long.valueOf(tag.getText().hashCode()), tag.getKeycode());
            assertEquals(1L, refreshed.getStatistics().getTotalTagSize().longValue());
            assertEquals(1, db.queryList("select text,count from tag").size());
            assertEquals(
                    2, ((Number) db.queryOne("select count from tag").get("count")).intValue());
            CacheService reloaded =
                    DaggerDataComponent.factory().create(() -> db.dataSource(), () -> true).cache();
            assertEquals(1, reloaded.getTags().size());
            assertEquals(tag.getText(), reloaded.getTags().get(0).getText());
            assertEquals(Long.valueOf(2), reloaded.getTags().get(0).getCount());
        }
    }

    @Test
    public void shouldRemoveEmptyDataPluginsFromCacheInit() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            insertPlugin(db, 1, "tags");
            insertPlugin(db, 2, "archives");
            insertPlugin(db, 3, "types");
            insertPlugin(db, 4, "links");
            insertPlugin(db, 5, "other");

            BaseDataInitVO init =
                    DaggerDataComponent.factory()
                            .create(() -> db.dataSource(), () -> true)
                            .baseData()
                            .queryCacheInit(Runnable::run);

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
            try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
                db.update("drop table " + table);

                BaseDataInitVO init =
                        DaggerDataComponent.factory()
                                .create(() -> db.dataSource(), () -> true)
                                .baseData()
                                .queryCacheInit(Runnable::run);

                assertNotNull(init);
                assertNotNull(init.getWebSite());
            }
        }
    }
}
