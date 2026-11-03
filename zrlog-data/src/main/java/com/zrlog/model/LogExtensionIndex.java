package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "log_extension_index", idColumn = "id", generatedId = true)
public class LogExtensionIndex extends BaseEntity<Long> {
    @Column("log_id")
    private Integer logId;
    private String namespace;
    @Column("extension_path")
    private String extensionPath;
    @Column("extension_value")
    private String extensionValue;

    public Integer getLogId() { return logId; }
    public void setLogId(Integer logId) { this.logId = logId; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getExtensionPath() { return extensionPath; }
    public void setExtensionPath(String extensionPath) { this.extensionPath = extensionPath; }

    public String getExtensionValue() { return extensionValue; }
    public void setExtensionValue(String extensionValue) { this.extensionValue = extensionValue; }
}
