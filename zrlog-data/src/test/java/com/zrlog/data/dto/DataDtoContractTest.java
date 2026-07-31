package com.zrlog.data.dto;

import com.zrlog.common.vo.Outline;
import com.zrlog.data.exception.DAOException;
import org.junit.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class DataDtoContractTest {

    @Test
    public void shouldExposeArticleBasicFields() {
        ArticleDetailDTO.TagsDTO tag = new ArticleDetailDTO.TagsDTO();
        tag.setName("java");
        tag.setUrl("/tag/java");
        ArticleBasicDTO dto = new ArticleBasicDTO();

        assertEquals(Integer.valueOf(0), dto.getSticky());
        dto.setId(1L);
        dto.setLogId(2L);
        dto.setAlias("alias");
        dto.setCanComment(true);
        dto.setClick(3L);
        dto.setVersion(4L);
        dto.setContent("content");
        dto.setPlain_content("plain");
        dto.setMarkdown("markdown");
        dto.setDigest("digest");
        dto.setKeywords("java,zrlog");
        dto.setRecommended(true);
        dto.setSticky(8);
        dto.setReleaseTime("2026-01-01");
        dto.setFullReleaseTime("2026-01-01T00:00:00+08:00");
        dto.setLast_update_date("2026-01-02");
        dto.setLastUpdateDate("2026-01-02");
        dto.setFullLastUpdateDate("2026-01-02T00:00:00+08:00");
        dto.setTitle("Title");
        dto.setTypeId(5L);
        dto.setUserId(6L);
        dto.setHot(true);
        dto.setRubbish(false);
        dto.setPrivacy(false);
        dto.setUserName("admin");
        dto.setHeader("/avatar.png");
        dto.setCommentSize(7L);
        dto.setTypeAlias("tech");
        dto.setTypeName("Tech");
        dto.setArrange_plugin("plugin");
        dto.setTypeUrl("/sort/tech");
        dto.setUrl("/post");
        dto.setThumbnail("/thumb.png");
        dto.setThumbnailAlt("alt");
        dto.setNoSchemeUrl("//example.com/post");
        dto.setCommentUrl("/post#comments");
        dto.setTags(List.of(tag));

        assertEquals(Long.valueOf(1), dto.getId());
        assertEquals(Long.valueOf(2), dto.getLogId());
        assertEquals("alias", dto.getAlias());
        assertEquals(Boolean.TRUE, dto.getCanComment());
        assertEquals(Long.valueOf(3), dto.getClick());
        assertEquals(Long.valueOf(4), dto.getVersion());
        assertEquals("content", dto.getContent());
        assertEquals("plain", dto.getPlain_content());
        assertEquals("markdown", dto.getMarkdown());
        assertEquals("digest", dto.getDigest());
        assertEquals("java,zrlog", dto.getKeywords());
        assertEquals(Boolean.TRUE, dto.getRecommended());
        assertEquals(Integer.valueOf(8), dto.getSticky());
        assertEquals("2026-01-01", dto.getReleaseTime());
        assertEquals("2026-01-01T00:00:00+08:00", dto.getFullReleaseTime());
        assertEquals("2026-01-02", dto.getLast_update_date());
        assertEquals("2026-01-02", dto.getLastUpdateDate());
        assertEquals("2026-01-02T00:00:00+08:00", dto.getFullLastUpdateDate());
        assertEquals("Title", dto.getTitle());
        assertEquals(Long.valueOf(5), dto.getTypeId());
        assertEquals(Long.valueOf(6), dto.getUserId());
        assertEquals(Boolean.TRUE, dto.getHot());
        assertEquals(Boolean.FALSE, dto.getRubbish());
        assertEquals(Boolean.FALSE, dto.getPrivacy());
        assertEquals("admin", dto.getUserName());
        assertEquals("/avatar.png", dto.getHeader());
        assertEquals(Long.valueOf(7), dto.getCommentSize());
        assertEquals("tech", dto.getTypeAlias());
        assertEquals("Tech", dto.getTypeName());
        assertEquals("plugin", dto.getArrange_plugin());
        assertEquals("/sort/tech", dto.getTypeUrl());
        assertEquals("/post", dto.getUrl());
        assertEquals("/thumb.png", dto.getThumbnail());
        assertEquals("alt", dto.getThumbnailAlt());
        assertEquals("//example.com/post", dto.getNoSchemeUrl());
        assertEquals("/post#comments", dto.getCommentUrl());
        assertSame(tag, dto.getTags().get(0));
        assertEquals("java", dto.getTags().get(0).getName());
        assertEquals("/tag/java", dto.getTags().get(0).getUrl());
    }

    @Test
    public void shouldExposeArticleDetailNestedFields() {
        ArticleDetailDTO detail = new ArticleDetailDTO();
        ArticleDetailDTO.LastLogDTO last = new ArticleDetailDTO.LastLogDTO();
        ArticleDetailDTO.NextLogDTO next = new ArticleDetailDTO.NextLogDTO();
        VisitorCommentDTO comment = new VisitorCommentDTO();
        Outline outline = new Outline();
        last.setTitle("Last");
        last.setAlias("last");
        last.setUrl("/last");
        next.setTitle("Next");
        next.setAlias("next");
        next.setUrl("/next");

        detail.setLastLog(last);
        detail.setNextLog(next);
        detail.setComments(List.of(comment));
        detail.setTocHtml("<ol></ol>");
        detail.setToc(List.of(outline));
        detail.setContentProtectorHtml("<div></div>");

        assertSame(last, detail.getLastLog());
        assertSame(next, detail.getNextLog());
        assertEquals("Last", detail.getLastLog().getTitle());
        assertEquals("last", detail.getLastLog().getAlias());
        assertEquals("/last", detail.getLastLog().getUrl());
        assertEquals("Next", detail.getNextLog().getTitle());
        assertEquals("next", detail.getNextLog().getAlias());
        assertEquals("/next", detail.getNextLog().getUrl());
        assertSame(comment, detail.getComments().get(0));
        assertEquals("<ol></ol>", detail.getTocHtml());
        assertSame(outline, detail.getToc().get(0));
        assertEquals("<div></div>", detail.getContentProtectorHtml());
    }

    @Test
    public void shouldExposeCommentAndFaviconFields() {
        CommentDTO comment = new CommentDTO();
        comment.setId(1L);
        comment.setUserComment("comment");
        comment.setHeader("/avatar.png");
        comment.setCommTime("2026-01-01");
        comment.setUserMail("mail@example.com");
        comment.setUserHome("https://example.com");
        comment.setUserIp("127.0.0.1");
        comment.setUserName("Reader");
        comment.setHide(false);
        comment.setLogId(2L);
        VisitorCommentDTO visitor = new VisitorCommentDTO();
        visitor.setId(3L);
        visitor.setUserComment("visitor");
        visitor.setHeader("/visitor.png");
        visitor.setCommTime("2026-01-02");
        visitor.setUserHome("https://visitor.example.com");
        visitor.setUserName("Visitor");
        visitor.setGravatarId("hash");
        FaviconBase64DTO favicon = new FaviconBase64DTO();
        favicon.setGenerator_html_status("true");
        favicon.setFavicon_ico_base64("ico");
        favicon.setFavicon_png_pwa_192_base64("192");
        favicon.setFavicon_png_pwa_512_base64("512");

        assertEquals(Long.valueOf(1), comment.getId());
        assertEquals("comment", comment.getUserComment());
        assertEquals("/avatar.png", comment.getHeader());
        assertEquals("2026-01-01", comment.getCommTime());
        assertEquals("mail@example.com", comment.getUserMail());
        assertEquals("https://example.com", comment.getUserHome());
        assertEquals("127.0.0.1", comment.getUserIp());
        assertEquals("Reader", comment.getUserName());
        assertEquals(Boolean.FALSE, comment.getHide());
        assertEquals(Long.valueOf(2), comment.getLogId());
        assertEquals(Long.valueOf(3), visitor.getId());
        assertEquals("visitor", visitor.getUserComment());
        assertEquals("/visitor.png", visitor.getHeader());
        assertEquals("2026-01-02", visitor.getCommTime());
        assertEquals("https://visitor.example.com", visitor.getUserHome());
        assertEquals("Visitor", visitor.getUserName());
        assertEquals("hash", visitor.getGravatarId());
        assertEquals("true", favicon.getGenerator_html_status());
        assertEquals("ico", favicon.getFavicon_ico_base64());
        assertEquals("192", favicon.getFavicon_png_pwa_192_base64());
        assertEquals("512", favicon.getFavicon_png_pwa_512_base64());
    }

    @Test
    public void shouldExposeDaoExceptionContract() {
        DAOException exception = new DAOException(new SQLException("db"));
        DAOException defaultException = new DAOException();

        assertEquals(9101, exception.getError());
        assertNotNull(exception.getMessage());
        assertEquals(9101, defaultException.getError());
    }
}
