package com.zrlog.util.test;

import com.zrlog.util.UserAgentUtils;
import org.junit.Assert;
import org.junit.Test;

public class UserAgentUtilsTest {

    @Test
    public void shouldParseZrLogCtlProductAndVersion() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse("zrlogctl/0.1.42-beta.1");

        Assert.assertEquals("Unknown", info.getOs());
        Assert.assertEquals("ZrLogCtl", info.getBrowser());
        Assert.assertEquals("0.1.42-beta.1", info.getBrowserVersion());
        Assert.assertEquals("ZrLogCtl 0.1.42-beta.1", info.getFullBrowser());
        Assert.assertFalse(info.isCrawler());
    }

    @Test
    public void shouldParseChromeVersion() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36");

        Assert.assertEquals("Linux", info.getOs());
        Assert.assertEquals("Chrome", info.getBrowser());
        Assert.assertEquals("146.0.0.0", info.getBrowserVersion());
        Assert.assertEquals("Chrome 146.0.0.0", info.getFullBrowser());
        Assert.assertFalse(info.isCrawler());
    }

    @Test
    public void shouldParseSafariVersionFromVersionToken() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15");

        Assert.assertEquals("Mac OS X", info.getOs());
        Assert.assertEquals("Safari", info.getBrowser());
        Assert.assertEquals("17.5", info.getBrowserVersion());
        Assert.assertFalse(info.isCrawler());
    }

    @Test
    public void shouldParseWeChatVersion() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.49");

        Assert.assertEquals("iPhone (iOS)", info.getOs());
        Assert.assertEquals("WeChat", info.getBrowser());
        Assert.assertEquals("8.0.49", info.getBrowserVersion());
        Assert.assertFalse(info.isCrawler());
    }

    @Test
    public void shouldRecognizeCrawlerUserAgent() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");

        Assert.assertEquals("Unknown", info.getOs());
        Assert.assertEquals("Unknown", info.getBrowser());
        Assert.assertEquals("Unknown", info.getBrowserVersion());
        Assert.assertTrue(info.isCrawler());
    }

    @Test
    public void shouldReturnUnknownForEmptyUserAgent() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse("");

        Assert.assertEquals("Unknown", info.getOs());
        Assert.assertEquals("Unknown", info.getBrowser());
        Assert.assertEquals("Unknown", info.getBrowserVersion());
        Assert.assertEquals("Unknown", info.getFullBrowser());
        Assert.assertFalse(info.isCrawler());
    }

    @Test
    public void shouldReturnUnknownForNullUserAgent() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(null);

        Assert.assertEquals("Unknown", info.getOs());
        Assert.assertEquals("Unknown", info.getBrowser());
        Assert.assertEquals("Unknown", info.getBrowserVersion());
        Assert.assertFalse(info.isCrawler());
    }

    @Test
    public void shouldParseWindowsEdge() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0");

        Assert.assertEquals("Windows 10/11", info.getOs());
        Assert.assertEquals("Edge", info.getBrowser());
        Assert.assertEquals("146.0.0.0", info.getBrowserVersion());
        Assert.assertFalse(info.isCrawler());
    }

    @Test
    public void shouldParseWindowsFirefox() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(
                "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:145.0) Gecko/20100101 Firefox/145.0");

        Assert.assertEquals("Windows 7", info.getOs());
        Assert.assertEquals("Firefox", info.getBrowser());
        Assert.assertEquals("145.0", info.getBrowserVersion());
    }

    @Test
    public void shouldParseInternetExplorerVersion() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(
                "Mozilla/5.0 (Windows NT 6.2; Trident/7.0; rv:11.0) like Gecko");

        Assert.assertEquals("Windows 8", info.getOs());
        Assert.assertEquals("Internet Explorer", info.getBrowser());
        Assert.assertEquals("11.0", info.getBrowserVersion());
        Assert.assertEquals("Internet Explorer 11.0", info.getFullBrowser());
    }

    @Test
    public void shouldParseSafariVersionFromSafariTokenWhenVersionMissing() {
        UserAgentUtils.UserAgentInfo info = UserAgentUtils.parse(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Safari/605.1.15");

        Assert.assertEquals("Mac OS X", info.getOs());
        Assert.assertEquals("Safari", info.getBrowser());
        Assert.assertEquals("605.1.15", info.getBrowserVersion());
    }

    @Test
    public void shouldParseMobileOperatingSystems() {
        UserAgentUtils.UserAgentInfo ipad = UserAgentUtils.parse(
                "Mozilla/5.0 (iPad; CPU OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1");
        UserAgentUtils.UserAgentInfo android = UserAgentUtils.parse(
                "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36");

        Assert.assertEquals("iPad (iOS)", ipad.getOs());
        Assert.assertEquals("Safari", ipad.getBrowser());
        Assert.assertEquals("Android", android.getOs());
        Assert.assertEquals("Chrome", android.getBrowser());
    }

    @Test
    public void shouldRecognizeHttpClientCrawlerPatterns() {
        UserAgentUtils.UserAgentInfo curl = UserAgentUtils.parse("curl/8.6.0");
        UserAgentUtils.UserAgentInfo okhttp = UserAgentUtils.parse("okhttp/4.12.0");

        Assert.assertTrue(curl.isCrawler());
        Assert.assertTrue(okhttp.isCrawler());
        Assert.assertEquals("Unknown", curl.getFullBrowser());
    }
}
