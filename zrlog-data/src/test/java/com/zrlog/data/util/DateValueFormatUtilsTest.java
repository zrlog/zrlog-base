package com.zrlog.data.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;

public class DateValueFormatUtilsTest {

    @Test
    public void shouldFormatStandardDateValues() {
        assertEquals(
                "2026-01-20", DateValueFormatUtils.format("2026-01-20 11:00:00", "yyyy-MM-dd"));
    }

    @Test
    public void shouldFallbackToLocalizedEnglishDateString() {
        assertEquals(
                "2026-01-20 11:00:00",
                DateValueFormatUtils.format(
                        "Jan 20, 2026, 11:00:00\u202fAM", "yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    public void shouldReturnStableFallbacksForUnparseableValues() {
        assertEquals("not-a-date", DateValueFormatUtils.format("not-a-date", "yyyy-MM-dd"));
        assertEquals("", DateValueFormatUtils.format(null, "yyyy-MM-dd"));
    }

    @Test
    public void shouldKeepUtilityConstructorPrivate() throws Exception {
        Constructor<DateValueFormatUtils> constructor =
                DateValueFormatUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertEquals(UnsupportedOperationException.class, thrown.getCause().getClass());
    }
}
