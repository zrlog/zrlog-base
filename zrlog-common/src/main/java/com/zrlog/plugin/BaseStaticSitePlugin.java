package com.zrlog.plugin;

import com.hibegin.http.server.api.HttpRequest;
import java.io.File;
import java.util.Objects;
import java.util.UUID;

public interface BaseStaticSitePlugin extends IPlugin {

    /** 随机生成 UA，仅让内部执行的程序才能判定为 StaticPlugin，才产生静态文件 */
    String STATIC_USER_AGENT =
            "Static-Blog-Plugin/" + UUID.randomUUID().toString().replace("-", "");

    static boolean isStaticPluginRequest(HttpRequest request) {
        if (Objects.isNull(request)) {
            return false;
        }
        String ua = request.getHeader("User-Agent");
        if (Objects.isNull(ua)) {
            return false;
        }
        return Objects.equals(ua, STATIC_USER_AGENT);
    }

    File loadCacheFile(HttpRequest request);
}
