package com.zrlog.common;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ZrLogConfigTest {

    @Test
    public void shouldInitializeServerRequestAndResponseDefaults() {
        TestZrLogConfig config = new TestZrLogConfig(false);

        ServerConfig serverConfig = config.getServerConfig();
        assertEquals("zrlog", serverConfig.getApplicationName());
        assertEquals(Integer.valueOf(19080), serverConfig.getPort());
        assertEquals("/blog", serverConfig.getContextPath());
        assertTrue(serverConfig.isDisableSession());
        assertTrue(serverConfig.isDisablePrintWebServerInfo());
        assertEquals("ZRLOG_PID_FILE", serverConfig.getPidFilePathEnvKey());
        assertEquals("ZRLOG_HTTP_PORT_FILE", serverConfig.getServerPortFilePathEnvKey());
        assertNotNull(serverConfig.getHttpJsonMessageConverter());
        assertNotNull(serverConfig.getErrorHandle(404));
        assertEquals(2, serverConfig.getOnCreateSuccessHandles().size());

        RequestConfig requestConfig = config.getRequestConfig();
        assertTrue(requestConfig.isDisableSession());
        assertSame(serverConfig.getRouter(), requestConfig.getRouter());
        assertEquals(1024 * 1024 * 1024, requestConfig.getMaxRequestBodySize());

        ResponseConfig responseConfig = config.getResponseConfig();
        assertEquals("utf-8", responseConfig.getCharSet());
        assertTrue(responseConfig.isEnableGzip());
        assertTrue(responseConfig.getGzipMimeTypes().contains("application/json"));
        assertFalse(config.isInstalled());
        assertNotNull(config.getProgramUptime());
        assertTrue(config.getCreatedDate() > 0);
    }

    @Test
    public void shouldExposePluginsAndLazyTokenService() {
        TestZrLogConfig config = new TestZrLogConfig(true);
        FakePlugin started = new FakePlugin(true, true);
        FakePlugin inactive = new FakePlugin(true, false);
        FakePlugin manual = new FakePlugin(false, false);
        config.basePlugins.add(started);
        config.basePlugins.add(inactive);
        config.basePlugins.add(manual);

        config.startPlugins(false);

        assertEquals(0, started.startCount);
        assertEquals(1, inactive.startCount);
        assertEquals(0, manual.startCount);
        assertSame(started, config.getPlugin(FakePlugin.class));
        assertEquals(3, config.getPluginsByClazz(FakePlugin.class).size());
        assertSame(config.tokenService, config.getTokenService());
        assertSame(config.tokenService, config.getTokenService());
    }

    @Test
    public void shouldSkipPluginStartupWhenNotInstalled() {
        TestZrLogConfig config = new TestZrLogConfig(false);
        FakePlugin plugin = new FakePlugin(true, false);
        config.basePlugins.add(plugin);

        config.startPlugins(false);

        assertEquals(0, plugin.startCount);
        assertTrue(config.getAllPlugins().isEmpty());
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private final Plugins basePlugins = new Plugins();
        private final TokenService tokenService = new NoopTokenService();
        private boolean installed;

        TestZrLogConfig(boolean installed) {
            super(19080, null, "/blog");
            this.installed = installed;
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
            return tokenService;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return basePlugins;
        }
    }

    private static class FakePlugin implements IPlugin {

        private final boolean autoStart;
        private boolean started;
        private int startCount;

        private FakePlugin(boolean autoStart, boolean started) {
            this.autoStart = autoStart;
            this.started = started;
        }

        @Override
        public boolean start() {
            startCount++;
            started = true;
            return true;
        }

        @Override
        public boolean autoStart() {
            return autoStart;
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public boolean stop() {
            started = false;
            return true;
        }
    }

    private static class NoopTokenService implements TokenService {

        @Override
        public void updateSessionTimeout(long sessionTimeoutInMinutes) {
        }

        @Override
        public AdminFullTokenVO getAdminTokenVO(HttpRequest request) {
            return null;
        }

        @Override
        public void removeAdminToken(HttpRequest request, HttpResponse response) {
        }

        @Override
        public void setAdminToken(Integer userId, String secretKey, String sessionId, String protocol,
                                  HttpRequest request, HttpResponse response) {
        }
    }
}
