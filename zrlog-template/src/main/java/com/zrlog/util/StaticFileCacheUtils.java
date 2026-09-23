package com.zrlog.util;

import com.hibegin.common.util.FileUtils;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.template.util.BlogResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class StaticFileCacheUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(StaticFileCacheUtils.class);

    private final Map<String, String> cacheFileMap = new ConcurrentHashMap<>();

    private static StaticFileCacheUtils instance;

    private StaticFileCacheUtils() {
        refreshCacheFileMap();
    }

    public static StaticFileCacheUtils getInstance() {
        if (Objects.isNull(instance)) {
            instance = new StaticFileCacheUtils();
        }
        return instance;
    }

    private Map<String, String> getCacheFileMap() {
        List<File> staticFiles = new ArrayList<>();
        String staticPath = PathUtil.getStaticPath();
        FileUtils.getAllFiles(staticPath, staticFiles);
        Map<String, String> cacheMap = new HashMap<>();
        List<String> cacheableFileExts = Arrays.asList(".css", ".js", ".png", ".jpg", ".png", ".webp", ".ico");
        Path staticRoot = Path.of(staticPath).toAbsolutePath().normalize();
        for (File file : staticFiles) {
            Path filePath = file.toPath().toAbsolutePath().normalize();
            if (!filePath.startsWith(staticRoot) || file.isDirectory()) {
                continue;
            }
            String uri = staticRoot.relativize(filePath).toString().replace(File.separatorChar, '/');
            if (cacheableFileExts.stream().noneMatch(e -> uri.toLowerCase().endsWith(e))) {
                continue;
            }
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                cacheMap.put(uri, getStreamTag(fileInputStream));
            } catch (IOException e) {
                LOGGER.warning("Get " + uri + " stream tag error " + e.getMessage());
            }
        }
        return cacheMap;
    }


    public void refreshCacheFileMap() {
        Map<String, String> tempMap = getCacheFileMap();
        cacheFileMap.clear();
        cacheFileMap.putAll(tempMap);
    }

    public boolean isCacheableByRequest(String uriPath) {
        //disable html client cache
        if (uriPath.endsWith(".html")) {
            return false;
        }
        return cacheFileMap.containsKey(uriPath.substring(1));
    }


    public String getStreamTag(InputStream inputStream) {
        return Math.abs(SecurityUtils.md5(inputStream).hashCode()) + "";
    }

    public String getFileFlagFirstByCache(String uri) {
        //外部链接，不查询缓存 id
        if (uri.startsWith("https://") || uri.startsWith("http://")) {
            return null;
        }
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        String s = cacheFileMap.get(uri);
        if (Objects.nonNull(s)) {
            return s;
        }
        if (BlogResourceUtils.getInstance().existsResource(uri)) {
            InputStream inputStream = StaticFileCacheUtils.class.getResourceAsStream("/" + uri);
            if (Objects.nonNull(inputStream)) {
                String flag = getStreamTag(inputStream);
                cacheFileMap.put(uri, flag);
                return flag;
            }
        }
        File staticFile = PathUtil.getStaticFile("/" + uri);
        if (!staticFile.exists()) {
            return null;
        }
        if (staticFile.isDirectory()) {
            return null;
        }
        try (FileInputStream fileInputStream = new FileInputStream(staticFile)) {
            String streamTag = getStreamTag(fileInputStream);
            cacheFileMap.put(uri, streamTag);
            return streamTag;
        } catch (IOException e) {
            LOGGER.warning("Get " + uri + " stream tag error " + e.getMessage());
        }
        return null;
    }
}
