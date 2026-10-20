package com.zrlog.business.version;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UpgradeVersionHandlerHelpersTest {

    @Test
    public void shouldCreateExistingUpgradeHandlerByVersion() {
        assertTrue(UpgradeVersionHandlerHelpers.getUpgradeVersionHandler(1234)
                instanceof V1234UpgradeVersionHandler);
    }

    @Test
    public void shouldReturnNullWhenUpgradeHandlerClassIsMissing() {
        assertNull(UpgradeVersionHandlerHelpers.getUpgradeVersionHandler(9999));
    }

    @Test
    public void shouldReturnNullForMissingHandlerInDebugMode() {
        String previousRunMode = System.getProperty("sws.run.mode");
        try {
            System.setProperty("sws.run.mode", "debug");

            assertNull(UpgradeVersionHandlerHelpers.getUpgradeVersionHandler(9998));
        } finally {
            if (previousRunMode == null) {
                System.clearProperty("sws.run.mode");
            } else {
                System.setProperty("sws.run.mode", previousRunMode);
            }
        }
    }
}
