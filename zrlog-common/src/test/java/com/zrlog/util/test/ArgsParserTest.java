package com.zrlog.util.test;

import com.zrlog.util.ArgsParser;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArgsParserTest {

    @Test
    public void shouldParsePortAndContextPathFromArgs() {
        String[] args = new String[]{"--port=19080", "--contextPath=/blog"};

        assertEquals(Integer.valueOf(19080), ArgsParser.getPort(args));
        assertEquals("/blog", ArgsParser.getContextPath(args));
    }

    @Test
    public void shouldUseDefaultPortAndContextPathWhenArgsMissing() {
        assertEquals(Integer.valueOf(8080), ArgsParser.getPort(null));
        assertEquals("", ArgsParser.getContextPath(null));
    }

    @Test
    public void shouldDetectDevFlag() {
        assertTrue(ArgsParser.isDev(new String[]{"--dev"}));
        assertTrue(ArgsParser.isDev(new String[]{"--dev=true"}));
        assertFalse(ArgsParser.isDev(new String[]{"--port=19080"}));
        assertFalse(ArgsParser.isDev(null));
    }
}
