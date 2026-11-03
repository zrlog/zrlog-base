package com.zrlog.business.plugin;

import static org.junit.Assert.*;

import com.zrlog.util.ExecutionDeadline;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class PluginGhostWatcherTest {

    @Test(timeout = 3000)
    public void closeReleasesAWatcherDuringSocketConnectionOrRead() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(1000);
            PluginGhostWatcher watcher =
                    new PluginGhostWatcher("127.0.0.1", server.getLocalPort(), 0);
            FutureTask<Boolean> result = new FutureTask<>(watcher::doWatch);
            Thread thread = new Thread(result);
            thread.start();
            try (Socket peer = server.accept()) {
                watcher.close();
                // accept() may finish before the client returns from connect(). Either
                // connection result is valid; cancellation must release both endpoints.
                result.get(1, TimeUnit.SECONDS);
                peer.setSoTimeout(1000);
                assertEquals(-1, peer.getInputStream().read());
            } finally {
                watcher.close();
                thread.join(1000);
            }
        }
    }

    @Test(timeout = 4000)
    public void startupDeadlineEndsAnIdleWatcherButConfirmationRemovesTheDeadline()
            throws Exception {
        for (boolean confirmed : new boolean[] {false, true}) {
            try (ServerSocket server = new ServerSocket(0)) {
                server.setSoTimeout(1000);
                AtomicReference<ExecutionDeadline> deadline =
                        new AtomicReference<>(ExecutionDeadline.afterMillis(300));
                PluginGhostWatcher watcher =
                        new PluginGhostWatcher("127.0.0.1", server.getLocalPort(), 0);
                FutureTask<Boolean> result = new FutureTask<>(() -> watcher.doWatch(deadline::get));
                Thread thread = new Thread(result);
                thread.start();
                try (Socket peer = server.accept()) {
                    if (confirmed) {
                        deadline.set(null);
                        assertThrows(
                                TimeoutException.class,
                                () -> result.get(500, TimeUnit.MILLISECONDS));
                        watcher.close();
                    }
                    assertTrue(result.get(1, TimeUnit.SECONDS));
                    peer.setSoTimeout(1000);
                    assertEquals(-1, peer.getInputStream().read());
                } finally {
                    watcher.close();
                    thread.join(1000);
                }
            }
        }
    }

    @Test
    public void shouldReadUntilPluginWatcherSocketCloses() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            FutureTask<Void> serverTask =
                    new FutureTask<>(
                            () -> {
                                try (Socket socket = serverSocket.accept();
                                        OutputStream outputStream = socket.getOutputStream()) {
                                    outputStream.write("alive".getBytes(StandardCharsets.UTF_8));
                                }
                                return null;
                            });
            Thread serverThread = new Thread(serverTask, "plugin-ghost-watcher-test-server");
            serverThread.start();

            assertTrue(
                    new PluginGhostWatcher("127.0.0.1", serverSocket.getLocalPort(), 0).doWatch());

            serverTask.get();
        }
    }

    @Test
    public void shouldReturnWhenWatcherSocketCannotConnect() throws Exception {
        int unusedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            unusedPort = serverSocket.getLocalPort();
        }

        assertFalse(new PluginGhostWatcher("127.0.0.1", unusedPort, 0).doWatch());

        assertTrue(unusedPort > 0);
    }

    @Test
    public void shouldPreserveInterruptWhileWaitingForPluginStartup() {
        Thread.currentThread().interrupt();
        try {
            assertFalse(new PluginGhostWatcher("127.0.0.1", 1, 1000).doWatch());

            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
