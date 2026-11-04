package com.zrlog.business.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.http.HttpUtil;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.ExecutionDeadline;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PluginCoreUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCoreUtils.class);
    private static final ReentrantLock DOWNLOAD_LOCK = new ReentrantLock();
    private static final long MAX_CORE_BYTES = 256L * 1024 * 1024;

    /** Unpacks the FaaS bundle cooperatively; each entry is published only after it is complete. */
    public static void unpackPlugins(Path archive, Path target, ExecutionDeadline deadline)
            throws IOException, InterruptedException {
        Path root = target.toAbsolutePath().normalize();
        deadline.requireRemainingMillis();
        Files.createDirectories(root);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                deadline.requireRemainingMillis();
                Path destination = root.resolve(entry.getName()).normalize();
                if (!destination.startsWith(root))
                    throw new IOException("Plugin archive entry escapes its directory");
                for (Path ancestor = destination;
                        ancestor != null && ancestor.startsWith(root);
                        ancestor = ancestor.getParent()) {
                    if (Files.isSymbolicLink(ancestor))
                        throw new IOException("Plugin archive entry crosses a symbolic link");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Path temporary =
                            Files.createTempFile(
                                    destination.getParent(), "plugin-unpack-", ".part");
                    try {
                        try (java.io.OutputStream output = Files.newOutputStream(temporary)) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                deadline.requireRemainingMillis();
                                output.write(buffer, 0, read);
                            }
                        }
                        deadline.requireRemainingMillis();
                        Files.move(
                                temporary,
                                destination,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static File getPluginFileName(String pluginsFolder) {
        return getPluginFileName(
                pluginsFolder, EnvKit.isNativeImage(), BlogBuildInfoUtil.getFileArch());
    }

    static File getPluginFileName(String pluginsFolder, boolean nativeImage, String fileArch) {
        if (!nativeImage) {
            return new File(pluginsFolder + "/plugin-core.jar");
        }
        if (fileArch.contains("Window")) {
            return new File(pluginsFolder + "/plugin-core-" + fileArch + ".exe");
        }
        return new File(pluginsFolder + "/plugin-core-" + fileArch + ".bin");
    }

    public static File tryDownloadPluginCoreFile(String pluginsFolder) {
        return tryDownloadPluginCoreFile(
                pluginsFolder, EnvKit.isNativeImage(), BlogBuildInfoUtil.getFileArch());
    }

    public static File tryDownloadPluginCoreFile(String pluginsFolder, ExecutionDeadline deadline) {
        return tryDownloadPluginCoreFile(
                pluginsFolder, EnvKit.isNativeImage(), BlogBuildInfoUtil.getFileArch(), deadline);
    }

    static File tryDownloadPluginCoreFile(
            String pluginsFolder, boolean nativeImage, String fileArch) {
        return tryDownloadPluginCoreFile(
                pluginsFolder, nativeImage, fileArch, ExecutionDeadline.afterMillis(600000));
    }

    static File tryDownloadPluginCoreFile(
            String pluginsFolder,
            boolean nativeImage,
            String fileArch,
            ExecutionDeadline deadline) {
        boolean locked = false;
        try {
            locked =
                    DOWNLOAD_LOCK.tryLock(deadline.requireRemainingMillis(), TimeUnit.MILLISECONDS);
            if (!locked)
                throw new HttpTimeoutException("Plugin-core download lock budget exhausted");
            return downloadPluginCoreFile(pluginsFolder, nativeImage, fileArch, deadline);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("download plugin core interrupted", interrupted);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "download plugin core error, " + failure.getMessage(), failure);
        } finally {
            if (locked) DOWNLOAD_LOCK.unlock();
        }
    }

    private static File downloadPluginCoreFile(
            String pluginsFolder, boolean nativeImage, String fileArch, ExecutionDeadline deadline)
            throws IOException, InterruptedException {
        deadline.requireRemainingMillis();
        File pluginCoreFile = getPluginFileName(pluginsFolder, nativeImage, fileArch);
        Path pluginCorePath = pluginCoreFile.toPath();
        Path downloadPath = null;
        try {
            Path pluginsPath = pluginCorePath.getParent();
            Files.createDirectories(pluginsPath);
            deleteStaleDownloadFiles(pluginsPath, pluginCoreFile.getName());
            if (isUsablePluginCore(pluginCoreFile, nativeImage, deadline)) {
                makeExecutable(pluginCoreFile);
                return pluginCoreFile;
            }
            String downloadPrefix =
                    pluginCoreFile.getName() + "." + ProcessHandle.current().pid() + ".";
            downloadPath = Files.createTempFile(pluginsPath, downloadPrefix, ".part");
            downloadPath.toFile().deleteOnExit();
            long cacheBuster = System.currentTimeMillis();
            String artifactUrl =
                    BlogBuildInfoUtil.getResourceDownloadUrl()
                            + "/plugin/core/"
                            + pluginCoreFile.getName();
            String withoutCacheDownloadUrl = artifactUrl + "?_t=" + cacheBuster;
            LOGGER.info(
                    pluginCoreFile.getName()
                            + " is missing or invalid; downloading from "
                            + withoutCacheDownloadUrl);
            Map<String, String> map = new HashMap<>();
            map.put("Cache-Control", "no-cache");
            String expectedNativeMd5 =
                    nativeImage
                            ? downloadNativeMd5(
                                    artifactUrl + ".md5?_t=" + cacheBuster,
                                    pluginCoreFile.getName(),
                                    map,
                                    deadline)
                            : null;
            HttpRequest.Builder download =
                    HttpRequest.newBuilder(URI.create(withoutCacheDownloadUrl)).GET();
            map.forEach(download::header);
            HttpUtil.getInstance()
                    .downloadToFile(download.build(), downloadPath, deadline, MAX_CORE_BYTES);
            PluginCoreArtifactValidator.validateDownloadedPluginCore(
                    downloadPath,
                    nativeImage,
                    pluginCoreFile.getName(),
                    expectedNativeMd5,
                    deadline);
            deadline.requireRemainingMillis();
            if (nativeImage) {
                publishNativeMd5(pluginCorePath, expectedNativeMd5);
            }
            publishDownloadedPluginCore(downloadPath, pluginCorePath);
            downloadPath = null;
            makeExecutable(pluginCoreFile);
        } finally {
            deleteDownloadFile(downloadPath);
        }
        return pluginCoreFile;
    }

    private static void makeExecutable(File file) throws IOException {
        if (file.getName().endsWith(".bin")
                && !file.canExecute()
                && !file.setExecutable(true, false)) {
            throw new IOException("Unable to make plugin-core executable");
        }
    }

    private static boolean isUsablePluginCore(
            File pluginCoreFile, boolean nativeImage, ExecutionDeadline deadline)
            throws IOException, InterruptedException {
        if (!pluginCoreFile.isFile() || pluginCoreFile.length() <= 0) {
            return false;
        }
        try {
            if (nativeImage) {
                PluginCoreArtifactValidator.validateNativeExecutable(
                        pluginCoreFile.toPath(), pluginCoreFile.getName(), deadline);
                PluginCoreArtifactValidator.validateLocalNativeMd5IfPresent(
                        pluginCoreFile.toPath(), deadline);
            } else {
                PluginCoreArtifactValidator.validateJar(pluginCoreFile.toPath(), deadline);
            }
            return true;
        } catch (HttpTimeoutException expired) {
            throw expired;
        } catch (IOException | SecurityException e) {
            LOGGER.warning(
                    pluginCoreFile.getName()
                            + " is invalid and will be downloaded again: "
                            + e.getMessage());
            return false;
        }
    }

    private static String downloadNativeMd5(
            String checksumUrl,
            String fileName,
            Map<String, String> headers,
            ExecutionDeadline deadline)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(checksumUrl)).GET();
        headers.forEach(request::header);
        var response = HttpUtil.getInstance().sendRequest(request.build(), deadline, 4096);
        if (response.statusCode() != 200) {
            throw new IOException(
                    "plugin core checksum download returned HTTP " + response.statusCode());
        }
        return PluginCoreArtifactValidator.parseNativeMd5(response.body(), fileName);
    }

    private static void publishNativeMd5(Path nativePath, String expectedMd5) throws IOException {
        Path checksumPath = PluginCoreArtifactValidator.nativeMd5Path(nativePath);
        Path temporaryChecksum =
                Files.createTempFile(
                        checksumPath.getParent(),
                        checksumPath.getFileName() + "." + ProcessHandle.current().pid() + ".",
                        ".part");
        try {
            Files.writeString(
                    temporaryChecksum, expectedMd5 + "  " + nativePath.getFileName() + "\n");
            Files.move(
                    temporaryChecksum,
                    checksumPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryChecksum);
        }
    }

    private static void deleteStaleDownloadFiles(Path pluginsPath, String pluginCoreFileName)
            throws IOException {
        String prefix = pluginCoreFileName + ".";
        try (java.util.stream.Stream<Path> paths = Files.list(pluginsPath)) {
            Path[] staleDownloads =
                    paths.filter(
                                    path -> {
                                        String fileName = path.getFileName().toString();
                                        return Files.isRegularFile(
                                                        path,
                                                        java.nio.file.LinkOption.NOFOLLOW_LINKS)
                                                && fileName.startsWith(prefix)
                                                && fileName.endsWith(".part");
                                    })
                            .toArray(Path[]::new);
            for (Path staleDownload : staleDownloads) {
                if (!belongsToLiveProcess(staleDownload, prefix)) {
                    Files.deleteIfExists(staleDownload);
                }
            }
        }
    }

    private static boolean belongsToLiveProcess(Path downloadPath, String prefix) {
        String fileName = downloadPath.getFileName().toString();
        int pidStart = prefix.length();
        if (fileName.startsWith("md5.", pidStart)) {
            pidStart += "md5.".length();
        }
        int pidEnd = fileName.indexOf('.', pidStart);
        if (pidEnd < 0) {
            return false;
        }
        try {
            long pid = Long.parseLong(fileName.substring(pidStart, pidEnd));
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void publishDownloadedPluginCore(Path downloadPath, Path pluginCorePath)
            throws IOException {
        Files.move(
                downloadPath,
                pluginCorePath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteDownloadFile(Path downloadPath) {
        if (downloadPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(downloadPath);
        } catch (IOException e) {
            LOGGER.warning(
                    "Unable to remove incomplete plugin-core download "
                            + downloadPath.getFileName());
        }
    }
}
