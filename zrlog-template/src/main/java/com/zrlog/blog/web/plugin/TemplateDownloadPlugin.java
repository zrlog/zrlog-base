package com.zrlog.blog.web.plugin;

import com.hibegin.common.BaseLockObject;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.business.template.util.TemplateDownloadUtils;
import com.zrlog.plugin.IPlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class TemplateDownloadPlugin extends BaseLockObject implements IPlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger(TemplateDownloadPlugin.class);

    private boolean started;
    private final Supplier<String> configuredTemplate;

    public TemplateDownloadPlugin(Supplier<String> configuredTemplate) {
        this.configuredTemplate = Objects.requireNonNull(configuredTemplate);
    }

    @Override
    public boolean start() {
        if (started) {
            return true;
        }
        started = true;
        precheckTemplate(configuredTemplate.get());
        return true;
    }

    @Override
    public boolean autoStart() {
        return !EnvKit.isFaaSMode();
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    public void precheckTemplate(String templatePath) {
        if (TemplateInfoHelper.isDefaultTemplate(templatePath)) {
            return;
        }
        lock.lock();
        try {
            TemplateDownloadUtils.installByTemplateName(templatePath, false);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            LOGGER.warning("Reinstall template error -> " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean stop() {
        started = false;
        return true;
    }
}
