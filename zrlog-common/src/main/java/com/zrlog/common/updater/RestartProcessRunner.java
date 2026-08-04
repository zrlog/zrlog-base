package com.zrlog.common.updater;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.common.Constants;

import java.util.logging.Logger;

class RestartProcessRunner {

    private static final Logger LOGGER = LoggerUtil.getLogger(RestartProcessRunner.class);

    private RestartProcessRunner() {
    }

    static void restartAsync(ProcessStarter processStarter) {
        Thread thread = new Thread(() -> {
            try {
                if (Constants.zrLogConfig != null) {
                    Constants.zrLogConfig.stop();
                }
                Thread.sleep(2000);
                processStarter.start();
                System.exit(0);
            } catch (Exception e) {
                LOGGER.warning("Restart error " + e.getMessage());
            }
        }, "zrlog-upgrade-restart");
        thread.setDaemon(false);
        thread.start();
    }

    interface ProcessStarter {

        void start() throws Exception;
    }
}
