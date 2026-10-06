package com.zrlog.common.vo;

public class AdminTokenVO {

    private long createdDate;
    private Long expiresAt;

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
    private int userId;
    private int authVersion;

    public int getAuthVersion() { return authVersion; }
    public void setAuthVersion(int authVersion) { this.authVersion = authVersion; }
    private String sessionId;
    private String protocol;

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }


    public String getSessionId() {

        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}