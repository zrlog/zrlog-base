package com.hibegin.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BooleanUtilsTest {

    @Test
    public void shouldTreatOnlyLowercaseTrueAsTrue() {
        assertTrue(BooleanUtils.isTrue("true"));
        assertFalse(BooleanUtils.isTrue("TRUE"));
        assertFalse(BooleanUtils.isTrue("false"));
        assertFalse(BooleanUtils.isTrue(null));
    }

    @Test
    public void shouldTreatNullAndLowercaseFalseAsFalse() {
        assertTrue(BooleanUtils.isFalse(null));
        assertTrue(BooleanUtils.isFalse("false"));
        assertFalse(BooleanUtils.isFalse("FALSE"));
        assertFalse(BooleanUtils.isFalse("true"));
    }
}
