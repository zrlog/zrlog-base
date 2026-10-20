package com.zrlog.business.plugin;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.handler.HttpRequestHandlerRunnable;
import com.hibegin.http.server.util.HttpRequestBuilder;
import com.zrlog.plugin.BaseStaticSitePlugin;

import java.io.File;
import java.util.Objects;

/**
 * Executes a static export request through the owning application's HTTP pipeline.
 */
public final class StaticSiteRenderer {
    private final RequestConfig requestConfig;
    private final ResponseConfig responseConfig;
    private final ApplicationContext applicationContext;

    public StaticSiteRenderer(RequestConfig requestConfig, ResponseConfig responseConfig,
                              ApplicationContext applicationContext) {
        this.requestConfig = Objects.requireNonNull(requestConfig);
        this.responseConfig = Objects.requireNonNull(responseConfig);
        this.applicationContext = Objects.requireNonNull(applicationContext);
    }

    RenderedPage render(String uri, String host, StaticSitePlugin plugin) throws Exception {
        responseConfig.setEnableGzip(false);
        HttpRequest request = HttpRequestBuilder.buildRequest(HttpMethod.GET, uri, host,
                BaseStaticSitePlugin.STATIC_USER_AGENT, requestConfig, applicationContext);
        int acceptedStatus = uri.equals(plugin.notFindFile()) ? 404 : 200;
        try (BodySaveResponse response = new BodySaveResponse(request, responseConfig, true, acceptedStatus, plugin)) {
            new HttpRequestHandlerRunnable(request, response).run();
            return new RenderedPage(request.getUri(), response.getCacheFile());
        }
    }

    static final class RenderedPage {
        final String uri;
        final File file;

        private RenderedPage(String uri, File file) {
            this.uri = uri;
            this.file = file;
        }
    }
}
