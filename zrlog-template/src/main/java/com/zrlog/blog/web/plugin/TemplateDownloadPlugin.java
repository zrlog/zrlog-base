package com.zrlog.blog.web.plugin;

import com.hibegin.common.BaseLockObject;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.zrlog.business.service.TemplateInfoHelper;
import com.zrlog.business.template.util.TemplateDownloadUtils;
import com.zrlog.common.Constants;
import com.zrlog.plugin.IPlugin;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.logging.Logger;

public class TemplateDownloadPlugin extends BaseLockObject implements IPlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger(TemplateDownloadPlugin.class);

    private boolean started;

    @Override
    public boolean start() {
        if (started) {
            return true;
        }
        started = true;
        precheckTemplate(Constants.getPublicWebSiteInfo().getTemplate());
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
