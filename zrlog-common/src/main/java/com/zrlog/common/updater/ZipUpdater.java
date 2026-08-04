package com.zrlog.common.updater;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.Constants;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;
import com.zrlog.util.ArgsParser;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ZipUpdater implements Updater {

    private static final Logger LOGGER = LoggerUtil.getLogger(ZipUpdater.class);
    private final String[] args;
    private final File execFile;

    public ZipUpdater(String[] args, File execFile) {
        this.args = args;
        this.execFile = execFile;
    }

    @Override
    public File execFile() {
        return execFile;
    }

    @Override
    public UpdaterTypeEnum getType() {
        return UpdaterTypeEnum.ZIP;
    }

    List<String> buildRestartCommand() {
        List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        // 构造完整的命令
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add("java");
        cmdArgs.addAll(inputArguments.stream().filter(e -> !e.startsWith("-javaagent")).collect(Collectors.toList()));
        cmdArgs.add("-jar");
        cmdArgs.add(execFile.toString());
        cmdArgs.addAll(Arrays.asList(args));
        if (cmdArgs.stream().noneMatch(e -> e.startsWith("--port="))) {
            int port = Objects.nonNull(Constants.zrLogConfig) ? Constants.zrLogConfig.getServerConfig().getPort() :
                    ArgsParser.getPort(args);
            cmdArgs.add("--port=" + port);
        }
        return cmdArgs;
    }

    public void restartProcessAsync(Version upgradeVersion) {
        List<String> cmdArgs = buildRestartCommand();
        LOGGER.info("ZrLog file updated. exec cmd\n" + String.join(" ", cmdArgs));
        RestartProcessRunner.restartAsync(() -> Runtime.getRuntime().exec(cmdArgs.toArray(new String[0])));
    }

    @Override
    public String getUnzipPath() {
        return PathUtil.getRootPath();
    }

}
