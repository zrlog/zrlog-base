package com.zrlog.blog.web.plugin;

import com.hibegin.common.BaseLockObject;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.*;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.StaticSiteRenderer;
import com.zrlog.business.plugin.StaticGenerationBudget;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.business.template.util.BlogResourceUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.data.dto.FaviconBase64DTO;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BlogPageStaticSitePlugin extends BaseLockObject implements StaticSitePlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger(BlogPageStaticSitePlugin.class);
    private final StaticSiteRenderer renderer;
    private final Supplier<FaviconBase64DTO> faviconSource;
    private final Supplier<PublicWebSiteInfo> site;
    private final Map<String, HandleState> handleStatusPageMap = new ConcurrentHashMap<>();
    private final String contextPath;
    private final String defaultLang;
    private final ReentrantLock parseLock = new ReentrantLock();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final List<File> cacheFiles = new CopyOnWriteArrayList<>();
    private final String favicon = "/favicon.ico";

    public BlogPageStaticSitePlugin(
            StaticSiteRenderer renderer,
            Supplier<PublicWebSiteInfo> site,
            String contextPath,
            Supplier<FaviconBase64DTO> faviconSource) {
        this.renderer = Objects.requireNonNull(renderer);
        this.faviconSource = Objects.requireNonNull(faviconSource);
        this.site = Objects.requireNonNull(site);
        this.contextPath = contextPath;
        this.defaultLang = Constants.DEFAULT_LANGUAGE;
        File cacheFolder = getCacheFile("/");
        if (cacheFolder.exists()) {
            FileUtils.deleteFile(cacheFolder.toString());
        }
    }

    @Override
    public PublicWebSiteInfo getSiteInfo() {
        return site.get();
    }

    @Override
    public ReentrantLock getParseLock() {
        return parseLock;
    }

    @Override
    public Executor getExecutorService() {
        return executorService;
    }

    private void refreshFavicon() {
        FaviconBase64DTO faviconBase64DTO = faviconSource.get();
        faviconHandle(faviconBase64DTO.getFavicon_ico_base64(), favicon, ResultValueConvertUtils.toBoolean(faviconBase64DTO.getGenerator_html_status()));
    }

    private void handleRobotsTxt(PublicWebSiteInfo webSite) {
        String robotTxt = webSite.getRobotRuleContent();

        if (StringUtils.isEmpty(robotTxt)) {
            return;
        }
        File robotFile = PathUtil.getStaticFile("robots.txt");
        if (!robotFile.getParentFile().exists()) {
            robotFile.getParentFile().mkdirs();
        }
        IOUtil.writeStrToFile(robotTxt, robotFile);
        if (webSite.getGenerator_html_status()) {
            try {
                saveToCacheFolder(new FileInputStream(robotFile), "/" + robotFile.getName());
            } catch (FileNotFoundException e) {
                LOGGER.warning("save to Cache error " + e.getMessage());
            }
        }
    }

    @Override
    public Map<String, HandleState> getHandleStatusPageMap() {
        return handleStatusPageMap;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getDefaultLang() {
        return defaultLang;
    }

    private void copyAssetsFiles() {
        BlogResourceUtils.getInstance().searchResources("assets/").forEach(e -> {
            copyResourceToCacheFolder("/" + e);
        });
    }

    private void copyFaviconFile() {
        File faviconFile = PathUtil.getStaticFile(favicon);
        if (faviconFile.exists()) {
            try {
                saveToCacheFolder(new FileInputStream(faviconFile), "/" + faviconFile.getName());
            } catch (FileNotFoundException e) {
                LOGGER.warning("Handle " + favicon + " resource " + faviconFile);
            }
            return;
        }
        //favicon
        copyResourceToCacheFolder(favicon);
    }

    private void copyTemplateAssets(String templatePath) {
        TemplateVO templateVO = TemplateInfoHelper.loadTemplateVO(templatePath);
        if (Objects.isNull(templateVO)) {
            return;
        }
        if (templateVO.isClasspathTemplate()) {
            templateVO.getStaticResources().forEach(e -> {
                List<String> searchResources = BlogResourceUtils.getInstance().searchResources(templatePath.substring(1) + "/" + e).stream().filter(x -> !x.endsWith(".styl")).collect(Collectors.toList());
                searchResources.forEach(x -> copyResourceToCacheFolder("/" + x));
            });
        } else {
            templateVO.getStaticResources().forEach(e -> {
                List<File> files = new ArrayList<>();
                FileUtils.getAllFiles(PathUtil.getStaticFile(templateVO.getTemplate() + "/" + e).toString(), files);
                for (File file : files) {
                    try {
                        saveToCacheFolder(new FileInputStream(file), file.toString().substring(PathUtil.getStaticPath().length()));
                    } catch (FileNotFoundException ex) {
                        LOGGER.warning("Handle " + file + " resource error: " + ex.getMessage());
                    }
                }
            });
        }
    }

    @Override
    public String getVersionFileName() {
        return "version.txt";
    }

    @Override
    public String getDbCacheKey() {
        return "static_blog_version";
    }

    private void doGenerator() {
        if (isDisabled()) {
            return;
        }
        PublicWebSiteInfo webSite = site.get();
        refreshFavicon();
        handleRobotsTxt(webSite);
        copyAssetsFiles();
        copyFaviconFile();
        copyTemplateAssets(webSite.getTemplate());
        handleStatusPageMap.clear();
        //从首页开始查找
        handleStatusPageMap.put(contextPath + "/", HandleState.NEW);
        //生成 404 页面，用于配置第三方 cdn，或者云存储的错误页面
        handleStatusPageMap.put(notFindFile(), HandleState.NEW);
        saveToCacheFolder(new ByteArrayInputStream(("/* " + notFindFile() + " 404").getBytes()), "/_redirects");
        doFetch(renderer);
    }

    @Override
    public boolean start() {
        lock.lock();
        try {
            cacheFiles.clear();
            doGenerator();
        } finally {
            lock.unlock();
        }
        return true;
    }

    @Override
    public boolean startWithinBudget(StaticGenerationBudget budget) throws Exception {
        budget.check();
        if (!lock.tryLock(budget.remainingMillis(), TimeUnit.MILLISECONDS))
            throw new IllegalStateException("Static generation is busy");
        try {
            budget.check();
            if (isDisabled()) return true;
            cacheFiles.clear();
            PublicWebSiteInfo webSite = site.get();
            FaviconBase64DTO icon = faviconSource.get();
            faviconWithinBudget(icon.getFavicon_ico_base64(), favicon,
                    ResultValueConvertUtils.toBoolean(icon.getGenerator_html_status()), budget);
            String robots = webSite.getRobotRuleContent();
            if (!StringUtils.isEmpty(robots)) {
                budget.check();
                File file = PathUtil.getStaticFile("robots.txt");
                java.nio.file.Files.createDirectories(file.toPath().getParent());
                java.nio.file.Files.writeString(file.toPath(), robots);
                saveToCacheFolderWithinBudget(new FileInputStream(file), "/robots.txt", budget);
            }
            for (String asset : BlogResourceUtils.getInstance().searchResources("assets/"))
                copyResourceWithinBudget("/" + asset, budget);
            File iconFile = PathUtil.getStaticFile(favicon);
            if (iconFile.exists()) saveToCacheFolderWithinBudget(new FileInputStream(iconFile), favicon, budget);
            else copyResourceWithinBudget(favicon, budget);
            copyTemplateWithinBudget(webSite.getTemplate(), budget);
            budget.check();
            handleStatusPageMap.clear();
            handleStatusPageMap.put(contextPath + "/", HandleState.NEW);
            handleStatusPageMap.put(notFindFile(), HandleState.NEW);
            saveToCacheFolderWithinBudget(new ByteArrayInputStream(("/* " + notFindFile() + " 404")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)), "/_redirects", budget);
            doFetchWithinBudget(renderer, budget);
            return true;
        } finally { lock.unlock(); }
    }

    private void copyTemplateWithinBudget(String templatePath, StaticGenerationBudget budget) throws Exception {
        budget.check();
        TemplateVO template = TemplateInfoHelper.loadTemplateVO(templatePath);
        if (template == null) throw new IllegalStateException("Static template is unavailable");
        for (String resource : template.getStaticResources()) {
            budget.check();
            if (template.isClasspathTemplate()) {
                for (String name : BlogResourceUtils.getInstance().searchResources(templatePath.substring(1) + "/" + resource))
                    if (!name.endsWith(".styl")) copyResourceWithinBudget("/" + name, budget);
            } else {
                java.nio.file.Path root = PathUtil.getStaticFile(template.getTemplate() + "/" + resource).toPath();
                if (!java.nio.file.Files.exists(root)) continue;
                try (var files = java.nio.file.Files.walk(root)) {
                    var iterator = files.iterator();
                    while (iterator.hasNext()) {
                        budget.check();
                        java.nio.file.Path file = iterator.next();
                        if (java.nio.file.Files.isRegularFile(file))
                            saveToCacheFolderWithinBudget(new FileInputStream(file.toFile()),
                                    file.toString().substring(PathUtil.getStaticPath().length()), budget);
                    }
                }
            }
        }
    }

    @Override
    public boolean autoStart() {
        if (EnvKit.isFaaSMode()) {
            return false;
        }
        return !isDisabled();
    }

    @Override
    public boolean isStarted() {
        return lock.isLocked();
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public List<File> getCacheFiles() {
        return cacheFiles;
    }

    @Override
    public StaticSiteType getType() {
        return StaticSiteType.BLOG;
    }
}
