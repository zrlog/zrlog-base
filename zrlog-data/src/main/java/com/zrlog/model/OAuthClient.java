package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "oauth_client", idColumn = "clientId")
public class OAuthClient extends BaseEntity<String> {
    private String name;
    private String redirectUris;
    private Boolean enabled = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRedirectUris() { return redirectUris; }
    public void setRedirectUris(String redirectUris) { this.redirectUris = redirectUris; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
