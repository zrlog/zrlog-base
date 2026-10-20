package com.zrlog.util.test;

import com.zrlog.util.ParseUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParseUtilTest {

    @Test
    public void shouldStripHtmlForPlainSearchText() {
        assertEquals("", ParseUtil.getPlainSearchText(null));
        assertEquals("", ParseUtil.getPlainSearchText(""));
        assertEquals("hello world", ParseUtil.getPlainSearchText("<p>hello <strong>world</strong></p>"));
    }

    @Test
    public void shouldBuildDigestWithoutBreakingTags() {
        assertNull(ParseUtil.autoDigest(null, 10));
        assertEquals("   ", ParseUtil.autoDigest("   ", 10));
        assertEquals("hello...", ParseUtil.autoDigest("hello world", 5));
        assertEquals("hello", ParseUtil.autoDigest("hello", 10));
        assertEquals("<p>hello...</p>", ParseUtil.autoDigest("<p>hello world</p>", 5));
        assertEquals("<p>hello</p>", ParseUtil.autoDigest("<p>hello</p>", 10));
        assertEquals("<p class=\"lead\">hello<em>world...</em></p>",
                ParseUtil.autoDigest("<p class=\"lead\">hello <em>worldwide</em></p>", 10));
    }

    @Test
    public void shouldRemoveHtmlElements() {
        assertNull(ParseUtil.removeHtmlElement(null));
        assertEquals("", ParseUtil.removeHtmlElement(""));
        assertEquals("hello world", ParseUtil.removeHtmlElement("<div>hello <em>world</em></div>"));
    }

    @Test
    public void shouldParseIntegersWithDefaultFallback() {
        assertEquals(12, ParseUtil.strToInt("12", 1));
        assertEquals(-12, ParseUtil.strToInt("-12", 1));
        assertEquals(1, ParseUtil.strToInt("0", 1));
        assertEquals(1, ParseUtil.strToInt("abc", 1));
        assertEquals(1, ParseUtil.strToInt(null, 1));
    }

    @Test
    public void shouldWrapKeywordCaseSensitivelyAndCaseInsensitively() {
        assertNull(ParseUtil.wrapperKeyword(null, "zrlog", 100));
        assertEquals("hello <font color=\"#CC0000\">ZrLog</font>",
                ParseUtil.wrapperKeyword("hello ZrLog", "ZrLog", 100));
        assertEquals("hello <font color=\"#CC0000\">ZrLog</font>",
                ParseUtil.wrapperKeyword("hello ZrLog", "zrlog", 100));
        assertEquals("hello world", ParseUtil.wrapperKeyword("hello world", "zrlog", 100));
        assertEquals("<font color=\"#CC0000\">ZrLog</font>",
                ParseUtil.wrapperKeyword("ZrLog", "zrlog", 100));
    }

    @Test
    public void shouldWrapMultipleCaseInsensitiveKeywords() {
        String result = ParseUtil.wrapperKeyword("one ZrLog and ZrLog", "zrlog", 100);

        assertEquals("one <font color=\"#CC0000\">ZrLog</font> and ZrLog", result);
    }

    @Test
    public void shouldTrimWrappedKeywordToDigestWindowAndSentenceBounds() {
        String result = ParseUtil.wrapperKeyword(
                "drop this sentence. keep before zrlog after。drop after sentence.",
                "zrlog", 200);

        assertEquals(" keep before <font color=\"#CC0000\">zrlog</font> after。", result);
    }

    @Test
    public void shouldTrimLongWrappedKeywordDigestNearMatch() {
        String result = ParseUtil.wrapperKeyword(
                "0123456789 prefix zrlog " + "x".repeat(180) + " suffix",
                "zrlog", 80);

        assertTrue(result.contains("<font color=\"#CC0000\">zrlog</font>"));
        assertTrue(result.length() < 260);
    }

    @Test
    public void shouldDetectNumericStrings() {
        assertTrue(ParseUtil.isNumeric("0"));
        assertTrue(ParseUtil.isNumeric("-1"));
        assertTrue(ParseUtil.isNumeric("12.34"));
        assertFalse(ParseUtil.isNumeric(""));
        assertFalse(ParseUtil.isNumeric(null));
        assertFalse(ParseUtil.isNumeric("01"));
        assertFalse(ParseUtil.isNumeric("abc"));
    }

    @Test
    public void shouldFormatDurationInChineseAndEnglish() {
        long duration = (((1L * 24 + 2) * 60 + 3) * 60 + 4) * 1000;

        assertEquals("1d 2h 3m 4s", ParseUtil.toNamingDurationString(duration, true));
        assertEquals("1天 2时 3分 4秒", ParseUtil.toNamingDurationString(duration, false));
        assertEquals("0d 0h 0m 0s", ParseUtil.toNamingDurationString(0, true));
    }
}
