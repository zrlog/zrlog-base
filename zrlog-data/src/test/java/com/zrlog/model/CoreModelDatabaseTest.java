package com.zrlog.model;

import com.hibegin.common.dao.dto.Direction;
import com.hibegin.common.dao.dto.OrderBy;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.common.cache.dto.PluginDTO;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.dto.ArticleDetailDTO;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.data.dto.VisitorCommentDTO;
import com.zrlog.data.support.InMemoryZrLogDatabase;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CoreModelDatabaseTest {

    @Test
    public void shouldQueryArticleModelViewsThroughRealTables() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedContent(db);
            db.update("update log set extensions=? where logId=?",
                    "{\"metadata\":{\"topicIds\":[\"tech\"],\"priority\":3}}", 1);
            Log log = new Log();

            PageData<ArticleBasicDTO> visitorPage = log.visitorFind(page(), "content");
            PageData<ArticleBasicDTO> adminPublished = log.adminFind(page(), "First", "tech", "published");
            PageRequestImpl clickAsc = page();
            clickAsc.setOrders(List.of(new OrderBy("click", Direction.ASC)));
            PageRequestImpl defaultSortAsc = page();
            defaultSortAsc.setOrders(List.of(new OrderBy("unknown", Direction.ASC)));
            PageData<ArticleBasicDTO> adminClickAsc = log.adminFind(clickAsc, null, null);
            PageData<ArticleBasicDTO> adminDefaultSortAsc = log.adminFind(defaultSortAsc, null, null);
            ArticleBasicDTO adminDetail = log.adminFindByIdOrAlias(1);
            ArticleBasicDTO adminDetailByLongId = log.adminFindByIdOrAlias(1L);

            assertEquals(3L, log.findMaxId());
            assertEquals(2L, visitorPage.getTotalElements());
            assertEquals("Second", visitorPage.getRows().get(0).getTitle());
            assertEquals(1L, adminPublished.getTotalElements());
            assertEquals("Draft", adminClickAsc.getRows().get(0).getTitle());
            assertEquals("First", adminDefaultSortAsc.getRows().get(0).getTitle());
            assertEquals("First", adminDetail.getTitle());
            assertEquals("First", adminDetailByLongId.getTitle());
            Map<String, Object> metadataExtensions =
                    (Map<String, Object>) adminDetail.getExtensions().get("metadata");
            assertEquals(Collections.singletonList("tech"), metadataExtensions.get("topicIds"));
            assertEquals(3.0d, metadataExtensions.get("priority"));
            assertEquals("admin", adminDetail.getUserName());
            assertEquals(2L, log.findByTypeAlias(1, 10, "tech").getTotalElements());
            assertEquals(1L, log.findByTag(1, 10, "zrlog").getTotalElements());
            assertEquals(2L, log.findByTag(1, 10, "java").getTotalElements());
            assertEquals(2L, log.findByDate(1, 10, "2026_01").getTotalElements());
            assertTrue(log.findByDate(1, 10, "").getRows().isEmpty());
            assertEquals(Long.valueOf(2), log.getArchives().get("2026_01"));
            assertEquals(Long.valueOf(1), log.getAdminArticleData().get("2026-01-15"));
            assertEquals(new BigDecimal(12), log.sumClick());
            assertEquals(2L, log.getVisitorCount());
            assertEquals(3L, log.countByTypeId(1));
            assertEquals(3L, log.getAdminCount());
        }
    }

    @Test
    public void shouldFilterAdminArticlesByDraftPrivateAndPublishedStatus() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedContent(db);
            insertArticle(db, 4, "private", "Private", "Private content", "secret",
                    "2026-02-02 10:00:00", 1, false, true);
            Log log = new Log();

            assertEquals(2L, log.adminFind(page(), null, null, "published").getTotalElements());
            assertEquals("Private", log.adminFind(page(), null, null, "private").getRows().get(0).getTitle());
            assertEquals("Draft", log.adminFind(page(), null, null, "draft").getRows().get(0).getTitle());
            assertEquals(4L, log.adminFind(page(), null, null, "unknown").getTotalElements());
            assertEquals("Private", log.adminFindByIdOrAlias("private").getTitle());
        }
    }

    @Test
    public void shouldQueryArticleDetailThroughRealTables() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedContent(db);
            Constants.zrLogConfig = testConfig();
            Log log = new Log();

            ArticleDetailDTO byId = log.findByIdOrAlias(1);
            ArticleDetailDTO byAlias = log.findByIdOrAlias("first");

            assertEquals("First", byId.getTitle());
            assertEquals("admin", byId.getUserName());
            assertEquals(1, byId.getComments().size());
            assertEquals("Reader", byId.getComments().get(0).getUserName());
            assertEquals("Second", byId.getNextLog().getTitle());
            assertEquals("First", byAlias.getTitle());
            assertEquals(null, log.findByIdOrAlias(null));
            assertEquals(null, log.findByIdOrAlias(999));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldSkipDetailCommentsWhenWebsiteDisablesComments() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedContent(db);
            Constants.zrLogConfig = testConfig(true);

            ArticleDetailDTO detail = new Log().findByIdOrAlias(1);

            assertEquals("First", detail.getTitle());
            assertTrue(detail.getComments().isEmpty());
            assertEquals("Second", detail.getNextLog().getTitle());
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldQueryCommentsThroughRealTables() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedContent(db);
            Comment comment = new Comment();

            PageData<CommentDTO> page = comment.find(page());
            List<Map<String, Object>> unread = comment.findHaveReadIsFalse();
            List<Map<String, Object>> byLog = comment.findAllByLogId(1);
            List<VisitorCommentDTO> visitorComments = comment.visitorFindAllByLogId(1);
            comment.doRead(1);

            assertEquals(Long.valueOf(2), comment.count());
            assertEquals(Long.valueOf(1), comment.countToDayComment());
            assertEquals(2L, page.getTotalElements());
            assertFalse(page.getRows().get(0).getCommTime().isEmpty());
            assertEquals(1, unread.size());
            assertEquals(1, byLog.size());
            assertEquals("Reader", byLog.get(0).get("userName"));
            assertEquals(1, visitorComments.size());
            assertFalse(visitorComments.get(0).getGravatarId().isEmpty());
            assertEquals(Boolean.TRUE, db.queryOne("select have_read from comment where commentId=?", 1)
                    .get("have_read"));
        }
    }

    @Test
    public void shouldQueryNavigationLinkTypeUserPluginAndTagModelsThroughRealTables() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedContent(db);

            List<LinkDTO> links = new Link().findAll();
            List<LogNavDTO> navs = new LogNav().findAll();
            List<TypeDTO> types = new Type().findAll();
            List<UserBasicDTO> users = new User().findBasicAll();
            List<PluginDTO> plugins = new Plugin().findAll();
            List<TagDTO> refreshedTags = new Tag().refreshTag();
            Set<String> tagTexts = refreshedTags.stream().map(TagDTO::getText).collect(Collectors.toSet());

            assertEquals("ZrLog", links.get(0).getLinkName());
            assertEquals(1L, new Link().find(page()).getTotalElements());
            assertEquals("Home", navs.get(0).getNavName());
            assertEquals(1L, new LogNav().find(page()).getTotalElements());
            assertEquals("Tech", types.get(0).getTypeName());
            assertEquals(2L, types.get(0).getTypeamount().longValue());
            assertEquals(1L, new Type().find(page()).getTotalElements());
            assertEquals("tech", new Type().findByAlias("tech").get("alias"));
            assertEquals("admin", users.get(0).getUserName());
            assertNotNull(new User().getUserByUserName("admin"));
            assertEquals("password", new User().getPasswordByUserId(1));
            assertTrue(new User().updatePassword(1, "new-password"));
            new User().updateEmailUserNameHeaderByUserId("new@example.com", "new-admin", "/new.png", 1);
            assertEquals("new-password", new User().getPasswordByUserId(1));
            assertEquals("new-admin", new User().getUserByUserName("new-admin").get("userName"));
            assertEquals(1, plugins.size());
            assertEquals("Plugin", plugins.get(0).getPluginName());
            assertTrue(tagTexts.contains("java"));
            assertTrue(tagTexts.contains("zrlog"));
            assertEquals(2L, refreshedTags.stream()
                    .filter(tag -> "java".equals(tag.getText()))
                    .findFirst()
                    .orElseThrow()
                    .getCount()
                    .longValue());
            assertTrue(new Tag().update(null, null));
            assertTrue(new Tag().update("java,zrlog", "java,zrlog"));
            assertTrue(new Tag().update("java,zrlog,db", "java,zrlog"));
            assertEquals(3, new Tag().find(page()).getRows().size());
            assertTrue(new Tag().update("zrlog", "java,zrlog,db"));
            assertEquals(2, new Tag().find(page()).getRows().size());
            assertEquals(1, new Tag().findAll().stream()
                    .filter(tag -> "java".equals(tag.getText()))
                    .findFirst()
                    .orElseThrow()
                    .getCount()
                    .intValue());
            assertFalse(new Tag().findAll().stream().anyMatch(tag -> "db".equals(tag.getText())));
        }
    }

    @Test
    public void shouldRefreshTagsInBatchesForManyUniqueKeywords() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            seedContent(db);
            insertArticle(db, 4, "many-tags", "Many Tags", "Many tag content",
                    "t01,t02,t03,t04,t05,t06,t07,t08,t09,t10,t11,t12",
                    "2026-03-01 10:00:00", 1, false, false);

            List<TagDTO> tags = new Tag().refreshTag();
            Set<String> texts = tags.stream().map(TagDTO::getText).collect(Collectors.toSet());

            assertEquals(14, tags.size());
            assertTrue(texts.contains("t01"));
            assertTrue(texts.contains("t12"));
            assertEquals(14L, new Tag().find(page()).getTotalElements());
        }
    }

    private static PageRequestImpl page() {
        return new PageRequestImpl(1L, 10L);
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
        db.update("insert into plugin(pluginId, content, isSystem, pTitle, sort, pluginName, level)"
                        + " values(?, ?, ?, ?, ?, ?, ?)",
                1, "{}", false, "Plugin", 1, "Plugin", 1);
        insertArticle(db, 1, "first", "First", "First content", "java,zrlog",
                "2026-01-15 10:00:00", 5, false, false);
        insertArticle(db, 2, "second", "Second", "Second content", "java",
                "2026-01-20 11:00:00", 7, false, false);
        insertArticle(db, 3, "draft", "Draft", "Draft content", "draft",
                "2026-02-01 09:00:00", 0, true, false);
        db.update("insert into comment(commentId, commTime, have_read, userComment, userMail, userHome, userIp,"
                        + " userName, hide, logId, header) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                1, todayAt("09:00:00"), false, "Nice post", "reader@example.com", "https://reader.example.com",
                "127.0.0.1", "Reader", false, 1, "/reader.png");
        db.update("insert into comment(commentId, commTime, have_read, userComment, userMail, userHome, userIp,"
                        + " userName, hide, logId, header) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                2, "2026-01-15 12:00:00", true, "Old post", null, "",
                "127.0.0.2", "Old", false, 2, "");
    }

    private static String todayAt(String time) {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + " " + time;
    }

    private static void insertArticle(InMemoryZrLogDatabase db, int id, String alias, String title, String content,
                                      String keywords, String releaseTime, int click, boolean rubbish,
                                      boolean privacy) throws Exception {
        db.update("insert into log(logId, alias, canComment, click, version, content, plain_content, markdown,"
                        + " digest, keywords, recommended, releaseTime, last_update_date, title, typeId, userId,"
                        + " hot, rubbish, privacy, editor_type) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                        + " ?, ?, ?, ?, ?, ?)",
                id, alias, true, click, 1, content, content, content, "Digest", keywords, false,
                releaseTime, releaseTime, title, 1, 1, false, rubbish, privacy, "markdown");
    }

    private static ZrLogConfig testConfig() throws Exception {
        return testConfig(false);
    }

    private static ZrLogConfig testConfig(boolean disableComment) throws Exception {
        TestZrLogConfig config = allocate(TestZrLogConfig.class);
        Field cacheServiceField = ZrLogConfig.class.getDeclaredField("cacheService");
        cacheServiceField.setAccessible(true);
        cacheServiceField.set(config, new TestCacheService(disableComment));
        return config;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((Unsafe) field.get(null)).allocateInstance(type);
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private TestZrLogConfig() {
            super(0, null, "");
        }

        @Override
        public boolean isInstalled() {
            return true;
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
    }

    private static class TestCacheService implements CacheService {

        private final boolean disableComment;

        TestCacheService(boolean disableComment) {
            this.disableComment = disableComment;
        }

        @Override
        public long getCurrentSqlVersion() {
            return 0;
        }

        @Override
        public long getWebSiteVersion() {
            return 0;
        }

        @Override
        public BaseDataInitVO getInitData() {
            return new BaseDataInitVO();
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            return getInitData();
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
            PublicWebSiteInfo info = new PublicWebSiteInfo();
            info.setDisable_comment_status(disableComment);
            return info;
        }

        @Override
        public List<TypeDTO> getArticleTypes() {
            return Collections.emptyList();
        }

        @Override
        public List<TagDTO> getTags() {
            return Collections.emptyList();
        }

        @Override
        public UserBasicDTO getUserInfoById(Long userId) {
            UserBasicDTO user = new UserBasicDTO();
            user.setUserId(userId);
            user.setUserName("admin");
            return user;
        }

        @Override
        public Map<String, Object> getTemplateConfigMapWithCache(String template) {
            return Collections.emptyMap();
        }
    }
}
