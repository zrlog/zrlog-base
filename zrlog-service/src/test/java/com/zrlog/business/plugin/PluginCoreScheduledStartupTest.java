package com.zrlog.business.plugin;

import com.sun.net.httpserver.HttpServer;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.util.ExecutionDeadline;
import org.junit.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class PluginCoreScheduledStartupTest {
    @Test(timeout = 5000)
    public void coldCoreMustBeConfirmedBeforeTickAndIsReusedAfterwards() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.ready.set(true);
            assertNotNull(fixture.plugin.tickScheduledTasks("first", 1, 10000));
            assertTrue(fixture.plugin.isStarted());
            assertEquals(1, fixture.starts.get());
            assertEquals(1, fixture.confirmations.get());
            assertEquals(1, fixture.ticks.get());
            assertTrue(fixture.startBudget.get() > 0 && fixture.startBudget.get() <= 2250);
            assertTrue(fixture.tickBudget.get() > 0 && fixture.tickBudget.get() < 5000);
            assertNotNull(fixture.plugin.tickScheduledTasks("second", 1, 7000));
            assertEquals(1, fixture.starts.get());
            assertEquals(2, fixture.ticks.get());
            assertEquals(0, fixture.cancellations.get());
        }
    }

    @Test(timeout = 6000)
    public void unreadyCoreIsCancelledWithoutSubmittingTasksAndCanStartAgain() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertNull(fixture.plugin.tickScheduledTasks("not-ready", 1, 9000));
            assertFalse(fixture.plugin.isStarted());
            assertEquals(0, fixture.ticks.get());
            assertEquals(1, fixture.cancellations.get());
            assertTrue(fixture.cleanupBudget.get() > 0);
            fixture.ready.set(true);
            assertNotNull(fixture.plugin.tickScheduledTasks("retry-start", 1, 10000));
            assertEquals(2, fixture.starts.get());
            assertEquals(1, fixture.ticks.get());
        }
    }

    @Test(timeout = 6000)
    public void concurrentWaiterExpiryDoesNotCancelTheStartupOwner() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CompletableFuture<PluginSchedulerTickResult> owner = CompletableFuture.supplyAsync(() -> {
                try { return fixture.plugin.tickScheduledTasks("owner", 1, 20000); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            });
            try {
                assertTrue(fixture.statusRead.await(1, TimeUnit.SECONDS));
                assertNull(fixture.plugin.tickScheduledTasks("waiter", 1, 8000));
                assertEquals(1, fixture.starts.get());
                assertEquals(0, fixture.cancellations.get());
                assertEquals(0, fixture.ticks.get());
                fixture.ready.set(true);
                assertNotNull(owner.get(2, TimeUnit.SECONDS));
                assertEquals(1, fixture.confirmations.get());
                assertEquals(1, fixture.ticks.get());
            } finally {
                fixture.ready.set(true);
                owner.get(2, TimeUnit.SECONDS);
            }
        }
    }

    @Test(timeout = 5000)
    public void interruptedStartupOwnerCancelsBeforeAnyBusinessDispatch() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread owner = new Thread(() -> {
                try { fixture.plugin.tickScheduledTasks("interrupted", 1, 20000); }
                catch (Throwable error) { failure.set(error); interrupted.set(Thread.currentThread().isInterrupted()); }
            });
            try {
                owner.start();
                assertTrue(fixture.statusRead.await(1, TimeUnit.SECONDS));
                owner.interrupt();
                owner.join(1500);
                assertFalse(owner.isAlive());
                assertTrue(failure.get() instanceof InterruptedException);
                assertTrue(interrupted.get());
                assertFalse(fixture.plugin.isStarted());
                assertEquals(1, fixture.cancellations.get());
                assertEquals(0, fixture.ticks.get());
            } finally { owner.interrupt(); owner.join(1500); }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicBoolean ready = new AtomicBoolean();
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger confirmations = new AtomicInteger();
        final AtomicInteger cancellations = new AtomicInteger();
        final AtomicInteger ticks = new AtomicInteger();
        final AtomicLong startBudget = new AtomicLong();
        final AtomicLong cleanupBudget = new AtomicLong();
        final AtomicLong tickBudget = new AtomicLong();
        final CountDownLatch statusRead = new CountDownLatch(1);
        final HttpServer server;
        final PluginCorePluginImpl plugin;

        Fixture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/status", exchange -> {
                statusRead.countDown();
                byte[] body = ("{\"status\":\"" + (ready.get() ? "STARTED" : "STARTING") + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/internal/runtime-scheduler/tick", exchange -> {
                ticks.incrementAndGet();
                assertTrue("readiness must be confirmed before dispatch", confirmations.get() > 0);
                tickBudget.set(Long.parseLong(exchange.getRequestHeaders().getFirst("X-ZrLog-Budget-Millis")));
                String trigger = exchange.getRequestHeaders().getFirst("X-ZrLog-Trigger-Id");
                byte[] body = ("{\"triggerId\":\"" + trigger + "\",\"children\":[]}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("X-ZrLog-Scheduler-Protocol", "1");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            plugin = new PluginCorePluginImpl(new File("unused.properties"), PublicWebSiteInfo::new,
                    onStop -> new PluginCoreProcess() {
                        public int pluginServerStart(String db, String args, String path, String version, String token) {
                            throw new AssertionError("scheduled startup must carry a deadline");
                        }
                        public int pluginServerStart(String db, String args, String path, String version, String token,
                                                     ExecutionDeadline deadline) {
                            starts.incrementAndGet();
                            startBudget.set(deadline.remainingMillis());
                            return server.getAddress().getPort();
                        }
                        public boolean confirmStarted(int port, ExecutionDeadline deadline) {
                            confirmations.incrementAndGet();
                            return ready.get();
                        }
                        public void cancelStartup(int port, ExecutionDeadline deadline) {
                            cancellations.incrementAndGet();
                            cleanupBudget.set(deadline.remainingMillis());
                        }
                        public void stopPluginCore() { }
                    });
        }

        public void close() { plugin.stop(); server.stop(0); }
    }
}
