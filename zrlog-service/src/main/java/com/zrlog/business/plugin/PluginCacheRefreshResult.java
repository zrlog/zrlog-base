package com.zrlog.business.plugin;

/** Internal plugin-core completion receipt for one host refresh. */
public final class PluginCacheRefreshResult {
    public String triggerId;
    public String cacheVersion;
    public boolean completed;
    public boolean outcomeUncertain;
    public int successCount;
    public int failedCount;
    public int unsupportedCount;
}
