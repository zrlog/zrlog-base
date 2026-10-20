package com.hibegin.common.util.http.handle;

import com.google.gson.Gson;
import com.hibegin.common.util.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

public class HttpResponseJsonHandle<T> extends HttpHandle<T> {

    private final Class<T> clazz;
    private int statusCode;

    public HttpResponseJsonHandle(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public boolean handle(HttpRequest request, HttpResponse<InputStream> response) {
        this.statusCode = response.statusCode();
        Optional<String> s = response.headers().firstValue("Content-Encoding");
        InputStream inputStream = response.body();
        if (s.isPresent() && s.get().equals("gzip")) {
            try {
                inputStream = new GZIPInputStream(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        String jsonStr = IOUtil.getStringInputStream(inputStream);
        if (response.statusCode() == 200) {
            setT(new Gson().fromJson(jsonStr, clazz));
        } else {
            setT(null);
        }
        return true;
    }
}
