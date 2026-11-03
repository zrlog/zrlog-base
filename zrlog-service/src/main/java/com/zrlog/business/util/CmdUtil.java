package com.zrlog.business.util;

import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.RuntimeMessage;
import com.hibegin.common.util.SystemType;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CmdUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(CmdUtil.class);

    public static int findPidByPort(int port) {
        String cmdStr;
        if (File.separatorChar != '\\') {
            cmdStr = "netstat -anp";
        } else {
            cmdStr = "netstat -atu";
        }
        String content = sendCmd(cmdStr);
        return findPidByPort(content, port, RuntimeMessage.getSystemRm());
    }

    static int findPidByPort(String content, int port, SystemType systemType) {
        if (content != null && !content.isEmpty() && content.split("\n").length > 1) {
            String[] cons = content.split("\n");
            for (int i = 2; i < cons.length; i++) {
                content = cons[i];
                StringBuilder nContext = new StringBuilder();
                // format
                for (int j = 0; j < content.length(); j++) {
                    if (j > 0) {
                        if (content.charAt(j) == ' '
                                && content.charAt(j) == content.charAt(j - 1)) {
                            continue;
                        }
                    }
                    nContext.append(content.charAt(j));
                }

                String[] strings = nContext.toString().trim().split(" ");
                int flag = 1;
                if (systemType == SystemType.LINUX) {
                    flag = 3;
                }
                if (strings.length <= flag) {
                    continue;
                }
                String[] ipPort = strings[flag].split(":");
                if (ipPort.length >= 2) {
                    int tempPort;
                    try {
                        tempPort = Integer.parseInt(ipPort[ipPort.length - 1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (port == tempPort) {
                        LOGGER.fine(nContext.toString());
                        if (systemType == SystemType.LINUX
                                && strings.length > 6
                                && strings[6].contains("/")) {
                            String procMsg = strings[6];
                            return Integer.parseInt(procMsg.substring(0, strings[6].indexOf("/")));
                        }
                    }
                }
            }
        }
        return -1;
    }

    public static void killProcByPid(int pid) {
        sendCmd("kill -9 ", pid + "");
    }

    public static void killProcByPort(int port) {
        killProcByPort(port, CmdUtil::findPidByPort, CmdUtil::killProcByPid);
    }

    static void killProcByPort(int port, PidFinder pidFinder, PidKiller pidKiller) {
        try {
            long start = System.currentTimeMillis();
            int pid = pidFinder.find(port);
            if (pid != -1) {
                pidKiller.kill(pid);
            }
            LOGGER.fine("Kill process by port cost " + (System.currentTimeMillis() - start) + "ms");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Kill process by port failed: " + port, e);
        }
    }

    public static String sendCmd(String cmd, String... args) {
        InputStream[] in = getCmdInputStream(cmd, args);
        if (in[0] != null) {
            return IOUtil.getStringInputStream(in[0]);
        }
        if (in[1] != null) {
            return IOUtil.getStringInputStream(in[1]);
        }
        return "";
    }

    public static InputStream[] getCmdInputStream(String cmd, String... args) {
        Process pr = getProcess(cmd, args);
        BufferedInputStream[] bufferedInputStreams = new BufferedInputStream[2];
        if (pr != null) {
            bufferedInputStreams[0] = new BufferedInputStream(pr.getInputStream());
            bufferedInputStreams[1] = new BufferedInputStream(pr.getErrorStream());
            return bufferedInputStreams;
        }
        return bufferedInputStreams;
    }

    public static Process getProcess(String cmd, String... args) {
        if (args != null) {
            cmd += " ";
            StringBuilder cmdBuilder = new StringBuilder(cmd);
            for (Object str : args) {
                cmdBuilder.append(str).append(" ");
            }
            cmd = cmdBuilder.toString();
        }
        Runtime rt = Runtime.getRuntime();
        try {
            return rt.exec(cmd);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Exec command failed: " + cmd, e);
        }
        return null;
    }

    @FunctionalInterface
    interface PidFinder {
        int find(int port);
    }

    @FunctionalInterface
    interface PidKiller {
        void kill(int pid);
    }
}
