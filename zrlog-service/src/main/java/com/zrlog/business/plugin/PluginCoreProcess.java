package com.zrlog.business.plugin;

import com.zrlog.util.ExecutionDeadline;

public interface PluginCoreProcess {

    /**
     * plugin-core 目前会监听2个TCP端口，一个用于和 ZrLog 通信，另一个用于和插件通信
     *
     * @param dbProperties
     * @param pluginJvmArgs
     * @param runtimePath    plugin-core 的运行目录
     * @param runTimeVersion zrlog 目前的版本
     * @param token
     * @return
     */
    int pluginServerStart(final String dbProperties, final String pluginJvmArgs,
                          final String runtimePath, final String runTimeVersion, String token);

    /** Starts a supervisor whose initial launch must finish inside this budget. */
    default int pluginServerStart(String dbProperties, String pluginJvmArgs, String runtimePath,
                                  String runTimeVersion, String token, ExecutionDeadline deadline) {
        return -1;
    }

    /** The HTTP readiness check confirms this particular launch, ending its startup deadline. */
    default boolean confirmStarted(int port, ExecutionDeadline deadline) { return false; }

    /** Cancels only an unconfirmed launch at this port; never stops a different/confirmed launch. */
    default void cancelStartup(int port, ExecutionDeadline deadline) { }

    /** Applies to the next launch, for both JVM and Native plugin-core. */
    default void setExternallyScheduled(boolean externallyScheduled) { }

    void stopPluginCore();
}
