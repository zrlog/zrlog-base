package com.zrlog.model;

import com.zrlog.data.repository.*;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.zrlog.common.cache.dto.*;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.dto.ArticleDetailDTO;
import com.zrlog.data.dto.VisitorCommentDTO;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CoreModelDatabaseTest {

    private final DatabaseType databaseType;

    public CoreModelDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    private static PageRequestImpl page() {
        return new PageRequestImpl(1L, 10L);
    }

    private static void seedContent(ZrLogTestDatabase db) throws Exception {
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

    private static void insertArticle(ZrLogTestDatabase db, int id, String alias, String title, String content,
                                      String keywords, String releaseTime, int click, boolean rubbish,
                                      boolean privacy) throws Exception {
        db.update("insert into log(logId, alias, canComment, click, version, content, plain_content, markdown,"
                        + " digest, keywords, recommended, releaseTime, last_update_date, title, typeId, userId,"
                        + " hot, rubbish, privacy, editor_type) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                        + " ?, ?, ?, ?, ?, ?)",
                id, alias, true, click, 1, content, content, content, "Digest", keywords, false,
                releaseTime, releaseTime, title, 1, 1, false, rubbish, privacy, "markdown");
    }

    @Test
    public void shouldQueryArticleModelViewsThroughRealTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            db.update("update log set extensions=? where logId=?",
                    "{\"metadata\":{\"topicIds\":[\"tech\"],\"priority\":3}}", 1);
            LogRepository log = new LogRepository(db.dataSource());

            PageData<ArticleBasicDTO> visitorPage = log.visitorFind(page(), "content");
            assertEquals(2L, visitorPage.getTotalElements());
            assertEquals("Second", visitorPage.getRows().get(0).getTitle());
            Map<String, Object> metadataExtensions =
                    (Map<String, Object>) log.findByIdOrAlias(1, true).getExtensions().get("metadata");
            assertEquals(Collections.singletonList("tech"), metadataExtensions.get("topicIds"));
            assertEquals(3.0d, metadataExtensions.get("priority"));
            assertEquals(2L, log.findByTypeAlias(1, 10, "tech").getTotalElements());
            assertEquals(1L, log.findByTag(1, 10, "zrlog").getTotalElements());
            assertEquals(2L, log.findByTag(1, 10, "java").getTotalElements());
            assertEquals(2L, log.findByDate(1, 10, "2026_01").getTotalElements());
            assertTrue(log.findByDate(1, 10, "").getRows().isEmpty());
            assertEquals(Long.valueOf(2), log.getArchives().get("2026_01"));
            assertEquals(2L, log.getVisitorCount());
        }
    }

    @Test
    public void shouldOrderAndPaginatePublicHomeArticlesByStickyPriority() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            insertArticle(db, 4, "newest", "Newest", "Newest content", "latest",
                    "2026-02-03 10:00:00", 1, false, false);
            insertArticle(db, 5, "private-sticky", "Private Sticky", "Private content", "secret",
                    "2026-02-04 10:00:00", 1, false, true);
            db.update("update log set sticky=? where logId=?", 5, 1);
            db.update("update log set sticky=? where logId=?", 5, 2);
            db.update("update log set sticky=? where logId=?", 100, 3);
            db.update("update log set sticky=? where logId=?", 100, 5);
            LogRepository log = new LogRepository(db.dataSource());

            PageData<ArticleBasicDTO> firstPage =
                    log.visitorFindHome(new PageRequestImpl(1L, 2L));
            PageData<ArticleBasicDTO> secondPage =
                    log.visitorFindHome(new PageRequestImpl(2L, 2L));
            PageData<ArticleBasicDTO> visitorPage =
                    log.visitorFind(new PageRequestImpl(1L, 10L), null);

            assertEquals(3L, firstPage.getTotalElements());
            assertEquals(2, firstPage.getRows().size());
            assertEquals("Second", firstPage.getRows().get(0).getTitle());
            assertEquals(Integer.valueOf(5), firstPage.getRows().get(0).getSticky());
            assertEquals("First", firstPage.getRows().get(1).getTitle());
            assertEquals(3L, secondPage.getTotalElements());
            assertEquals(1, secondPage.getRows().size());
            assertEquals("Newest", secondPage.getRows().get(0).getTitle());
            assertEquals(Integer.valueOf(0), secondPage.getRows().get(0).getSticky());
            assertEquals("Newest", visitorPage.getRows().get(0).getTitle());
            assertEquals("Second", visitorPage.getRows().get(1).getTitle());
        }
    }

    @Test
    public void shouldQueryArticleDetailThroughRealTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            LogRepository log = new LogRepository(db.dataSource());

            ArticleDetailDTO byId = log.findByIdOrAlias(1, false);
            ArticleDetailDTO byAlias = log.findByIdOrAlias("first", false);

            assertEquals("First", byId.getTitle());
            assertEquals("admin", byId.getUserName());
            assertEquals(1, byId.getComments().size());
            assertEquals("Reader", byId.getComments().get(0).getUserName());
            assertEquals("Second", byId.getNextLog().getTitle());
            assertEquals("First", byAlias.getTitle());
            assertEquals(null, log.findByIdOrAlias(null, false));
            assertEquals(null, log.findByIdOrAlias(999, false));
        }
    }

    @Test
    public void shouldSkipDetailCommentsWhenWebsiteDisablesComments() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);

            ArticleDetailDTO detail = new LogRepository(db.dataSource()).findByIdOrAlias(1, true);

            assertEquals("First", detail.getTitle());
            assertTrue(detail.getComments().isEmpty());
            assertEquals("Second", detail.getNextLog().getTitle());
        }
    }

    @Test
    public void shouldQueryCommentsThroughRealTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            CommentRepository comment = new CommentRepository(db.dataSource());

            List<Comment> byLog = comment.findAllByLogId(1);
            List<VisitorCommentDTO> visitorComments = comment.visitorFindAllByLogId(1);

            assertEquals(1, byLog.size());
            assertEquals("Reader", byLog.get(0).getUserName());
            assertEquals(1, visitorComments.size());
            assertFalse(visitorComments.get(0).getGravatarId().isEmpty());
        }
    }

    @Test
    public void shouldQueryNavigationLinkTypeUserPluginAndTagModelsThroughRealTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);

            List<LinkDTO> links = new LinkRepository(db.dataSource()).findAll();
            List<LogNavDTO> navs = new LogNavRepository(db.dataSource()).findAll();
            List<TypeDTO> types = new TypeRepository(db.dataSource()).findAll();
            List<UserBasicDTO> users = new UserRepository(db.dataSource()).findBasicAll();
            List<PluginDTO> plugins = new PluginRepository(db.dataSource()).findAll();
            List<TagDTO> refreshedTags = new TagRepository(db.dataSource()).refreshTag();
            Set<String> tagTexts = refreshedTags.stream().map(TagDTO::getText).collect(Collectors.toSet());

            assertEquals("ZrLog", links.get(0).getLinkName());
            assertEquals("Home", navs.get(0).getNavName());
            assertEquals("Tech", types.get(0).getTypeName());
            assertEquals(2L, types.get(0).getTypeamount().longValue());
            assertEquals("tech", new TypeRepository(db.dataSource()).findByAlias("tech").getAlias());
            assertEquals("admin", users.get(0).getUserName());
            assertNotNull(new UserRepository(db.dataSource()).getUserByUserName("admin"));
            assertEquals("password", new UserRepository(db.dataSource()).getPasswordByUserId(1));
            assertTrue(new UserRepository(db.dataSource()).updatePassword(1, "new-password"));
            new UserRepository(db.dataSource()).updateEmailUserNameHeaderByUserId("new@example.com", "new-admin", "/new.png", 1);
            assertEquals("new-password", new UserRepository(db.dataSource()).getPasswordByUserId(1));
            assertEquals("new-admin", new UserRepository(db.dataSource()).getUserByUserName("new-admin").getUserName());
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
            assertTrue(new TagRepository(db.dataSource()).updateArticleTags(null, null));
            assertTrue(new TagRepository(db.dataSource()).updateArticleTags("java,zrlog", "java,zrlog"));
            assertTrue(new TagRepository(db.dataSource()).updateArticleTags("java,zrlog,db", "java,zrlog"));
            assertEquals(3, new TagRepository(db.dataSource()).find(page()).getRows().size());
            assertTrue(new TagRepository(db.dataSource()).updateArticleTags("zrlog", "java,zrlog,db"));
            assertEquals(2, new TagRepository(db.dataSource()).find(page()).getRows().size());
            assertEquals(1, new TagRepository(db.dataSource()).findAll().stream()
                    .filter(tag -> "java".equals(tag.getText()))
                    .findFirst()
                    .orElseThrow()
                    .getCount()
                    .intValue());
            assertFalse(new TagRepository(db.dataSource()).findAll().stream().anyMatch(tag -> "db".equals(tag.getText())));
        }
    }

    @Test
    public void shouldRefreshTagsInBatchesForManyUniqueKeywords() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            insertArticle(db, 4, "many-tags", "Many Tags", "Many tag content",
                    "t01,t02,t03,t04,t05,t06,t07,t08,t09,t10,t11,t12",
                    "2026-03-01 10:00:00", 1, false, false);

            List<TagDTO> tags = new TagRepository(db.dataSource()).refreshTag();
            Set<String> texts = tags.stream().map(TagDTO::getText).collect(Collectors.toSet());

            assertEquals(14, tags.size());
            assertTrue(texts.contains("t01"));
            assertTrue(texts.contains("t12"));
            assertEquals(14L, new TagRepository(db.dataSource()).find(page()).getTotalElements());
        }
    }

}
