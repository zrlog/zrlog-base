package com.zrlog.common.cache.dto;

import java.io.Serializable;

public class PluginDTO implements Serializable {

    private Long id;
    private String pluginName;
    private Boolean isSystem;
    private Long level;
    private String content;
    private String pTitle;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public Boolean getSystem() {
        return isSystem;
    }

    public void setSystem(Boolean system) {
        this.isSystem = system;
    }

    /**
     * freemarker
     */
    public Boolean getIsSystem() {
        return isSystem;
    }

    public Long getLevel() {
        return level;
    }

    public void setLevel(Long level) {
        this.level = level;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getpTitle() {
        return pTitle;
    }

    public void setpTitle(String pTitle) {
        this.pTitle = pTitle;
    }
}
