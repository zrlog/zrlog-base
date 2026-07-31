package com.zrlog.business.service;

import com.hibegin.common.util.StringUtils;
import com.zrlog.business.rest.response.BackupProtectionStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

public class BackupProtectionService {

    static final long BACKUP_MAX_AGE_MILLIS = 36L * 60 * 60 * 1000;
    static final long VERIFICATION_MAX_AGE_MILLIS = 8L * 24 * 60 * 60 * 1000;
    private static final long MAX_CLOCK_SKEW_MILLIS = 5L * 60 * 1000;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    static final String LAST_BACKUP_AT = "backupProtectionLastBackupAt";
    static final String LAST_BACKUP_FILE = "backupProtectionLastBackupFile";
    static final String LAST_BACKUP_SHA256 = "backupProtectionLastBackupSha256";
    static final String LAST_VERIFIED_AT = "backupProtectionLastVerifiedAt";
    static final String LAST_VERIFIED_FILE = "backupProtectionLastVerifiedFile";
    static final String LAST_VERIFIED_SHA256 = "backupProtectionLastVerifiedSha256";
    static final String LAST_VERIFICATION_SUCCESS = "backupProtectionLastVerificationSuccess";
    static final String LAST_VERIFICATION_MESSAGE = "backupProtectionLastVerificationMessage";

    private static final List<String> EVIDENCE_KEYS = Arrays.asList(
            LAST_BACKUP_AT,
            LAST_BACKUP_FILE,
            LAST_BACKUP_SHA256,
            LAST_VERIFIED_AT,
            LAST_VERIFIED_FILE,
            LAST_VERIFIED_SHA256,
            LAST_VERIFICATION_SUCCESS,
            LAST_VERIFICATION_MESSAGE
    );

    private final WebsiteKvService websiteKvService;
    private final LongSupplier currentTimeMillis;

    public BackupProtectionService() {
        this(new WebsiteKvService(), System::currentTimeMillis);
    }

    BackupProtectionService(WebsiteKvService websiteKvService, LongSupplier currentTimeMillis) {
        this.websiteKvService = websiteKvService;
        this.currentTimeMillis = currentTimeMillis;
    }

    public BackupProtectionStatus getStatus() {
        Map<String, Object> evidence = websiteKvService.getByNames(EVIDENCE_KEYS);
        BackupProtectionStatus status = new BackupProtectionStatus();
        status.setLastBackupAt(toLong(evidence.get(LAST_BACKUP_AT)));
        status.setLastBackupFile(toStringValue(evidence.get(LAST_BACKUP_FILE)));
        status.setLastBackupSha256(normalizeSha256(evidence.get(LAST_BACKUP_SHA256)));
        status.setLastVerifiedAt(toLong(evidence.get(LAST_VERIFIED_AT)));
        status.setLastVerifiedFile(toStringValue(evidence.get(LAST_VERIFIED_FILE)));
        status.setLastVerifiedSha256(normalizeSha256(evidence.get(LAST_VERIFIED_SHA256)));
        status.setVerificationSuccess(toBoolean(evidence.get(LAST_VERIFICATION_SUCCESS)));
        status.setVerificationMessage(toStringValue(evidence.get(LAST_VERIFICATION_MESSAGE)));
        status.setBackupMaxAgeMillis(BACKUP_MAX_AGE_MILLIS);
        status.setVerificationMaxAgeMillis(VERIFICATION_MAX_AGE_MILLIS);
        evaluate(status, currentTimeMillis.getAsLong());
        return status;
    }

    static void evaluate(BackupProtectionStatus status, long now) {
        String result;
        if (status.getLastBackupAt() == null
                || StringUtils.isEmpty(status.getLastBackupFile())
                || StringUtils.isEmpty(status.getLastBackupSha256())) {
            result = BackupProtectionStatus.MISSING_BACKUP;
        } else if (!validEvidence(status, now)) {
            result = BackupProtectionStatus.INVALID_EVIDENCE;
        } else if (now - status.getLastBackupAt() > BACKUP_MAX_AGE_MILLIS) {
            result = BackupProtectionStatus.BACKUP_STALE;
        } else if (status.getLastVerifiedAt() == null
                || StringUtils.isEmpty(status.getLastVerifiedFile())
                || StringUtils.isEmpty(status.getLastVerifiedSha256())
                || status.getVerificationSuccess() == null) {
            result = BackupProtectionStatus.MISSING_VERIFICATION;
        } else if (!Objects.equals(status.getVerificationSuccess(), true)) {
            result = BackupProtectionStatus.VERIFICATION_FAILED;
        } else if (now - status.getLastVerifiedAt() > VERIFICATION_MAX_AGE_MILLIS) {
            result = BackupProtectionStatus.VERIFICATION_STALE;
        } else if (!status.getLastBackupSha256().equalsIgnoreCase(status.getLastVerifiedSha256())) {
            result = BackupProtectionStatus.BACKUP_CHANGED_AFTER_VERIFICATION;
        } else {
            result = BackupProtectionStatus.READY;
        }
        boolean ready = BackupProtectionStatus.READY.equals(result);
        status.setStatus(result);
        status.setReady(ready);
        status.setRequiresRiskAcceptance(!ready);
    }

    private static boolean validEvidence(BackupProtectionStatus status, long now) {
        if (status.getLastBackupAt() < 0 || status.getLastBackupAt() > now + MAX_CLOCK_SKEW_MILLIS) {
            return false;
        }
        if (!SHA256.matcher(status.getLastBackupSha256()).matches()) {
            return false;
        }
        if (status.getLastVerifiedAt() == null) {
            return true;
        }
        return status.getLastVerifiedAt() >= 0
                && status.getLastVerifiedAt() <= now + MAX_CLOCK_SKEW_MILLIS
                && (StringUtils.isEmpty(status.getLastVerifiedSha256())
                || SHA256.matcher(status.getLastVerifiedSha256()).matches());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            String text = toStringValue(value);
            return StringUtils.isEmpty(text) ? null : Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = toStringValue(value);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private static String normalizeSha256(Object value) {
        String text = toStringValue(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? null : text;
    }
}
