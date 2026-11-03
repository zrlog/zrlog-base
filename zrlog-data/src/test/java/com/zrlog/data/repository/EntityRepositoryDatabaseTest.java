package com.zrlog.data.repository;

import com.hibegin.common.dao.entity.OptimisticLockException;
import com.zrlog.model.Comment;
import com.zrlog.model.Log;
import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class EntityRepositoryDatabaseTest {
    private final String backend;
    public EntityRepositoryDatabaseTest(String backend) { this.backend = backend; }
    @Parameterized.Parameters(name = "{0}")
    public static Object[] backends() { return new Object[]{"H2", "SQLITE", "WEB_API"}; }
    private ZrLogTestDatabase open() throws Exception {
        return backend.equals("WEB_API") ? ZrLogTestDatabase.openWebApi()
                : ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.valueOf(backend));
    }

    @Test
    public void projectionsCannotBeUsedToLoadPartialEntitiesOrUntypedRows() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            LogRepository repository = new LogRepository(db.dataSource());
            assertThrows(IllegalArgumentException.class,
                    () -> repository.queryOne("SELECT title FROM log", Log.class));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.queryList("SELECT title FROM log", java.util.Map.class));
            assertNull(repository.loadById(-1L));
        }
    }

    @Test
    public void articleWritesPreservePublicationTimeAndRejectConcurrentChanges() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            LogRepository repository = new LogRepository(db.dataSource());
            Log article = new Log();
            article.setId(7L);
            article.setTitle("original");
            article.setContent("body");
            article.setReleaseTime(Timestamp.valueOf("2013-01-01 12:00:00"));
            repository.insert(article);
            assertEquals(Long.valueOf(0), article.getVersion());
            assertTrue(article.getCreatedAt().getTime() > article.getReleaseTime().getTime());
            Log first = repository.loadById(7L);
            Log stale = repository.loadById(7L);
            first.setTitle("saved");
            assertTrue(repository.update(first));
            assertEquals(Long.valueOf(1), first.getVersion());
            stale.setContent("stale body");
            assertThrows(OptimisticLockException.class, () -> repository.update(stale));
            Log saved = repository.loadById(7L);
            assertEquals("saved", saved.getTitle());
            assertEquals("body", saved.getContent());
            assertEquals(article.getReleaseTime(), saved.getReleaseTime());
            assertEquals(article.getCreatedAt(), saved.getCreatedAt());
            AtomicInteger writes = new AtomicInteger();
            db.beforeWebApiUpdate((sql, params) -> writes.incrementAndGet());
            assertFalse(repository.update(saved));
            assertEquals(0, writes.get());
            assertEquals(Long.valueOf(1), saved.getVersion());
        }
    }

    @Test
    public void importedCommentDisplayTimeIsSeparateFromRowCreation() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            CommentRepository repository = new CommentRepository(db.dataSource());
            Comment comment = new Comment();
            comment.setCommTime(Timestamp.valueOf("2013-01-01 12:00:00"));
            comment.setUserComment("imported comment");
            repository.insert(comment);
            Comment loaded = repository.findById(comment.getId()).orElseThrow();
            assertEquals(comment.getCommTime(), loaded.getCommTime());
            assertTrue(loaded.getCreatedAt().getTime() > loaded.getCommTime().getTime());
            Timestamp created = loaded.getCreatedAt();
            loaded.setHide(true);
            repository.update(loaded);
            Comment saved = repository.findById(comment.getId()).orElseThrow();
            assertEquals(created, saved.getCreatedAt());
            assertEquals(comment.getCommTime(), saved.getCommTime());
            assertEquals(Long.valueOf(1), saved.getVersion());
            assertTrue(saved.getHide());
        }
    }

    @Test
    public void historicalUnknownCreationTimeAndAuthorizationVersionRemainIndependent() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            db.update("INSERT INTO user(userId,userName,authVersion) VALUES(?,?,?)", 1, "existing", 7);
            UserRepository repository = new UserRepository(db.dataSource());
            var row = repository.loadById(1L);
            row.setEmail("saved@example.com");
            repository.update(row);
            assertNull(row.getCreatedAt());
            assertEquals(Integer.valueOf(7), row.getAuthVersion());
            repository.updatePassword(1, "new-password");
            row.setEmail("stale@example.com");
            assertThrows(OptimisticLockException.class, () -> repository.update(row));
            var saved = repository.loadById(1L);
            assertEquals("saved@example.com", saved.getEmail());
            assertEquals("new-password", saved.getPassword());
            assertEquals(Integer.valueOf(7), saved.getAuthVersion());
            assertEquals(Long.valueOf(2), saved.getVersion());
        }
    }
}
