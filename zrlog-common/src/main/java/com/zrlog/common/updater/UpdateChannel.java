package com.zrlog.common.updater;

public enum UpdateChannel {

    RELEASE("release"), PREVIEW("preview");

    private final String value;

    UpdateChannel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
