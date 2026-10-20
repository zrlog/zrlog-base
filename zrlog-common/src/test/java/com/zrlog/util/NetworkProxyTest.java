package com.zrlog.util;

import com.hibegin.common.dao.DataSourceWrapperImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NetworkProxyTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private static NetworkProxy.Settings settings(String url, String noProxy) {
        return NetworkProxy.resolve(Map.of(NetworkProxy.URL_ENV, url, NetworkProxy.HTTPS_URL_ENV, url,
                NetworkProxy.NO_PROXY_ENV, noProxy), new Properties());
    }

    @Test
    public void fileSettingsAreOverriddenPerFieldIncludingExplicitDisable() throws Exception {
        Properties file = new Properties();
        file.setProperty(NetworkProxy.URL_KEY, "http://file.example:3128");
        file.setProperty(NetworkProxy.NO_PROXY_KEY, ".internal.example");
        NetworkProxy.Settings configured = NetworkProxy.resolve(Map.of(), file);
        assertEquals("file", configured.getSource());
        assertEquals("http://file.example:3128", configured.getUrl());
        NetworkProxy.Settings overridden = NetworkProxy.resolve(Map.of(NetworkProxy.URL_ENV, "http://env.example:8080"), file);
        assertEquals("environment", overridden.getSource());
        assertEquals("http://env.example:8080", overridden.getUrl());
        assertEquals(".internal.example", overridden.getNoProxy());
        NetworkProxy.Settings disabledHttp = NetworkProxy.resolve(Map.of(NetworkProxy.URL_ENV, ""), file);
        assertEquals("", disabledHttp.getUrl());
        assertEquals("http://file.example:3128", disabledHttp.getHttpsUrl());
        NetworkProxy.Settings lower = NetworkProxy.resolve(Map.of("http_proxy", "http://lower.example:8080",
                "HTTP_PROXY", "http://upper.example:8080", "https_proxy", "http://secure.example:3128",
                "no_proxy", ".local.example"), file);
        assertEquals("http://lower.example:8080", lower.getUrl());
        assertEquals("http://secure.example:3128", lower.getHttpsUrl());
        assertEquals("http_proxy", lower.getHttpVariable());
        assertEquals(".local.example", lower.getNoProxy());
        assertEquals("", NetworkProxy.resolve(Map.of("http_proxy", "", "HTTP_PROXY", "http://upper.example:80"), file).getUrl());
        for (String invalid : new String[]{"https://proxy.example:80", "http://user:password@proxy.example", "http://proxy:0",
                "http://proxy:65536", "http://proxy/path", "http://proxy?key=secret", "http://proxy#secret"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> NetworkProxy.resolve(Map.of(NetworkProxy.URL_ENV, invalid), new Properties()));
            assertFalse(error.getMessage().contains("password"));
            assertFalse(error.getMessage().contains("secret"));
        }
        for (String invalid : new String[]{"10.0.0.0/8", "internal:8080", "http://internal", ".", "a..b", "[not:ipv6]"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> NetworkProxy.resolve(Map.of(NetworkProxy.NO_PROXY_ENV, invalid), new Properties()));
        }
    }

    @Test
    public void savedChangesRemainOnDiskAndDoNotChangeTheCurrentPolicy() throws Exception {
        Path path = temporary.newFolder().toPath().resolve("proxy.properties");
        NetworkProxy.Settings active = settings("http://active.example:3128", "");
        NetworkProxy.save(path, "http://next.example:8080", ".internal.example");
        Properties file = new Properties();
        try (var input = Files.newInputStream(path)) {
            file.load(input);
        }
        assertEquals("http://next.example:8080", file.getProperty(NetworkProxy.URL_KEY));
        assertFalse(active.samePolicy(NetworkProxy.resolve(Map.of(), file)));
        assertEquals("http://active.example:3128", active.getUrl());
        byte[] before = Files.readAllBytes(path);
        assertThrows(IllegalArgumentException.class,
                () -> NetworkProxy.save(path, "http://user:secret@other:80", ""));
        assertArrayEquals(before, Files.readAllBytes(path));
        NetworkProxy.save(path, "http://editable.example:80", "localhost");
        try (var input = Files.newInputStream(path)) {
            file.load(input);
        }
        assertEquals("http://editable.example:80", file.getProperty(NetworkProxy.URL_KEY));
        assertEquals("http://env.example:80", NetworkProxy.resolve(Map.of("HTTP_PROXY", "http://env.example:80"), file).getUrl());
        assertEquals("http://editable.example:80", NetworkProxy.resolve(Map.of("HTTP_PROXY", "http://env.example:80"), file).getHttpsUrl());
        assertEquals("localhost", file.getProperty(NetworkProxy.NO_PROXY_KEY));
        NetworkProxy.save(path, "", "");
        try (var input = Files.newInputStream(path)) {
            file.load(input);
        }
        NetworkProxy.Settings disabled = NetworkProxy.resolve(Map.of(), file);
        assertFalse(disabled.isEnabled());
        assertEquals("file", disabled.getSource());
        assertEquals(Proxy.Type.DIRECT, disabled.proxySelector().select(URI.create("https://outside.invalid/")).get(0).type());
    }

    @Test
    public void standardVariablesSelectDifferentProxiesForHttpAndHttps() {
        NetworkProxy.Settings configured = NetworkProxy.resolve(Map.of("HTTP_PROXY", "http://plain.example:3128",
                "HTTPS_PROXY", "http://secure.example:8080", "NO_PROXY", ".upper.example", "no_proxy", ".lower.example,::1,2001:db8::2"), new Properties());
        assertEquals("no_proxy", configured.getNoProxyVariable());
        assertEquals(".lower.example,::1,2001:db8::2", configured.getNoProxy());
        var http = (java.net.InetSocketAddress) configured.proxySelector().select(URI.create("http://outside.invalid/")).get(0).address();
        var https = (java.net.InetSocketAddress) configured.proxySelector().select(URI.create("https://outside.invalid/")).get(0).address();
        assertEquals("plain.example", http.getHostString());
        assertEquals(3128, http.getPort());
        assertEquals("secure.example", https.getHostString());
        assertEquals(8080, https.getPort());
        assertEquals(Proxy.Type.DIRECT, configured.proxySelector().select(URI.create("https://db.lower.example/")).get(0).type());
        assertEquals(Proxy.Type.DIRECT, configured.proxySelector().select(URI.create("https://[2001:db8::2]/")).get(0).type());
        assertTrue(configured.launchArguments().contains("-Dhttp.proxyHost=plain.example"));
        assertTrue(configured.launchArguments().contains("-Dhttps.proxyHost=secure.example"));
    }

    @Test
    public void pendingClientUsesItsOwnPolicyWithoutChangingGlobalRouting() throws Exception {
        try (ProxyFixture proxy = new ProxyFixture()) {
            NetworkProxy.Settings candidate = settings(proxy.url(), ".internal.example,*.private,*example.net*");
            ProxySelector active = ProxySelector.getDefault();
            String activeHost = System.getProperty("http.proxyHost");
            HttpResponse<String> response = HttpClient.newBuilder().proxy(candidate.proxySelector()).build().send(
                    HttpRequest.newBuilder(URI.create("http://upstream.invalid/test")).timeout(Duration.ofSeconds(3)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals("proxied", response.body());
            assertEquals("GET http://upstream.invalid/test HTTP/1.1", proxy.request.get(3, TimeUnit.SECONDS));
            assertSame(active, ProxySelector.getDefault());
            assertEquals(activeHost, System.getProperty("http.proxyHost"));
            try (Policy ignored = new Policy(candidate)) {
                for (String url : new String[]{"https://[::1]/", "https://[::ffff:127.0.0.1]/", "https://127.9.1.2/",
                        "https://internal.example/", "https://db.internal.example/", "https://db.private/",
                        "https://anexample.net/", "https://outside.invalid/"}) {
                    assertEquals(ProxySelector.getDefault().select(URI.create(url)).get(0).type(),
                            candidate.proxySelector().select(URI.create(url)).get(0).type());
                }
            }
        }
    }

    @Test
    public void fileWriteFailurePreservesThePreviousConfiguration() throws Exception {
        Path parent = temporary.newFile().toPath();
        assertThrows(java.io.IOException.class,
                () -> NetworkProxy.save(parent.resolve("proxy.properties"), "http://proxy.example:80", ""));
        assertEquals(0, Files.size(parent));
    }

    @Test
    public void routesHttpThroughProxyAndKeepsLoopbackAndConfiguredDomainsDirect() throws Exception {
        try (ProxyFixture proxy = new ProxyFixture(); Policy ignored = new Policy(settings(proxy.url(), ".internal.example"))) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://upstream.invalid/document")).timeout(Duration.ofSeconds(3)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals("proxied", response.body());
            assertEquals("GET http://upstream.invalid/document HTTP/1.1", proxy.request.get(3, TimeUnit.SECONDS));
            for (String url : new String[]{"http://localhost/", "https://127.0.0.1/", "https://[::1]/",
                    "https://internal.example/", "http://db.internal.example/"}) {
                assertEquals(Proxy.Type.DIRECT, ProxySelector.getDefault().select(URI.create(url)).get(0).type());
            }
            assertEquals(1, ProxySelector.getDefault().select(URI.create("https://outside.invalid/")).size());
            assertEquals(Proxy.Type.HTTP, ProxySelector.getDefault().select(URI.create("https://outside.invalid/")).get(0).type());
        }
    }

    @Test
    public void webApiDatabaseUsesTheSameProxyBeforeAnyDatabaseSettingsAreAvailable() throws Exception {
        try (ProxyFixture proxy = new ProxyFixture(); Policy ignored = new Policy(settings(proxy.url(), ""))) {
            Properties database = new Properties();
            database.setProperty("jdbcUrl", "jdbc:webapi://database.invalid:443/query");
            database.setProperty("password", "test-token");
            try (DataSourceWrapperImpl source = new DataSourceWrapperImpl(database, false)) {
                assertThrows(java.sql.SQLException.class, source::testConnection);
            }
            assertEquals("CONNECT database.invalid:443 HTTP/1.1", proxy.request.get(3, TimeUnit.SECONDS));
        }
    }

    private static final class Policy implements AutoCloseable {
        private final Map<String, String> previous = new LinkedHashMap<>();

        Policy(NetworkProxy.Settings settings) {
            for (String argument : settings.launchArguments()) {
                int separator = argument.indexOf('=');
                String key = argument.substring(2, separator);
                previous.put(key, System.getProperty(key));
                System.setProperty(key, argument.substring(separator + 1));
            }
        }

        public void close() {
            previous.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }

    private static final class ProxyFixture implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        private final CompletableFuture<String> request = new CompletableFuture<>();
        private final Thread worker;

        ProxyFixture() throws Exception {
            worker = new Thread(() -> {
                try (var connection = socket.accept()) {
                    connection.setSoTimeout(3000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                    String first = reader.readLine();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    }
                    request.complete(first);
                    String response = first.startsWith("CONNECT ")
                            ? "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            : "HTTP/1.1 200 OK\r\nContent-Length: 7\r\nConnection: close\r\n\r\nproxied";
                    connection.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                } catch (Exception exception) {
                    request.completeExceptionally(exception);
                }
            });
            worker.setDaemon(true);
            worker.start();
        }

        String url() {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }

        public void close() throws Exception {
            socket.close();
            worker.join(4000);
        }
    }
}
