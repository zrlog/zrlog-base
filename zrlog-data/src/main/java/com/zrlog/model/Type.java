package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "type", idColumn = "typeId", generatedId = true)
public class Type extends BaseEntity<Long> {
    private String alias;
    private String remark;
    private String typeName;
    private Integer pid;
    @Column("arrange_plugin")
    private String arrangePlugin;

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public String getArrangePlugin() {
        return arrangePlugin;
    }

    public void setArrangePlugin(String arrangePlugin) {
        this.arrangePlugin = arrangePlugin;
    }
}
