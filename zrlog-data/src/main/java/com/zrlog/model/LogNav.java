package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "lognav", idColumn = "navId", generatedId = true)
public class LogNav extends BaseEntity<Long> {
    private String navName;
    private Integer sort;
    private String url;
    private String icon;

    public String getNavName() { return navName; }
    public void setNavName(String navName) { this.navName = navName; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
