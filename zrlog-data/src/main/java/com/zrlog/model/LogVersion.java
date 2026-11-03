package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "log_version", idColumn = "id", generatedId = true, createdAtColumn = "created_at")
public class LogVersion extends BaseEntity<Long> {
    @Column("log_id")
    private Integer logId;
    @Column("article_version")
    private Integer articleVersion;
    @Column("from_version")
    private Integer fromVersion;
    @Column("patch_json")
    private String patchJson;
    private String title;
    @Column("user_id")
    private Integer userId;

    public Integer getLogId() { return logId; }
    public void setLogId(Integer logId) { this.logId = logId; }

    public Integer getArticleVersion() { return articleVersion; }
    public void setArticleVersion(Integer articleVersion) { this.articleVersion = articleVersion; }

    public Integer getFromVersion() { return fromVersion; }
    public void setFromVersion(Integer fromVersion) { this.fromVersion = fromVersion; }

    public String getPatchJson() { return patchJson; }
    public void setPatchJson(String patchJson) { this.patchJson = patchJson; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
}
