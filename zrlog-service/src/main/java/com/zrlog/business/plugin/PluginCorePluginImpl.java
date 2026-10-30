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
import com.zrlog.plugin.PluginAdminAppearance;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ExecutionDeadline;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginCorePluginImpl extends BaseLockObject implements PluginCorePlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCorePluginImpl.class);

    private final File dbPropertiesPath;
    private final String pluginJvmArgs;
    private final PluginCoreProcess pluginCoreProcess;
    private final Supplier<PublicWebSiteInfo> siteInfo;
    private final String token;
    private volatile String pluginServerBaseUrl;
    private volatile String pendingPluginServerBaseUrl;
    private ExecutionDeadline pendingStartupDeadline;
    private long pluginServerGeneration;

    public PluginCorePluginImpl(File dbPropertiesPath, String contextPath, IntSupplier hostPort,
                                Supplier<PublicWebSiteInfo> siteInfo) {
        this(dbPropertiesPath, siteInfo, onStop -> new PluginCoreProcessImpl(onStop, contextPath, hostPort));
    }

    PluginCorePluginImpl(File dbPropertiesPath, Supplier<PublicWebSiteInfo> siteInfo,
                         Function<Runnable, PluginCoreProcess> processFactory) {
        this.dbPropertiesPath = dbPropertiesPath;
        this.siteInfo = Objects.requireNonNull(siteInfo);
        String args = ConfigKit.get("pluginJvmArgs", "-Xms8m -Xmx64m -Dfile.encoding=UTF-8");
        if (EnvKit.isDevMode()) {
            args += " -Dsws.run.mode=dev";
        }
        this.pluginJvmArgs = args;
        this.pluginCoreProcess = Objects.requireNonNull(processFactory.apply(this::stop));
        this.token = UUID.randomUUID().toString().replace("-", "");
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

    @Override
    public boolean supportsHostScheduler() { return true; }

    @Override
    public void setExternallyScheduled(boolean externallyScheduled) {
        pluginCoreProcess.setExternallyScheduled(externallyScheduled);
    }

    @Override
    public boolean autoStart() {
        return !EnvKit.isFaaSMode();
    }

    private Map<String, String> genHeaderMapByRequest(HttpRequest request, AdminTokenVO adminTokenVO) {
        Map<String, String> map = new HashMap<>();
        if (adminTokenVO != null) {
            map.put("LoginUserId", adminTokenVO.getUserId() + "");
        }
        map.put("IsLogin", (adminTokenVO != null) + "");

        map.put("Blog-Version", BlogBuildInfoUtil.getVersion());
        PublicWebSiteInfo publicWebSiteInfo = siteInfo.get();
        map.put("Current-Locale", I18nUtil.getCurrentLocale(publicWebSiteInfo.getLanguage()));
        PluginAdminAppearance appearance = new PluginAdminAppearance(publicWebSiteInfo.getAdmin_theme(),
                Boolean.TRUE.equals(publicWebSiteInfo.getAdmin_darkMode()), publicWebSiteInfo.getAdmin_color_primary(),
                Boolean.TRUE.equals(publicWebSiteInfo.getAdmin_compactMode()));
        if (adminTokenVO != null && request != null && request.getAttr() != null) {
            Object personalAppearance = request.getAttr().get(PluginAdminAppearance.REQUEST_ATTRIBUTE);
            if (personalAppearance instanceof PluginAdminAppearance) {
                appearance = (PluginAdminAppearance) personalAppearance;
            }
        }
        map.put("Dark-Mode", Boolean.toString(appearance.isDarkMode()));
        map.put("Admin-Theme", StringUtils.isEmpty(appearance.getTheme()) ? "default" : appearance.getTheme());
        map.put("Admin-Compact-Mode", Boolean.toString(appearance.isCompactMode()));
        if (EnvKit.isDevMode()) {
            map.put("DEV_MODE", "true");
        }
        map.put("Admin-Color-Primary", appearance.getColorPrimary());
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
        return isStarted() || start();
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
    public String renderViewWithinBudget(String uri, HttpRequest source, StaticGenerationBudget budget)
            throws IOException, InterruptedException {
        budget.check();
        if (budget.executionId() == null || !budget.executionId().matches("[a-zA-Z0-9_.-]{1,128}") || budget.actorId() < 1)
            throw new IllegalArgumentException("Static plugin views require an execution identity");
        java.net.URI view = parsePublicViewUri(uri);
        String path = view.getPath();
        int slash = path.indexOf('/', 1);
        String name = slash < 0 ? path.substring(1) : path.substring(1, slash);
        String action = slash < 0 ? "/" : path.substring(slash);
        String encodedAction;
        try { encodedAction = new java.net.URI(null, null, action, null).toASCIIString(); }
        catch (URISyntaxException invalid) { throw new IOException("Invalid plugin view path", invalid); }
        String baseUrl = pluginServerBaseUrl;
        long requestMillis = Math.min(650000, budget.remainingMillis());
        if (baseUrl == null || requestMillis < 12000) throw new IOException("Plugin view execution budget is unavailable");
        String invocationId = java.util.UUID.randomUUID().toString();
        String query = view.getRawQuery() == null ? "" : view.getRawQuery();
        if (source.getQueryStr() != null && !source.getQueryStr().isEmpty())
            query += (query.isEmpty() ? "" : "&") + source.getQueryStr();
        if (query.length() > 8192) throw new IllegalArgumentException("Plugin view query is too large");
        String target = baseUrl + "/internal/runtime-scheduler/render-view" + (query.isEmpty() ? "" : "?" + query);
        var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create(target))
                .timeout(java.time.Duration.ofMillis(requestMillis))
                .header("X-Plugin-Token", token).header("X-ZrLog-Actor-Id", String.valueOf(budget.actorId()))
                .header("X-ZrLog-Trigger-Id", budget.executionId()).header("X-ZrLog-Request-Id", invocationId)
                .header("X-ZrLog-Plugin-Name", name).header("X-ZrLog-View-Path", encodedAction)
                .header("X-ZrLog-Budget-Millis", String.valueOf(requestMillis - 5000));
        Map<String, String> publicHeaders = genHeaderMapByRequest(source, null);
        for (String key : java.util.List.of("User-Agent", "Blog-Version", "Current-Locale", "AccessUrl", "Full-Url",
                "Dark-Mode", "Admin-Theme", "Admin-Compact-Mode", "Admin-Color-Primary")) {
            String value = publicHeaders.get(key);
            if (value != null) builder.header("User-Agent".equals(key) ? "X-ZrLog-View-User-Agent" : key, value);
        }
        budget.check();
        var response = HttpUtil.getInstance().sendDirectRequest(builder.POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                ExecutionDeadline.afterMillis(budget.remainingMillis()).capMillis(requestMillis), 16 * 1024 * 1024);
        budget.check();
        if (response.statusCode() != 200 || !"1".equals(response.headers().firstValue("X-ZrLog-Scheduler-Protocol").orElse(null)))
            throw new IOException("Plugin view completion is unavailable");
        try {
            PluginViewRenderResult result = new Gson().fromJson(response.body(), PluginViewRenderResult.class);
            if (result == null || !budget.executionId().equals(result.triggerId) || !invocationId.equals(result.requestId)
                    || !result.completed || result.outcomeUncertain || result.content == null
                    || result.content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
                throw new IllegalArgumentException();
            return result.content;
        } catch (RuntimeException invalid) { throw new IOException("Plugin view did not return confirmed content"); }
    }

    static java.net.URI parsePublicViewUri(String value) {
        if (value == null || value.length() > 10000) throw new IllegalArgumentException("Invalid plugin view URI");
        java.net.URI uri = java.net.URI.create(java.net.URI.create(value).toASCIIString());
        String path = uri.getPath();
        if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null || path == null
                || !path.startsWith("/") || path.length() > 1153 || path.contains("\\")
                || uri.getRawQuery() != null && uri.getRawQuery().length() > 8192)
            throw new IllegalArgumentException("Invalid plugin view URI");
        String[] segments = path.substring(1).split("/", -1);
        if (segments.length == 0 || !segments[0].matches("[a-zA-Z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid plugin view name");
        String action = path.substring(segments[0].length() + 1);
        if (action.length() > 1024 || action.startsWith("//") || action.contains("?") || action.contains("#"))
            throw new IllegalArgumentException("Invalid plugin view path");
        for (String segment : segments) {
            if (".".equals(segment) || "..".equals(segment)) throw new IllegalArgumentException("Invalid plugin view path");
            for (int i = 0; i < segment.length(); i++) if (Character.isISOControl(segment.charAt(i)))
                throw new IllegalArgumentException("Invalid plugin view path");
        }
        return uri;
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
        String serverUrl;
        long serverGeneration;
        lock.lock();
        try {
            if (isStarted()) {
                return true;
            }
            discardExpiredStartup(ExecutionDeadline.afterMillis(5000));
            serverUrl = pendingPluginServerBaseUrl;
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
                this.pendingStartupDeadline = null;
                pluginServerGeneration++;
            }
            serverGeneration = pluginServerGeneration;
        } finally {
            lock.unlock();
        }

        if (!waitToStarted(serverUrl, token)) {
            LOGGER.warning("plugin-core is not ready yet at " + serverUrl);
            return false;
        }

        lock.lock();
        try {
            if (serverGeneration != pluginServerGeneration) {
                return false;
            }
            if (Objects.equals(pluginServerBaseUrl, serverUrl)) {
                return true;
            }
            if (!Objects.equals(pendingPluginServerBaseUrl, serverUrl)) {
                return false;
            }
            if (pendingStartupDeadline != null && !pluginCoreProcess.confirmStarted(
                    java.net.URI.create(serverUrl).getPort(), pendingStartupDeadline)) return false;
            this.pluginServerBaseUrl = serverUrl;
            this.pendingPluginServerBaseUrl = null;
            this.pendingStartupDeadline = null;
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
            pluginServerGeneration++;
            pluginServerBaseUrl = null;
            pendingPluginServerBaseUrl = null;
            pendingStartupDeadline = null;
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

    @Override
    public boolean refreshCacheWithinBudget(String cacheVersion, String triggerId, int actorId, long remainingMillis)
            throws IOException, InterruptedException {
        if (triggerId == null || !triggerId.matches("[a-zA-Z0-9_.-]{1,128}") || actorId < 1
                || cacheVersion == null || !cacheVersion.matches("[a-zA-Z0-9_.-]{1,128}"))
            throw new IllegalArgumentException("Invalid cache refresh identity");
        String baseUrl = pluginServerBaseUrl;
        if (remainingMillis < 12000 || !isStarted() || baseUrl == null) return false;
        long requestMillis = Math.min(remainingMillis, 650000);
        ExecutionDeadline deadline = ExecutionDeadline.afterMillis(requestMillis);
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/internal/runtime-scheduler/refresh-cache"))
                .timeout(java.time.Duration.ofMillis(requestMillis))
                .header("X-Plugin-Token", token)
                .header("X-ZrLog-Actor-Id", String.valueOf(actorId))
                .header("X-ZrLog-Trigger-Id", triggerId)
                .header("X-ZrLog-Cache-Version", cacheVersion)
                .header("X-ZrLog-Budget-Millis", String.valueOf(requestMillis - 5000))
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
        // Single attempt: a lost response does not prove the plugin had no effects.
        var response = HttpUtil.getInstance().sendDirectRequest(request, deadline, 65536);
        if (java.util.Set.of(401, 403, 404, 405).contains(response.statusCode())) return false;
        if (response.statusCode() != 200 || !"1".equals(response.headers().firstValue("X-ZrLog-Scheduler-Protocol").orElse(null)))
            throw new IOException("Plugin cache refresh outcome unavailable");
        try {
            PluginCacheRefreshResult result = new com.google.gson.Gson().fromJson(response.body(), PluginCacheRefreshResult.class);
            if (result == null || !triggerId.equals(result.triggerId) || !cacheVersion.equals(result.cacheVersion)
                    || result.successCount < 0 || result.failedCount < 0 || result.unsupportedCount < 0
                    || result.completed && (result.outcomeUncertain || result.failedCount != 0 || result.unsupportedCount != 0))
                throw new IllegalArgumentException();
            return result.completed;
        } catch (RuntimeException malformed) { throw new IOException("Plugin cache refresh response is invalid"); }
    }

    @Override
    public PluginSchedulerTickResult tickScheduledTasks(String triggerId, int actorId, long remainingMillis)
            throws IOException, InterruptedException {
        if (triggerId == null || !triggerId.matches("[a-zA-Z0-9_.-]{1,128}") || actorId < 1) {
            throw new IllegalArgumentException("Invalid scheduler identity");
        }
        if (remainingMillis < 7000) return null;
        long requestMillis = Math.min(650000, remainingMillis);
        ExecutionDeadline deadline = ExecutionDeadline.afterMillis(requestMillis);
        if (!isStarted() && !startWithinBudget(deadline.reserveMillis(7000).capMillis(30000))) return null;
        String baseUrl = pluginServerBaseUrl;
        requestMillis = deadline.remainingMillis();
        if (baseUrl == null || requestMillis < 6000) return null;
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/internal/runtime-scheduler/tick"))
                .timeout(java.time.Duration.ofMillis(requestMillis))
                .header("X-Plugin-Token", token)
                .header("X-ZrLog-Actor-Id", String.valueOf(actorId))
                .header("X-ZrLog-Trigger-Id", triggerId)
                .header("X-ZrLog-Budget-Millis", String.valueOf(requestMillis - 5000))
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
        // Never use the browser proxy path, redirect, retry, or start a process after the tick was submitted.
        var response = HttpUtil.getInstance().sendDirectRequest(request, deadline, 262144);
        if (java.util.Set.of(401, 403, 404, 405).contains(response.statusCode())) return null;
        if (response.statusCode() != 200 || !"1".equals(response.headers().firstValue("X-ZrLog-Scheduler-Protocol").orElse(null))
                || response.body().length() > 262144) throw new IOException("Plugin scheduler outcome unavailable");
        try {
            PluginSchedulerTickResult result = new com.google.gson.Gson().fromJson(response.body(), PluginSchedulerTickResult.class);
            if (result == null || !triggerId.equals(result.triggerId) || result.children == null || result.children.size() > 64
                    || result.executedCount < 0 || result.failedCount < 0 || result.skippedCount < 0 || result.uncertainCount < 0
                    || result.uncertainCount > result.failedCount) throw new IllegalArgumentException();
            int completed = 0, failed = 0, uncertain = 0;
            for (PluginSchedulerTickResult.Child child : result.children) {
                if (child == null || child.automationId == null || child.name == null || child.status == null
                        || !java.util.Set.of("success", "error", "uncertain", "skipped").contains(child.status)) {
                    throw new IllegalArgumentException();
                }
                if (!"skipped".equals(child.status) && (child.runId == null || child.runId.isBlank())) throw new IllegalArgumentException();
                if ("success".equals(child.status)) completed++;
                if ("error".equals(child.status) || "uncertain".equals(child.status)) failed++;
                if ("uncertain".equals(child.status)) uncertain++;
            }
            if (completed != result.executedCount || failed != result.failedCount || uncertain != result.uncertainCount) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (RuntimeException malformed) {
            throw new IOException("Plugin scheduler response is invalid");
        }
    }

    @Override
    public boolean startWithinBudget(long remainingMillis) throws InterruptedException {
        if (remainingMillis < 1) return false;
        return startWithinBudget(ExecutionDeadline.afterMillis(Math.min(remainingMillis, 30000)));
    }

    private boolean startWithinBudget(ExecutionDeadline deadline) throws InterruptedException {
        if (deadline.remainingMillis() == 0 || Thread.currentThread().isInterrupted()) return false;
        String serverUrl;
        long generation;
        boolean owner = false;
        ExecutionDeadline readinessDeadline = deadline;
        if (!lock.tryLock(deadline.remainingMillis(), TimeUnit.MILLISECONDS)) return false;
        try {
            if (isStarted()) return true;
            discardExpiredStartup(deadline);
            serverUrl = pendingPluginServerBaseUrl;
            if (serverUrl == null) {
                readinessDeadline = deadline.reserveMillis(Math.min(5000, deadline.remainingMillis() / 4));
                if (readinessDeadline.remainingMillis() == 0) return false;
                int port = pluginCoreProcess.pluginServerStart(dbPropertiesPath.toString(), pluginJvmArgs,
                        PathUtil.getStaticPath(), BlogBuildInfoUtil.getVersion(), token, readinessDeadline);
                if (port <= 0) return false;
                serverUrl = "http://127.0.0.1:" + port;
                pendingPluginServerBaseUrl = serverUrl;
                pendingStartupDeadline = readinessDeadline;
                pluginServerGeneration++;
                owner = true;
            } else if (pendingStartupDeadline != null) {
                readinessDeadline = deadline.capMillis(Math.min(86400000, pendingStartupDeadline.remainingMillis()));
            }
            generation = pluginServerGeneration;
        } finally { lock.unlock(); }
        try {
            if (!waitToStarted(serverUrl, readinessDeadline)) return false;
            if (!lock.tryLock(deadline.remainingMillis(), TimeUnit.MILLISECONDS)) return false;
            try {
                if (generation != pluginServerGeneration) return false;
                if (Objects.equals(pluginServerBaseUrl, serverUrl)) return true;
                if (!Objects.equals(pendingPluginServerBaseUrl, serverUrl)) return false;
                if (pendingStartupDeadline != null && !pluginCoreProcess.confirmStarted(
                        java.net.URI.create(serverUrl).getPort(), deadline)) return false;
                pluginServerBaseUrl = serverUrl;
                pendingPluginServerBaseUrl = null;
                pendingStartupDeadline = null;
                return true;
            } finally { lock.unlock(); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            if (owner) {
                // Cleanup still runs when the caller was interrupted, without clearing its interrupt flag.
                boolean acquired;
                try {
                    acquired = Thread.currentThread().isInterrupted() ? lock.tryLock()
                            : lock.tryLock(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    acquired = lock.tryLock();
                }
                if (acquired) {
                    try {
                        if (generation == pluginServerGeneration && Objects.equals(pendingPluginServerBaseUrl, serverUrl)) {
                            pluginCoreProcess.cancelStartup(java.net.URI.create(serverUrl).getPort(), deadline);
                            pendingPluginServerBaseUrl = null;
                            pendingStartupDeadline = null;
                            pluginServerGeneration++;
                        }
                    } finally { lock.unlock(); }
                }
            }
        }
    }

    private void discardExpiredStartup(ExecutionDeadline deadline) {
        if (pendingStartupDeadline != null && pendingStartupDeadline.remainingMillis() == 0
                && pendingPluginServerBaseUrl != null) {
            pluginCoreProcess.cancelStartup(java.net.URI.create(pendingPluginServerBaseUrl).getPort(), deadline);
            pendingPluginServerBaseUrl = null;
            pendingStartupDeadline = null;
            pluginServerGeneration++;
        }
    }

    private boolean waitToStarted(String baseUrl, ExecutionDeadline deadline) throws InterruptedException {
        while (deadline.remainingMillis() > 0) {
            try {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/api/status?token=" + token))
                        .GET().build();
                var response = HttpUtil.getInstance().sendDirectRequest(request, deadline.capMillis(1000), 65536);
                PluginStatusResponse status = response.statusCode() == 200
                        ? new Gson().fromJson(response.body(), PluginStatusResponse.class) : null;
                if (status != null && status.getStatus() == PluginCoreStatus.STARTED) return true;
            } catch (IOException | com.google.gson.JsonParseException unavailable) {
                // No task has been submitted; readiness may be retried within this startup budget.
            }
            long pause = Math.min(100, deadline.remainingMillis());
            if (pause > 0) Thread.sleep(pause);
        }
        return false;
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
}
