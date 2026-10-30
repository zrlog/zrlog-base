package com.zrlog.business.plugin;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.StringUtils;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.plugin.IPlugin;
import com.zrlog.util.ThreadUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 当超过指定时间后，删除缓存数据，避免缓存到脏数据一直存在。
 */
@Singleton
public class CacheManagerPlugin implements IPlugin {

    private final CacheService cacheService;
    private final WebsiteKvService settings;
    private ScheduledExecutorService scheduledThreadPoolExecutor;
    private boolean started = false;
    private boolean externallyScheduled;

    @Inject
    public CacheManagerPlugin(CacheService cacheService, WebsiteKvService settings) {
        this.settings = settings;
        this.cacheService = cacheService;
    }

    @Override
    public boolean autoStart() {
        return !externallyScheduled && !EnvKit.isFaaSMode();
    }

    @Override
    public boolean start() {
        if (externallyScheduled) return true;
        if (started) {
            return true;
        }
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, ThreadUtils.getThreadFactory());
        scheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() - Constants.getLastAccessTime() > getInitDataMaxCacheTimeout()) {
                cacheService.refreshInitData();
            }
        }, 0, getInitDataMaxCacheTimeout(), TimeUnit.SECONDS);
        this.started = true;
        return true;
    }

    /** The host's durable scheduler takes ownership while other deployments retain the legacy timer. */
    public synchronized void setExternallyScheduled(boolean value) {
        if (value) stop();
        externallyScheduled = value;
    }

    private int getInitDataMaxCacheTimeout() {
        String dbSettingSize = settings.getString("cache_timeout_minutes");
        if (StringUtils.isEmpty(dbSettingSize)) {
            dbSettingSize = "5";
        }
        return (int) (Double.parseDouble(dbSettingSize) * 60 * 1000);
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean stop() {
        if (Objects.nonNull(scheduledThreadPoolExecutor)) {
            scheduledThreadPoolExecutor.shutdown();
            scheduledThreadPoolExecutor = null;
        }
        started = false;
        return true;
    }
}
