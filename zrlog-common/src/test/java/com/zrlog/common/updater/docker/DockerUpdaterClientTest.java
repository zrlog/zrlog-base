package com.zrlog.common.updater.docker;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DockerUpdaterClientTest {
    @Test
    public void checksMetadataConfigurationAndPreservesJobIdentity() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String token = "test_token_123456789012345678901234";
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicBoolean mismatch = new AtomicBoolean();
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String path = exchange.getRequestURI().getPath();
            String json;
            int code = 200;
            if (path.equals("/v1/capabilities")) {
                json = "{\"protocolVersion\":1,\"ready\":true,\"metadataBaseUrl\":\"https://custom.example/updates/\","
                        + "\"manifestFilename\":\"" + (mismatch.get() ? "wrong.json" : "docker.json") + "\"}";
            } else {
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                json = "{\"id\":\"request-1234567890\",\"phase\":\"queued\",\"version\":\"4.0.0-SNAPSHOT\"}";
                if (exchange.getRequestMethod().equals("POST")) { code = 202; }
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            DockerUpdaterClient client = new DockerUpdaterClient("http://127.0.0.1:" + server.getAddress().getPort(), token,
                    "https://custom.example/updates", "docker.json");
            assertTrue(client.capabilities().ready);
            assertEquals("Bearer " + token, authorization.get());
            DockerUpdaterProtocol.UpgradeRequest request = new DockerUpdaterProtocol.UpgradeRequest();
            request.requestId = "request-1234567890";
            request.version = "4.0.0-SNAPSHOT";
            request.buildId = "1234567";
            request.channel = "preview";
            assertEquals(request.requestId, client.submit(request).id);
            assertTrue(body.get().contains("4.0.0-SNAPSHOT"));
            assertEquals("queued", client.status(request.requestId).phase);
            mismatch.set(true);
            assertThrows(IllegalStateException.class, () -> client.submit(request));
            // Recovery remains available even if metadata settings change after acceptance.
            assertEquals(request.requestId, client.status(request.requestId).id);
            assertThrows(IllegalArgumentException.class, () -> client.status("../../health"));
        } finally { server.stop(0); }
    }

    @Test
    public void preservesSafeMetadataErrorsAndBoundsUntrustedBodies() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger status = new java.util.concurrent.atomic.AtomicInteger(502);
        server.createContext("/", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        DockerUpdaterClient client = new DockerUpdaterClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "test_token_123456789012345678901234", "https://dl.example/", "docker.json");
        try {
            for (String code : new String[]{"metadata_unavailable", "metadata_not_found"}) {
                body.set("{\"code\":\"" + code + "\",\"message\":\"private service detail\"}");
                var error = assertThrows(DockerUpdaterClient.RequestException.class, client::capabilities);
                assertEquals(code, error.getCode());
                assertEquals(502, error.getStatus());
                assertFalse(error.getMessage().contains("private"));
            }
            for (String invalid : new String[]{"<html>proxy error</html>", "null", "[]", "{\"code\":\"private detail\"}",
                    "{\"code\":\"metadata_unavailable\",\"message\":\"" + "x".repeat(4096) + "\"}"}) {
                body.set(invalid);
                assertEquals("updater_request_failed", assertThrows(DockerUpdaterClient.RequestException.class,
                        client::capabilities).getCode());
            }
            status.set(401);
            assertEquals("updater_unauthorized", assertThrows(DockerUpdaterClient.RequestException.class,
                    client::capabilities).getCode());
        } finally { server.stop(0); }
        assertEquals("updater_unavailable", assertThrows(DockerUpdaterClient.RequestException.class,
                client::capabilities).getCode());
    }

    @Test
    public void refusesRedirectsWithoutLeakingServiceToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean followed = new AtomicBoolean();
        server.createContext("/v1/capabilities", exchange -> {
            exchange.getResponseHeaders().set("Location", "/other");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/other", exchange -> { followed.set(true); exchange.close(); });
        server.start();
        try {
            DockerUpdaterClient client = new DockerUpdaterClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    "test_token_123456789012345678901234", "https://dl.example/", "docker.json");
            assertEquals(302, assertThrows(DockerUpdaterClient.RequestException.class, client::capabilities).getStatus());
            assertFalse(followed.get());
        } finally { server.stop(0); }
    }

    @Test
    public void rejectsInvalidConfiguredAddressesAndTokens() {
        for (String url : new String[]{"file:///tmp/socket", "http://user:pass@updater", "http://updater/x", "//updater", "http://updater/?x=1"}) {
            assertThrows(IllegalArgumentException.class, () -> new DockerUpdaterClient(url,
                    "test_token_123456789012345678901234", "https://dl.example", "docker.json"));
        }
        assertThrows(IllegalStateException.class, () -> new DockerUpdaterClient("http://updater", "short", "https://dl.example", "docker.json"));
    }
}
