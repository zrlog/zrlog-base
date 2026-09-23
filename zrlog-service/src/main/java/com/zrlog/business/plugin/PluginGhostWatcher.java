package com.zrlog.business.plugin;

import com.hibegin.common.util.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * 处理由于未知原因插件异常停止后，还可以通过命令重新加载，保证插件的高可用。
 */
class PluginGhostWatcher {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginGhostWatcher.class);
    static final int READ_BUFFER_SIZE = 8 * 1024;
    private final int port;
    private final String host;
    private final long waitMillis;

    public PluginGhostWatcher(String host, int port) {
        this(host, port, 3000);
    }

    PluginGhostWatcher(String host, int port, long waitMillis) {
        this.host = host;
        this.port = port;
        this.waitMillis = waitMillis;
    }

    public boolean doWatch() {
        //使用Socket的方式进行监听，如果插件服务停止后，那么SocketServer也会被关闭，标记插件服务停止。
        boolean connected = false;
        try {
            //待插件启动
            Thread.sleep(waitMillis);
            try (Socket socket = new Socket(host, port)) {
                connected = true;
                drainUntilEof(socket.getInputStream());
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

    static void drainUntilEof(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        while (inputStream.read(buffer) >= 0) {
            // The watcher protocol uses only EOF as a lifecycle signal.
        }
    }
}
