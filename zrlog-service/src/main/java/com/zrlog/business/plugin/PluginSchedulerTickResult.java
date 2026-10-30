package com.zrlog.business.plugin;

import java.util.ArrayList;
import java.util.List;

/** Narrow host protocol: only the results of this tick, never plugin task configuration. */
public final class PluginSchedulerTickResult {
    public String triggerId;
    public int executedCount;
    public int skippedCount;
    public int failedCount;
    public int uncertainCount;
    public List<String> runIds = new ArrayList<>();
    public List<Child> children;

    public static final class Child {
        public String automationId;
        public String name;
        public String pluginId;
        public String capabilityKey;
        public String runId;
        public String status;
        public String reason;
        public String errorMessage;
        public Long startedAt;
        public Long finishedAt;
    }
}
