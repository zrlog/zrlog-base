package com.hibegin.common.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class VersionComparatorTest {

    private final VersionComparator comparator = new VersionComparator();

    @Test
    public void shouldCompareNumericVersions() {
        assertTrue(comparator.compare("3.5.6", "3.5.5") > 0);
        assertTrue(comparator.compare("3.5.5", "3.5.6") < 0);
        assertEquals(0, comparator.compare("3.5.0", "3.5"));
    }

    @Test
    public void shouldCompareSuffixVersions() {
        assertTrue(comparator.compare("3.5.6", "3.5.6-SNAPSHOT") > 0);
        assertTrue(comparator.compare("3.5.6-alpha", "3.5.6-beta") < 0);
        assertTrue(comparator.compare("3.5.6-rc1", "3.5.6") < 0);
    }

    @Test
    public void shouldCompareDifferentLengthVersions() {
        assertEquals(0, comparator.compare("3.5", "3.5.0.0"));
        assertTrue(comparator.compare("3.5.0.1", "3.5") > 0);
        assertTrue(comparator.compare("3.5", "3.5.0.1") < 0);
    }

    @Test
    public void shouldExposeEqualityHelper() {
        assertTrue(comparator.equals("3.5.0", "3.5"));
        assertFalse(comparator.equals("3.5.1", "3.5"));
    }
}
