package com.zrlog.util;

import com.hibegin.http.server.config.ConfigKit;
import java.util.Objects;

public class ArgsParser {

    public static Integer getPort(String[] args) {
        if (Objects.nonNull(args)) {
            for (String arg : args) {
                if (arg.startsWith("--port=")) {
                    return Integer.parseInt(arg.split("=")[1]);
                }
            }
        }
        String webPort = System.getenv("PORT");
        if (Objects.nonNull(webPort)) {
            return Integer.parseInt(webPort);
        }
        return ConfigKit.getInt("server.port", 8080);
    }

    public static String getContextPath(String[] args) {
        if (Objects.nonNull(args)) {
            for (String arg : args) {
                if (arg.startsWith("--contextPath=")) {
                    return arg.split("=")[1];
                }
            }
        }
        String contextPath = System.getenv("contextPath");
        if (Objects.nonNull(contextPath)) {
            return contextPath;
        }
        return ConfigKit.get("server.contextPath", "").toString();
    }

    public static boolean isDev(String[] args) {
        if (Objects.nonNull(args)) {
            for (String arg : args) {
                if (arg.startsWith("--dev")) {
                    return true;
                }
            }
        }
        return false;
    }
}
