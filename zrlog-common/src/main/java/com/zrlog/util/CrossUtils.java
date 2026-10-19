package com.zrlog.util;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;

import java.util.Objects;

public class CrossUtils {

    public static boolean isEnableOrigin(HttpRequest request, boolean staticHtml) {
        String origin = request.getHeader("Origin");
        return staticHtml || Objects.nonNull(origin);
    }


    public static void cross(HttpRequest request, HttpResponse response, boolean staticHtml) {
        if (CrossUtils.isEnableOrigin(request, staticHtml)) {
            //可以跨域请求
            response.addHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
            response.addHeader("Access-Control-Allow-Credentials", "true");
        }
    }

}
