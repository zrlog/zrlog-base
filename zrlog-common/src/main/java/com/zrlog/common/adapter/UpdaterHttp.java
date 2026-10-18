package com.zrlog.common.adapter;

import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpFileHandle;
import com.hibegin.common.util.http.handle.HttpStringHandle;
import com.zrlog.updater.spi.UpdateHttp;
import com.zrlog.util.NetworkProxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Supplies the application's existing HTTP client and user agent to updater modules. */
final class UpdaterHttp implements UpdateHttp {
    public static final UpdaterHttp INSTANCE = new UpdaterHttp();
    private final URI dockerEndpoint;
    private UpdaterHttp() {
        URI endpoint = null;
        String url = System.getenv("ZRLOG_UPDATER_URL");
        if (url != null) {
            try {
                URI candidate = URI.create(url);
                if (("http".equals(candidate.getScheme()) || "https".equals(candidate.getScheme()))
                        && candidate.getHost() != null && candidate.getUserInfo() == null
                        && candidate.getQuery() == null && candidate.getFragment() == null
                        && (candidate.getPath().isEmpty() || "/".equals(candidate.getPath()))) endpoint = candidate;
            } catch (IllegalArgumentException ignored) {
                // The Docker client reports invalid deployment settings.
            }
        }
        dockerEndpoint = endpoint;
    }

    public String read(URI uri) throws IOException {
        try {
            HttpStringHandle response = new HttpStringHandle();
            HttpUtil.getInstance().sendGetRequest(uri.toString(), response, Map.of());
            int status = response.getStatusCode();
            if (status == 404) throw new FileNotFoundException("Update metadata source returned HTTP 404");
            if (status != 200) throw new IOException("Update metadata source returned HTTP " + status);
            return response.getT();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Update metadata request interrupted", error);
        } catch (URISyntaxException error) {
            throw new IOException("Invalid update metadata URL", error);
        }
    }
    public int download(URI uri, File file) throws Exception {
        HttpFileHandle handle = new HttpFileHandle(file.getParent(), file.getName());
        handle.setT(file);
        HttpUtil.getInstance().sendGetRequest(uri.toString(), handle, Map.of());
        return handle.getStatusCode();
    }
    /** Docker control traffic stays within the deployment, independently of outbound proxy settings. */
    public HttpResponse<String> send(HttpRequest request) throws Exception {
        if (dockerEndpoint == null || !dockerEndpoint.getScheme().equals(request.uri().getScheme())
                || !dockerEndpoint.getRawAuthority().equalsIgnoreCase(request.uri().getRawAuthority())) {
            return HttpUtil.getInstance().sendRequest(request);
        }
        NetworkProxy.Settings settings = NetworkProxy.initialize();
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
                .expectContinue(request.expectContinue());
        request.timeout().ifPresent(builder::timeout);
        request.version().ifPresent(builder::version);
        request.headers().map().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (!"default".equals(settings.getSource())) builder.setHeader("X-ZrLog-HTTP-Proxy", settings.getUrl());
        if (!"default".equals(settings.getHttpsSource())) builder.setHeader("X-ZrLog-HTTPS-Proxy", settings.getHttpsUrl());
        if (!"default".equals(settings.getNoProxySource())) builder.setHeader("X-ZrLog-No-Proxy", settings.getNoProxy());
        return HttpUtil.getInstance().sendDirectRequest(builder.build());
    }
}
