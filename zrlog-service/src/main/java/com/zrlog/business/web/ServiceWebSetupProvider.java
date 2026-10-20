package com.zrlog.business.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;

import java.util.Set;

public final class ServiceWebSetupProvider implements WebSetupProvider {
    @Override
    public String name() {
        return "services";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Set<String> requiredModules() {
        return Set.of("data");
    }

    @Override
    public WebSetup create(WebSetupContext context) {
        return new ServiceWebSetup(context);
    }
}
