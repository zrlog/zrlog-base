package com.zrlog.common.updater;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;
import com.zrlog.util.ArgsParser;
import com.zrlog.util.BlogBuildInfoUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.logging.Logger;

public class NativeImageUpdater implements Updater {

    private final String[] args;
    private final File execFile;

    private static final Logger LOGGER = LoggerUtil.getLogger(NativeImageUpdater.class);

    public NativeImageUpdater(String[] args, File execFile) {
        this.args = args;
        this.execFile = execFile.toPath().toAbsolutePath().normalize().toFile();
    }

    public File execFile() {
        return execFile;
    }

    @Override
    public UpdaterTypeEnum getType() {
        return UpdaterTypeEnum.NATIVE_IMAGE;
    }

    String buildExec(File root, File updateTemp, long oldPid) {
        StringJoiner shells = new StringJoiner("\n");
        shells.add("#!/bin/sh");
        shells.add("set -e");
        shells.add("trap 'result=$?; if [ \"$result\" -ne 0 ]; then echo \"ZrLog upgrade failed (exit $result)\" >&2; fi' 0");
        // The running native executable must be released before it can be overwritten.
        shells.add("while kill -0 " + oldPid + " 2>/dev/null; do sleep 1; done");
        shells.add("cd " + shellQuote(root.getAbsolutePath()));
        shells.add("cp -R " + shellQuote(updateTemp.getAbsolutePath() + "/.") + " "
                + shellQuote(root.getAbsolutePath()));
        File newBinFile = new File(root, "zrlog").toPath().toAbsolutePath().normalize().toFile();
        if (!newBinFile.equals(execFile)) {
            shells.add("mv " + shellQuote(newBinFile.toString()) + " " + shellQuote(execFile.toString()));
        }
        shells.add("chmod a+x " + shellQuote(execFile.toString()));
        shells.add("rm -rf " + shellQuote(updateTemp.getAbsolutePath()));
        shells.add("echo 'Starting upgraded ZrLog'");
        shells.add("exec " + buildStartExec(false));
        return shells + "\n";
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String buildStartExec(boolean windows) {
        StringJoiner cmdArgs = new StringJoiner(" ");
        cmdArgs.add(windows ? "\"" + execFile + "\"" : shellQuote(execFile.toString()));
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                continue;
            }
            cmdArgs.add(windows ? "\"" + arg + "\"" : shellQuote(arg));
        }
        cmdArgs.add("--port=" + ArgsParser.getPort(args));
        return cmdArgs.toString();
    }

    private String buildWindowsBatExec() {
        StringJoiner shells = new StringJoiner("\n");
        String zipBinName = "zrlog.exe";
        shells.add("timeout /t 1 /nobreak > nul");
        shells.add("move " + getUpdateTempPath() + "\\*" + " " + PathUtil.getRootPath());
        File newBinFile = new File(PathUtil.getRootPath() + "\\" + zipBinName);
        //try update exec name
        if (!Objects.equals(newBinFile.toString(), execFile.toString())) {
            shells.add("move " + newBinFile + " " + execFile);
        }
        shells.add(buildStartExec(true));
        return shells.toString();
    }

    private ProcessBuilder buildUpgradeProcess(Version upgradeVersion) throws IOException {
        StringJoiner stringJoiner = new StringJoiner("-");
        stringJoiner.add("upgrade");
        if (Objects.nonNull(upgradeVersion) && StringUtils.isNotEmpty(upgradeVersion.getVersion())) {
            stringJoiner.add(upgradeVersion.getVersion());
        }
        if (Objects.nonNull(upgradeVersion) && StringUtils.isNotEmpty(upgradeVersion.getBuildId())) {
            stringJoiner.add(upgradeVersion.getBuildId());
        }
        stringJoiner.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String fileName = stringJoiner.toString();
        Files.createDirectories(new File(PathUtil.getTempPath()).toPath());
        boolean windows = BlogBuildInfoUtil.getFileArch().startsWith("Windows");
        File tempUpgradeFile = new File(PathUtil.getTempPath(), fileName + (windows ? ".bat" : ".sh"));
        IOUtil.writeStrToFile(windows ? buildWindowsBatExec()
                : buildExec(new File(PathUtil.getRootPath()), getUpdateTempPath(), ProcessHandle.current().pid()),
                tempUpgradeFile);
        File logFile = new File(PathUtil.getTempPath(), fileName + ".log");
        ProcessBuilder process = windows
                ? new ProcessBuilder("cmd", "/c", tempUpgradeFile.getAbsolutePath())
                : new ProcessBuilder("nohup", "sh", tempUpgradeFile.getAbsolutePath());
        process.directory(new File(PathUtil.getRootPath()));
        process.redirectInput(new File(windows ? "NUL" : "/dev/null"));
        process.redirectErrorStream(true);
        process.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        LOGGER.info("ZrLog upgrade script: " + tempUpgradeFile + "; output: " + logFile);
        return process;
    }

    @Override
    public void restartProcessAsync(Version upgradeVersion) {
        final ProcessBuilder process;
        try {
            process = buildUpgradeProcess(upgradeVersion);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare native upgrade", e);
        }
        RestartProcessRunner.restartAsync(process::start);
    }

    @Override
    public String getUnzipPath() {
        return getUpdateTempPath().getPath();
    }
}
