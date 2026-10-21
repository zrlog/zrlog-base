package com.zrlog.common;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.plugin.IPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ZrLogConfigShutdownTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 30000)
    public void shouldStopOwnedExecutorsInAnIsolatedApplicationJvm() throws Exception {
        // A real application stop also closes the process-wide outbound HTTP
        // client. Keep that lifecycle isolated from unrelated HTTP tests.
        Path output = temporary.newFile("shutdown.log").toPath();
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                "org.junit.runner.JUnitCore", Scenarios.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue("Shutdown JVM did not exit: " + output, process.waitFor(25, TimeUnit.SECONDS));
            String result = Files.readString(output);
            System.out.print(result);
            assertEquals(result, 0, process.exitValue());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    public static class Scenarios {
        @Test(timeout = 15000)
        public void shouldDrainDecodeHandoffBeforeStoppingPluginsAndWaitForHostCleanup() throws Exception {
            TestConfig config = new TestConfig();
            ExecutorService decode = (ExecutorService) config.getServerConfig().getDecodeExecutor();
            ExecutorService requests = (ExecutorService) config.getServerConfig().getRequestExecutor();
            CountDownLatch decoding = new CountDownLatch(1);
            CountDownLatch finishDecode = new CountDownLatch(1);
            CountDownLatch cleaningHost = new CountDownLatch(1);
            CountDownLatch finishHost = new CountDownLatch(1);
            CompletableFuture<Boolean> requestResult = new CompletableFuture<>();
            config.after = () -> {
                assertTrue(decode.isTerminated());
                assertTrue(requests.isTerminated());
                assertEquals(1, config.pluginStops.get());
                cleaningHost.countDown();
                await(finishHost);
            };
            Thread first = new Thread(config::stop);
            CountDownLatch secondStarted = new CountDownLatch(1);
            CountDownLatch secondStopped = new CountDownLatch(1);
            Thread second = new Thread(() -> {
                secondStarted.countDown();
                config.stop();
                secondStopped.countDown();
            });
            try {
                decode.execute(() -> {
                    decoding.countDown();
                    await(finishDecode);
                    requests.execute(() -> {
                        // stop() must not hold the config's database monitor.
                        synchronized (config) {
                            requestResult.complete(config.pluginStops.get() == 0);
                        }
                    });
                });
                assertTrue(decoding.await(2, TimeUnit.SECONDS));
                first.start();
                awaitShutdown(decode);
                assertEquals(1, config.beforeCalls.get());
                assertFalse(requests.isShutdown());
                finishDecode.countDown();
                assertTrue(requestResult.get(3, TimeUnit.SECONDS));
                assertTrue(cleaningHost.await(2, TimeUnit.SECONDS));
                second.start();
                assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
                assertFalse(secondStopped.await(100, TimeUnit.MILLISECONDS));
                finishHost.countDown();
                first.join(3000);
                second.join(3000);
                assertFalse(first.isAlive());
                assertFalse(second.isAlive());
                config.stop();
                assertEquals(1, config.beforeCalls.get());
                assertEquals(1, config.afterCalls.get());
                assertTrue(config.getServerConfig().getRequestCheckerExecutor().isShutdown());
            } finally {
                finishDecode.countDown();
                finishHost.countDown();
                first.join(3000);
                second.join(3000);
                config.stop();
            }
        }

        @Test(timeout = 10000)
        public void shouldCancelWorkAndPreserveAnAlreadyInterruptedStop() throws Exception {
            assertInterruptedStop(true);
        }

        @Test(timeout = 10000)
        public void shouldCancelWorkWhenInterruptedWhileWaiting() throws Exception {
            assertInterruptedStop(false);
        }

        private void assertInterruptedStop(boolean initiallyInterrupted) throws Exception {
            TestConfig config = new TestConfig();
            ExecutorService requests = (ExecutorService) config.getServerConfig().getRequestExecutor();
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch cancelled = new CountDownLatch(1);
            AtomicBoolean interruptPreserved = new AtomicBoolean();
            requests.execute(() -> {
                running.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    cancelled.countDown();
                    Thread.currentThread().interrupt();
                }
            });
            Thread stop = new Thread(() -> {
                if (initiallyInterrupted) {
                    Thread.currentThread().interrupt();
                }
                config.stop();
                interruptPreserved.set(Thread.currentThread().isInterrupted());
            });
            try {
                assertTrue(running.await(2, TimeUnit.SECONDS));
                stop.start();
                if (!initiallyInterrupted) {
                    awaitShutdown(requests);
                    stop.interrupt();
                }
                stop.join(3000);
                assertFalse(stop.isAlive());
                assertTrue(interruptPreserved.get());
                assertTrue(cancelled.await(2, TimeUnit.SECONDS));
                assertTrue(requests.awaitTermination(2, TimeUnit.SECONDS));
                assertEquals(1, config.pluginStops.get());
                assertEquals(1, config.afterCalls.get());
                assertTrue(config.getServerConfig().getRequestCheckerExecutor().isShutdown());
            } finally {
                requests.shutdownNow();
                stop.interrupt();
                stop.join(3000);
                config.stop();
            }
        }

        @Test(timeout = 15000)
        public void shouldCancelWorkAfterTheGracefulBudget() throws Exception {
            TestConfig config = new TestConfig();
            ExecutorService requests = (ExecutorService) config.getServerConfig().getRequestExecutor();
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch cancelled = new CountDownLatch(1);
            try {
                requests.execute(() -> {
                    running.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException e) {
                        cancelled.countDown();
                    }
                });
                assertTrue(running.await(2, TimeUnit.SECONDS));
                config.stop();
                assertEquals(0, cancelled.getCount());
                assertTrue(requests.isTerminated());
                assertEquals(1, config.afterCalls.get());
            } finally {
                requests.shutdownNow();
                config.stop();
            }
        }

        @Test
        public void shouldStillCloseOwnedResourcesWhenEitherHostHookFails() {
            for (boolean failBefore : new boolean[]{true, false}) {
                TestConfig config = new TestConfig();
                Runnable fail = () -> { throw new IllegalStateException("host cleanup failure"); };
                if (failBefore) {
                    config.before = fail;
                } else {
                    config.after = fail;
                }
                assertThrows(IllegalStateException.class, config::stop);
                assertTrue(((ExecutorService) config.getServerConfig().getDecodeExecutor()).isTerminated());
                assertTrue(((ExecutorService) config.getServerConfig().getRequestExecutor()).isTerminated());
                assertTrue(config.getServerConfig().getRequestCheckerExecutor().isShutdown());
                assertEquals(1, config.pluginStops.get());
                assertEquals(1, config.afterCalls.get());
                config.stop();
                assertEquals(1, config.beforeCalls.get());
                assertEquals(1, config.afterCalls.get());
            }
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for test work");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }

        private static void awaitShutdown(ExecutorService executor) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!executor.isShutdown() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertTrue(executor.isShutdown());
        }

        private static class TestConfig extends ZrLogConfig {
            private final AtomicInteger beforeCalls = new AtomicInteger();
            private final AtomicInteger afterCalls = new AtomicInteger();
            private final AtomicInteger pluginStops = new AtomicInteger();
            private Runnable before = () -> { };
            private Runnable after = () -> { };

            TestConfig() {
                super(0, null, "");
                plugins.add(new IPlugin() {
                    @Override public boolean start() { return true; }
                    @Override public boolean autoStart() { return false; }
                    @Override public boolean isStarted() { return true; }
                    @Override public boolean stop() { pluginStops.incrementAndGet(); return true; }
                });
            }

            @Override public boolean isInstalled() { return false; }
            @Override public DataSourceWrapper configDatabase() { return null; }
            @Override public List<IPlugin> getBasePluginList() { return Collections.emptyList(); }
            @Override protected void beforeStop() { beforeCalls.incrementAndGet(); before.run(); }
            @Override protected void afterStop() { afterCalls.incrementAndGet(); after.run(); }
        }
    }
}
