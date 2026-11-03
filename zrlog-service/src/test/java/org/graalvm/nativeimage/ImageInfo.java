package org.graalvm.nativeimage;

public final class ImageInfo {

    private static boolean inImageRuntimeCode;

    private ImageInfo() {}

    public static boolean inImageRuntimeCode() {
        return inImageRuntimeCode;
    }

    public static void setInImageRuntimeCode(boolean value) {
        inImageRuntimeCode = value;
    }
}
