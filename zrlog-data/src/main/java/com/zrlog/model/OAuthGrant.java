package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "oauth_grant", idColumn = "id")
public class OAuthGrant extends BaseEntity<String> {
    private Integer userId;
    private String clientId;
    private String scope;
    private String resource;
    private Integer authVersion;
    private Boolean revoked = false;

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public Integer getAuthVersion() { return authVersion; }
    public void setAuthVersion(Integer authVersion) { this.authVersion = authVersion; }

    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }
}
