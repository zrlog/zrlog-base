package com.zrlog.business.plugin;

import com.google.gson.Gson;
import com.hibegin.common.BaseLockObject;
import com.hibegin.common.util.*;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.common.util.http.handle.HttpHandle;
import com.hibegin.common.util.http.handle.HttpResponseJsonHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.business.rest.response.PluginCoreStatus;
import com.zrlog.business.rest.response.PluginStatusResponse;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginCorePluginImpl extends BaseLockObject implements PluginCorePlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCorePluginImpl.class);

    private final File dbPropertiesPath;
    private final String pluginJvmArgs;
    private final PluginCoreProcess pluginCoreProcess;
    private final String token;
    private volatile String pluginServerBaseUrl;
    private volatile String pendingPluginServerBaseUrl;

    public PluginCorePluginImpl(File dbPropertiesPath, String contextPath) {
        this.dbPropertiesPath = dbPropertiesPath;
        String args = ConfigKit.get("pluginJvmArgs", "-Xms8m -Xmx64m -Dfile.encoding=UTF-8");
        if (EnvKit.isDevMode()) {
            args += " -Dsws.run.mode=dev";
        }
        this.pluginJvmArgs = args;
        this.pluginCoreProcess = new PluginCoreProcessImpl(this::stop, contextPath);
        this.token = UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public boolean autoStart() {
        return !EnvKit.isFaaSMode();
    }

    private static Map<String, String> genHeaderMapByRequest(HttpRequest request, AdminTokenVO adminTokenVO) {
        Map<String, String> map = new HashMap<>();
        if (adminTokenVO != null) {
            map.put("LoginUserId", adminTokenVO.getUserId() + "");
        }
        map.put("IsLogin", (adminTokenVO != null) + "");
        map.put("Current-Locale", I18nUtil.getCurrentLocale());
        map.put("Blog-Version", BlogBuildInfoUtil.getVersion());
        PublicWebSiteInfo publicWebSiteInfo = Constants.zrLogConfig.getCacheService().getPublicWebSiteInfo();
        map.put("Dark-Mode", publicWebSiteInfo.getAdmin_darkMode() + "");
        if (EnvKit.isDevMode()) {
            map.put("DEV_MODE", "true");
        }
        map.put("Admin-Color-Primary", publicWebSiteInfo.getAdmin_color_primary());
        if (Objects.isNull(request)) {
            return map;
        }
        if (Objects.nonNull(request.getHeader("Cookie"))) {
            map.put("Cookie", request.getHeader("Cookie"));
        }
        map.put("AccessUrl", "http://127.0.0.1:" + request.getServerConfig().getPort());
        if (Objects.nonNull(request.getHeader("Content-Type"))) {
            map.put("Content-Type", request.getHeader("Content-Type"));
        }
        if (StringUtils.isNotEmpty(request.getHeader("Referer"))) {
            map.put("Referer", request.getHeader("Referer"));
        }
        //透传新字段
        if (StringUtils.isNotEmpty(request.getHeader("User-Agent"))) {
            map.put("User-Agent", request.getHeader("User-Agent"));
        }
        if (StringUtils.isNotEmpty(request.getHeader("Authorization"))) {
            map.put("Authorization", request.getHeader("Authorization"));
        }
        WebTools.putRealIpHeader(map, request);
        String fullUrl;
        if (Objects.nonNull(adminTokenVO)) {
            fullUrl = request.getFullUrl().replaceFirst("http://", adminTokenVO.getProtocol() + "://");
        } else {
            fullUrl = request.getFullUrl();
        }
        map.put("Full-Url", UrlEncodeUtils.encodeUrl(fullUrl));
        return map;
    }

    private boolean waitToStarted() {
        lock.lock();
        try {
            if (this.isStarted()) {
                return true;
            }
            return this.start();
        } finally {
            lock.unlock();
        }
    }

    private String requirePluginServerBaseUrl() throws IOException {
        if (!waitToStarted()) {
            throw new IOException("plugin-core is not ready");
        }
        String serverBaseUrl = pluginServerBaseUrl;
        if (Objects.isNull(serverBaseUrl)) {
            throw new IOException("plugin-core is not ready");
        }
        return serverBaseUrl;
    }

    @Override
    public CloseResponseHandle getContext(String uri, HttpMethod method, HttpRequest request, AdminTokenVO adminTokenVO) throws IOException, URISyntaxException, InterruptedException {
        String serverBaseUrl = requirePluginServerBaseUrl();
        CloseResponseHandle handle = new CloseResponseHandle();
        String forwardUrl = serverBaseUrl + uri + (uri.contains("?") ? "&" : "?") + request.getQueryStr();
        //GET请求不关心request.getInputStream() 的数据
        if (method.equals(request.getMethod()) && method == HttpMethod.GET) {
            HttpUtil.getInstance().sendGetRequest(forwardUrl, new HashMap<>(), handle, genHeaderMapByRequest(request, adminTokenVO));
        } else {
            HttpUtil.getInstance().sendPostRequest(forwardUrl, IOUtil.getByteByInputStream(request.getInputStream()), handle, genHeaderMapByRequest(request, adminTokenVO));
        }
        return handle;
    }

    @Override
    public <T> T requestService(HttpRequest inputRequest, Map<String, String[]> body, AdminTokenVO adminTokenVO, Class<T> clazz) throws IOException, InterruptedException {
        String serverBaseUrl = requirePluginServerBaseUrl();
        return HttpUtil.getInstance().sendPostRequest(serverBaseUrl + "/service", body, new HttpResponseJsonHandle<>(clazz), genHeaderMapByRequest(inputRequest, adminTokenVO)).getT();
    }


    @Override
    public boolean accessPlugin(String uri, HttpRequest request, HttpResponse response, AdminTokenVO adminTokenVO) throws IOException, URISyntaxException, InterruptedException {
        if (!waitToStarted()) {
            return false;
        }
        CloseResponseHandle handle = getContext(uri, request.getMethod(), request, adminTokenVO);
        if (Objects.isNull(handle.getT()) || Objects.isNull(handle.getT().body())) {
            return false;
        }
        List<String> ignoreHeaderKeys = Arrays.asList("content-encoding", "transfer-encoding", "content-length", "server", "connection");
        try (InputStream inputStream = handle.getT().body()) {
            for (Map.Entry<String, List<String>> header : handle.getT().headers().map().entrySet()) {
                if (ignoreHeaderKeys.stream().anyMatch(x -> Objects.equals(x, header.getKey()))) {
                    continue;
                }
                String value = header.getValue().get(0);
                //处理 302，contextPath 丢失的问题
                if (Objects.equals(header.getKey().toLowerCase(), "location") && handle.getT().statusCode() == 302) {
                    if (value.startsWith("/")) {
                        value = request.getContextPath() + value;
                    }
                }
                response.addHeader(header.getKey(), value);
            }
            //将插件服务的HTTP的body返回给调用者
            response.write(inputStream, handle.getT().statusCode());
            return true;
        }
    }

    /**
     * 这里使用独立的线程进行启动，主要是为了防止插件服务出问题后，影响整体，同时是避免启动过慢的问题。
     *
     * @return
     */
    @Override
    public boolean start() {
        if (isStarted()) {
            return true;
        }
        lock.lock();
        try {
            if (isStarted()) {
                return true;
            }
            String serverUrl = pendingPluginServerBaseUrl;
            if (Objects.isNull(serverUrl)) {
                //加载 ZrLog 提供的插件
                int port = pluginCoreProcess.pluginServerStart(dbPropertiesPath.toString(), pluginJvmArgs,
                        PathUtil.getStaticPath(), BlogBuildInfoUtil.getVersion(), token);
                if (port <= 0) {
                    pluginCoreProcess.stopPluginCore();
                    this.pluginServerBaseUrl = null;
                    this.pendingPluginServerBaseUrl = null;
                    return false;
                }
                serverUrl = "http://127.0.0.1:" + port;
                this.pendingPluginServerBaseUrl = serverUrl;
            }
            if (!waitToStarted(serverUrl, token)) {
                LOGGER.warning("plugin-core is not ready yet at " + serverUrl);
                return false;
            }
            this.pluginServerBaseUrl = serverUrl;
            this.pendingPluginServerBaseUrl = null;
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isStarted() {
        return Objects.nonNull(pluginServerBaseUrl);
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public boolean stop() {
        lock.lock();
        try {
            pluginServerBaseUrl = null;
            pendingPluginServerBaseUrl = null;
            pluginCoreProcess.stopPluginCore();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean refreshCache(String cacheVersion, HttpRequest request) {
        if (!waitToStarted() || Objects.isNull(pluginServerBaseUrl)) {
            return false;
        }
        refreshCacheWithRetry(EnvKit.isFaaSMode() ? Integer.MAX_VALUE : 5, cacheVersion);
        return true;
    }


    private void refreshCacheWithRetry(int retryCount, String cacheVersion) {
        try {
            HttpUtil.getInstance().getSuccessTextByUrl(pluginServerBaseUrl + "/api/refreshCache?cacheVersion=" + cacheVersion + "&token=" + token);
        } catch (IOException | InterruptedException | URISyntaxException e) {
            int rCount = retryCount - 1;
            if (retryCount < 0) {
                LOGGER.log(Level.SEVERE, "refresh plugin cache error ", e);
                return;
            }
            try {
                pauseBeforeRefreshCacheRetry();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
            refreshCacheWithRetry(rCount, cacheVersion);
        }
    }

    void pauseBeforeRefreshCacheRetry() throws InterruptedException {
        Thread.sleep(2000);
    }

    boolean waitToStarted(String pluginServerBaseUrl, String token) {
        return waitToStarted(pluginServerBaseUrl, token, 360);
    }

    private static boolean waitToStarted(String pluginServerBaseUrl, String token, int retryCount) {
        if (retryCount < 0) {
            LOGGER.severe("plugin-core readiness check has no attempts remaining");
            return false;
        }
        int seek = 1000;
        Exception lastFailure = null;
        for (int remainingRetries = retryCount; remainingRetries >= 0; remainingRetries--) {
            try {
                HttpHandle<PluginStatusResponse> httpHandle = HttpUtil.getInstance().sendGetRequest(
                        pluginServerBaseUrl + "/api/status?token=" + token,
                        new HttpResponseJsonHandle<>(PluginStatusResponse.class), new ConcurrentHashMap<>());
                PluginStatusResponse statusResponse = httpHandle.getT();
                if (Objects.nonNull(statusResponse) && Objects.nonNull(statusResponse.getStatus())) {
                    if (Constants.debugLoggerPrintAble()) {
                        LOGGER.info("Plugin status: " + new Gson().toJson(statusResponse));
                    }
                    if (Objects.equals(statusResponse.getStatus(), PluginCoreStatus.STARTED)) {
                        return true;
                    }
                } else {
                    lastFailure = new IllegalStateException("plugin-core status response is missing status");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "plugin-core readiness check interrupted", e);
                return false;
            } catch (Exception e) {
                lastFailure = e;
            }
            if (remainingRetries == 0) {
                break;
            }
            try {
                Thread.sleep(seek);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "plugin-core readiness wait interrupted", e);
                return false;
            }
        }
        if (Objects.isNull(lastFailure)) {
            LOGGER.severe("plugin-core readiness timed out at " + pluginServerBaseUrl);
        } else {
            LOGGER.log(Level.WARNING, "plugin-core readiness failed at " + pluginServerBaseUrl, lastFailure);
        }
        return false;
    }
}
