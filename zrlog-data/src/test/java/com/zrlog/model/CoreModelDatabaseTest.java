package com.zrlog.model;

import static org.junit.Assert.*;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.hibegin.common.dao.dto.UnPageRequestImpl;
import com.zrlog.common.cache.dto.*;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.dto.ArticleDetailDTO;
import com.zrlog.data.dto.VisitorCommentDTO;
import com.zrlog.data.repository.*;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
        Plugin plugin = new Plugin();
        plugin.setContent("{}");
        plugin.setIsSystem(false);
        plugin.setPTitle("Plugin");
        plugin.setSort(1);
        plugin.setPluginName("Plugin");
        plugin.setLevel(1);
        new PluginRepository(db.dataSource()).insert(plugin);
        insertArticle(
                db,
                1,
                "first",
                "First",
                "First content",
                "java,zrlog",
                "2026-01-15 10:00:00",
                5,
                false,
                false);
        insertArticle(
                db,
                2,
                "second",
                "Second",
                "Second content",
                "java",
                "2026-01-20 11:00:00",
                7,
                false,
                false);
        insertArticle(
                db,
                3,
                "draft",
                "Draft",
                "Draft content",
                "draft",
                "2026-02-01 09:00:00",
                0,
                true,
                false);
        Comment comment = new Comment();
        comment.setCommTime(Timestamp.valueOf(todayAt("09:00:00")));
        comment.setHaveRead(false);
        comment.setUserComment("Nice post");
        comment.setUserMail("reader@example.com");
        comment.setUserHome("https://reader.example.com");
        comment.setUserIp("127.0.0.1");
        comment.setUserName("Reader");
        comment.setHide(false);
        comment.setLogId(1);
        comment.setHeader("/reader.png");
        CommentRepository comments = new CommentRepository(db.dataSource());
        comments.insert(comment);
        Comment old = new Comment();
        old.setCommTime(Timestamp.valueOf("2026-01-15 12:00:00"));
        old.setHaveRead(true);
        old.setUserComment("Old post");
        old.setUserHome("");
        old.setUserIp("127.0.0.2");
        old.setUserName("Old");
        old.setHide(false);
        old.setLogId(2);
        old.setHeader("");
        comments.insert(old);
    }

    private static String todayAt(String time) {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + " " + time;
    }

    private static void insertArticle(
            ZrLogTestDatabase db,
            int id,
            String alias,
            String title,
            String content,
            String keywords,
            String releaseTime,
            int click,
            boolean rubbish,
            boolean privacy)
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
        article.setRubbish(rubbish);
        article.setPrivacy(privacy);
        article.setEditorType("markdown");
        new LogRepository(db.dataSource()).insert(article);
    }

    @Test
    public void shouldQueryArticleModelViewsThroughRealTables() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            LogRepository log = new LogRepository(db.dataSource());
            Log article = log.loadById(1L);
            article.setExtensions("{\"metadata\":{\"topicIds\":[\"tech\"],\"priority\":3}}");
            log.update(article);

            PageData<ArticleBasicDTO> visitorPage = log.visitorFind(page(), "content");
            assertEquals(2L, visitorPage.getTotalElements());
            assertEquals("Second", visitorPage.getRows().get(0).getTitle());
            Map<String, Object> metadataExtensions =
                    (Map<String, Object>)
                            log.findByIdOrAlias(1, true).getExtensions().get("metadata");
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
    public void shouldAggregateArchivesWithMinuteAndSecondPrecisionDates() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            // SQLite preserves legacy date text; typed databases may normalize it on write.
            db.dataSource()
                    .getQueryRunner()
                    .update("UPDATE log SET releaseTime=? WHERE logId=?", "2026-10-30 01:33", 1);
            db.dataSource()
                    .getQueryRunner()
                    .update("UPDATE log SET releaseTime=? WHERE logId=?", "2026-10-31 02:34:56", 2);

            assertEquals(
                    Collections.singletonMap("2026_10", 2L),
                    new LogRepository(db.dataSource()).getArchives());
        }
    }

    @Test
    public void shouldOrderAndPaginatePublicHomeArticlesByStickyPriority() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            insertArticle(
                    db,
                    4,
                    "newest",
                    "Newest",
                    "Newest content",
                    "latest",
                    "2026-02-03 10:00:00",
                    1,
                    false,
                    false);
            insertArticle(
                    db,
                    5,
                    "private-sticky",
                    "Private Sticky",
                    "Private content",
                    "secret",
                    "2026-02-04 10:00:00",
                    1,
                    false,
                    true);
            LogRepository log = new LogRepository(db.dataSource());
            for (long id : new long[] {1, 2, 3, 5}) {
                Log article = log.loadById(id);
                article.setSticky(id <= 2 ? 5 : 100);
                log.update(article);
            }

            PageData<ArticleBasicDTO> firstPage = log.visitorFindHome(new PageRequestImpl(1L, 2L));
            PageData<ArticleBasicDTO> secondPage = log.visitorFindHome(new PageRequestImpl(2L, 2L));
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
    public void publicPagesKeepFiltersTotalsAndCommentsWhenRelatedRowsAreMissing()
            throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            for (int id = 4; id <= 8; id++) {
                insertArticle(
                        db,
                        id,
                        "article-" + id,
                        "Article " + id,
                        "Body " + id,
                        id == 7 ? "javascript,notjava" : "java",
                        "2026-02-03 10:00:00",
                        0,
                        false,
                        id == 4);
            }
            db.update("update log set userId=null where logId=5");
            db.update("update log set typeId=999 where logId=6");
            db.update("insert into type(typeId,alias,typeName) values(2,'other','Other')");
            db.update("update log set typeId=2 where logId=8");
            db.update("update log set sticky=100 where logId in (3,4,5,6)");
            db.update("update log set sticky=5,extensions=? where logId=1", "{\"topic\":\"java\"}");
            // commentSize has always included hidden comments; keep that existing contract.
            db.update(
                    "insert into comment(logId,userComment,hide) values(1,'Hidden comment',?)",
                    true);
            LogRepository logs = new LogRepository(db.dataSource());

            PageData<ArticleBasicDTO> first = logs.visitorFind(new PageRequestImpl(1L, 2L), null);
            PageData<ArticleBasicDTO> second = logs.visitorFind(new PageRequestImpl(2L, 2L), null);
            assertEquals(4L, first.getTotalElements());
            assertEquals(Arrays.asList(8L, 7L), articleIds(first));
            assertEquals(Arrays.asList(2L, 1L), articleIds(second));
            assertEquals("Body 8", first.getRows().get(0).getContent());
            assertEquals("admin", first.getRows().get(0).getUserName());
            assertEquals("/avatar.png", first.getRows().get(0).getHeader());
            assertEquals("other", first.getRows().get(0).getTypeAlias());
            assertEquals("Other", first.getRows().get(0).getTypeName());
            assertEquals(Long.valueOf(0), first.getRows().get(0).getCommentSize());
            assertEquals(Long.valueOf(2), second.getRows().get(1).getCommentSize());
            assertEquals("java", second.getRows().get(1).getExtensions().get("topic"));
            assertEquals(
                    Arrays.asList(1L, 8L),
                    articleIds(logs.visitorFindHome(new PageRequestImpl(1L, 2L))));
            assertEquals(3L, logs.findByTypeAlias(1, 1, "tech").getTotalElements());
            assertEquals(
                    Collections.singletonList(7L), articleIds(logs.findByTypeAlias(1, 1, "tech")));
            assertEquals(Arrays.asList(8L, 2L, 1L), articleIds(logs.findByTag(1, 10, "java")));
            assertEquals(Arrays.asList(2L, 1L), articleIds(logs.findByDate(1, 10, "2026_01")));
            assertEquals(
                    Collections.singletonList(1L), articleIds(logs.visitorFind(page(), "First")));
            assertEquals(4L, logs.getVisitorCount());
            assertEquals(
                    Arrays.asList(8L, 7L),
                    logs.findRecentBasic(2).stream()
                            .map(com.zrlog.common.cache.vo.HotLogBasicInfoEntry::getLogId)
                            .collect(Collectors.toList()));
            assertEquals(
                    Arrays.asList(7L, 2L),
                    logs.findRecentBasicByTypeAlias("tech", 2).stream()
                            .map(com.zrlog.common.cache.vo.HotLogBasicInfoEntry::getLogId)
                            .collect(Collectors.toList()));
            assertTrue(logs.findRecentBasic(0).isEmpty());
            assertTrue(logs.findRecentBasicByTypeAlias("missing", 6).isEmpty());
            PageData<ArticleBasicDTO> empty = logs.visitorFind(new PageRequestImpl(99L, 2L), null);
            assertTrue(empty.getRows().isEmpty());
            assertEquals(4L, empty.getTotalElements());
            assertEquals(0L, logs.findByTypeAlias(1, 10, "missing").getTotalElements());
        }
    }

    @Test
    public void publicListsPreserveCountOnlyAndUnpagedRequests() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            LogRepository logs = new LogRepository(db.dataSource());
            PageRequestImpl request = new PageRequestImpl(2L, 0L);
            request.setOrders(null);
            PageData<ArticleBasicDTO> countOnly = logs.visitorFind(request, null);
            assertEquals(2L, countOnly.getTotalElements());
            assertTrue(countOnly.getRows().isEmpty());
            assertEquals(Long.valueOf(2), countOnly.getPage());
            assertEquals(Long.valueOf(0), countOnly.getSize());
            assertTrue(countOnly.getSort().isEmpty());
            PageData<ArticleBasicDTO> all = logs.visitorFind(new UnPageRequestImpl(1L), null);
            assertEquals(Arrays.asList(2L, 1L), articleIds(all));
            assertEquals(2L, all.getTotalElements());
            assertEquals(Long.valueOf(1000000), all.getSize());
        }
    }

    @Test
    public void publicPagesWorkThroughSingleStatementWebApiWithoutJdbc() throws Exception {
        org.junit.Assume.assumeTrue(databaseType == DatabaseType.SQLITE);
        try (ZrLogTestDatabase db = ZrLogTestDatabase.openWebApi()) {
            seedContent(db);
            LogRepository logs = new LogRepository(db.dataSource());
            PageData<ArticleBasicDTO> data = logs.findByTypeAlias(2, 1, "tech");
            assertEquals(2L, data.getTotalElements());
            assertEquals(Collections.singletonList(1L), articleIds(data));
            assertEquals(Long.valueOf(1), data.getRows().get(0).getCommentSize());
            assertEquals(2L, logs.getVisitorCount());
        }
    }

    private static List<Long> articleIds(PageData<ArticleBasicDTO> page) {
        return page.getRows().stream().map(ArticleBasicDTO::getLogId).collect(Collectors.toList());
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
    public void shouldQueryNavigationLinkTypeUserPluginAndTagModelsThroughRealTables()
            throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);

            List<LinkDTO> links = new LinkRepository(db.dataSource()).findAll();
            List<LogNavDTO> navs = new LogNavRepository(db.dataSource()).findAll();
            List<TypeDTO> types = new TypeRepository(db.dataSource()).findAll();
            List<UserBasicDTO> users = new UserRepository(db.dataSource()).findBasicAll();
            List<PluginDTO> plugins = new PluginRepository(db.dataSource()).findAll();
            List<TagDTO> refreshedTags = new TagRepository(db.dataSource()).refreshTag();
            Set<String> tagTexts =
                    refreshedTags.stream().map(TagDTO::getText).collect(Collectors.toSet());

            assertEquals("ZrLog", links.get(0).getLinkName());
            assertEquals("Home", navs.get(0).getNavName());
            assertEquals("Tech", types.get(0).getTypeName());
            assertEquals(2L, types.get(0).getTypeamount().longValue());
            assertEquals(
                    "tech", new TypeRepository(db.dataSource()).findByAlias("tech").getAlias());
            assertEquals("admin", users.get(0).getUserName());
            assertEquals(1, plugins.size());
            assertEquals("Plugin", plugins.get(0).getPluginName());
            assertTrue(tagTexts.contains("java"));
            assertTrue(tagTexts.contains("zrlog"));
            assertEquals(
                    2L,
                    refreshedTags.stream()
                            .filter(tag -> "java".equals(tag.getText()))
                            .findFirst()
                            .orElseThrow()
                            .getCount()
                            .longValue());
        }
    }

    @Test
    public void shouldRefreshTagsInBatchesForManyUniqueKeywords() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            seedContent(db);
            insertArticle(
                    db,
                    4,
                    "many-tags",
                    "Many Tags",
                    "Many tag content",
                    "t01,t02,t03,t04,t05,t06,t07,t08,t09,t10,t11,t12",
                    "2026-03-01 10:00:00",
                    1,
                    false,
                    false);

            List<TagDTO> tags = new TagRepository(db.dataSource()).refreshTag();
            Set<String> texts = tags.stream().map(TagDTO::getText).collect(Collectors.toSet());

            assertEquals(14, tags.size());
            assertTrue(texts.contains("t01"));
            assertTrue(texts.contains("t12"));
            assertEquals(14L, new TagRepository(db.dataSource()).find(page()).getTotalElements());
        }
    }
}
