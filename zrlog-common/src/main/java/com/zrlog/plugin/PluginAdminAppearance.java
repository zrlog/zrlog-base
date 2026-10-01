package com.zrlog.plugin;

/** Request-scoped appearance resolved by the authenticated admin host, never from browser headers. */
public final class PluginAdminAppearance {

    public static final String REQUEST_ATTRIBUTE = PluginAdminAppearance.class.getName();

    private final String theme;
    private final boolean darkMode;
    private final String colorPrimary;
    private final boolean compactMode;

    public PluginAdminAppearance(String theme, boolean darkMode, String colorPrimary, boolean compactMode) {
        this.theme = theme;
        this.darkMode = darkMode;
        this.colorPrimary = colorPrimary;
        this.compactMode = compactMode;
    }

    public String getTheme() { return theme; }
    public boolean isDarkMode() { return darkMode; }
    public String getColorPrimary() { return colorPrimary; }
    public boolean isCompactMode() { return compactMode; }
}
