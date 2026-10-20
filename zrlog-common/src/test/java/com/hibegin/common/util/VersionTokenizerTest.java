package com.hibegin.common.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class VersionTokenizerTest {

    @Test
    public void shouldReadNumericAndSuffixParts() {
        VersionTokenizer tokenizer = new VersionTokenizer("3.5.6-SNAPSHOT");

        assertTrue(tokenizer.MoveNext());
        assertEquals(3, tokenizer.getNumber());
        assertEquals("", tokenizer.getSuffix());
        assertTrue(tokenizer.hasValue());

        assertTrue(tokenizer.MoveNext());
        assertEquals(5, tokenizer.getNumber());
        assertEquals("", tokenizer.getSuffix());

        assertTrue(tokenizer.MoveNext());
        assertEquals(6, tokenizer.getNumber());
        assertEquals("-SNAPSHOT", tokenizer.getSuffix());
        assertFalse(tokenizer.MoveNext());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNullVersionString() {
        new VersionTokenizer(null);
    }
}
