package com.hibegin.common.util.http;

import com.sun.net.httpserver.HttpServer;
import com.zrlog.util.ExecutionDeadline;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class BoundedHttpExchangeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 5000)
    public void partialControlResponseCannotOutliveItsBudget() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        HttpServer server = stalledServer(false, started, release, new CountDownLatch(1));
        try {
            CompletableFuture<Exception> response = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpUtil.getInstance().sendDirectRequest(request(server), ExecutionDeadline.afterMillis(500), 1024);
                    return null;
                } catch (Exception failure) { return failure; }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(response.get(2, TimeUnit.SECONDS) instanceof HttpTimeoutException);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test(timeout = 6000)
    public void expiredDownloadsPreserveTargetBeforeAndAfterLateHeadersOrBody() throws Exception {
        for (boolean delayHeaders : new boolean[]{true, false}) {
            Path directory = temporary.newFolder().toPath();
            Path target = directory.resolve("core.jar");
            byte[] existing = "existing".getBytes(StandardCharsets.UTF_8);
            Files.write(target, existing);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            HttpServer server = stalledServer(delayHeaders, started, release, finished);
            try {
                CompletableFuture<Exception> response = CompletableFuture.supplyAsync(() -> {
                    try {
                        HttpUtil.getInstance().downloadToFile(request(server), target, ExecutionDeadline.afterMillis(500), 1024);
                        return null;
                    } catch (Exception failure) { return failure; }
                });
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertTrue(response.get(2, TimeUnit.SECONDS) instanceof HttpTimeoutException);
                assertArrayEquals(existing, Files.readAllBytes(target));
                try (Stream<Path> files = Files.list(directory)) { assertEquals(1, files.count()); }
                release.countDown();
                assertTrue(finished.await(1, TimeUnit.SECONDS));
                assertArrayEquals(existing, Files.readAllBytes(target));
                try (Stream<Path> files = Files.list(directory)) { assertEquals(1, files.count()); }
            } finally {
                release.countDown();
                server.stop(0);
            }
        }
    }

    @Test(timeout = 5000)
    public void interruptionAbortsFileTransferAndPreservesInterruptFlag() throws Exception {
        Path target = temporary.newFolder().toPath().resolve("core.jar");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = stalledServer(false, started, release, new CountDownLatch(1));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread downloader = new Thread(() -> {
            try {
                HttpUtil.getInstance().downloadToFile(request(server), target, ExecutionDeadline.afterMillis(30000), 1024);
            } catch (Throwable error) {
                failure.set(error);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            downloader.start();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            downloader.interrupt();
            downloader.join(1000);
            assertFalse(downloader.isAlive());
            assertTrue(failure.get() instanceof InterruptedException);
            assertEquals(Boolean.TRUE, interrupted.get());
            try (Stream<Path> files = Files.list(target.getParent())) { assertEquals(0, files.count()); }
        } finally {
            downloader.interrupt();
            release.countDown();
            downloader.join(1000);
            server.stop(0);
        }
    }

    @Test(timeout = 5000)
    public void boundedResponsesPreserveUserAgentRejectOversizeAndNeverFollowRedirects() throws Exception {
        byte[] text = "任务结果".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> userAgent = new AtomicReference<>();
        AtomicInteger redirected = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(text);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/forbidden");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/forbidden", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            assertEquals("任务结果", HttpUtil.getInstance().sendDirectRequest(request(server),
                    ExecutionDeadline.afterMillis(2000), text.length).body());
            assertEquals(HttpUtil.getUserAgent(), userAgent.get());
            assertThrows(IOException.class, () -> HttpUtil.getInstance().sendDirectRequest(request(server),
                    ExecutionDeadline.afterMillis(2000), text.length - 1));
            HttpRequest redirect = HttpRequest.newBuilder(request(server).uri().resolve("/redirect")).GET().build();
            assertEquals(307, HttpUtil.getInstance().sendDirectRequest(redirect,
                    ExecutionDeadline.afterMillis(2000), 1024).statusCode());
            assertEquals(0, redirected.get());
            Path target = temporary.newFile("keep.jar").toPath();
            Files.writeString(target, "keep");
            assertThrows(IOException.class, () -> HttpUtil.getInstance().downloadToFile(redirect, target,
                    ExecutionDeadline.afterMillis(2000), 1024));
            assertEquals("keep", Files.readString(target));
        } finally { server.stop(0); }
    }

    private static HttpRequest request(HttpServer server) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/")).GET().build();
    }

    private static HttpServer stalledServer(boolean delayHeaders, CountDownLatch started,
                                            CountDownLatch release, CountDownLatch finished) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                if (!delayHeaders) {
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write("first".getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                }
                started.countDown();
                release.await();
                if (delayHeaders) exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("late".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            catch (IOException disconnected) { /* Expected when the client deadline closes the transfer. */ }
            finally { exchange.close(); finished.countDown(); }
        });
        server.start();
        return server;
    }
}
