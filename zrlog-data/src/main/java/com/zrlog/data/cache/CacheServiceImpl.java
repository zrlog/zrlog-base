package com.zrlog.data.cache;

import com.google.gson.Gson;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ObjectUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.service.BaseDataDbService;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.model.Tag;
import com.zrlog.model.Type;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;
import com.zrlog.util.ThreadUtils;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 对缓存数据的操作
 */
public class CacheServiceImpl implements CacheService {
    private static final Logger LOGGER = LoggerUtil.getLogger(CacheServiceImpl.class);

    private final AtomicLong version;
    private volatile BaseDataInitVO cacheInit;
    private final String CACHE_KEY = "base_data_init_cache_v3";
    private final long sqlVersion;
    private final BooleanSupplier installed;
    private final Lock lock;
    private final jakarta.inject.Provider<WebSite> websites;
    private final jakarta.inject.Provider<Type> types;
    private final jakarta.inject.Provider<Tag> tags;
    private final jakarta.inject.Provider<User> users;
    private final BaseDataDbService baseData;

    @Inject
    public CacheServiceImpl(
            BooleanSupplier installed,
            jakarta.inject.Provider<WebSite> websites,
            jakarta.inject.Provider<Type> types,
            jakarta.inject.Provider<Tag> tags,
            jakarta.inject.Provider<User> users,
            BaseDataDbService baseData,
            DistributedLock.Factory locks) {
        this.websites = Objects.requireNonNull(websites);
        this.types = Objects.requireNonNull(types);
        this.tags = Objects.requireNonNull(tags);
        this.users = Objects.requireNonNull(users);
        this.baseData = Objects.requireNonNull(baseData);
        this.lock = locks.create("base-data-init-cache");
        this.installed = Objects.requireNonNull(installed, "installed");
        long versionStart = Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "00000");
        this.version = new AtomicLong(versionStart);
        Map<String, Object> baseInfo =
                websites.get().getWebSiteByNameIn(Arrays.asList(CACHE_KEY, ZRLOG_SQL_VERSION_KEY));
        String dbCache = (String) baseInfo.get(CACHE_KEY);
        if (StringUtils.isNotEmpty(dbCache)) {
            try {
                this.cacheInit = new Gson().fromJson(dbCache, BaseDataInitVO.class);
            } catch (Exception e) {
                LOGGER.info("Load cache error " + e.getMessage());
            }
        } else {
            this.cacheInit = getInitData();
        }
        String sqlVersion = (String) baseInfo.get(ZRLOG_SQL_VERSION_KEY);
        if (StringUtils.isNotEmpty(sqlVersion)) {
            this.sqlVersion = Long.parseLong(sqlVersion);
        } else {
            this.sqlVersion = -1L;
        }
    }

    @Override
    public long getCurrentSqlVersion() {
        return sqlVersion;
    }

    @Override
    public long getWebSiteVersion() {
        if (Objects.isNull(cacheInit)) {
            return 0;
        }
        return ObjectUtil.requireNonNullElse(cacheInit.getWebSiteVersion(), 0L);
    }

    private long getUpdateVersion(boolean cleanAble) {
        if (Objects.isNull(cacheInit) || cleanAble) {
            return version.incrementAndGet();
        }
        return version.get();
    }

    @Override
    public BaseDataInitVO getInitData() {
        return getInitData(false);
    }

    @Override
    public BaseDataInitVO refreshInitData() {
        return getInitData(true);
    }

    private BaseDataInitVO getInitData(boolean cleanAble) {
        long expectVersion = getUpdateVersion(cleanAble);
        if (!cleanAble && Objects.nonNull(cacheInit)) {
            return cacheInit;
        }
        try {
            try {
                boolean locked = lock.tryLock(2, TimeUnit.MINUTES);
                if (!locked) {
                    LOGGER.warning("RefreshInitDataCache [" + version.get() + "] get lock missing");
                }
            } catch (InterruptedException e) {
                throw new DAOException(e);
            }
            if (!Objects.equals(version.get(), expectVersion)) {
                if (Constants.debugLoggerPrintAble()) {
                    LOGGER.info("Version skip " + version.get() + " -> " + expectVersion);
                }
                return cacheInit;
            }
            long start = System.currentTimeMillis();
            ExecutorService executor = ThreadUtils.newFixedThreadPool(10);
            try {
                cacheInit = baseData.queryCacheInit(executor);
                cacheInit.setVersion(expectVersion);
                try {
                    websites.get().updateByKV(CACHE_KEY, new Gson().toJson(cacheInit));
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "save cache error " + e.getMessage(), e);
                }
            } finally {
                executor.shutdown();
                if (Constants.debugLoggerPrintAble()) {
                    LOGGER.info("RefreshInitDataCache [" + version + "] used time " + (System.currentTimeMillis() - start) + "ms");
                }
            }
        } finally {
            lock.unlock();
        }
        return cacheInit;
    }

    @Override
    public List<TypeDTO> getArticleTypes() {
        if (Objects.nonNull(cacheInit)) {
            return cacheInit.getTypes();
        }
        try {
            return types.get().findAll();
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    @Override
    public List<TagDTO> getTags() {
        if (Objects.nonNull(cacheInit)) {
            return cacheInit.getTags();
        }
        try {
            return tags.get().findAll();
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    @Override
    public UserBasicDTO getUserInfoById(Long userId) {
        if (Objects.nonNull(cacheInit)) {
            UserBasicDTO userBasicDTO = cacheInit.getUsers().stream().filter(e -> Objects.equals(e.getUserId(), userId)).findFirst().orElse(null);
            if (Objects.nonNull(userBasicDTO)) {
                return userBasicDTO;
            }
        }
        try {
            return ResultBeanUtils.convert(users.get().loadById(userId), UserBasicDTO.class);
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    @Override
    public Map<String, Object> getTemplateConfigMapWithCache(String template) {
        if (Objects.nonNull(cacheInit)) {
            Map<String, Map<String, Object>> templateConfigCacheMap = cacheInit.getTemplateConfigCacheMap();
            if (Objects.nonNull(templateConfigCacheMap)) {
                Map<String, Object> configMap = templateConfigCacheMap.get(template);
                if (Objects.nonNull(configMap)) {
                    return configMap;
                }
            }
        }
        return websites.get().getTemplateConfigMap(template);
    }

    @Override
    public PublicWebSiteInfo getPublicWebSiteInfo() {
        if (!installed.getAsBoolean()) {
            PublicWebSiteInfo publicWebSiteInfo = WebSiteUtils.fillDefaultInfo(new PublicWebSiteInfo());
            //blog
            publicWebSiteInfo.setAppId("");
            publicWebSiteInfo.setTitle("");
            publicWebSiteInfo.setSecond_title("");
            publicWebSiteInfo.setKeywords("");
            publicWebSiteInfo.setDescription("");
            publicWebSiteInfo.setStaticResourceHost("");

            //other
            publicWebSiteInfo.setWebCm("");
            publicWebSiteInfo.setIcp("");
            publicWebSiteInfo.setRobotRuleContent("");
            //plugin
            publicWebSiteInfo.setComment_plugin_name("");
            publicWebSiteInfo.setHost("");
            publicWebSiteInfo.setSystem_notification("");
            publicWebSiteInfo.setAuthor("");
            return publicWebSiteInfo;
        }
        if (Objects.nonNull(cacheInit)) {
            PublicWebSiteInfo db = cacheInit.getWebSite();
            if (Objects.nonNull(db)) {
                return db;
            }
        }
        return websites.get().getPublicWebSite();
    }

}
