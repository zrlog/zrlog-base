package com.zrlog.business.plugin;

import com.hibegin.common.util.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import com.zrlog.util.ExecutionDeadline;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 处理由于未知原因插件异常停止后，还可以通过命令重新加载，保证插件的高可用。
 */
class PluginGhostWatcher implements AutoCloseable {

    static final int READ_BUFFER_SIZE = 8 * 1024;
    private static final Logger LOGGER = LoggerUtil.getLogger(PluginGhostWatcher.class);
    private final int port;
    private final String host;
    private final long waitMillis;
    private volatile Socket activeSocket;
    private volatile boolean closed;

    public PluginGhostWatcher(String host, int port) {
        this(host, port, 3000);
    }

    PluginGhostWatcher(String host, int port, long waitMillis) {
        this.host = host;
        this.port = port;
        this.waitMillis = waitMillis;
    }

    static void drainUntilEof(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        while (inputStream.read(buffer) >= 0) {
            // The watcher protocol uses only EOF as a lifecycle signal.
        }
    }

    public boolean doWatch() {
        return doWatch(() -> null);
    }

    boolean doWatch(Supplier<ExecutionDeadline> startupDeadline) {
        //使用Socket的方式进行监听，如果插件服务停止后，那么SocketServer也会被关闭，标记插件服务停止。
        boolean connected = false;
        try {
            //待插件启动
            ExecutionDeadline deadline = startupDeadline.get();
            Thread.sleep(deadline == null ? waitMillis : Math.min(waitMillis, deadline.requireRemainingMillis()));
            if (closed) return false;
            try (Socket socket = new Socket()) {
                activeSocket = socket;
                if (closed) return false;
                deadline = startupDeadline.get();
                int connectMillis = deadline == null ? 3000 : (int) Math.min(3000, deadline.requireRemainingMillis());
                socket.connect(new InetSocketAddress(host, port), connectMillis);
                connected = true;
                byte[] buffer = new byte[READ_BUFFER_SIZE];
                while (!closed) {
                    deadline = startupDeadline.get();
                    socket.setSoTimeout(deadline == null ? 250 : (int) Math.min(250, deadline.requireRemainingMillis()));
                    try {
                        if (socket.getInputStream().read(buffer) < 0) break;
                    } catch (SocketTimeoutException pending) {
                        // Recheck cancellation/startup budget even when the watcher sends no data.
                    }
                }
            } finally {
                activeSocket = null;
            }
            return connected;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Plugin watcher interrupted " + e.getMessage());
            return connected;
        } catch (Exception e) {
            LOGGER.warning("Plugin exception stop " + e.getMessage());
            return connected;
        }
    }

    @Override
    public void close() {
        closed = true;
        Socket socket = activeSocket;
        if (socket != null) {
            try { socket.close(); }
            catch (IOException ignored) { /* Closing again is harmless. */ }
        }
    }
}
