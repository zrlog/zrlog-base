package com.zrlog.business.service;

import com.zrlog.common.vo.Version;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class UpgradeServiceHelpersTest {

    @Test
    public void shouldBuildSanitizedUpgradeKey() throws Exception {
        Version buildIdVersion = new Version();
        buildIdVersion.setBuildId("3.6.0 SNAPSHOT/abc");
        buildIdVersion.setVersion("ignored");
        Version versionFallback = new Version();
        versionFallback.setVersion("4.0.0+meta");

        String buildIdKey = UpgradeService.buildUpgradeKey(buildIdVersion);
        String versionKey = UpgradeService.buildUpgradeKey(versionFallback);
        String fallbackKey = UpgradeService.buildUpgradeKey(null);

        assertTrue(buildIdKey.startsWith("upgrade-3.6.0-SNAPSHOT-abc-"));
        assertTrue(versionKey.startsWith("upgrade-4.0.0-meta-"));
        assertTrue(fallbackKey.startsWith("upgrade-"));
        assertTrue(buildIdKey.matches("[A-Za-z0-9._-]+"));
        assertTrue(versionKey.matches("[A-Za-z0-9._-]+"));
    }

}
