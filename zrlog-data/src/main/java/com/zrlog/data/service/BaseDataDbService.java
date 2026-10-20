package com.zrlog.data.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.vo.Archive;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.cache.vo.HotLogBasicInfoEntry;
import com.zrlog.common.cache.vo.HotTypeLogInfo;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.util.DateValueFormatUtils;
import com.zrlog.model.*;
import com.zrlog.model.Link;
import com.zrlog.model.Log;
import com.zrlog.model.LogNav;
import com.zrlog.model.Plugin;
import com.zrlog.model.Tag;
import com.zrlog.model.Type;
import com.zrlog.model.User;
import com.zrlog.model.WebSite;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 全局基础数据的查询
 */
public class BaseDataDbService {

    private final jakarta.inject.Provider<Link> linkProvider;
    private final jakarta.inject.Provider<Log> logProvider;
    private final jakarta.inject.Provider<LogNav> logNavProvider;
    private final jakarta.inject.Provider<Plugin> pluginProvider;
    private final jakarta.inject.Provider<Tag> tagProvider;
    private final jakarta.inject.Provider<Type> typeProvider;
    private final jakarta.inject.Provider<User> userProvider;
    private final jakarta.inject.Provider<WebSite> webSiteProvider;

    @Inject
    public BaseDataDbService(
            jakarta.inject.Provider<Link> linkProvider,
            jakarta.inject.Provider<Log> logProvider,
            jakarta.inject.Provider<LogNav> logNavProvider,
            jakarta.inject.Provider<Plugin> pluginProvider,
            jakarta.inject.Provider<Tag> tagProvider,
            jakarta.inject.Provider<Type> typeProvider,
            jakarta.inject.Provider<User> userProvider,
            jakarta.inject.Provider<WebSite> webSiteProvider) {
        this.linkProvider = Objects.requireNonNull(linkProvider);
        this.logProvider = Objects.requireNonNull(logProvider);
        this.logNavProvider = Objects.requireNonNull(logNavProvider);
        this.pluginProvider = Objects.requireNonNull(pluginProvider);
        this.tagProvider = Objects.requireNonNull(tagProvider);
        this.typeProvider = Objects.requireNonNull(typeProvider);
        this.userProvider = Objects.requireNonNull(userProvider);
        this.webSiteProvider = Objects.requireNonNull(webSiteProvider);
    }

    private static final Logger LOGGER = LoggerUtil.getLogger(BaseDataDbService.class);

    private HotLogBasicInfoEntry convertToBasicVO(ArticleBasicDTO log) {
        String format = "yyyy-MM-dd";
        log.setReleaseTime(DateValueFormatUtils.format(log.getReleaseTime(), format));
        log.setLastUpdateDate(DateValueFormatUtils.format(log.getLastUpdateDate(), format));
        log.setLast_update_date(DateValueFormatUtils.format(log.getLast_update_date(), format));
        return BeanUtil.convert(log, HotLogBasicInfoEntry.class);
    }

    public static long getWebSiteVersion(PublicWebSiteInfo website) {
        return new Gson().toJson(website).hashCode();
    }

    private static List<Archive> getConvertedArchives(Map<String, Long> archiveMap) {
        List<Archive> archives = new ArrayList<>();
        for (Map.Entry<String, Long> entry : archiveMap.entrySet()) {
            Archive archive = new Archive();
            archive.setCount(entry.getValue());
            archive.setText(entry.getKey());
            String tagUri = Constants.getArticleUri() + "record/" + entry.getKey();
            archive.setUrl(tagUri);
            archives.add(archive);
        }
        return archives;
    }

    public BaseDataInitVO queryCacheInit(Executor executor) {
        BaseDataInitVO cacheInit = new BaseDataInitVO();
        // first set website info
        PublicWebSiteInfo refreshWebSite = webSiteProvider.get().getPublicWebSite();
        cacheInit.setWebSite(refreshWebSite);
        cacheInit.setWebSiteVersion(getWebSiteVersion(refreshWebSite));
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        BaseDataInitVO.Statistics statistics = new BaseDataInitVO.Statistics();
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            statistics.setTotalArticleSize(logProvider.get().getVisitorCount());
                            cacheInit.setStatistics(statistics);
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                cacheInit.setLinks(linkProvider.get().findAll());
                            } catch (SQLException e) {
                                throw new DAOException(e);
                            }
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                cacheInit.setTypes(typeProvider.get().findAll());
                                statistics.setTotalTypeSize((long) cacheInit.getTypes().size());
                                List<TypeDTO> types = cacheInit.getTypes();
                                List<HotTypeLogInfo> indexHotLog = new ArrayList<>();
                                cacheInit.setTypeHotLogs(indexHotLog);
                                // 设置分类Hot
                                for (TypeDTO type : types) {
                                    futures.add(
                                            CompletableFuture.runAsync(
                                                    () -> {
                                                        HotTypeLogInfo hotTypeLogInfo =
                                                                new HotTypeLogInfo();
                                                        String alias = type.getAlias();
                                                        hotTypeLogInfo.setTypeAlias(alias);
                                                        hotTypeLogInfo.setTypeName(
                                                                type.getTypeName());
                                                        hotTypeLogInfo.setTypeId(type.getId());
                                                        hotTypeLogInfo.setLogs(
                                                                logProvider
                                                                        .get()
                                                                        .findByTypeAlias(
                                                                                1, 6, alias)
                                                                        .getRows()
                                                                        .stream()
                                                                        .map(this::convertToBasicVO)
                                                                        .collect(
                                                                                Collectors
                                                                                        .toList()));
                                                        indexHotLog.add(hotTypeLogInfo);
                                                    },
                                                    executor));
                                }
                            } catch (SQLException e) {
                                throw new DAOException(e);
                            }
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            // Last article
                            cacheInit.setHotLogs(
                                    logProvider
                                            .get()
                                            .visitorFind(new PageRequestImpl(1L, 6L), "")
                                            .getRows()
                                            .stream()
                                            .map(this::convertToBasicVO)
                                            .collect(Collectors.toList()));
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                cacheInit.setLogNavs(logNavProvider.get().findAll());
                            } catch (SQLException e) {
                                throw new DAOException(e);
                            }
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                cacheInit.setPlugins(pluginProvider.get().findAll());
                            } catch (SQLException e) {
                                throw new DAOException(e);
                            }
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                cacheInit.setArchives(logProvider.get().getArchives());
                                cacheInit.setArchiveList(
                                        getConvertedArchives(cacheInit.getArchives()));
                            } catch (SQLException e) {
                                throw new DAOException(e);
                            }
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                cacheInit.setUsers(userProvider.get().findBasicAll());
                            } catch (SQLException e) {
                                throw new DAOException(e);
                            }
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                cacheInit.setTags(tagProvider.get().refreshTag());
                            } catch (SQLException e) {
                                throw new DAOException(e);
                            }
                            statistics.setTotalTagSize((long) cacheInit.getTags().size());
                        },
                        executor));
        futures.add(
                CompletableFuture.runAsync(
                        () -> {
                            String template = refreshWebSite.getTemplate();
                            if (StringUtils.isNotEmpty(template)) {
                                cacheInit
                                        .getTemplateConfigCacheMap()
                                        .put(
                                                template,
                                                webSiteProvider
                                                        .get()
                                                        .getTemplateConfigMap(template));
                            }
                        }));
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            LOGGER.warning("Load data error " + e.getMessage());
        }
        if (cacheInit.getTags() == null || cacheInit.getTags().isEmpty()) {
            cacheInit.getPlugins().stream().filter(e -> Objects.equals(e.getPluginName(), "tags")).findFirst().ifPresent(e -> {
                cacheInit.getPlugins().remove(e);
            });
        }
        if (cacheInit.getArchives() == null || cacheInit.getArchives().isEmpty()) {
            cacheInit.getPlugins().stream().filter(e -> Objects.equals(e.getPluginName(), "archives")).findFirst().ifPresent(e -> {
                cacheInit.getPlugins().remove(e);
            });
        }
        if (cacheInit.getTypes() == null || cacheInit.getTypes().isEmpty()) {
            cacheInit.getPlugins().stream().filter(e -> Objects.equals(e.getPluginName(), "types")).findFirst().ifPresent(e -> {
                cacheInit.getPlugins().remove(e);
            });
        }
        if (cacheInit.getLinks() == null || cacheInit.getLinks().isEmpty()) {
            cacheInit.getPlugins().stream().filter(e -> Objects.equals(e.getPluginName(), "links")).findFirst().ifPresent(e -> {
                cacheInit.getPlugins().remove(e);
            });
        }
        cacheInit.getTypeHotLogs().sort(Comparator.comparing(x -> Math.toIntExact(x.getTypeId())));
        return cacheInit;
    }
}
