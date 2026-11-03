package com.zrlog.business.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

final class StaticSiteHtmlLinks {

    private StaticSiteHtmlLinks() {}

    static void parseInto(
            File file, String blogHost, Map<String, StaticSitePlugin.HandleState> pageMap)
            throws IOException {
        if (file.getName().endsWith(".js")
                || file.getName().endsWith(".json")
                || file.getName().endsWith(".css")) {
            return;
        }
        Document document = Jsoup.parse(file);
        Elements links = document.select("a");
        links.forEach(
                element -> {
                    String uri = normalizeHref(element.attr("href"), blogHost);
                    if (pageMap.containsKey(uri)) {
                        return;
                    }
                    if (uri.startsWith("/") && uri.endsWith(".html")) {
                        pageMap.put(uri, StaticSitePlugin.HandleState.NEW);
                    }
                });
    }

    static String normalizeHref(String href, String blogHost) {
        if (href.startsWith("#")) {
            return "";
        }
        if (href.startsWith("javascript:;")) {
            return "";
        }
        String uri = href.split("#")[0];
        if (uri.isEmpty()) {
            return "";
        }
        if (uri.startsWith("//")) {
            String baseUrl = "//" + Objects.toString(blogHost, "");
            if (uri.equals(baseUrl)) {
                return "/";
            }
            if (uri.startsWith(baseUrl + "/")) {
                return uri.substring(baseUrl.length());
            }
            return "";
        }
        return uri;
    }
}
