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
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.zrlog.plugin.BaseStaticSitePlugin.isStaticPluginRequest;

public interface StaticSitePlugin extends BaseStaticSitePlugin {

    static String getSuffix(HttpRequest request, boolean staticHtml) {
        if (isStaticPluginRequest(request) || staticHtml) {
            return ".html";
        }
        return "";
    }

    Logger LOGGER = LoggerUtil.getLogger(StaticSitePlugin.class);

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

    static boolean isDisabled(boolean staticHtml, String host) {
        return !staticHtml || StringUtils.isEmpty(host);
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
