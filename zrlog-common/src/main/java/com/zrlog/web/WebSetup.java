package com.zrlog.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.plugin.Plugins;

/**
 * 模块配置
 * 及需要在对应 Interceptor 中自行通过路由进行拦截，和设置路由
 */
public interface WebSetup {

    void setup();

    /** Runs versioned schema preparation before any module initializes database-backed services. */
    default void beforeDatabaseReady(DataSourceWrapper dataSource) throws Exception {
    }

    /**
     * Runs after all modules have registered and the database is available, including after installation.
     */
    default void onDatabaseReady(DataSourceWrapper dataSource) throws Exception {
    }

    /** Stop module-owned work and release its database locks before the datasource closes. */
    default void stop() {
    }

    default Plugins getPlugins() {
        return new Plugins();
    }
}
