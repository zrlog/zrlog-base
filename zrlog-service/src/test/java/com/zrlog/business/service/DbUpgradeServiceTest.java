package com.zrlog.business.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DbUpgradeServiceTest {

    @Test
    public void shouldResolveDatabaseNameFromCommonJdbcUrls() throws Exception {
        assertEquals("blog", DbUpgradeService.resolveDbName("jdbc:mysql://127.0.0.1:3306/blog?useSSL=false"));
        assertEquals("zrlog_base", DbUpgradeService.resolveDbName("jdbc:h2:mem:zrlog_base;MODE=MySQL"));
        assertEquals("zrlog", DbUpgradeService.resolveDbName("jdbc:sqlite:/var/lib/zrlog/zrlog"));
        assertEquals("zrlog.db", DbUpgradeService.resolveDbName(
                "jdbc:sqlite:/var/lib/zrlog/zrlog.db?date_class=TEXT&date_string_format=yyyy-MM-dd HH:mm:ss"));
        assertEquals("broken url", DbUpgradeService.resolveDbName("jdbc:broken url"));
        assertEquals("", DbUpgradeService.resolveDbName(""));
        assertEquals("", DbUpgradeService.resolveDbName(null));
    }
}
