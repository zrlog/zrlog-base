package com.zrlog.common.updater.handle;

import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.aws.AwsCredentials;
import com.zrlog.common.updater.aws.AwsLambdaUpdateConfig;
import com.zrlog.common.vo.Version;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ManualUpdateVersionHandleTest {

    @Test
    public void shouldBuildDockerManualUpgradeMessage() {
        DockerUpdateVersionHandle handle = new DockerUpdateVersionHandle(backend());

        assertFalse(handle.isFinish());
        handle.doHandle();

        assertTrue(handle.isFinish());
        assertEquals("Run sh upgrade.sh", handle.getMessage());
    }

    @Test
    public void shouldBuildSystemServiceManualUpgradeMessage() {
        Version release = new Version();
        release.setVersion("3.6.0");
        Version preview = new Version();
        preview.setVersion("3.7.0-SNAPSHOT");

        SystemServiceUpdateVersionHandle releaseHandle = new SystemServiceUpdateVersionHandle(backend(), release);
        SystemServiceUpdateVersionHandle previewHandle = new SystemServiceUpdateVersionHandle(backend(), preview);
        SystemServiceUpdateVersionHandle nullVersionHandle = new SystemServiceUpdateVersionHandle(backend(), null);

        releaseHandle.doHandle();
        previewHandle.doHandle();
        nullVersionHandle.doHandle();

        assertTrue(releaseHandle.isFinish());
        assertEquals("Use /etc/init.d/zrlog upgrade", releaseHandle.getMessage());
        assertEquals("Use /etc/init.d/zrlog upgrade-preview", previewHandle.getMessage());
        assertEquals("Use /etc/init.d/zrlog upgrade", nullVersionHandle.getMessage());
    }

    @Test
    public void shouldFallbackToManualFaasMessageWhenOnlineUpgradeIsUnavailable() {
        Version version = new Version();
        version.setZipDownloadUrl("https://example.com/zrlog.zip");
        AtomicInteger events = new AtomicInteger();
        AtomicReference<UpgradeProgressEvent.Data> lastData = new AtomicReference<>();
        FaasUpdateVersionHandler handle = new FaasUpdateVersionHandler(
                backend(),
                version,
                null,
                (event, data) -> {
                    events.incrementAndGet();
                    assertEquals(UpgradeProgressEvent.EVENT, event);
                    lastData.set(data);
                },
                new AwsLambdaUpdateConfig(new AwsCredentials(null, null, null), null, null, null));

        handle.doHandle();
        handle.doHandle();

        assertTrue(handle.isFinish());
        assertTrue(handle.getMessage().contains("https://example.com/zrlog.zip"));
        assertEquals(1, events.get());
        assertEquals(UpgradeProgressEvent.STAGE_MANUAL, lastData.get().getStage());
        assertEquals(UpgradeProgressEvent.STATUS_MANUAL, lastData.get().getStatus());
    }

     private static Map<String, Object> backend() {
        Map<String, Object> backend = new HashMap<>();
        backend.put("upgrade.tips.docker", "Run sh upgrade.sh");
        backend.put("upgrade.tips.systemService", "Use /etc/init.d/zrlog upgrade");
        backend.put("upgrade.lambda.online.missing", "Missing Lambda online update config");
        backend.put("upgrade.lambda.online.missingEnv", "Missing env:");
        backend.put("upgrade.lambda.title", "Update Lambda manually");
        return backend;
    }
}
