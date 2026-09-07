package com.hibegin.common.util;


import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileUtils {

    public static void getAllFiles(String path, List<File> files) {
        getAllFilesBySuffix(path, null, files);
    }

    public static void getAllFilesBySuffix(String path, String suffix, List<File> files) {
        File file = new File(path);
        if (file.isDirectory()) {
            File[] fs = file.listFiles();
            if (fs != null) {
                for (File file2 : fs) {
                    if (file2.isDirectory()) {
                        getAllFilesBySuffix(file2.getPath(), suffix, files);
                    } else {
                        if (suffix != null) {
                            if (file2.toString().endsWith(suffix)) {
                                files.add(file2);
                            }
                        } else {
                            files.add(file2);
                        }
                    }
                }
            }
        } else {
            if (suffix != null) {
                if (file.toString().endsWith(suffix)) {
                    files.add(file);
                }
            } else {
                files.add(file);
            }
        }
    }

    public static void moveOrCopyFolder(String dest, String targetFolder, boolean isMove) {
        File f = new File(dest);
        if (f.isDirectory()) {
            File[] fs = new File(dest).listFiles();
            targetFolder = targetFolder + File.separator + f.getName();
            new File(targetFolder).mkdirs();
            if (fs != null) {
                for (File fl : fs) {
                    if (fl.isDirectory()) {
                        moveOrCopyFolder(fl.toString(), targetFolder, isMove);
                    } else {
                        moveOrCopyFile(fl.toString(), targetFolder + File.separator + fl.getName(), isMove);
                    }
                }
            }
        } else {
            moveOrCopyFile(f.toString(), targetFolder + File.separator + f.getName(), isMove);
        }
    }


    public static void moveOrCopyFile(String destFile, String targetFile, boolean isMove) {
        Path source = Path.of(destFile).toAbsolutePath().normalize();
        Path target = Path.of(targetFile).toAbsolutePath().normalize();
        try {
            if (source.equals(target) || (Files.exists(target) && Files.isSameFile(source, target))) {
                return;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!isMove) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            if (parent == null || Files.getFileStore(source).equals(Files.getFileStore(parent))) {
                moveReplacing(source, target);
                return;
            }
            copyThenDelete(source, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to " + (isMove ? "move" : "copy") + " file "
                    + source + " to " + target, e);
        }
    }

    static void copyThenDelete(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Target file has no parent directory: " + target);
        }
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + "-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            moveReplacing(temporary, target);
            Files.delete(source);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static boolean deleteFile(String file) {
        File f = new File(file);
        if (f.isDirectory()) {
            deleteDir(file);
            return !f.exists();
        } else {
            return new File(file).delete();
        }
    }

    private static void deleteDir(String filer) {
        File f = new File(filer);
        if (f.isDirectory()) {
            File[] fs = new File(filer).listFiles();
            if (fs != null && fs.length > 0) {
                for (File fl : fs) {
                    if (fl.isDirectory()) {
                        deleteDir(fl.toString());
                    } else {
                        fl.delete();
                    }
                }
            }
        }
        f.delete();
    }


    public static void tryResizeDiskSpace(String path, long currentLength, long maxLength) {
        List<File> fileList = new ArrayList<>();
        FileUtils.getAllFiles(path, fileList);
        long totalSize = currentLength;
        for (File tFile : fileList) {
            totalSize += tFile.length();
        }
        if (totalSize >= maxLength) {
            Collections.sort(fileList, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return Long.compare(o1.lastModified(), o2.lastModified());
                }
            });
            long needRemoveSize = totalSize - maxLength;
            for (File tFile : fileList) {
                needRemoveSize -= tFile.length();
                tFile.delete();
                if (needRemoveSize <= 0) {
                    break;
                }
            }
        }
    }

    public static String getFileExt(String fileName) {
        String name = new File(fileName).getName();
        if (!name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf(".") + 1);
    }

}
