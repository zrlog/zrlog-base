package com.zrlog.business.rest.response;

import java.io.Serializable;

public class BackupProtectionStatus implements Serializable {

    public static final String READY = "READY";
    public static final String MISSING_BACKUP = "MISSING_BACKUP";
    public static final String BACKUP_STALE = "BACKUP_STALE";
    public static final String MISSING_VERIFICATION = "MISSING_VERIFICATION";
    public static final String VERIFICATION_FAILED = "VERIFICATION_FAILED";
    public static final String VERIFICATION_STALE = "VERIFICATION_STALE";
    public static final String BACKUP_CHANGED_AFTER_VERIFICATION = "BACKUP_CHANGED_AFTER_VERIFICATION";
    public static final String INVALID_EVIDENCE = "INVALID_EVIDENCE";

    private Boolean ready;
    private Boolean requiresRiskAcceptance;
    private String status;
    private Long lastBackupAt;
    private String lastBackupFile;
    private String lastBackupSha256;
    private Long lastVerifiedAt;
    private String lastVerifiedFile;
    private String lastVerifiedSha256;
    private Boolean verificationSuccess;
    private String verificationMessage;
    private Long backupMaxAgeMillis;
    private Long verificationMaxAgeMillis;

    public Boolean getReady() {
        return ready;
    }

    public void setReady(Boolean ready) {
        this.ready = ready;
    }

    public Boolean getRequiresRiskAcceptance() {
        return requiresRiskAcceptance;
    }

    public void setRequiresRiskAcceptance(Boolean requiresRiskAcceptance) {
        this.requiresRiskAcceptance = requiresRiskAcceptance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getLastBackupAt() {
        return lastBackupAt;
    }

    public void setLastBackupAt(Long lastBackupAt) {
        this.lastBackupAt = lastBackupAt;
    }

    public String getLastBackupFile() {
        return lastBackupFile;
    }

    public void setLastBackupFile(String lastBackupFile) {
        this.lastBackupFile = lastBackupFile;
    }

    public String getLastBackupSha256() {
        return lastBackupSha256;
    }

    public void setLastBackupSha256(String lastBackupSha256) {
        this.lastBackupSha256 = lastBackupSha256;
    }

    public Long getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(Long lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getLastVerifiedFile() {
        return lastVerifiedFile;
    }

    public void setLastVerifiedFile(String lastVerifiedFile) {
        this.lastVerifiedFile = lastVerifiedFile;
    }

    public String getLastVerifiedSha256() {
        return lastVerifiedSha256;
    }

    public void setLastVerifiedSha256(String lastVerifiedSha256) {
        this.lastVerifiedSha256 = lastVerifiedSha256;
    }

    public Boolean getVerificationSuccess() {
        return verificationSuccess;
    }

    public void setVerificationSuccess(Boolean verificationSuccess) {
        this.verificationSuccess = verificationSuccess;
    }

    public String getVerificationMessage() {
        return verificationMessage;
    }

    public void setVerificationMessage(String verificationMessage) {
        this.verificationMessage = verificationMessage;
    }

    public Long getBackupMaxAgeMillis() {
        return backupMaxAgeMillis;
    }

    public void setBackupMaxAgeMillis(Long backupMaxAgeMillis) {
        this.backupMaxAgeMillis = backupMaxAgeMillis;
    }

    public Long getVerificationMaxAgeMillis() {
        return verificationMaxAgeMillis;
    }

    public void setVerificationMaxAgeMillis(Long verificationMaxAgeMillis) {
        this.verificationMaxAgeMillis = verificationMaxAgeMillis;
    }
}
