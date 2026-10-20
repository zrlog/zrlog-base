package com.hibegin.common.util;

import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class CommonUtilityContractsTest {

    @Test
    public void shouldConvertMapToBean() {
        Map<String, Object> source = new HashMap<>();
        source.put("name", "zrlog");
        source.put("count", 3);

        assertEquals(BeanUtil.class, new BeanUtil().getClass());
        SampleBean bean = BeanUtil.convert(source, SampleBean.class);

        assertEquals("zrlog", bean.name);
        assertEquals(3, bean.count);
    }

    @Test
    public void shouldRequireNonNullElseLikeJdkHelper() {
        Object value = new Object();
        Object fallback = new Object();

        assertEquals(ObjectHelpers.class, new ObjectHelpers().getClass());
        assertSame(value, ObjectHelpers.requireNonNullElse(value, fallback));
        assertSame(fallback, ObjectHelpers.requireNonNullElse(null, fallback));
        assertThrows(NullPointerException.class, () -> ObjectHelpers.requireNonNullElse(null, null));
    }

    @Test
    public void shouldRequireNonNullElseGetLazily() {
        Object value = new Object();
        AtomicBoolean called = new AtomicBoolean(false);

        assertSame(value, ObjectHelpers.requireNonNullElseGet(value, () -> {
            called.set(true);
            return new Object();
        }));
        assertFalse(called.get());
        assertNotNull(ObjectHelpers.requireNonNullElseGet(null, Object::new));
        assertThrows(NullPointerException.class, () -> ObjectHelpers.requireNonNullElseGet(null, null));
        assertThrows(NullPointerException.class, () -> ObjectHelpers.requireNonNullElseGet(null, () -> null));
    }

    @Test
    public void shouldExposeSystemTypeValuesAndRuntimeType() {
        assertEquals(RuntimeMessage.class, new RuntimeMessage().getClass());
        assertEquals(0, SystemType.LINUX.getType());
        assertEquals("0", SystemType.LINUX.toString());
        assertEquals(1, SystemType.WINDOWS.getType());

        try {
            assertSame(SystemType.WINDOWS, SystemType.WINDOWS.setType(7));
            assertEquals("7", SystemType.WINDOWS.toString());
        } finally {
            SystemType.WINDOWS.setType(1);
        }

        SystemType expected = File.separatorChar == '/' ? SystemType.LINUX : SystemType.WINDOWS;
        assertEquals(expected, RuntimeMessage.getSystemRm());
    }

    private static class SampleBean {
        private String name;
        private int count;
    }
}
