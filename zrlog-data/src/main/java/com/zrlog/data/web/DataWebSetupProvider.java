package com.zrlog.data.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;

public final class DataWebSetupProvider implements WebSetupProvider {
    @Override
    public String name() { return "data"; }

    @Override
    public int order() { return 0; }

    @Override
    public WebSetup create(WebSetupContext context) {
        return new DataWebSetup(context.getZrLogConfig());
    }
}
