package com.zrlog.business.template;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.StaticFileCacheUtils;
import com.zrlog.util.ThreadUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 对响应的模板文件生成的 html 进行转化，主要处理静态资源文件和插件相关的逻辑
 */
public class HtmlTemplateProcessor {

    private static final Logger LOGGER = LoggerUtil.getLogger(HtmlTemplateProcessor.class);

    private final long startTime;
    private final AtomicLong pluginId = new AtomicLong(0);

    private final String baseUrl;

    private final HttpRequest request;

    private final AdminTokenVO adminTokenVO;

    private final String staticResourceBaseUrl;
    private final PluginCorePlugin pluginCore;

    public HtmlTemplateProcessor(HttpRequest request, AdminTokenVO adminTokenVO, String staticResourceBaseUrl,
                                 PluginCorePlugin pluginCore) {
        this.pluginCore = pluginCore;
        this.baseUrl = request.getContextPath() + "/";
        this.request = request;
        this.adminTokenVO = adminTokenVO;
        this.startTime = request.getCreateTime();
        this.staticResourceBaseUrl = staticResourceBaseUrl + request.getContextPath();
    }

    private void handlePluginTag(Document document, Map<String, String> replaceMap) {
        Elements plugins = document.select("plugin[name]");
        if (plugins.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Element tag : plugins) {
            ExecutorService executorService = ThreadUtils.newFixedThreadPool(Math.min(4, plugins.size()));
            try {
                CompletableFuture<Void> voidCompletableFuture = parseCustomHtmlTag(tag, replaceMap, executorService);
                if (Objects.nonNull(voidCompletableFuture)) {
                    futures.add(voidCompletableFuture);
                }
            } finally {
                executorService.shutdown();
            }
        }
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    public String transform(String htmlStr) {
        Document document = Jsoup.parse(htmlStr, "", Parser.htmlParser());
        Map<String, String> replaceMap = new ConcurrentHashMap<>();
        for (Element tag : document.getAllElements()) {
            String tagName = tag.tagName();
            addStaticResourceFlag(tag, tagName);
        }
        handlePluginTag(document, replaceMap);
        String html = document.html();
        for (Map.Entry<String, String> entry : replaceMap.entrySet()) {
            html = html.replace(entry.getKey(), entry.getValue());
        }
        String versionInfo = SecurityUtils.md5(html);
        if (BaseStaticSitePlugin.isStaticPluginRequest(request)) {
            return html + "<!--" + versionInfo + "-->";
        }
        return html + "<!--" + (System.currentTimeMillis() - startTime) + "ms(" + versionInfo + ")-->";
    }

    private CompletableFuture<Void> parseCustomHtmlTag(Element element, Map<String, String> replaceMap, ExecutorService executorService) {
        String name = element.attr("name");
        if (name.isEmpty()) {
            return null;
        }
        element.attr("data-plugin-id", pluginId.incrementAndGet() + "");
        return CompletableFuture.runAsync(() -> {
            String url = "/" + element.attr("name") + "/" + element.attr("view").replaceFirst("/", "");
            if (!element.attr("param").isEmpty()) {
                url += "?" + element.attr("param");
            }

            try {
                CloseResponseHandle handle = pluginCore.getContext(url, HttpMethod.GET, request, adminTokenVO);
                try (InputStream in = handle.getT().body()) {
                    byte[] bytes = IOUtil.getByteByInputStream(in);
                    if (handle.getStatusCode() != 200) {
                        throw new RuntimeException("Template plugin page render status error " + handle.getStatusCode() + ", response body " + new String(bytes));
                    }
                    replaceMap.put(element.outerHtml(), new String(bytes, StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Template plugin page render error " + e.getMessage(), e);
            }
        }, executorService);

    }

    private void addStaticResourceFlag(Element tag, String tagName) {
        if ("script".equals(tagName)) {
            String src = tag.attr("src");
            if (!src.isEmpty()) {
                tag.attr("src", tryReplace(src));
            }
        }
        if ("link".equals(tagName)) {
            String src = tag.attr("href");
            if (!src.isEmpty()) {
                tag.attr("href", tryReplace(src));
            }
        }
    }

    private String tryReplace(String href) {
        String uriPath = href;
        if (href.startsWith(staticResourceBaseUrl)) {
            uriPath = href.substring(staticResourceBaseUrl.length());
        } else if (href.startsWith(baseUrl)) {
            uriPath = href.substring(baseUrl.length());
        }
        String flag = StaticFileCacheUtils.getInstance().getFileFlagFirstByCache(uriPath.split("\\?")[0]);
        if (flag != null) {
            if (href.contains("?")) {
                href = href + "&t=" + flag;
            } else {
                href = href + "?t=" + flag;
            }
        }
        return href;
    }

}