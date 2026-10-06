package com.zrlog.common.web;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.common.exception.MissingRequestBodyException;
import com.zrlog.common.exception.NotImplementException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.common.vo.I18nVO;
import com.zrlog.util.I18nUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ZrLogErrorHandleTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Logger errorHandleLogger = Logger.getLogger(ZrLogErrorHandle.class.getName());
    private Level previousLevel;
    private boolean previousUseParentHandlers;
    private I18nVO previousI18n;

    @Before
    public void setUp() {
        previousI18n = I18nUtil.threadLocal.get();
        useLocale("zh_CN");
        previousLevel = errorHandleLogger.getLevel();
        previousUseParentHandlers = errorHandleLogger.getUseParentHandlers();
        errorHandleLogger.setUseParentHandlers(false);
        errorHandleLogger.setLevel(Level.OFF);
    }

    @After
    public void tearDown() {
        if (previousI18n == null) {
            I18nUtil.removeI18n();
        } else {
            I18nUtil.threadLocal.set(previousI18n);
        }
        errorHandleLogger.setLevel(previousLevel);
        errorHandleLogger.setUseParentHandlers(previousUseParentHandlers);
    }

    @Test
    public void shouldRenderApiBusinessErrorsAsJson() {
        ResponseRecorder recorder = new ResponseRecorder();

        new ZrLogErrorHandle(500).doHandle(request("/api/article", "id=1"), recorder.response(),
                new TestBusinessException());

        ApiStandardResponse<?> response = (ApiStandardResponse<?>) recorder.json;
        assertEquals(9001, response.getError());
        assertEquals("business-error", response.getMessage());
    }

    @Test
    public void shouldRenderApiNotFoundAndUnexpectedErrorsAsJson() {
        for (String locale : new String[]{"zh_CN", "en_US"}) {
            useLocale(locale);
            ResponseRecorder notFound = new ResponseRecorder();
            new ZrLogErrorHandle(404).doHandle(request("/api/missing", ""), notFound.response(),
                    new NotFindResourceException("internal-resource-path"));
            ApiStandardResponse<?> missingResponse = (ApiStandardResponse<?>) notFound.json;
            assertEquals(9404, missingResponse.getError());
            assertEquals(I18nUtil.getBackendStringFromRes("request.error.notFound"), missingResponse.getMessage());
            assertFalse(missingResponse.getMessage().isBlank());
            assertFalse(missingResponse.getMessage().contains("internal-resource-path"));

            for (String detail : new String[]{"internal-database-error", null}) {
                ResponseRecorder unexpected = new ResponseRecorder();
                new ZrLogErrorHandle(500).doHandle(request("/api/error", ""), unexpected.response(),
                        new IllegalStateException(detail));
                ApiStandardResponse<?> errorResponse = (ApiStandardResponse<?>) unexpected.json;
                assertEquals(9999, errorResponse.getError());
                assertEquals(I18nUtil.getBackendStringFromRes("unknownError"), errorResponse.getMessage());
                assertFalse(errorResponse.getMessage().isBlank());
                assertFalse(errorResponse.getMessage().contains("internal-database-error"));
            }
        }
    }

    @Test
    public void shouldReturnLocalizedMessagesForPreviouslyEmptyBusinessErrors() {
        for (String locale : new String[]{"zh_CN", "en_US"}) {
            useLocale(locale);
            AbstractBusinessException[] errors = {new MissingRequestBodyException(), new NotImplementException()};
            String[] keys = {"request.validation.bodyRequired", "request.error.unsupportedOperation"};
            int[] codes = {9030, 9088};
            for (int index = 0; index < errors.length; index++) {
                ResponseRecorder recorder = new ResponseRecorder();
                new ZrLogErrorHandle(500).doHandle(request("/api/article", ""), recorder.response(), errors[index]);
                ApiStandardResponse<?> response = (ApiStandardResponse<?>) recorder.json;
                assertEquals(codes[index], response.getError());
                assertEquals(I18nUtil.getBackendStringFromRes(keys[index]), response.getMessage());
                assertFalse(response.getMessage().isBlank());
            }
        }
    }

    @Test
    public void shouldUseFallbackMessageForEmptyBusinessErrorsWithoutChangingTheirCode() {
        ResponseRecorder recorder = new ResponseRecorder();
        new ZrLogErrorHandle(500).doHandle(request("/api/article", ""), recorder.response(),
                new TestBusinessException() {
                    @Override
                    public String getMessage() {
                        return " ";
                    }
                });
        ApiStandardResponse<?> response = (ApiStandardResponse<?>) recorder.json;
        assertEquals(9001, response.getError());
        assertEquals(I18nUtil.getBackendStringFromRes("unknownError"), response.getMessage());
    }

    @Test
    public void shouldRedirectAdminErrorsToStatusPages() {
        ResponseRecorder notFound = new ResponseRecorder();
        new ZrLogErrorHandle(404).doHandle(request("/admin/missing", "q=zrlog"), notFound.response(),
                new NotFindResourceException("missing"));
        assertTrue(notFound.redirect.startsWith("/admin/404?queryString=q=zrlog&uriPath=/admin/missing"));
        assertTrue(notFound.redirect.endsWith("&message=" + URLEncoder.encode(
                I18nUtil.getBackendStringFromRes("request.error.notFound"), StandardCharsets.UTF_8)));

        ResponseRecorder serverError = new ResponseRecorder();
        new ZrLogErrorHandle(500).doHandle(request("/admin/error", ""), serverError.response(),
                new IllegalStateException("boom"));
        assertEquals("/admin/500?message=" + URLEncoder.encode(
                I18nUtil.getBackendStringFromRes("unknownError"), StandardCharsets.UTF_8), serverError.redirect);
    }

    @Test
    public void shouldPreserveBusinessMessageAsOneEncodedRedirectParameter() {
        String message = "输入无效 &next=/example?value=1#section";
        ResponseRecorder recorder = new ResponseRecorder();
        new ZrLogErrorHandle(500).doHandle(request("/admin/error", ""), recorder.response(),
                new TestBusinessException() {
                    @Override
                    public String getMessage() {
                        return message;
                    }
                });
        String prefix = "/admin/500?message=";
        assertTrue(recorder.redirect.startsWith(prefix));
        assertFalse(recorder.redirect.contains("&next="));
        assertFalse(recorder.redirect.contains("#section"));
        assertEquals(message, URLDecoder.decode(recorder.redirect.substring(prefix.length()), StandardCharsets.UTF_8));
    }

    @Test
    public void shouldRenderBlogErrorTemplateWhenConfigured() throws Exception {
        withRootPath(() -> {
            java.io.File errorDir = new java.io.File(PathUtil.getConfPath(), "error");
            assertTrue(errorDir.mkdirs());
            Files.writeString(new java.io.File(errorDir, "404.html").toPath(),
                    "<html><body><h1>not found</h1></body></html>", StandardCharsets.UTF_8);
            ResponseRecorder recorder = new ResponseRecorder();

            new ZrLogErrorHandle(404).doHandle(request("/post.html", ""), recorder.response(),
                    new IllegalStateException("missing"));

            assertEquals("text/html;charset=utf-8", recorder.headers.get("Content-Type"));
            assertEquals(Integer.valueOf(404), recorder.writeCode);
            assertTrue(recorder.writtenBody.contains("not found"));
            assertNull(recorder.renderCode);
        });
    }

    @Test
    public void shouldUseBundledBlogErrorTemplateWhenLocalTemplateIsMissing() throws Exception {
        withRootPath(() -> {
            ResponseRecorder recorder = new ResponseRecorder();

            new ZrLogErrorHandle(404).doHandle(request("/post.html", ""), recorder.response(),
                    new IllegalStateException("missing"));

            assertEquals("text/html;charset=utf-8", recorder.headers.get("Content-Type"));
            if (recorder.writeCode == null) {
                assertEquals(Integer.valueOf(500), recorder.renderCode);
            } else {
                assertEquals(Integer.valueOf(404), recorder.writeCode);
                assertTrue(recorder.writtenBody.length() > 0);
                assertNull(recorder.renderCode);
            }
        });
    }

    @Test
    public void shouldAppendStackTraceToBlogErrorTemplateInDebugMode() throws Exception {
        String previousRunMode = System.getProperty("sws.run.mode");
        try {
            withRootPath(() -> {
                System.setProperty("sws.run.mode", "debug");
                java.io.File errorDir = new java.io.File(PathUtil.getConfPath(), "error");
                assertTrue(errorDir.mkdirs());
                Files.writeString(new java.io.File(errorDir, "500.html").toPath(),
                        "<html><body><h1>server error</h1></body></html>", StandardCharsets.UTF_8);
                ResponseRecorder recorder = new ResponseRecorder();

                new ZrLogErrorHandle(404).doHandle(request("/post.html", ""), recorder.response(),
                        new IllegalStateException("debug-boom"));

                assertEquals(Integer.valueOf(404), recorder.writeCode);
                assertTrue(recorder.writtenBody.contains("debug-boom"));
            });
        } finally {
            restoreProperty("sws.run.mode", previousRunMode);
        }
    }

    private void withRootPath(ThrowingRunnable runnable) throws Exception {
        String previousRootPath = System.getProperty("sws.root.path");
        String previousRunMode = System.getProperty("sws.run.mode");
        try {
            System.setProperty("sws.root.path", temporaryFolder.newFolder("zrlog-error").getAbsolutePath());
            System.clearProperty("sws.run.mode");
            runnable.run();
        } finally {
            restoreProperty("sws.run.mode", previousRunMode);
            restoreProperty("sws.root.path", previousRootPath);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static void useLocale(String locale) {
        I18nVO resources = new I18nVO();
        resources.setBackend(I18nUtil.getI18nVOCache().getBackend());
        resources.setLocale(locale);
        I18nUtil.threadLocal.set(resources);
    }

    private static HttpRequest request(String uri, String query) {
        return (HttpRequest) Proxy.newProxyInstance(
                ZrLogErrorHandleTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("getQueryStr".equals(method.getName())) {
                        return query;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static class ResponseRecorder {

        private Object json;
        private String redirect;
        private final Map<String, String> headers = new HashMap<>();
        private Integer renderCode;
        private Integer writeCode;
        private String writtenBody;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    ZrLogErrorHandleTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("renderJson".equals(method.getName())) {
                            json = args[0];
                        } else if ("redirect".equals(method.getName())) {
                            redirect = (String) args[0];
                        } else if ("addHeader".equals(method.getName())) {
                            headers.put((String) args[0], (String) args[1]);
                        } else if ("renderCode".equals(method.getName())) {
                            renderCode = (Integer) args[0];
                        } else if ("write".equals(method.getName()) && args.length == 2) {
                            writeCode = (Integer) args[1];
                            writtenBody = new String(((InputStream) args[0]).readAllBytes(), StandardCharsets.UTF_8);
                        }
                        return null;
                    });
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class TestBusinessException extends AbstractBusinessException {

        @Override
        public int getError() {
            return 9001;
        }

        @Override
        public String getMessage() {
            return "business-error";
        }
    }
}
