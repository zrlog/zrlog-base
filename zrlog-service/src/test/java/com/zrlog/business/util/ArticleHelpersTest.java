package com.zrlog.business.util;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSiteInfoProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ArticleHelpersTest {

    private final ZrLogConfig previousConfig = Constants.zrLogConfig;

    @After
    public void tearDown() {
        Constants.zrLogConfig = previousConfig;
    }

    @Test
    public void shouldSkipEmptyKeywordWithoutRequiringGlobalConfig() {
        ArticleBasicDTO article = article("ZrLog title", "digest", "<p>content</p>");
        PageData<ArticleBasicDTO> pageData = new PageData<>(1L, Arrays.asList(article));

        ArticleHelpers.wrapperSearchKeyword(pageData, "");

        assertEquals("ZrLog title", article.getTitle());
        assertEquals("digest", article.getDigest());
    }

    @Test
    public void shouldSkipEmptyRowsWithoutRequiringGlobalConfig() {
        PageData<ArticleBasicDTO> pageData = new PageData<>(0L, null);

        ArticleHelpers.wrapperSearchKeyword(pageData, "zrlog");

        assertNull(pageData.getRows());
    }

    @Test
    public void shouldHighlightTitleAndDigestWhenDigestContainsKeyword() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(120);
        ArticleBasicDTO article = article("About ZrLog", "ZrLog digest", "<p>body</p>");

        ArticleHelpers.wrapperSearchKeyword(new PageData<>(1L, Arrays.asList(article)), "ZrLog");

        assertEquals("About <font color=\"#CC0000\">ZrLog</font>", article.getTitle());
        assertEquals("<font color=\"#CC0000\">ZrLog</font> digest", article.getDigest());
    }

    @Test
    public void shouldFallbackToPlainContentWhenDigestDoesNotContainKeyword() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(120);
        ArticleBasicDTO article = article("Plain title", "short digest", "<p>Hello <strong>ZrLog</strong></p>");

        ArticleHelpers.wrapperSearchKeyword(new PageData<>(1L, Arrays.asList(article)), "ZrLog");

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

    private static class TestZrLogConfig extends ZrLogConfig {
        private CacheService cacheService;

        @Override
        public <T> T getWebSetup(Class<T> capability) {
            if (capability == WebSiteInfoProvider.class) {
                return capability.cast((WebSiteInfoProvider) cacheService::getPublicWebSiteInfo);
            }
            return super.getWebSetup(capability);
        }


        TestZrLogConfig(long digestLength) throws Exception {
            super(18080, null, "/");
            PublicWebSiteInfo info = new PublicWebSiteInfo();
            info.setArticle_auto_digest_length(digestLength);
            this.cacheService = new TestCacheService(info);
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

        private final PublicWebSiteInfo publicWebSiteInfo;

        TestCacheService(PublicWebSiteInfo publicWebSiteInfo) {
            this.publicWebSiteInfo = publicWebSiteInfo;
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
            return null;
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            return null;
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
            return publicWebSiteInfo;
        }

        @Override
        public List<TypeDTO> getArticleTypes() {
            return null;
        }

        @Override
        public List<TagDTO> getTags() {
            return null;
        }

        @Override
        public UserBasicDTO getUserInfoById(Long userId) {
            return null;
        }

        @Override
        public Map<String, Object> getTemplateConfigMapWithCache(String template) {
            return null;
        }
    }
}
