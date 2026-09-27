package com.zrlog.web;

import java.util.Set;

public interface WebSetupProvider {

    String name();

    default int order() {
        return 100;
    }

    /** Required providers must have an earlier order. Disabling one disables its dependents. */
    default Set<String> requiredModules() {
        return Set.of();
    }

    WebSetup create(WebSetupContext context);
}
