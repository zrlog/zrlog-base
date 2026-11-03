package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "oauth_credential", idColumn = "hash")
public class OAuthCredential extends BaseEntity<String> {
    private String kind;
    private String grantId;
    private String payload;
    private Long expiresAt;
    private Boolean used = false;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getGrantId() { return grantId; }
    public void setGrantId(String grantId) { this.grantId = grantId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }
}
