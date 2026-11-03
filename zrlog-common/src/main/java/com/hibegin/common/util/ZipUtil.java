package com.hibegin.common.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    public static void unZip(String src, String target) throws IOException {
        File targetDir = new File(target).getCanonicalFile();
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Create unzip target folder failed: " + targetDir);
        }
        try (ZipFile zip = new ZipFile(src)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                File file = new File(targetDir, entry.getName()).getCanonicalFile();
                if (!file.toPath().startsWith(targetDir.toPath())) {
                    throw new IOException("Unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!file.exists() && !file.mkdirs()) {
                        throw new IOException("Create unzip folder failed: " + file);
                    }
                    continue;
                }
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Create unzip folder failed: " + parent);
                }
                try (FileOutputStream fout = new FileOutputStream(file)) {
                    zip.getInputStream(entry).transferTo(fout);
                }
            }
        }
    }

    public static void inZip(List<File> files, String basePath, String target) throws IOException {
        List<File> cfiles = new ArrayList<>(files);
        for (File file : cfiles) {
            if (file.isDirectory()) {
                FileUtils.getAllFiles(file.toString(), files);
            }
        }
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target));
        for (File file : files) {
            ZipEntry entry;
            byte[] b = new byte[1024];
            int len;
            if (file.isFile()) {
                entry = new ZipEntry(file.toString().substring(basePath.length()));
                zos.putNextEntry(entry);
                InputStream is = new BufferedInputStream(new FileInputStream(file));
                while ((len = is.read(b, 0, b.length)) != -1) {
                    zos.write(b, 0, len);
                }
                is.close();
            }
        }
        zos.close();
    }

    public static void main(String[] args) throws IOException {
        unZip("/Users/xiaochun/git/zrlog/conf/plugins.zip", "/tmp/conf");
    }
}
