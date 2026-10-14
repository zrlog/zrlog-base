package com.zrlog.common.updater;

import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpFileHandle;
import com.hibegin.common.util.http.handle.HttpStringHandle;
import com.zrlog.updater.spi.UpdateHttp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/** Supplies the application's existing HTTP client and user agent to updater modules. */
public final class UpdaterHttp implements UpdateHttp {
    public static final UpdaterHttp INSTANCE = new UpdaterHttp();
    private UpdaterHttp() { }

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
    public HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpUtil.getInstance().sendRequest(request);
    }
}
