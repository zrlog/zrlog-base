package com.zrlog.business.plugin;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.util.ThreadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 读取 plugin 日志文件，并输出到控制台
 */
public class PluginConsole implements AutoCloseable {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginConsole.class);
    static final int READ_BUFFER_SIZE = 8 * 1024;
    static final long MAX_BYTES_PER_POLL = 1024L * 1024L;
    private static final byte[] CORRUPT_JAR_MARKER =
            "Error: Invalid or corrupt jarfile".getBytes(StandardCharsets.US_ASCII);

    private final File outputFile;
    private final File serverFileName;
    private final ScheduledExecutorService scheduler;
    private final boolean errorStream;
    private long lastFileSize = 0;
    private int corruptJarMarkerMatchLength;
    private boolean lineStart = true;

    public PluginConsole(File outputFile, File serverFileName, boolean errorStream) {
        this.outputFile = outputFile;
        this.serverFileName = serverFileName;
        this.errorStream = errorStream;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = ThreadUtils.unstarted(runnable);
            thread.setName("plugin-console-print-thread");
            return thread;
        });
    }

    public void printAsync() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!outputFile.exists()) {
                try {
                    close();
                } catch (Exception e) {
                    LOGGER.warning("Error closing PluginConsole: " + e.getMessage());
                }
                return;
            }

            try {
                printNewContent();
            } catch (Exception e) {
                LOGGER.warning("Error reading file: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS); // 每秒检查一次文件大小变化
    }

    long printNewContent() throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(outputFile)) {
            return printNewContent(fileInputStream);
        }
    }

    private long printNewContent(FileInputStream fileInputStream) throws IOException {
        FileChannel channel = fileInputStream.getChannel();
        long currentFileSize = channel.size();
        if (currentFileSize < lastFileSize) {
            lastFileSize = 0;
            resetCorruptJarMarkerDetector();
        }
        long remaining = currentFileSize - lastFileSize;
        if (remaining <= 0) {
            return 0L;
        }
        remaining = Math.min(remaining, MAX_BYTES_PER_POLL);
        channel.position(lastFileSize);
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        PrintStream output = errorStream ? System.err : System.out;
        long processed = 0L;
        while (remaining > 0) {
            int bytesRead = fileInputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (bytesRead <= 0) {
                return processed;
            }
            output.write(buffer, 0, bytesRead);
            lastFileSize += bytesRead;
            remaining -= bytesRead;
            processed += bytesRead;
            if (containsCorruptJarMarker(buffer, bytesRead)) {
                serverFileName.delete();
                close();
                return processed;
            }
        }
        return processed;
    }

    private boolean containsCorruptJarMarker(byte[] buffer, int length) {
        for (int i = 0; i < length; i++) {
            byte value = buffer[i];
            if (corruptJarMarkerMatchLength > 0) {
                if (value == CORRUPT_JAR_MARKER[corruptJarMarkerMatchLength]) {
                    corruptJarMarkerMatchLength++;
                    if (corruptJarMarkerMatchLength == CORRUPT_JAR_MARKER.length) {
                        return true;
                    }
                    continue;
                }
                corruptJarMarkerMatchLength = 0;
                lineStart = value == '\n' || value == '\r';
            }
            if (lineStart && value == CORRUPT_JAR_MARKER[0]) {
                corruptJarMarkerMatchLength = 1;
                lineStart = false;
            } else if (value == '\n' || value == '\r') {
                lineStart = true;
            } else {
                lineStart = false;
            }
        }
        return false;
    }

    private void resetCorruptJarMarkerDetector() {
        corruptJarMarkerMatchLength = 0;
        lineStart = true;
    }

    @Override
    public void close() {
        if (outputFile.exists()) {
            outputFile.delete();
        }
        scheduler.shutdown(); // 停止定时器
    }
}
