package com.zrlog.common;

import com.zrlog.business.type.TemplateType;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.common.cache.dto.PluginDTO;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.Archive;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.cache.vo.HotLogBasicInfoEntry;
import com.zrlog.common.cache.vo.HotTypeLogInfo;
import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.exception.MissingRequestBodyException;
import com.zrlog.common.exception.NotFindDbEntryException;
import com.zrlog.common.exception.NotImplementException;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.common.exception.UnknownException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.rest.response.StandardResponse;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.BaseTemplateVO;
import com.zrlog.common.vo.I18nVO;
import com.zrlog.common.vo.IDataInitVO;
import com.zrlog.common.vo.LockVO;
import com.zrlog.common.vo.Outline;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.common.vo.SocialPreviewDTO;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.common.vo.Version;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CommonDtoContractTest {

    //TODO: Add fixture-backed tests for FaaS/Lambda update clients, updater implementations,
    // resource loaders, and web interceptors once those paths can avoid real HTTP/process state.
    @Test
    public void shouldExposeDtoBeanProperties() throws Exception {
        assertBeanProperties(LinkDTO.class);
        assertBeanProperties(LogNavDTO.class);
        assertBeanProperties(PluginDTO.class);
        assertBeanProperties(TagDTO.class);
        assertBeanProperties(TypeDTO.class);
        assertBeanProperties(UserBasicDTO.class);
        assertBeanProperties(Archive.class);
        assertBeanProperties(BaseDataInitVO.class);
        assertBeanProperties(BaseDataInitVO.Statistics.class);
        assertBeanProperties(HotLogBasicInfoEntry.class);
        assertBeanProperties(HotTypeLogInfo.class);
        assertBeanProperties(AdminFullTokenVO.class);
        assertBeanProperties(AdminTokenVO.class);
        assertBeanProperties(BaseTemplateVO.class);
        assertBeanProperties(I18nVO.class);
        assertBeanProperties(IDataInitVO.class);
        assertBeanProperties(LockVO.class);
        assertBeanProperties(Outline.class);
        assertBeanProperties(PublicWebSiteInfo.class);
        assertBeanProperties(SocialPreviewDTO.class);
        assertBeanProperties(TemplateVO.class);
        assertBeanProperties(TemplateVO.TemplateConfigVO.class);
        assertBeanProperties(Version.class);
    }

    @Test
    public void shouldExposeResponseConstructorsAndFields() {
        StandardResponse standard = new StandardResponse();
        standard.setError(12);
        standard.setMessage("message");

        assertEquals(12, standard.getError());
        assertEquals("message", standard.getMessage());

        ApiStandardResponse<String> empty = new ApiStandardResponse<>();
        ApiStandardResponse<String> withData = new ApiStandardResponse<>("data");
        ApiStandardResponse<String> withMessage = new ApiStandardResponse<>("data", "ok");
        withData.setData("changed");

        assertEquals(null, empty.getData());
        assertEquals("changed", withData.getData());
        assertEquals("data", withMessage.getData());
        assertEquals("ok", withMessage.getMessage());
    }

    @Test
    public void shouldExposeBusinessExceptionCodesAndMessages() {
        assertException(new ArgsException(), 9012);
        assertException(new ArgsException("id"), 9012);
        assertException(new MissingRequestBodyException(), 9030);
        assertException(new NotFindDbEntryException(), 9014);
        assertException(new NotImplementException(), 9088);
        assertException(new ResourceLockedException(), 9100);
        assertException(new UnknownException(), 9999);
        assertException(new UnknownException(new IllegalStateException("boom")), 9999);
    }

    @Test
    public void shouldBuildUpgradeProgressEventPayloads() {
        UpgradeProgressEvent.Data running = UpgradeProgressEvent.data(UpgradeProgressEvent.STAGE_DOWNLOAD, "Downloading");
        UpgradeProgressEvent.Data complete = UpgradeProgressEvent.data(
                UpgradeProgressEvent.STAGE_COMPLETE,
                UpgradeProgressEvent.STATUS_COMPLETE,
                null,
                "done");
        UpgradeProgressEvent.Data blankDetail = UpgradeProgressEvent.data("stage", "status", "message", " ");

        assertEquals(UpgradeProgressEvent.EVENT, "upgrade-progress");
        assertEquals(UpgradeProgressEvent.STAGE_DOWNLOAD, running.getStage());
        assertEquals(UpgradeProgressEvent.STATUS_RUNNING, running.getStatus());
        assertEquals("Downloading", running.getMessage());
        assertEquals(null, running.getDetail());
        assertEquals("", complete.getMessage());
        assertEquals("done", complete.getDetail());
        assertEquals(null, blankDetail.getDetail());
    }

    private static void assertException(AbstractBusinessException exception, int error) {
        assertEquals(error, exception.getError());
        org.junit.Assert.assertNotNull(exception.getMessage());
        org.junit.Assert.assertFalse(exception.getMessage().isBlank());
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
            return Collections.singletonList("value");
        }
        if (type == Map.class) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("key", "value");
            return value;
        }
        if (type == TemplateType.class) {
            return TemplateType.STANDARD;
        }
        if (type == TemplateVO.TemplateConfigMap.class) {
            TemplateVO.TemplateConfigMap value = new TemplateVO.TemplateConfigMap();
            value.put("key", new TemplateVO.TemplateConfigVO());
            return value;
        }
        if (type == Object.class) {
            return "object-value";
        }
        return newInstance(type);
    }

    private static Object newInstance(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
