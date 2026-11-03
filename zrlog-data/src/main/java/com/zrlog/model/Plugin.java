package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "plugin", idColumn = "pluginId", generatedId = true)
public class Plugin extends BaseEntity<Long> {
    private String content;
    private Boolean isSystem;
    private String pTitle;
    private Integer sort;
    private String pluginName;
    private Integer level;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getIsSystem() { return isSystem; }
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }

    public String getPTitle() { return pTitle; }
    public void setPTitle(String pTitle) { this.pTitle = pTitle; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getPluginName() { return pluginName; }
    public void setPluginName(String pluginName) { this.pluginName = pluginName; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
}
