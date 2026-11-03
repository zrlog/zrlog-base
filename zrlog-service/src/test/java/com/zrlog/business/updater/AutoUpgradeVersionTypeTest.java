package com.zrlog.business.updater;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AutoUpgradeVersionTypeTest {

    @Test
    public void shouldResolveKnownCycles() {
        assertEquals(AutoUpgradeVersionType.NEVER, AutoUpgradeVersionType.cycle(-1));
        assertEquals(AutoUpgradeVersionType.ONE_MINUTE, AutoUpgradeVersionType.cycle(60));
        assertEquals(AutoUpgradeVersionType.ONE_HOUR, AutoUpgradeVersionType.cycle(3600));
        assertEquals(AutoUpgradeVersionType.ONE_DAY, AutoUpgradeVersionType.cycle(86400));
        assertEquals(AutoUpgradeVersionType.ONE_WEEK, AutoUpgradeVersionType.cycle(604800));
        assertEquals(AutoUpgradeVersionType.HALF_MONTH, AutoUpgradeVersionType.cycle(1296000));
    }

    @Test
    public void shouldDefaultUnknownCycleToOneDay() {
        assertEquals(AutoUpgradeVersionType.ONE_DAY, AutoUpgradeVersionType.cycle(0));
        assertEquals(AutoUpgradeVersionType.ONE_DAY, AutoUpgradeVersionType.cycle(120));
    }

    @Test
    public void shouldExposeCycleSeconds() {
        assertEquals(-1, AutoUpgradeVersionType.NEVER.getCycle());
        assertEquals(86400, AutoUpgradeVersionType.ONE_DAY.getCycle());
    }
}
