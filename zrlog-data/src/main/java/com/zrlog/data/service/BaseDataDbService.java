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
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 全局基础数据的查询
 */
public class BaseDataDbService {

    private static final Logger LOGGER = LoggerUtil.getLogger(BaseDataDbService.class);
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

    private HotLogBasicInfoEntry convertToBasicVO(ArticleBasicDTO log) {
        String format = "yyyy-MM-dd";
        log.setReleaseTime(DateValueFormatUtils.format(log.getReleaseTime(), format));
        log.setLastUpdateDate(DateValueFormatUtils.format(log.getLastUpdateDate(), format));
        log.setLast_update_date(DateValueFormatUtils.format(log.getLast_update_date(), format));
        return BeanUtil.convert(log, HotLogBasicInfoEntry.class);
    }

    private CompletableFuture<List<HotTypeLogInfo>> loadTypeHotLogs(List<TypeDTO> types, Executor executor) {
        List<CompletableFuture<HotTypeLogInfo>> loads = new ArrayList<>();
        for (TypeDTO type : types) {
            loads.add(CompletableFuture.supplyAsync(() -> {
                HotTypeLogInfo info = new HotTypeLogInfo();
                info.setTypeAlias(type.getAlias());
                info.setTypeName(type.getTypeName());
                info.setTypeId(type.getId());
                info.setLogs(logProvider.get().findByTypeAlias(1, 6, type.getAlias()).getRows()
                        .stream().map(this::convertToBasicVO).collect(Collectors.toList()));
                return info;
            }, executor));
        }
        // Include every category load in the parent future before publishing the list.
        return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> loads.stream().map(CompletableFuture::join).collect(Collectors.toList()));
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
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                List<TypeDTO> types = typeProvider.get().findAll();
                                cacheInit.setTypes(types);
                                statistics.setTotalTypeSize((long) types.size());
                                return types;
                            } catch (SQLException e) {
                                throw new DAOException(e);
                            }
                        },
                        executor)
                        .thenCompose(types -> loadTypeHotLogs(types, executor))
                        .thenAccept(cacheInit::setTypeHotLogs));
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
