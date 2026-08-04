package com.zrlog.common.updater;

import com.zrlog.common.Constants;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.util.ArgsParser;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZipUpdaterTest {

    @Test
    public void shouldBuildRestartCommandBeforeGlobalConfigExists() {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = null;
            File execFile = new File("zrlog-starter.jar");
            ZipUpdater updater = new ZipUpdater(new String[0], execFile);

            List<String> command = updater.buildRestartCommand();

            assertEquals("java", command.get(0));
            assertTrue(command.contains(execFile.toString()));
            assertTrue(command.contains("--port=" + ArgsParser.getPort(new String[0])));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }
}
