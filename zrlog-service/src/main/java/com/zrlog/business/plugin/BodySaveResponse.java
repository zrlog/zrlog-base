package com.zrlog.business.plugin;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.impl.SimpleHttpResponse;

import java.io.*;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 静态化核心逻辑，拦截 httpResponse 的 body 写入到文件
 */
public class BodySaveResponse extends SimpleHttpResponse implements AutoCloseable {

    private final File cacheFile;
    private final Lock initLock = new ReentrantLock();
    private final boolean requiredStatusCodeSuccess;
    private final int additionalAcceptedStatusCode;
    private OutputStream outputStream;
    private int statusCode;

    public BodySaveResponse(HttpRequest request, ResponseConfig responseConfig, boolean requiredStatusCodeSuccess,
                            StaticSitePlugin staticSitePlugin) {
        this(request, responseConfig, requiredStatusCodeSuccess, 200, staticSitePlugin);
    }

    BodySaveResponse(HttpRequest request, ResponseConfig responseConfig, boolean requiredStatusCodeSuccess,
                     int additionalAcceptedStatusCode, StaticSitePlugin staticSitePlugin) {
        super(request, responseConfig);
        this.additionalAcceptedStatusCode = additionalAcceptedStatusCode;
        this.cacheFile = buildCacheFile(staticSitePlugin);
        if (Objects.nonNull(cacheFile)) {
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            cacheFile.getParentFile().mkdirs();
        }
        this.requiredStatusCodeSuccess = requiredStatusCodeSuccess;
    }

    private File buildCacheFile(StaticSitePlugin staticSitePlugin) {
        if (Objects.isNull(staticSitePlugin)) {
            return null;
        }
        File cacheFile = staticSitePlugin.loadCacheFile(request);
        if (request.getUri().contains(".")) {
            return cacheFile;
        }
        if (!request.getUri().startsWith("/admin")) {
            return cacheFile;
        }
        //如果没有文件后缀的情况下，默认为需要服务端的渲染，添加文件后缀，便于 cdn 服务，通过后缀自动渲染为网页
        return new File(cacheFile + ".html");
    }

    @Override
    protected boolean needChunked(InputStream inputStream, long bodyLength) {
        return false;
    }


    @Override
    protected byte[] wrapperBaseResponseHeader(int statusCode) {
        this.statusCode = statusCode;
        return super.wrapperBaseResponseHeader(statusCode);
    }


    @Override
    protected void send(byte[] bytes, boolean body, boolean close) {
        if (Objects.isNull(cacheFile)) {
            return;
        }
        if (bytes.length == 0) {
            return;
        }
        if (!body) {
            return;
        }
        if (statusCode != 200 && statusCode != additionalAcceptedStatusCode && requiredStatusCodeSuccess) {
            return;
        }
        initLock.lock();
        try {
            if (Objects.isNull(this.outputStream)) {
                try {
                    this.outputStream = new FileOutputStream(cacheFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            initLock.unlock();
        }
        try {
            this.outputStream.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File getCacheFile() {
        if (statusCode != 200 && statusCode != additionalAcceptedStatusCode) {
            return null;
        }
        return cacheFile;
    }

    @Override
    public void close() throws Exception {
        if (Objects.isNull(outputStream)) {
            return;
        }
        this.outputStream.close();
    }
}
