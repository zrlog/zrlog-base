package com.zrlog.common.resource;

import com.hibegin.common.util.IOUtil;
import java.io.File;
import java.util.Objects;

public class ZrLogResourceLoader {
    public static String read(String fullPath) {
        try {
            if (fullPath.startsWith("classpath:")) {
                return new String(
                        ZrLogResourceLoader.class
                                .getResourceAsStream(fullPath.split("classpath:")[1])
                                .readAllBytes());
            }
            return new String(IOUtil.getByteByFile(new File(fullPath)));
        } catch (Exception e) {
            throw new RuntimeException("无法文件: " + fullPath, e);
        }
    }

    public static boolean exists(String fullPath) {
        if (fullPath.startsWith("classpath:")) {
            return Objects.nonNull(
                    ZrLogResourceLoader.class.getResourceAsStream(fullPath.split("classpath:")[1]));
        }
        return new File(fullPath).exists();
    }
}
