package com.zrlog.web.inteceptor;

import com.hibegin.http.server.api.HandleAbleInterceptor;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 全局拦截对程序状态的处理，访问非安装的页面时，统一跳转到 /install 下
 */
public class InstallStatusInterceptor implements HandleAbleInterceptor {
    private final BooleanSupplier installed;
    private final BooleanSupplier missingConfig;

    public InstallStatusInterceptor(BooleanSupplier installed, BooleanSupplier missingConfig) {
        this.installed = Objects.requireNonNull(installed);
        this.missingConfig = Objects.requireNonNull(missingConfig);
    }

    @Override
    public boolean isHandleAble(HttpRequest request) {
        String uri = request.getUri();
        if (uri.startsWith("/api")) {
            return false;
        }
        if (uri.equals("/favicon.ico")) {
            return false;
        }
        return !Objects.equals(uri, "/install") && !uri.startsWith("/install/");
    }

    @Override
    public boolean doInterceptor(HttpRequest request, HttpResponse response) {
        String target = URLEncoder.encode(request.getUri(), StandardCharsets.UTF_8);
        if (!installed.getAsBoolean()) {
            response.redirect("/install?ref=" + target);
            return false;
        }
        if (missingConfig.getAsBoolean()) {
            response.redirect("/install?ref=" + target + "&missingConfig=true");
            return false;
        }
        return true;
    }
}
