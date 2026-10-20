package com.hibegin.common.util.http;

import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.common.util.http.handle.HttpFileHandle;
import com.hibegin.common.util.http.handle.HttpResponseJsonHandle;
import com.hibegin.common.util.http.handle.HttpStringHandle;
import com.zrlog.util.ThreadUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public class HttpHandleAndUtilTest {

    private static Object invokeStatic(String methodName, Object... args) throws Exception {
        Method method = null;
        for (Method declaredMethod : HttpUtil.class.getDeclaredMethods()) {
            if (declaredMethod.getName().equals(methodName) && declaredMethod.getParameterCount() == args.length) {
                method = declaredMethod;
                break;
            }
        }
        if (method == null) {
            throw new IllegalArgumentException(methodName);
        }
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static HttpRequest request(String uri) {
        return HttpRequest.newBuilder(URI.create(uri)).GET().build();
    }

    private static HttpResponse<InputStream> response(int statusCode, byte[] body, Map<String, List<String>> headers) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return HttpHandleAndUtilTest.request("https://example.com/api");
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public Optional<javax.net.ssl.SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://example.com/api");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static byte[] gzip(String value) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return outputStream.toByteArray();
    }

    @Test
    public void shouldHandlePlainAndGzipStringResponses() throws Exception {
        HttpStringHandle plain = new HttpStringHandle();
        HttpStringHandle gzip = new HttpStringHandle();
        HttpRequest request = request("https://example.com/api");

        assertFalse(plain.handle(request, response(200, "hello".getBytes(StandardCharsets.UTF_8), Map.of())));
        assertFalse(gzip.handle(request, response(201, gzip("hello gzip"), Map.of("Content-Encoding", List.of("gzip")))));

        assertEquals(200, plain.getStatusCode());
        assertEquals("hello", plain.getT());
        assertEquals(201, gzip.getStatusCode());
        assertEquals("hello gzip", gzip.getT());
    }

    @Test
    public void shouldHandleJsonResponsesOnlyOnSuccess() throws Exception {
        HttpRequest request = request("https://example.com/api");
        HttpResponseJsonHandle<SampleJson> success = new HttpResponseJsonHandle<>(SampleJson.class);
        HttpResponseJsonHandle<SampleJson> failed = new HttpResponseJsonHandle<>(SampleJson.class);

        assertTrue(success.handle(request, response(200, gzip("{\"name\":\"zrlog\",\"count\":3}"),
                Map.of("Content-Encoding", List.of("gzip")))));
        assertTrue(failed.handle(request, response(500, "{}".getBytes(StandardCharsets.UTF_8), Map.of())));

        assertEquals("zrlog", success.getT().name);
        assertEquals(3, success.getT().count);
        assertEquals(200, success.getStatusCode());
        assertNull(failed.getT());
        assertEquals(500, failed.getStatusCode());
    }

    @Test
    public void shouldSaveHttpResponseBodyToFile() throws Exception {
        File root = Files.createTempDirectory("zrlog-http-file").toFile();
        HttpRequest request = request("https://example.com/download/plugin-core.jar");
        HttpFileHandle handle = new HttpFileHandle(root.getAbsolutePath());

        assertFalse(handle.handle(request, response(200, "jar".getBytes(StandardCharsets.UTF_8), Map.of())));

        assertEquals(200, handle.getStatusCode());
        assertNotNull(handle.getT());
        assertEquals("plugin-core.jar", handle.getT().getName());
        assertEquals("jar", Files.readString(handle.getT().toPath()));
    }

    @Test
    public void shouldExposeCloseResponseHandleStatusAndResponse() {
        HttpRequest request = request("https://example.com/api");
        HttpResponse<InputStream> response = response(204, new byte[0], Map.of());
        CloseResponseHandle handle = new CloseResponseHandle();

        assertTrue(handle.handle(request, response));

        assertEquals(204, handle.getStatusCode());
        assertEquals(response, handle.getT());
    }

    @Test
    public void shouldBuildUrisAndUserAgentsWithoutSendingNetworkRequests() throws Exception {
        Map<String, String[]> params = new HashMap<>();
        params.put("q", new String[]{"hello world"});
        params.put("tag", new String[]{"a", "b"});

        URI uri = (URI) invokeStatic("getUri", "https://example.com/search?exists=1", params);
        String query = (String) invokeStatic("mapToQueryStr", params);
        String userAgent = HttpUtil.buildUserAgent("App", "1.0", "build");

        assertTrue(uri.toString().startsWith("https://example.com/search?exists=1&"));
        assertTrue(uri.toString().contains("q=hello+world"));
        assertTrue(uri.toString().contains("tag=a"));
        assertTrue(uri.toString().contains("tag=b"));
        assertTrue(query.contains("q=hello+world"));
        assertTrue(userAgent.contains("App/1.0"));
        assertTrue(userAgent.contains("build/build"));
    }

    @Test
    public void shouldCreateAndStartThreads() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);
        Thread thread = ThreadUtils.start(() -> ran.set(true));
        thread.join(1000);

        Thread unstarted = ThreadUtils.unstarted(() -> {
        });
        ExecutorService executorService = ThreadUtils.newFixedThreadPool(1);
        try {
            assertTrue(ran.get());
            assertFalse(ThreadUtils.isEnableLoom());
            assertFalse(unstarted.isAlive());
            assertNotNull(ThreadUtils.getThreadFactory().newThread(() -> {
            }));
            assertNotNull(executorService);
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static class SampleJson {
        private String name;
        private int count;
    }
}
