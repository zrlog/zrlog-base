package com.zrlog.business.plugin;

import com.zrlog.util.ExecutionDeadline;

public abstract class AbstractPluginCoreProcessHandle implements Runnable {

    abstract boolean close();

    boolean close(ExecutionDeadline deadline) { return false; }

    boolean confirmStarted(int port) { return false; }

    boolean isPendingAt(int port) { return false; }

    void cancelStartup() { }
}
