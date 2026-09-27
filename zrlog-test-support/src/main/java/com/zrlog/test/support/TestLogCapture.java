package com.zrlog.test.support;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class TestLogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final boolean previousUseParentHandlers;
    private final List<LogRecord> records = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private TestLogCapture(Class<?> loggerClass) {
        this.logger = Logger.getLogger(loggerClass.getName());
        this.previousLevel = logger.getLevel();
        this.previousUseParentHandlers = logger.getUseParentHandlers();
        this.handler.setLevel(Level.ALL);
        this.logger.addHandler(handler);
        this.logger.setLevel(Level.ALL);
        this.logger.setUseParentHandlers(false);
    }

    public static TestLogCapture forClass(Class<?> loggerClass) {
        return new TestLogCapture(loggerClass);
    }

    public boolean contains(Level level, String messagePart) {
        return records.stream()
                .anyMatch(record -> record.getLevel().equals(level)
                        && record.getMessage() != null
                        && record.getMessage().contains(messagePart));
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
        logger.setUseParentHandlers(previousUseParentHandlers);
    }
}
