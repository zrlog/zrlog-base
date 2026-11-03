package com.zrlog.business.type;

public enum TemplateType {
    STANDARD("template.properties", "setting/config-form.json"),
    NODE_JS("package.json", "_config.yml");

    private final String infoFile;
    private final String configFile;

    TemplateType(String infoFile, String configFile) {
        this.infoFile = infoFile;
        this.configFile = configFile;
    }

    public String getInfoFile() {
        return infoFile;
    }

    public String getConfigFile() {
        return configFile;
    }
}
