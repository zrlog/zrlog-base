package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "user_passkey_challenge", idColumn = "id", generatedId = true)
public class UserPasskeyChallenge extends BaseEntity<Long> {
    private String requestId;
    private String ceremony;
    private Integer userId;
    private String requestJson;
    private Long expiresAt;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getCeremony() { return ceremony; }
    public void setCeremony(String ceremony) { this.ceremony = ceremony; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
}
