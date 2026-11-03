package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "user_passkey", idColumn = "id", generatedId = true)
public class UserPasskey extends BaseEntity<Long> {
    private Integer userId;
    private String credentialIdHash;
    private String credentialId;
    private String publicKeyCose;
    private Long signatureCount = 0L;
    private String transports;
    private String name;
    private String aaguid;
    private Boolean backupEligible = false;
    private Boolean backupState = false;
    private String origin;
    private String rpId;
    private Long lastUsedAt;

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getCredentialIdHash() { return credentialIdHash; }
    public void setCredentialIdHash(String credentialIdHash) { this.credentialIdHash = credentialIdHash; }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getPublicKeyCose() { return publicKeyCose; }
    public void setPublicKeyCose(String publicKeyCose) { this.publicKeyCose = publicKeyCose; }

    public Long getSignatureCount() { return signatureCount; }
    public void setSignatureCount(Long signatureCount) { this.signatureCount = signatureCount; }

    public String getTransports() { return transports; }
    public void setTransports(String transports) { this.transports = transports; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAaguid() { return aaguid; }
    public void setAaguid(String aaguid) { this.aaguid = aaguid; }

    public Boolean getBackupEligible() { return backupEligible; }
    public void setBackupEligible(Boolean backupEligible) { this.backupEligible = backupEligible; }

    public Boolean getBackupState() { return backupState; }
    public void setBackupState(Boolean backupState) { this.backupState = backupState; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getRpId() { return rpId; }
    public void setRpId(String rpId) { this.rpId = rpId; }

    public Long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
