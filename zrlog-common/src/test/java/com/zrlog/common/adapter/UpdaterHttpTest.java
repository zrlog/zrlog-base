package com.zrlog.common.adapter;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdaterHttpTest {
    @Test
    public void preservesApplicationIdentityWhenReadingMetadata() throws Exception {
        String document = "{\"version\":\"4.0.0-SNAPSHOT\",\"buildId\":\"preview\"}";
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/metadata.json", exchange -> {
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            if (userAgent == null || !userAgent.startsWith("Mozilla/5.0 (")
                    || !userAgent.contains(" ZrLog/") || !userAgent.contains(" build/")) {
                exchange.getResponseHeaders().set("cf-mitigated", "challenge");
                exchange.sendResponseHeaders(403, -1);
            } else {
                byte[] bytes = document.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        try {
            java.net.URI uri = java.net.URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/metadata.json");
            assertEquals(document, UpdaterHttp.INSTANCE.read(uri));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void distinguishesAnUnpublishedManifestFromOtherHttpFailures() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicInteger status = new java.util.concurrent.atomic.AtomicInteger(404);
        server.createContext("/metadata.json", exchange -> {
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        try {
            java.net.URI uri = java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/metadata.json");
            assertThrows(java.io.FileNotFoundException.class, () -> UpdaterHttp.INSTANCE.read(uri));
            for (int code : new int[]{403, 500, 503}) {
                status.set(code);
                java.io.IOException error = assertThrows(java.io.IOException.class, () -> UpdaterHttp.INSTANCE.read(uri));
                assertFalse(error instanceof java.io.FileNotFoundException);
                assertTrue(error.getMessage().contains(Integer.toString(code)));
            }
        } finally {
            server.stop(0);
        }
    }

}
