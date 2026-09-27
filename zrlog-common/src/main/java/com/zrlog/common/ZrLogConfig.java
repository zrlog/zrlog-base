package com.zrlog.common;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.http.server.config.AbstractServerConfig;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.hooks.TermSignalHandler;
import com.zrlog.common.web.ZrLogErrorHandle;
import com.zrlog.common.web.ZrLogHttpJsonMessageConverter;
import com.zrlog.common.web.ZrLogHttpRequestListener;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.util.*;
import com.zrlog.web.BaseWebSetup;
import com.zrlog.web.WebSetup;
import sun.misc.Signal;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局服务配置，包含 数据库，插件，更新的实现者等
 */
public abstract class ZrLogConfig extends AbstractServerConfig {

    protected static final Logger LOGGER = LoggerUtil.getLogger(ZrLogConfig.class);
    public static boolean nativeImageAgent = false;
    protected final File installLockFile;
    protected final Plugins plugins;
    protected final Updater updater;
    protected final long createdDate;
    protected final List<WebSetup> webSetups;
    protected DataSourceWrapper dataSource;
    protected final File dbPropertiesFile;
    protected final ServerConfig serverConfig;
    protected CacheService cacheService;
    protected TokenService tokenService;
    protected ZrLogHttpRequestListener zrLogHttpRequestListener = new ZrLogHttpRequestListener();
    private static final String TERM_SIGNAL = "TERM";


    static {
        disableHikariLogging();
    }

    protected ZrLogConfig(Integer port, Updater updater, String contextPath) {
        this.createdDate = ManagementFactory.getRuntimeMXBean().getStartTime();
        this.plugins = new Plugins();
        this.webSetups = new ArrayList<>();
        this.updater = updater;
        this.serverConfig = initServerConfig(contextPath, port);
        //init
        this.installLockFile = PathUtil.getConfFile("/" + Objects.requireNonNullElse(System.getenv("INSTALL_LOCK_FILE_NAME"), "install.lock"));
        this.dbPropertiesFile = DbUtils.initDbPropertiesFile(this);
        this.dataSource = DbUtils.configDatabaseWithRetry(30, this);
        this.webSetups.add(new BaseWebSetup(this));
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public String getProgramUptime() {
        if (EnvKit.isFaaSMode()) {
            return ParseUtil.toNamingDurationString(zrLogHttpRequestListener.getTotalHandleTime(), I18nUtil.getCurrentLocale().contains("en"));
        }
        return ParseUtil.toNamingDurationString(System.currentTimeMillis() - createdDate, I18nUtil.getCurrentLocale().contains("en"));
    }


    private static void disableHikariLogging() {
        System.setProperty("org.slf4j.simpleLogger.log.com.zaxxer.hikari", "off");
    }


    public boolean isTest() {
        return "junit-test".equals(System.getProperties().getProperty("env"));
    }

    public DataSourceWrapper configDatabase() throws Exception {
        // 如果没有安装的情况下不初始化数据
        if (!isInstalled()) {
            return null;
        }
        Properties dbProperties = DbUtils.getDbProp(dbPropertiesFile);
        //启动时候进行数据库连接
        dataSource = DataSourceUtil.buildDataSource(dbProperties);
        DAO.setDs(dataSource);
        return dataSource;
    }

    public Plugins getAllPlugins() {
        return this.plugins;
    }

    public <T extends IPlugin> T getPlugin(Class<T> pluginClass) {
        for (IPlugin plugin : getAllPlugins()) {
            if (pluginClass.isInstance(plugin)) {
                return pluginClass.cast(plugin);
            }
        }
        return null;
    }

    public <T extends IPlugin> List<T> getPluginsByClazz(Class<T> pluginClass) {
        List<T> plugins = new ArrayList<>();
        for (IPlugin plugin : getAllPlugins()) {
            if (pluginClass.isInstance(plugin)) {
                plugins.add(pluginClass.cast(plugin));
            }
        }
        return plugins;
    }

    public Updater getUpdater() {
        return updater;
    }

    public CacheService getCacheService() {
        return cacheService;
    }

    public TokenService getTokenService() {
        if (tokenService == null) {
            this.tokenService = initTokenService();
        }
        return tokenService;
    }

    protected abstract TokenService initTokenService();

    public DataSource getDataSource() {
        return dataSource;
    }


    /**
     * 配置的常用参数，这里可以看出来推崇使用代码进行配置的，而不是像Spring这样的通过预定配置方式。代码控制的好处在于高度可控制性，
     * 当然这也导致了很多程序员直接硬编码的问题。
     */
    @Override
    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    /** Find an enabled module's capability without depending on its implementation. */
    public <T> T getWebSetup(Class<T> capability) {
        return webSetups.stream().filter(capability::isInstance).map(capability::cast).findFirst().orElse(null);
    }

    public void stop() {
        try {
            plugins.forEach(IPlugin::stop);
            plugins.clear();
            if (Objects.nonNull(dataSource)) {
                dataSource.close();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        } finally {
            HttpUtil.getInstance().closeHttpClient();
        }
    }

    /**
     * 通过检查特定目录下面是否存在 install.lock 文件，同时判断环境变量里面是否存在配置，进行判断是否已经完成安装
     */
    public boolean isInstalled() {
        if (StringUtils.isNotEmpty(ZrLogUtil.getDbInfoByEnv())) {
            return true;
        }
        return installLockFile.exists();
    }

    public ServerConfig initServerConfig(String contextPath, Integer port) {
        ServerConfig serverConfig = new ServerConfig().setApplicationName("zrlog").setApplicationVersion(BlogBuildInfoUtil.getVersionInfo()).setDisablePrintWebServerInfo(true);
        serverConfig.setNativeImageAgent(nativeImageAgent);
        serverConfig.setDisableSession(true);
        serverConfig.setPort(port);
        serverConfig.setContextPath(contextPath);
        serverConfig.setPidFilePathEnvKey("ZRLOG_PID_FILE");
        serverConfig.setServerPortFilePathEnvKey("ZRLOG_HTTP_PORT_FILE");
        serverConfig.setDisableSavePidFile(EnvKit.isFaaSMode());
        serverConfig.setHttpJsonMessageConverter(new ZrLogHttpJsonMessageConverter());
        serverConfig.addErrorHandle(400, new ZrLogErrorHandle(400));
        serverConfig.addErrorHandle(403, new ZrLogErrorHandle(403));
        serverConfig.addErrorHandle(404, new ZrLogErrorHandle(404));
        serverConfig.addErrorHandle(500, new ZrLogErrorHandle(500));
        serverConfig.setRequestExecutor(ThreadUtils.newFixedThreadPool(200));
        serverConfig.setDecodeExecutor(ThreadUtils.newFixedThreadPool(20));
        //optimize cpu usage
        serverConfig.setSelectNowSleepTime(200);
        serverConfig.setRequestCheckerExecutor(new ScheduledThreadPoolExecutor(1, ThreadUtils::unstarted));
        serverConfig.addRequestListener(zrLogHttpRequestListener);
        //启动成功，更新一次缓存数据
        serverConfig.addCreateSuccessHandle(() -> {
            startPlugins(!EnvKit.isFaaSMode());
            return null;
        });
        serverConfig.addCreateSuccessHandle(() -> {
            LOGGER.info("Start " + serverConfig.getApplicationName() + " used time " + (System.currentTimeMillis() - createdDate) + "ms");
            return null;
        });
        Runtime rt = Runtime.getRuntime();
        rt.addShutdownHook(new Thread(this::stop));
        Signal signalTerm = new Signal(TERM_SIGNAL);
        Signal.handle(signalTerm, new TermSignalHandler(this));
        return serverConfig;
    }


    @Override
    public RequestConfig getRequestConfig() {
        RequestConfig requestConfig = new RequestConfig();
        requestConfig.setDisableSession(true);
        requestConfig.setRouter(getServerConfig().getRouter());
        //最大的提交的body的大小
        requestConfig.setMaxRequestBodySize(1024 * 1024 * 1024);
        return requestConfig;
    }

    @Override
    public ResponseConfig getResponseConfig() {
        ResponseConfig config = new ResponseConfig();
        config.setCharSet("utf-8");
        config.setEnableGzip(true);
        config.setGzipMimeTypes(Arrays.asList("text/", "application/javascript", "application/json"));
        return config;
    }

    public abstract List<IPlugin> getBasePluginList();

    /**
     * 调用了该方法，主要用于启动插件功能，以及相应的 ZrLog 插件服务。
     */
    public void startPlugins(boolean async) {
        if (!isInstalled()) {
            return;
        }
        this.plugins.forEach(IPlugin::stop);
        this.plugins.clear();
        this.plugins.addAll(getBasePluginList());
        this.webSetups.forEach(e -> plugins.addAll(e.getPlugins()));
        if (async) {
            ThreadUtils.start(this::startPlugin);
        } else {
            startPlugin();
        }
    }

    private void startPlugin() {
        for (IPlugin plugin : plugins) {
            if (!plugin.autoStart()) {
                continue;
            }
            if (plugin.isStarted()) {
                continue;
            }
            try {
                plugin.start();
            } catch (Exception e) {
                LOGGER.severe("plugin error, " + e.getMessage());
            }
        }
    }

    public boolean isAskConfig() {
        if (EnvKit.isFaaSMode()) {
            return true;
        }
        return ZrLogUtil.isShellDockerMode();
    }

    public boolean isMissingConfig() {
        if (!isAskConfig()) {
            return false;
        }
        return StringUtils.isEmpty(ZrLogUtil.getDbInfoByEnv());
    }

}
