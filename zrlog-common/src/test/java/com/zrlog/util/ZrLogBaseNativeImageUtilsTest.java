package com.zrlog.util;

import com.hibegin.http.server.util.NativeImageUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ZrLogBaseNativeImageUtilsTest {

    private final Logger nativeImageLogger = Logger.getLogger(NativeImageUtils.class.getName());
    private Level previousLevel;
    private boolean previousUseParentHandlers;

    @Before
    public void setUp() {
        previousLevel = nativeImageLogger.getLevel();
        previousUseParentHandlers = nativeImageLogger.getUseParentHandlers();
        nativeImageLogger.setUseParentHandlers(false);
        nativeImageLogger.setLevel(Level.OFF);
    }

    @After
    public void tearDown() {
        nativeImageLogger.setLevel(previousLevel);
        nativeImageLogger.setUseParentHandlers(previousUseParentHandlers);
    }

    @Test
    public void shouldExposeExecutableAndCaseInsensitiveMapHelpers() {
        Map<String, Object> map = ZrLogBaseNativeImageUtils.MyBasicRowProcessor.createMap();
        map.put("Name", "zrlog");

        assertEquals("zrlog", map.get("name"));
        assertNotNull(ZrLogBaseNativeImageUtils.getExecFile());
        assertFalse(ZrLogBaseNativeImageUtils.getExecFile().isEmpty());
    }

    @Test
    public void shouldRegisterSimpleGetterClassForNativeImage() {
        ZrLogBaseNativeImageUtils.regWithGetMethod(SimpleGetter.class);

        assertTrue(true);
    }

    @Test
    public void shouldRunNativeImageRegistrationSmoke() {
        assertTrue(ZrLogBaseNativeImageUtils.gsonClasses().contains(LinkedHashMap.class));
        ZrLogBaseNativeImageUtils.reg();

        assertTrue(true);
    }

    public static class SimpleGetter {
        public String getName() {
            return "zrlog";
        }
    }
}
