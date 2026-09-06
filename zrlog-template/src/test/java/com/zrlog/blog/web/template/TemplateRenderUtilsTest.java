package com.zrlog.blog.web.template;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.StringJoiner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TemplateRenderUtilsTest {

    @Test
    public void shouldAppendListTypeAndNameToTitle() {
        StringJoiner title = new StringJoiner(" - ");

        TemplateRenderUtils.appendListPageTitle(title, "Category", "Java");

        assertEquals("Category - Java", title.toString());
    }

    @Test
    public void shouldSkipMissingListTitleParts() {
        StringJoiner title = new StringJoiner(" - ");

        TemplateRenderUtils.appendListPageTitle(title, "Tags", "");
        TemplateRenderUtils.appendListPageTitle(title, null, null);

        assertEquals("Tags", title.toString());
    }

    @Test
    public void shouldResolveNotFoundPageTitle() {
        assertFalse(TemplateRenderUtils.notFoundPageTitle().isEmpty());
    }

    @Test
    public void shouldKeepHomeNavigationInsideContextPath() {
        LogNavDTO home = new LogNavDTO();
        home.setUrl("/");
        BaseDataInitVO init = new BaseDataInitVO();
        init.setLogNavs(List.of(home));
        ZrLogConfig previousConfig = Constants.zrLogConfig;

        try {
            Constants.zrLogConfig = new TestZrLogConfig();
            TemplateRenderUtils.fullNavBar(request("/blog", "example.com", "/"), "", init);
        } finally {
            Constants.zrLogConfig = previousConfig;
        }

        assertEquals("//example.com/blog/", home.getUrl());
        assertTrue(home.getCurrent());
    }

    private static HttpRequest request(String contextPath, String host, String uri) {
        return (HttpRequest) Proxy.newProxyInstance(
                TemplateRenderUtilsTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getContextPath".equals(method.getName())) {
                        return contextPath;
                    }
                    if ("getHeader".equals(method.getName()) && "Host".equals(args[0])) {
                        return host;
                    }
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private TestZrLogConfig() {
            super(19083, null, "/");
        }

        @Override
        public boolean isInstalled() {
            return false;
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
}
