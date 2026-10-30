package com.zrlog.business.plugin;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.cache.CacheServiceImpl;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.I18nUtil;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.zrlog.plugin.BaseStaticSitePlugin.isStaticPluginRequest;

public interface StaticSitePlugin extends BaseStaticSitePlugin {

    Logger LOGGER = LoggerUtil.getLogger(StaticSitePlugin.class);

    /** Implementations must complete their owned work before returning and propagate failures. */
    default boolean startWithinBudget(StaticGenerationBudget budget) throws Exception {
        throw new UnsupportedOperationException("Static generation does not support an execution budget");
    }

    default void saveToCacheFolderWithinBudget(InputStream input, String uri, StaticGenerationBudget budget) throws IOException {
        try (InputStream source = input) {
            budget.check();
            if (!isStaticHtmlEnabled()) return;
            File file = getCacheFile(uri);
            java.nio.file.Files.createDirectories(file.toPath().getParent());
            try (OutputStream output = new FileOutputStream(file)) {
                budget.copy(source, output);
            }
            budget.check();
            getCacheFiles().add(file);
        }
    }

    default void copyResourceWithinBudget(String name, StaticGenerationBudget budget) throws IOException {
        budget.check();
        InputStream source = CacheServiceImpl.class.getResourceAsStream(name);
        if (source == null) throw new IOException("Missing static resource");
        saveToCacheFolderWithinBudget(source, name, budget);
    }

    default void faviconWithinBudget(String value, String path, boolean cache, StaticGenerationBudget budget) throws IOException {
        budget.check();
        if (StringUtils.isEmpty(value)) {
            copyResourceWithinBudget(path, budget);
            return;
        }
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(value.contains(",") ? value.substring(value.indexOf(',') + 1) : value); }
        catch (IllegalArgumentException error) { throw new IOException("Invalid favicon data", error); }
        File file = PathUtil.getStaticFile(path);
        java.nio.file.Files.createDirectories(file.toPath().getParent());
        try (OutputStream output = new FileOutputStream(file)) {
            budget.copy(new ByteArrayInputStream(bytes), output);
        }
        if (cache) saveToCacheFolderWithinBudget(new ByteArrayInputStream(bytes), path, budget);
        budget.check();
    }

    default String getSiteVersionWithinBudget(StaticGenerationBudget budget) throws IOException {
        StringBuilder hashes = new StringBuilder();
        for (File file : new TreeSet<>(getCacheFiles())) {
            budget.check();
            java.security.MessageDigest digest;
            try { digest = java.security.MessageDigest.getInstance("MD5"); }
            catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            try (InputStream input = new FileInputStream(file);
                 OutputStream output = new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                budget.copy(input, output);
            }
            hashes.append(String.format(Locale.ROOT, "%032x", new java.math.BigInteger(1, digest.digest())));
        }
        budget.check();
        return Math.abs(hashes.toString().hashCode()) + "";
    }

    static String getSuffix(HttpRequest request, boolean staticHtml) {
        if (isStaticPluginRequest(request) || staticHtml) {
            return ".html";
        }
        return "";
    }

    static boolean isDisabled(boolean staticHtml, String host) {
        return !staticHtml || StringUtils.isEmpty(host);
    }

    default void faviconHandle(String faviconIconBase64, String saveFilePath, Boolean saveToCache) {
        if (StringUtils.isEmpty(faviconIconBase64)) {
            copyResourceToCacheFolder(saveFilePath);
            return;
        }
        try {
            File file = PathUtil.getStaticFile(saveFilePath);
            file.getParentFile().mkdirs();
            byte[] binBytes;
            if (faviconIconBase64.contains(",")) {
                binBytes = Base64.getDecoder().decode(faviconIconBase64.split(",")[1]);
            } else {
                binBytes = Base64.getDecoder().decode(faviconIconBase64);
            }
            IOUtil.writeBytesToFile(binBytes, file);
            if (Objects.equals(saveToCache, true)) {
                saveToCacheFolder(new ByteArrayInputStream(binBytes), saveFilePath);
            }
        } catch (Exception e) {
            LOGGER.warning("Save favicon error " + e.getMessage());
        }
    }

    default void copyResourceToCacheFolder(String resourceName) {
        InputStream inputStream = CacheServiceImpl.class.getResourceAsStream(resourceName);
        if (Objects.isNull(inputStream)) {
            LOGGER.warning("Missing resource " + resourceName);
            return;
        }
        saveToCacheFolder(inputStream, resourceName);
    }

    PublicWebSiteInfo getSiteInfo();

    default boolean isStaticHtmlEnabled() {
        PublicWebSiteInfo site = getSiteInfo();
        return site != null && Boolean.TRUE.equals(site.getGenerator_html_status());
    }

    default boolean isDisabled() {
        PublicWebSiteInfo site = getSiteInfo();
        return site == null || isDisabled(Boolean.TRUE.equals(site.getGenerator_html_status()), site.getHost());
    }

    default String getSiteVersion() {
        StringBuilder sb = new StringBuilder();
        for (File file : new TreeSet<>(getCacheFiles())) {
            sb.append(SecurityUtils.md5ByFile(file));
        }
        return Math.abs(sb.toString().hashCode()) + "";
    }

    String getVersionFileName();

    String getDbCacheKey();

    default boolean isSynchronized(String scheme) {
        try {
            if (isDisabled()) {
                return true;
            }
            String versionFile = (Objects.nonNull(scheme) ? scheme : "https") + "://" + getSiteInfo().getHost() + getContextPath() + "/" + getVersionFileName();
            String remoteSiteVersion = HttpUtil.getInstance().getSuccessTextByUrl(versionFile + "?_t=" + System.currentTimeMillis());
            return Objects.equals(getSiteVersion(), remoteSiteVersion);
        } catch (Exception e) {
            if (Constants.debugLoggerPrintAble()) {
                LOGGER.severe("isSynchronized error, " + LoggerUtil.recordStackTraceMsg(e));
            } else {
                LOGGER.warning("isSynchronized error " + e.getMessage());
            }
        }
        return false;
    }

    /** A scheduled caller must not enter an unbounded network wait. */
    default boolean isSynchronizedWithinBudget(String scheme, long remainingMillis) throws IOException, InterruptedException {
        if (isDisabled()) return true;
        if (remainingMillis < 1) return false;
        String url = (scheme == null ? "https" : scheme) + "://" + getSiteInfo().getHost()
                + getContextPath() + "/" + getVersionFileName() + "?_t=" + System.currentTimeMillis();
        var deadline = com.zrlog.util.ExecutionDeadline.afterMillis(remainingMillis).capMillis(10000);
        var budget = new StaticGenerationBudget(deadline, () -> {});
        String expected = getSiteVersionWithinBudget(budget);
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(Duration.ofMillis(Math.min(remainingMillis, 10000))).GET().build();
        var response = HttpUtil.getInstance().sendRequest(request, deadline, 1024);
        return response.statusCode() == 200 && Objects.equals(expected, response.body());
    }

    default File loadCacheFile(HttpRequest request) {
        String lang = I18nUtil.getAcceptLocal(request);
        String cacheKey = request.getUri();
        if (Objects.equals(cacheKey, "/")) {
            cacheKey = "/index.html";
        }
        File file = getCacheFile(lang, cacheKey);
        if (file.exists()) {
            return file;
        }
        return getCacheFile(getDefaultLang(), cacheKey);
    }

    String getContextPath();

    String getDefaultLang();

    private File getCacheFile(String lang, String uri) {
        StringJoiner fileSj = new StringJoiner("/");
        fileSj.add(lang);
        fileSj.add(getContextPath());
        fileSj.add(uri);
        return PathUtil.getCacheFile(fileSj.toString());
    }

    default File getCacheFile(String uri) {
        return getCacheFile(getDefaultLang(), uri);
    }

    default void saveToCacheFolder(InputStream inputStream, String uri) {
        if (!isStaticHtmlEnabled()) {
            return;
        }
        File file = getCacheFile(uri);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            inputStream.transferTo(outputStream);
            inputStream.close();
            getCacheFiles().add(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Map<String, HandleState> getHandleStatusPageMap();

    Lock getParseLock();

    private void doParseHtml(File file) throws IOException {
        getParseLock().lock();
        try {
            StaticSiteHtmlLinks.parseInto(file, getSiteInfo().getHost(), getHandleStatusPageMap());
        } finally {
            getParseLock().unlock();
        }
    }

    default String notFindFile() {
        return getContextPath() + "/error/404.html";
    }

    private CompletableFuture<Void> doAsyncFetch(String key, StaticSiteRenderer renderer, Executor executor) {
        getHandleStatusPageMap().put(key, HandleState.HANDING);
        return CompletableFuture.runAsync(() -> {
            boolean error = false;
            boolean notFoundDocument = Objects.equals(key, notFindFile());
            try {
                StaticSiteRenderer.RenderedPage page = renderer.render(key, getSiteInfo().getHost(), this);
                File file = page.file;
                if (Objects.nonNull(file)) {
                    getCacheFiles().add(file);
                }
                if (page.uri.startsWith(Constants.ADMIN_URI_BASE_PATH + "/")) {
                    return;
                }
                if (Objects.isNull(file) || !file.exists()) {
                    LOGGER.warning("Generator " + key + " error: missing static file");
                    error = true;
                    return;
                }
                if (!notFoundDocument) {
                    doParseHtml(file);
                }
            } catch (Exception ex) {
                LOGGER.warning("Generator " + key + " error: " + ex.getMessage());
            } finally {
                if (error) {
                    //如果抓取错误了，需要暂停一下，避免重新获取过快
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        LOGGER.warning("Generator " + key + " error: " + ex.getMessage());
                    }
                }
                getHandleStatusPageMap().put(key, error ? HandleState.RE_FETCH : HandleState.HANDLED);
            }
        }, executor);
    }

    Executor getExecutorService();

    List<File> getCacheFiles();

    default void doFetch(StaticSiteRenderer renderer) {
        long start = System.currentTimeMillis();

        try {
            while (getHandleStatusPageMap().values().stream().anyMatch(e -> e == HandleState.NEW)) {
                CompletableFuture.allOf(getHandleStatusPageMap().entrySet().stream().filter(e -> Arrays.asList(HandleState.NEW, HandleState.RE_FETCH).contains(e.getValue())).map(e -> {
                    return doAsyncFetch(e.getKey(), renderer, getExecutorService());
                }).toArray(CompletableFuture[]::new)).join();
            }
            String version = saveCacheVersion();
            long usedTime = (System.currentTimeMillis() - start);
            if (Constants.debugLoggerPrintAble()) {
                LOGGER.info("Generator [" + getDbCacheKey() + "] [" + version + "] " + "size " + getHandleStatusPageMap().size() + " finished in " + usedTime + "ms");
            } else if (usedTime > Duration.ofSeconds(10).toMillis()) {
                LOGGER.warning("Generator [" + getDbCacheKey() + "] [" + version + "] slow size " + getHandleStatusPageMap().size() + " finished in " + usedTime + "ms");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "do fetch error", e.getMessage());
        }
    }

    /** Request-lifetime generation never submits page work to the legacy executor. */
    default void doFetchWithinBudget(StaticSiteRenderer renderer, StaticGenerationBudget budget) throws Exception {
        while (true) {
            budget.check();
            String key = getHandleStatusPageMap().entrySet().stream()
                    .filter(entry -> entry.getValue() == HandleState.NEW || entry.getValue() == HandleState.RE_FETCH)
                    .map(Map.Entry::getKey).sorted().findFirst().orElse(null);
            if (key == null) break;
            getHandleStatusPageMap().put(key, HandleState.HANDING);
            try {
                StaticSiteRenderer.RenderedPage page = renderer.render(key, getSiteInfo().getHost(), this, budget);
                budget.check();
                if (page.file == null || !page.file.isFile()) throw new IOException("Static page generation produced no file");
                getCacheFiles().add(page.file);
                if (!page.uri.startsWith(Constants.ADMIN_URI_BASE_PATH + "/") && !Objects.equals(key, notFindFile())) {
                    Lock parse = getParseLock();
                    if (!parse.tryLock(budget.remainingMillis(), TimeUnit.MILLISECONDS))
                        throw new IllegalStateException("Static page parsing time budget exhausted");
                    try {
                        budget.check();
                        StaticSiteHtmlLinks.parseInto(page.file, getSiteInfo().getHost(), getHandleStatusPageMap());
                    } finally { parse.unlock(); }
                }
                budget.check();
                getHandleStatusPageMap().put(key, HandleState.HANDLED);
            } catch (Exception error) {
                getHandleStatusPageMap().put(key, HandleState.RE_FETCH);
                throw error;
            }
        }
        if (getHandleStatusPageMap().values().stream().anyMatch(state -> state != HandleState.HANDLED))
            throw new IllegalStateException("Static page generation is incomplete");
        String version = getSiteVersionWithinBudget(budget);
        budget.check();
        java.nio.file.Files.createDirectories(getCacheFile(getVersionFileName()).toPath().getParent());
        java.nio.file.Files.writeString(getCacheFile(getVersionFileName()).toPath(), version);
        budget.check();
    }

    StaticSiteType getType();

    private String saveCacheVersion() {
        String siteVersion = getSiteVersion();
        IOUtil.writeStrToFile(siteVersion, getCacheFile(getVersionFileName()));
        return siteVersion;
    }

    enum HandleState {
        NEW, HANDING, RE_FETCH, HANDLED
    }
}
