package com.zrlog.business.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpHandle;
import com.hibegin.common.util.http.handle.HttpStringHandle;
import com.zrlog.util.BlogBuildInfoUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.logging.Logger;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PluginCoreUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCoreUtils.class);
    private static final int ELF_MAGIC = 0x7f454c46;
    private static final int MACH_O_MAGIC = 0xfeedface;
    private static final int MACH_O_CIGAM = 0xcefaedfe;
    private static final int MACH_O_64_MAGIC = 0xfeedfacf;
    private static final int MACH_O_64_CIGAM = 0xcffaedfe;
    private static final int ELF_MACHINE_X86_64 = 62;
    private static final int ELF_MACHINE_ARM64 = 183;
    private static final int PE_MACHINE_X86_64 = 0x8664;
    private static final int PE_MACHINE_ARM64 = 0xaa64;
    private static final int MACH_O_CPU_X86_64 = 0x01000007;
    private static final int MACH_O_CPU_ARM64 = 0x0100000c;

    private static File getPluginFileName(String pluginsFolder) {
        return getPluginFileName(pluginsFolder, EnvKit.isNativeImage(), BlogBuildInfoUtil.getFileArch());
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
        return tryDownloadPluginCoreFile(pluginsFolder, EnvKit.isNativeImage(), BlogBuildInfoUtil.getFileArch());
    }

    static synchronized File tryDownloadPluginCoreFile(String pluginsFolder, boolean nativeImage, String fileArch) {
        File pluginCoreFile = getPluginFileName(pluginsFolder, nativeImage, fileArch);
        Path pluginCorePath = pluginCoreFile.toPath();
        Path downloadPath = null;
        boolean usablePluginCore = false;
        try {
            Path pluginsPath = pluginCorePath.getParent();
            Files.createDirectories(pluginsPath);
            deleteStaleDownloadFiles(pluginsPath, pluginCoreFile.getName());
            if (isUsablePluginCore(pluginCoreFile, nativeImage)) {
                usablePluginCore = true;
                return pluginCoreFile;
            }
            String downloadPrefix = pluginCoreFile.getName() + "." + ProcessHandle.current().pid() + ".";
            downloadPath = Files.createTempFile(pluginsPath, downloadPrefix, ".part");
            downloadPath.toFile().deleteOnExit();
            long cacheBuster = System.currentTimeMillis();
            String artifactUrl = BlogBuildInfoUtil.getResourceDownloadUrl() + "/plugin/core/"
                    + pluginCoreFile.getName();
            String withoutCacheDownloadUrl = artifactUrl + "?_t=" + cacheBuster;
            LOGGER.info(pluginCoreFile.getName() + " is missing or invalid; downloading from "
                    + withoutCacheDownloadUrl);
            Map<String, String> map = new HashMap<>();
            map.put("Cache-Control", "no-cache");
            String expectedNativeMd5 = nativeImage
                    ? downloadNativeMd5(artifactUrl + ".md5?_t=" + cacheBuster,
                    pluginCoreFile.getName(), map) : null;
            PluginCoreDownloadHandle downloadHandle = new PluginCoreDownloadHandle(downloadPath);
            HttpUtil.getInstance().sendGetRequest(withoutCacheDownloadUrl, new HashMap<>(), downloadHandle, map);
            downloadHandle.requireComplete();
            validateDownloadedPluginCore(downloadPath, nativeImage, pluginCoreFile.getName(),
                    expectedNativeMd5);
            if (nativeImage) {
                publishNativeMd5(pluginCorePath, expectedNativeMd5);
            }
            publishDownloadedPluginCore(downloadPath, pluginCorePath);
            downloadPath = null;
            usablePluginCore = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("download plugin core error, " + e.getMessage(), e);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("download plugin core error, " + e.getMessage(), e);
        } finally {
            deleteDownloadFile(downloadPath);
            if (usablePluginCore) {
                if (pluginCoreFile.getName().endsWith(".bin")) {
                    CmdUtil.sendCmd("chmod", "a+x", pluginCoreFile.toString());
                }
            }
        }
        return pluginCoreFile;
    }

    private static boolean isUsablePluginCore(File pluginCoreFile, boolean nativeImage) {
        if (!pluginCoreFile.isFile() || pluginCoreFile.length() <= 0) {
            return false;
        }
        try {
            if (nativeImage) {
                validateNativeExecutable(pluginCoreFile.toPath(), pluginCoreFile.getName());
                validateLocalNativeMd5IfPresent(pluginCoreFile.toPath());
            } else {
                validateJar(pluginCoreFile.toPath());
            }
            return true;
        } catch (IOException | SecurityException e) {
            LOGGER.warning(pluginCoreFile.getName() + " is invalid and will be downloaded again: "
                    + e.getMessage());
            return false;
        }
    }

    private static void validateDownloadedPluginCore(Path downloadPath, boolean nativeImage,
                                                     String targetFileName,
                                                     String expectedNativeMd5) throws IOException {
        if (!Files.isRegularFile(downloadPath) || Files.size(downloadPath) <= 0) {
            throw new IOException("downloaded plugin core is empty");
        }
        if (nativeImage) {
            validateNativeExecutable(downloadPath, targetFileName);
            requireMd5(downloadPath, expectedNativeMd5);
        } else {
            validateJar(downloadPath);
        }
    }

    private static String downloadNativeMd5(String checksumUrl, String fileName,
                                            Map<String, String> headers)
            throws IOException, InterruptedException, URISyntaxException {
        HttpStringHandle checksumHandle = new HttpStringHandle();
        HttpUtil.getInstance().sendGetRequest(checksumUrl, new HashMap<>(), checksumHandle, headers);
        if (checksumHandle.getStatusCode() != 200) {
            throw new IOException("plugin core checksum download returned HTTP "
                    + checksumHandle.getStatusCode());
        }
        return parseNativeMd5(checksumHandle.getT(), fileName);
    }

    private static String parseNativeMd5(String checksumText, String fileName) throws IOException {
        if (checksumText == null) {
            throw new IOException("plugin core checksum response is empty");
        }
        String normalized = checksumText.trim();
        String suffix = "  " + fileName;
        if (normalized.length() != 32 + suffix.length() || !normalized.endsWith(suffix)) {
            throw new IOException("plugin core checksum response has an invalid format");
        }
        String checksum = normalized.substring(0, 32).toLowerCase(Locale.ROOT);
        for (int index = 0; index < checksum.length(); index++) {
            char value = checksum.charAt(index);
            if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) {
                throw new IOException("plugin core checksum response has an invalid digest");
            }
        }
        return checksum;
    }

    private static void validateLocalNativeMd5IfPresent(Path nativePath) throws IOException {
        Path checksumPath = nativeMd5Path(nativePath);
        if (!Files.exists(checksumPath)) {
            return;
        }
        if (!Files.isRegularFile(checksumPath)) {
            throw new IOException("plugin core checksum is not a regular file");
        }
        String expectedMd5 = parseNativeMd5(Files.readString(checksumPath),
                nativePath.getFileName().toString());
        requireMd5(nativePath, expectedMd5);
    }

    private static void requireMd5(Path path, String expectedMd5) throws IOException {
        if (expectedMd5 == null || !expectedMd5.equals(md5(path))) {
            throw new IOException("plugin core checksum mismatch");
        }
    }

    private static String md5(Path path) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = Files.newInputStream(path)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder value = new StringBuilder(32);
        for (byte part : digest.digest()) {
            value.append(String.format(Locale.ROOT, "%02x", part & 0xff));
        }
        return value.toString();
    }

    private static Path nativeMd5Path(Path nativePath) {
        return nativePath.resolveSibling(nativePath.getFileName() + ".md5");
    }

    private static void publishNativeMd5(Path nativePath, String expectedMd5) throws IOException {
        Path checksumPath = nativeMd5Path(nativePath);
        Path temporaryChecksum = Files.createTempFile(checksumPath.getParent(),
                checksumPath.getFileName() + "." + ProcessHandle.current().pid() + ".", ".part");
        try {
            Files.writeString(temporaryChecksum, expectedMd5 + "  " + nativePath.getFileName() + "\n");
            Files.move(temporaryChecksum, checksumPath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryChecksum);
        }
    }

    private static void validateNativeExecutable(Path path, String targetFileName) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long fileSize = file.length();
            if (fileSize < 4) {
                throw new IOException("native plugin core is too short");
            }
            boolean windowsExecutable = targetFileName.startsWith("plugin-core-Windows-")
                    && targetFileName.endsWith(".exe");
            boolean linuxExecutable = targetFileName.startsWith("plugin-core-Linux-")
                    && targetFileName.endsWith(".bin");
            boolean macExecutable = targetFileName.startsWith("plugin-core-Darwin-")
                    && targetFileName.endsWith(".bin");
            int magic = buffer(file, 0, 4, ByteOrder.BIG_ENDIAN).getInt();
            if (windowsExecutable && (magic >>> 16) == 0x4d5a) {
                validatePortableExecutable(file, fileSize, targetFileName);
            } else if (linuxExecutable && magic == ELF_MAGIC) {
                validateElf(file, fileSize, targetFileName);
            } else if (macExecutable && (magic == MACH_O_MAGIC || magic == MACH_O_CIGAM
                    || magic == MACH_O_64_MAGIC || magic == MACH_O_64_CIGAM)) {
                validateMachO(file, fileSize, magic, targetFileName);
            } else {
                throw new IOException("native plugin core format does not match its target platform");
            }
        }
    }

    private static void validateElf(RandomAccessFile file, long fileSize, String targetFileName) throws IOException {
        ByteBuffer identity = buffer(file, 0, 16, ByteOrder.BIG_ENDIAN);
        int fileClass = Byte.toUnsignedInt(identity.get(4));
        int dataEncoding = Byte.toUnsignedInt(identity.get(5));
        if ((fileClass != 1 && fileClass != 2) || (dataEncoding != 1 && dataEncoding != 2)
                || Byte.toUnsignedInt(identity.get(6)) != 1) {
            throw new IOException("native plugin core has an invalid ELF identity");
        }
        boolean is64Bit = fileClass == 2;
        ByteOrder order = dataEncoding == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        int minimumHeaderSize = is64Bit ? 64 : 52;
        ByteBuffer header = buffer(file, 0, minimumHeaderSize, order);
        requireTargetArchitecture(targetFileName, Short.toUnsignedInt(header.getShort(18)),
                ELF_MACHINE_X86_64, ELF_MACHINE_ARM64);
        long programHeaderOffset = is64Bit ? positiveLong(header.getLong(32), "ELF program header offset")
                : Integer.toUnsignedLong(header.getInt(28));
        int headerSize = Short.toUnsignedInt(header.getShort(is64Bit ? 52 : 40));
        int programEntrySize = Short.toUnsignedInt(header.getShort(is64Bit ? 54 : 42));
        int programEntryCount = Short.toUnsignedInt(header.getShort(is64Bit ? 56 : 44));
        int minimumProgramEntrySize = is64Bit ? 56 : 32;
        if (headerSize < minimumHeaderSize || programEntrySize < minimumProgramEntrySize
                || programEntryCount == 0 || programEntryCount == 0xffff) {
            throw new IOException("native plugin core has an invalid ELF program table");
        }
        requireRange(programHeaderOffset, (long) programEntrySize * programEntryCount,
                fileSize, "ELF program table");
        for (int index = 0; index < programEntryCount; index++) {
            long entryOffset = programHeaderOffset + (long) index * programEntrySize;
            ByteBuffer entry = buffer(file, entryOffset, minimumProgramEntrySize, order);
            long segmentOffset = is64Bit ? positiveLong(entry.getLong(8), "ELF segment offset")
                    : Integer.toUnsignedLong(entry.getInt(4));
            long segmentSize = is64Bit ? positiveLong(entry.getLong(32), "ELF segment size")
                    : Integer.toUnsignedLong(entry.getInt(16));
            requireRange(segmentOffset, segmentSize, fileSize, "ELF segment");
        }
    }

    private static void validatePortableExecutable(RandomAccessFile file, long fileSize,
                                                   String targetFileName) throws IOException {
        if (fileSize < 64) {
            throw new IOException("native plugin core has an incomplete DOS header");
        }
        ByteBuffer dosHeader = buffer(file, 0, 64, ByteOrder.LITTLE_ENDIAN);
        if (Short.toUnsignedInt(dosHeader.getShort(0)) != 0x5a4d) {
            throw new IOException("native plugin core has an invalid DOS signature");
        }
        long peOffset = Integer.toUnsignedLong(dosHeader.getInt(60));
        requireRange(peOffset, 24, fileSize, "PE header");
        ByteBuffer coffHeader = buffer(file, peOffset, 24, ByteOrder.LITTLE_ENDIAN);
        if (coffHeader.getInt(0) != 0x00004550) {
            throw new IOException("native plugin core has an invalid PE signature");
        }
        requireTargetArchitecture(targetFileName, Short.toUnsignedInt(coffHeader.getShort(4)),
                PE_MACHINE_X86_64, PE_MACHINE_ARM64);
        int sectionCount = Short.toUnsignedInt(coffHeader.getShort(6));
        int optionalHeaderSize = Short.toUnsignedInt(coffHeader.getShort(20));
        if (sectionCount == 0 || optionalHeaderSize < 2) {
            throw new IOException("native plugin core has an invalid PE section table");
        }
        long optionalHeaderOffset = peOffset + 24;
        requireRange(optionalHeaderOffset, optionalHeaderSize, fileSize, "PE optional header");
        int optionalMagic = Short.toUnsignedInt(buffer(file, optionalHeaderOffset, 2,
                ByteOrder.LITTLE_ENDIAN).getShort());
        if (optionalMagic != 0x10b && optionalMagic != 0x20b) {
            throw new IOException("native plugin core has an unsupported PE optional header");
        }
        long sectionTableOffset = optionalHeaderOffset + optionalHeaderSize;
        requireRange(sectionTableOffset, (long) sectionCount * 40, fileSize, "PE section table");
        for (int index = 0; index < sectionCount; index++) {
            ByteBuffer section = buffer(file, sectionTableOffset + (long) index * 40,
                    40, ByteOrder.LITTLE_ENDIAN);
            long rawSize = Integer.toUnsignedLong(section.getInt(16));
            long rawOffset = Integer.toUnsignedLong(section.getInt(20));
            if (rawSize > 0) {
                requireRange(rawOffset, rawSize, fileSize, "PE section");
            }
        }
    }

    private static void validateMachO(RandomAccessFile file, long fileSize, int magic,
                                      String targetFileName) throws IOException {
        boolean is64Bit = magic == MACH_O_64_MAGIC || magic == MACH_O_64_CIGAM;
        ByteOrder order = magic == MACH_O_MAGIC || magic == MACH_O_64_MAGIC
                ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        int headerSize = is64Bit ? 32 : 28;
        ByteBuffer header = buffer(file, 0, headerSize, order);
        requireTargetArchitecture(targetFileName, header.getInt(4),
                MACH_O_CPU_X86_64, MACH_O_CPU_ARM64);
        long commandCount = Integer.toUnsignedLong(header.getInt(16));
        long commandsSize = Integer.toUnsignedLong(header.getInt(20));
        if (commandCount == 0 || commandCount > Integer.MAX_VALUE) {
            throw new IOException("native plugin core has an invalid Mach-O command count");
        }
        requireRange(headerSize, commandsSize, fileSize, "Mach-O load commands");
        long commandOffset = headerSize;
        long commandsEnd = headerSize + commandsSize;
        int segmentCount = 0;
        for (long index = 0; index < commandCount; index++) {
            requireRange(commandOffset, 8, commandsEnd, "Mach-O load command");
            ByteBuffer commandHeader = buffer(file, commandOffset, 8, order);
            long command = Integer.toUnsignedLong(commandHeader.getInt(0));
            long commandSize = Integer.toUnsignedLong(commandHeader.getInt(4));
            if (commandSize < 8) {
                throw new IOException("native plugin core has an invalid Mach-O load command");
            }
            requireRange(commandOffset, commandSize, commandsEnd, "Mach-O load command");
            if (command == 0x1 || command == 0x19) {
                boolean segment64 = command == 0x19;
                int minimumSegmentSize = segment64 ? 72 : 56;
                if (commandSize < minimumSegmentSize) {
                    throw new IOException("native plugin core has an incomplete Mach-O segment");
                }
                ByteBuffer segment = buffer(file, commandOffset, minimumSegmentSize, order);
                long segmentOffset = segment64
                        ? positiveLong(segment.getLong(40), "Mach-O segment offset")
                        : Integer.toUnsignedLong(segment.getInt(32));
                long segmentSize = segment64
                        ? positiveLong(segment.getLong(48), "Mach-O segment size")
                        : Integer.toUnsignedLong(segment.getInt(36));
                requireRange(segmentOffset, segmentSize, fileSize, "Mach-O segment");
                segmentCount++;
            }
            commandOffset += commandSize;
        }
        if (commandOffset != commandsEnd || segmentCount == 0) {
            throw new IOException("native plugin core has an invalid Mach-O load command table");
        }
    }

    private static void requireTargetArchitecture(String targetFileName, int actualMachine,
                                                  int x86Machine, int armMachine) throws IOException {
        String normalizedTarget = targetFileName.toLowerCase(Locale.ROOT);
        final int expectedMachine;
        if (normalizedTarget.contains("-amd64.") || normalizedTarget.contains("-x86_64.")) {
            expectedMachine = x86Machine;
        } else if (normalizedTarget.contains("-arm64.") || normalizedTarget.contains("-aarch64.")) {
            expectedMachine = armMachine;
        } else {
            throw new IOException("native plugin core target architecture is unsupported");
        }
        if (actualMachine != expectedMachine) {
            throw new IOException("native plugin core architecture does not match its target");
        }
    }

    private static long positiveLong(long value, String label) throws IOException {
        if (value < 0) {
            throw new IOException(label + " exceeds the supported file size");
        }
        return value;
    }

    private static void requireRange(long offset, long length, long boundary, String label) throws IOException {
        if (offset < 0 || length < 0 || offset > boundary || length > boundary - offset) {
            throw new IOException(label + " extends beyond the executable");
        }
    }

    private static ByteBuffer buffer(RandomAccessFile file, long offset, int length,
                                     ByteOrder order) throws IOException {
        byte[] bytes = new byte[length];
        file.seek(offset);
        file.readFully(bytes);
        return ByteBuffer.wrap(bytes).order(order);
    }

    private static void validateJar(Path jarPath) throws IOException {
        try (JarFile ignored = new JarFile(jarPath.toFile())) {
            // Opening the archive validates its central directory before every entry is read below.
        }
        int fileEntries = 0;
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = Files.newInputStream(jarPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    fileEntries++;
                    while (zipInputStream.read(buffer) != -1) {
                        // Reading through the entry validates its declared size, stream and CRC.
                    }
                }
                zipInputStream.closeEntry();
            }
        }
        if (fileEntries == 0) {
            throw new IOException("plugin core JAR contains no file entries");
        }
    }

    private static void deleteStaleDownloadFiles(Path pluginsPath, String pluginCoreFileName) throws IOException {
        String prefix = pluginCoreFileName + ".";
        try (java.util.stream.Stream<Path> paths = Files.list(pluginsPath)) {
            Path[] staleDownloads = paths.filter(path -> {
                String fileName = path.getFileName().toString();
                return Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        && fileName.startsWith(prefix) && fileName.endsWith(".part");
            }).toArray(Path[]::new);
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

    private static void publishDownloadedPluginCore(Path downloadPath, Path pluginCorePath) throws IOException {
        Files.move(downloadPath, pluginCorePath, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteDownloadFile(Path downloadPath) {
        if (downloadPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(downloadPath);
        } catch (IOException e) {
            LOGGER.warning("Unable to remove incomplete plugin-core download " + downloadPath.getFileName());
        }
    }

    private static class PluginCoreDownloadHandle extends HttpHandle<File> {

        private final Path downloadPath;
        private int statusCode;
        private IOException failure;

        private PluginCoreDownloadHandle(Path downloadPath) {
            this.downloadPath = downloadPath;
        }

        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public boolean handle(HttpRequest request, HttpResponse<InputStream> response) {
            statusCode = response.statusCode();
            try (InputStream inputStream = response.body()) {
                if (statusCode != 200) {
                    return false;
                }
                long copied = Files.copy(inputStream, downloadPath, StandardCopyOption.REPLACE_EXISTING);
                OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
                if (contentLength.isPresent() && copied != contentLength.getAsLong()) {
                    failure = new IOException("incomplete plugin core download: expected "
                            + contentLength.getAsLong() + " bytes but received " + copied);
                    return false;
                }
                setT(downloadPath.toFile());
            } catch (IOException e) {
                failure = e;
            }
            return false;
        }

        private void requireComplete() throws IOException {
            if (statusCode != 200) {
                throw new IOException("plugin core download returned HTTP " + statusCode);
            }
            if (failure != null) {
                throw failure;
            }
            if (getT() == null) {
                throw new IOException("plugin core download did not produce a file");
            }
        }
    }
}
