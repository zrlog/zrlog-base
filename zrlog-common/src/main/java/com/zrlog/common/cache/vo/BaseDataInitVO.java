package com.zrlog.common.cache.vo;

import com.zrlog.common.cache.dto.*;
import com.zrlog.common.vo.PublicWebSiteInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 程序初始化的数据（及全局的数据），存放了标签，分类，网站设置等信息。
 */
public class BaseDataInitVO implements Serializable {

    private Long version;
    private Long webSiteVersion;
    private List<TagDTO> tags = new ArrayList<>();
    private List<TypeDTO> types = new ArrayList<>();
    private List<LinkDTO> links = new ArrayList<>();
    private List<PluginDTO> plugins = new ArrayList<>();
    private Map<String, Long> archives = new HashMap<>();
    private List<Archive> archiveList = new ArrayList<>();
    private PublicWebSiteInfo webSite = new PublicWebSiteInfo();
    private List<HotLogBasicInfoEntry> hotLogs = new ArrayList<>();
    private List<LogNavDTO> logNavs = new ArrayList<>();
    private List<HotTypeLogInfo> typeHotLogs = new ArrayList<>();
    private Statistics statistics = new Statistics();
    private List<UserBasicDTO> users = new ArrayList<>();
    private Map<String, Map<String, Object>> templateConfigCacheMap = new ConcurrentHashMap<>();
    public BaseDataInitVO() {
    }

    public Map<String, Long> getArchives() {
        return archives;
    }

    public void setArchives(Map<String, Long> archives) {
        this.archives = archives;
    }

    public List<Archive> getArchiveList() {
        return archiveList;
    }

    public void setArchiveList(List<Archive> archiveList) {
        this.archiveList = archiveList;
    }

    public List<HotLogBasicInfoEntry> getHotLogs() {
        return hotLogs;
    }

    public void setHotLogs(List<HotLogBasicInfoEntry> hotLogs) {
        this.hotLogs = hotLogs;
    }

    public List<TypeDTO> getTypes() {
        return types;
    }

    public void setTypes(List<TypeDTO> types) {
        this.types = types;
    }

    public List<LinkDTO> getLinks() {
        return links;
    }

    public void setLinks(List<LinkDTO> links) {
        this.links = links;
    }

    public List<LogNavDTO> getLogNavs() {
        return logNavs;
    }

    public void setLogNavs(List<LogNavDTO> logNavs) {
        this.logNavs = logNavs;
    }

    public List<HotTypeLogInfo> getTypeHotLogs() {
        return typeHotLogs;
    }

    public void setTypeHotLogs(List<HotTypeLogInfo> typeHotLogs) {
        this.typeHotLogs = typeHotLogs;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public Long getWebSiteVersion() {
        return webSiteVersion;
    }

    public void setWebSiteVersion(Long webSiteVersion) {
        this.webSiteVersion = webSiteVersion;
    }

    public PublicWebSiteInfo getWebSite() {
        return webSite;
    }

    public void setWebSite(PublicWebSiteInfo webSite) {
        this.webSite = webSite;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<TagDTO> getTags() {
        return tags;
    }

    public void setTags(List<TagDTO> tags) {
        this.tags = tags;
    }

    public List<PluginDTO> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<PluginDTO> plugins) {
        this.plugins = plugins;
    }

    public List<UserBasicDTO> getUsers() {
        return users;
    }

    public void setUsers(List<UserBasicDTO> users) {
        this.users = users;
    }

    public Map<String, Map<String, Object>> getTemplateConfigCacheMap() {
        return templateConfigCacheMap;
    }

    public void setTemplateConfigCacheMap(Map<String, Map<String, Object>> templateConfigCacheMap) {
        this.templateConfigCacheMap = templateConfigCacheMap;
    }

    public static class Statistics implements Serializable {

        private Long totalArticleSize;
        private Long totalTagSize;
        private Long totalTypeSize;
        public Statistics() {
        }

        public Long getTotalArticleSize() {
            return totalArticleSize;
        }

        public void setTotalArticleSize(Long totalArticleSize) {
            this.totalArticleSize = totalArticleSize;
        }

        public Long getTotalTagSize() {
            return totalTagSize;
        }

        public void setTotalTagSize(Long totalTagSize) {
            this.totalTagSize = totalTagSize;
        }

        public Long getTotalTypeSize() {
            return totalTypeSize;
        }

        public void setTotalTypeSize(Long totalTypeSize) {
            this.totalTypeSize = totalTypeSize;
        }
    }
}
