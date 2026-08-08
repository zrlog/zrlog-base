package com.zrlog.business.plugin;

import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginGhostWatcherTest {

    @Test
    public void shouldDrainWatcherStreamWithFixedSizeReads() throws Exception {
        AtomicInteger remaining = new AtomicInteger(PluginGhostWatcher.READ_BUFFER_SIZE * 5 + 123);
        AtomicInteger largestRequestedRead = new AtomicInteger();
        InputStream inputStream = new InputStream() {
            @Override
            public int read() {
                return remaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0 ? 0 : -1;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                largestRequestedRead.accumulateAndGet(length, Math::max);
                int available = remaining.get();
                if (available <= 0) {
                    return -1;
                }
                int read = Math.min(available, length);
                remaining.addAndGet(-read);
                return read;
            }
        };

        PluginGhostWatcher.drainUntilEof(inputStream);

        assertEquals(0, remaining.get());
        assertEquals(PluginGhostWatcher.READ_BUFFER_SIZE, largestRequestedRead.get());
    }

    @Test
    public void shouldReadUntilPluginWatcherSocketCloses() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            FutureTask<Void> serverTask = new FutureTask<>(() -> {
                try (Socket socket = serverSocket.accept();
                     OutputStream outputStream = socket.getOutputStream()) {
                    outputStream.write("alive".getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            Thread serverThread = new Thread(serverTask, "plugin-ghost-watcher-test-server");
            serverThread.start();

            assertTrue(new PluginGhostWatcher("127.0.0.1", serverSocket.getLocalPort(), 0).doWatch());

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
