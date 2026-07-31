package com.zrlog.business.service;

import com.zrlog.business.rest.response.BackupProtectionStatus;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupProtectionServiceTest {

    private static final long NOW = 2_000_000_000_000L;
    private static final String SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void shouldBeReadyForRecentMatchingVerifiedBackup() {
        Map<String, Object> evidence = completeEvidence(SHA_A, SHA_A);

        BackupProtectionStatus status = service(evidence).getStatus();

        assertEquals(BackupProtectionStatus.READY, status.getStatus());
        assertTrue(status.getReady());
        assertFalse(status.getRequiresRiskAcceptance());
    }

    @Test
    public void shouldRequireAcceptanceWhenBackupEvidenceIsMissing() {
        BackupProtectionStatus status = service(new HashMap<>()).getStatus();

        assertEquals(BackupProtectionStatus.MISSING_BACKUP, status.getStatus());
        assertFalse(status.getReady());
        assertTrue(status.getRequiresRiskAcceptance());
    }

    @Test
    public void shouldRejectStaleBackup() {
        Map<String, Object> evidence = completeEvidence(SHA_A, SHA_A);
        evidence.put(BackupProtectionService.LAST_BACKUP_AT,
                String.valueOf(NOW - BackupProtectionService.BACKUP_MAX_AGE_MILLIS - 1));

        assertEquals(BackupProtectionStatus.BACKUP_STALE, service(evidence).getStatus().getStatus());
    }

    @Test
    public void shouldRejectFailedOrStaleVerification() {
        Map<String, Object> failed = completeEvidence(SHA_A, SHA_A);
        failed.put(BackupProtectionService.LAST_VERIFICATION_SUCCESS, "false");
        assertEquals(BackupProtectionStatus.VERIFICATION_FAILED, service(failed).getStatus().getStatus());

        Map<String, Object> stale = completeEvidence(SHA_A, SHA_A);
        stale.put(BackupProtectionService.LAST_VERIFIED_AT,
                String.valueOf(NOW - BackupProtectionService.VERIFICATION_MAX_AGE_MILLIS - 1));
        assertEquals(BackupProtectionStatus.VERIFICATION_STALE, service(stale).getStatus().getStatus());
    }

    @Test
    public void shouldRejectChangedBackupAndMalformedEvidence() {
        assertEquals(BackupProtectionStatus.BACKUP_CHANGED_AFTER_VERIFICATION,
                service(completeEvidence(SHA_A, SHA_B)).getStatus().getStatus());

        Map<String, Object> malformed = completeEvidence("not-sha256", SHA_A);
        assertEquals(BackupProtectionStatus.INVALID_EVIDENCE, service(malformed).getStatus().getStatus());
    }

    private BackupProtectionService service(Map<String, Object> values) {
        WebsiteKvService kvService = new WebsiteKvService() {
            @Override
            public Map<String, Object> getByNames(List<String> keys) {
                return values;
            }
        };
        return new BackupProtectionService(kvService, () -> NOW);
    }

    private Map<String, Object> completeEvidence(String backupSha, String verifiedSha) {
        Map<String, Object> values = new HashMap<>();
        values.put(BackupProtectionService.LAST_BACKUP_AT, String.valueOf(NOW - 60_000));
        values.put(BackupProtectionService.LAST_BACKUP_FILE, "zrlog.sql");
        values.put(BackupProtectionService.LAST_BACKUP_SHA256, backupSha);
        values.put(BackupProtectionService.LAST_VERIFIED_AT, String.valueOf(NOW - 30_000));
        values.put(BackupProtectionService.LAST_VERIFIED_FILE, "zrlog.sql");
        values.put(BackupProtectionService.LAST_VERIFIED_SHA256, verifiedSha);
        values.put(BackupProtectionService.LAST_VERIFICATION_SUCCESS, "true");
        values.put(BackupProtectionService.LAST_VERIFICATION_MESSAGE, "ok");
        return values;
    }
}
