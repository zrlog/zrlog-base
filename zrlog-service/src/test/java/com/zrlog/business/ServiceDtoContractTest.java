package com.zrlog.business;

import com.zrlog.business.dto.StoredUpgradeNotice;
import com.zrlog.business.exception.DownloadUpgradeFileException;
import com.zrlog.business.exception.MissingInstallException;
import com.zrlog.business.plugin.RequestInfo;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PluginCoreStatus;
import com.zrlog.business.rest.response.PluginStatusResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.BackupProtectionStatus;
import com.zrlog.business.rest.response.PublicInfoVO;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.vo.Version;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ServiceDtoContractTest {

    //TODO: Add fixture-backed tests for plugin runtime, upgrade services, and DAO-backed KV writes.
    // Those paths depend on DAO state, subprocesses, plugin runtime, or network/update artifacts.
    @Test
    public void shouldExposeBeanStyleServiceDtos() throws Exception {
        assertBeanProperties(StoredUpgradeNotice.class);
        assertBeanProperties(UpgradeWebSiteInfo.class);
        assertBeanProperties(CheckVersionResponse.class);
        assertBeanProperties(PluginStatusResponse.class);
        assertBeanProperties(PreCheckVersionResponse.class);
        assertBeanProperties(BackupProtectionStatus.class);
        assertBeanProperties(RequestInfo.class);
    }

    @Test
    public void shouldValidateUpgradeWebsiteInfo() {
        UpgradeWebSiteInfo info = new UpgradeWebSiteInfo();

        assertThrows(ArgsException.class, info::doValid);

        info.setAutoUpgradeVersion(86400);
        info.doValid();
        assertEquals(Integer.valueOf(86400), info.getAutoUpgradeVersion());
    }

    @Test
    public void shouldExposeConstructorOnlyResponses() {
        PublicInfoVO publicInfo = new PublicInfoVO(
                "3.6.0",
                "ZrLog",
                "https://example.com",
                true,
                "#1677ff",
                "#ffffff",
                "app-id");
        UpgradeProcessResponse upgradeProcess = new UpgradeProcessResponse(false, "running");

        assertEquals("3.6.0", publicInfo.getCurrentVersion());
        assertEquals("ZrLog", publicInfo.getWebsiteTitle());
        assertEquals("https://example.com", publicInfo.getHomeUrl());
        assertEquals(true, publicInfo.getAdmin_darkMode());
        assertEquals("#1677ff", publicInfo.getAdmin_color_primary());
        assertEquals("#ffffff", publicInfo.getPwaThemeColor());
        assertEquals("app-id", publicInfo.getAppId());

        assertEquals(false, upgradeProcess.getFinish());
        assertEquals("running", upgradeProcess.getMessage());
        upgradeProcess.setFinish(true);
        upgradeProcess.setMessage("done");
        assertEquals(true, upgradeProcess.getFinish());
        assertEquals("done", upgradeProcess.getMessage());
    }

    @Test
    public void shouldExposeEnumsAndBusinessExceptions() {
        assertEquals(PluginCoreStatus.STARTED, PluginCoreStatus.valueOf("STARTED"));
        assertEquals(PluginCoreStatus.STARTING, PluginCoreStatus.valueOf("STARTING"));
        assertEquals(StaticSiteType.BLOG, StaticSiteType.valueOf("BLOG"));
        assertEquals(StaticSiteType.ADMIN, StaticSiteType.valueOf("ADMIN"));

        assertException(new DownloadUpgradeFileException(), 9026);
        assertException(new DownloadUpgradeFileException(new IllegalStateException("boom")), 9026);
        assertException(new MissingInstallException(), 9029);
    }

    private static void assertException(AbstractBusinessException exception, int error) {
        assertEquals(error, exception.getError());
        assertNotNull(exception.getMessage());
    }

    private static void assertBeanProperties(Class<?> type) throws Exception {
        Object bean = newInstance(type);
        int setterCount = 0;
        for (Method setter : type.getMethods()) {
            if (!isSetter(setter)) {
                continue;
            }
            Object value = sampleValue(setter.getParameterTypes()[0]);
            setter.invoke(bean, value);
            Method getter = getterFor(type, setter);
            if (getter != null) {
                assertEquals(value, getter.invoke(bean));
            }
            setterCount++;
        }
        for (Method getter : type.getMethods()) {
            if (isGetter(getter)) {
                getter.invoke(bean);
            }
        }
        assertTrue("No bean setters found for " + type.getName(), setterCount > 0);
    }

    private static boolean isSetter(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && method.getName().startsWith("set")
                && method.getParameterCount() == 1
                && method.getDeclaringClass() != Object.class;
    }

    private static boolean isGetter(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 0
                && method.getDeclaringClass() != Object.class
                && (method.getName().startsWith("get") || method.getName().startsWith("is"));
    }

    private static Method getterFor(Class<?> type, Method setter) {
        String suffix = setter.getName().substring(3);
        try {
            return type.getMethod("get" + suffix);
        } catch (NoSuchMethodException ignored) {
            try {
                return type.getMethod("is" + suffix);
            } catch (NoSuchMethodException ignoredAgain) {
                return null;
            }
        }
    }

    private static Object sampleValue(Class<?> type) throws Exception {
        if (type == String.class) {
            return "value";
        }
        if (type == Long.class || type == long.class) {
            return 7L;
        }
        if (type == Integer.class || type == int.class) {
            return 3;
        }
        if (type == Boolean.class || type == boolean.class) {
            return true;
        }
        if (type == Date.class) {
            return new Date(1_000);
        }
        if (type == List.class) {
            return Collections.singletonList("plugin");
        }
        if (type == Version.class) {
            Version version = new Version();
            version.setVersion("3.6.0");
            return version;
        }
        if (type == PluginCoreStatus.class) {
            return PluginCoreStatus.STARTED;
        }
        return newInstance(type);
    }

    private static Object newInstance(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
