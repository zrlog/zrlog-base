package com.zrlog.data.cache;

import com.google.gson.Gson;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.support.InMemoryZrLogDatabase;
import com.zrlog.data.support.InMemoryZrLogDatabase.DatabaseType;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class CacheServiceImplDatabaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    private final DatabaseType databaseType;

    public CacheServiceImplDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Test
    public void shouldLoadExistingInitCacheFromWebsiteTable() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            Constants.zrLogConfig = testConfig(true);
            BaseDataInitVO cached = cachedInit();
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    "base_data_init_cache_v3", new Gson().toJson(cached), "");
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    "zrlogSqlVersion", "7", "");

            CacheServiceImpl service = new CacheServiceImpl();

            assertEquals(7L, service.getCurrentSqlVersion());
            assertEquals(99L, service.getWebSiteVersion());
            assertEquals("Cached Type", service.getArticleTypes().get(0).getTypeName());
            assertEquals("cached", service.getTags().get(0).getText());
            assertEquals("cached-user", service.getUserInfoById(7L).getUserName());
            assertEquals("red", service.getTemplateConfigMapWithCache("default").get("color"));
            assertEquals("Cached Site", service.getPublicWebSiteInfo().getTitle());
            assertEquals(cached.getVersion(), service.getInitData().getVersion());
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldFallbackToDatabaseWhenStoredInitCacheIsInvalid() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            Constants.zrLogConfig = testConfig(false);
            seedLookupTables(db);
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    "base_data_init_cache_v3", "{bad-json", "");
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    "default_setting", "{\"color\":\"blue\"}", "");

            CacheServiceImpl service = new CacheServiceImpl();

            assertEquals(-1L, service.getCurrentSqlVersion());
            assertEquals(0L, service.getWebSiteVersion());
            assertEquals("Db Type", service.getArticleTypes().get(0).getTypeName());
            assertEquals("db-tag", service.getTags().get(0).getText());
            assertEquals("db-user", service.getUserInfoById(1L).getUserName());
            assertEquals("blue", service.getTemplateConfigMapWithCache("default").get("color"));
            assertEquals("", service.getPublicWebSiteInfo().getTitle());
            assertFalse(service.getPublicWebSiteInfo().getDisable_comment_status());
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldReadPublicWebsiteInfoFromDatabaseWhenInstalledAndCacheIsMissing() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            Constants.zrLogConfig = testConfig(true);
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    "base_data_init_cache_v3", "{bad-json", "");
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    "title", "Installed Site", "");
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    "author", "Installed Author", "");

            PublicWebSiteInfo info = new CacheServiceImpl().getPublicWebSiteInfo();

            assertEquals("Installed Site", info.getTitle());
            assertEquals("Installed Author", info.getAuthor());
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    private static BaseDataInitVO cachedInit() {
        BaseDataInitVO init = new BaseDataInitVO();
        init.setVersion(123L);
        init.setWebSiteVersion(99L);
        PublicWebSiteInfo webSite = new PublicWebSiteInfo();
        webSite.setTitle("Cached Site");
        init.setWebSite(webSite);
        TypeDTO type = new TypeDTO();
        type.setId(7L);
        type.setAlias("cached");
        type.setTypeName("Cached Type");
        TagDTO tag = new TagDTO();
        tag.setId(7L);
        tag.setText("cached");
        tag.setCount(1L);
        UserBasicDTO user = new UserBasicDTO();
        user.setUserId(7L);
        user.setUserName("cached-user");
        init.setTypes(List.of(type));
        init.setTags(List.of(tag));
        init.setUsers(List.of(user));
        init.setTemplateConfigCacheMap(Map.of("default", Map.of("color", "red")));
        return init;
    }

    private static void seedLookupTables(InMemoryZrLogDatabase db) throws Exception {
        db.update("insert into user(userId, email, password, userName, header) values(?, ?, ?, ?, ?)",
                1, "db@example.com", "password", "db-user", "/avatar.png");
        db.update("insert into type(typeId, alias, typeName, remark) values(?, ?, ?, ?)",
                1, "db", "Db Type", "Db posts");
        db.update("insert into tag(tagId, text, count) values(?, ?, ?)",
                1, "db-tag", 2);
    }

    private static ZrLogConfig testConfig(boolean installed) throws Exception {
        TestZrLogConfig config = allocate(TestZrLogConfig.class);
        Field installedField = TestZrLogConfig.class.getDeclaredField("installed");
        installedField.setAccessible(true);
        installedField.set(config, installed);
        return config;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((Unsafe) field.get(null)).allocateInstance(type);
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private boolean installed;

        private TestZrLogConfig() {
            super(0, null, "");
        }

        @Override
        public boolean isInstalled() {
            return installed;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }

        @Override
        public <T extends IPlugin> List<T> getPluginsByClazz(Class<T> pluginClass) {
            return Collections.emptyList();
        }
    }
}
