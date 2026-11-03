package com.zrlog.common.exception;

/**
 * Module initialization failures must not be treated as an unavailable or uninstalled database.
 */
public final class DatabaseInitializationException extends IllegalStateException {
    public DatabaseInitializationException(Throwable cause) {
        super("Database initialization failed; application startup stopped", cause);
    }
}
