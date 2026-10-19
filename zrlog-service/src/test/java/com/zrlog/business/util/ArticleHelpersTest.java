package com.zrlog.business.util;

import com.hibegin.common.dao.dto.PageData;
import com.zrlog.data.dto.ArticleBasicDTO;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ArticleHelpersTest {


    @Test
    public void shouldSkipEmptyKeywordWithoutRequiringGlobalConfig() {
        ArticleBasicDTO article = article("ZrLog title", "digest", "<p>content</p>");
        PageData<ArticleBasicDTO> pageData = new PageData<>(1L, Arrays.asList(article));

        ArticleHelpers.wrapperSearchKeyword(pageData, "", 120);

        assertEquals("ZrLog title", article.getTitle());
        assertEquals("digest", article.getDigest());
    }

    @Test
    public void shouldSkipEmptyRowsWithoutRequiringGlobalConfig() {
        PageData<ArticleBasicDTO> pageData = new PageData<>(0L, null);

        ArticleHelpers.wrapperSearchKeyword(pageData, "zrlog", 120);

        assertNull(pageData.getRows());
    }

    @Test
    public void shouldHighlightTitleAndDigestWhenDigestContainsKeyword() throws Exception {
        ArticleBasicDTO article = article("About ZrLog", "ZrLog digest", "<p>body</p>");

        ArticleHelpers.wrapperSearchKeyword(new PageData<>(1L, Arrays.asList(article)), "ZrLog", 120);

        assertEquals("About <font color=\"#CC0000\">ZrLog</font>", article.getTitle());
        assertEquals("<font color=\"#CC0000\">ZrLog</font> digest", article.getDigest());
    }

    @Test
    public void shouldFallbackToPlainContentWhenDigestDoesNotContainKeyword() throws Exception {
        ArticleBasicDTO article = article("Plain title", "short digest", "<p>Hello <strong>ZrLog</strong></p>");

        ArticleHelpers.wrapperSearchKeyword(new PageData<>(1L, Arrays.asList(article)), "ZrLog", 120);

        assertEquals("Plain title", article.getTitle());
        assertEquals("Hello <font color=\"#CC0000\">ZrLog</font>", article.getDigest());
    }

    private static ArticleBasicDTO article(String title, String digest, String content) {
        ArticleBasicDTO article = new ArticleBasicDTO();
        article.setTitle(title);
        article.setDigest(digest);
        article.setContent(content);
        return article;
    }

}
