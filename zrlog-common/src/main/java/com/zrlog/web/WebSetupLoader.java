package com.zrlog.web;

import com.hibegin.common.util.LoggerUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared discovery and startup selection for production and standalone development. */
public final class WebSetupLoader {

    private static final Logger LOGGER = LoggerUtil.getLogger(WebSetupLoader.class);

    private WebSetupLoader() { }

    public static List<WebSetup> load(WebSetupContext context) {
        return load(context, provider -> true);
    }

    public static List<WebSetup> load(WebSetupContext context, Predicate<WebSetupProvider> included) {
        boolean strictWebSetup = parseStrictMode(System.getenv("WEB_SETUP_STRICT"));
        List<WebSetupProvider> providers = loadWebSetupProviders(strictWebSetup).stream()
                .filter(included).collect(Collectors.toList());
        return create(context, providers, parseDisableModules(System.getenv("DISABLE_MODULES")), strictWebSetup);
    }

    static List<WebSetup> create(WebSetupContext webSetupContext, List<WebSetupProvider> webSetupProviders,
                                 Set<String> disableModules, boolean strictWebSetup) {
        List<WebSetup> webSetups = new ArrayList<>();
        LOGGER.info("Discovered web modules: " + names(webSetupProviders));
        if (strictWebSetup) {
            LOGGER.info("Web setup strict mode enabled");
        }
        if (!disableModules.isEmpty()) {
            LOGGER.info("Disabled web modules: " + disableModules);
        }
        List<String> loadedModules = new ArrayList<>();
        Set<String> unavailableModules = new HashSet<>(disableModules);
        for (WebSetupProvider webSetupProvider : webSetupProviders) {
            String name = providerName(webSetupProvider);
            if (disableModules.contains(name)
                    || webSetupProvider.requiredModules().stream().anyMatch(unavailableModules::contains)) {
                unavailableModules.add(name);
                LOGGER.info("Skip disabled web module: " + name);
                continue;
            }
            if (!loadedModules.containsAll(webSetupProvider.requiredModules())) {
                unavailableModules.add(name);
                handleSetupFailure(strictWebSetup, "Skip web module " + name
                        + ", required modules must be loaded earlier: " + webSetupProvider.requiredModules(), null);
                continue;
            }
            try {
                WebSetup webSetup = webSetupProvider.create(webSetupContext);
                if (Objects.nonNull(webSetup)) {
                    webSetups.add(webSetup);
                    loadedModules.add(name);
                } else {
                    unavailableModules.add(name);
                    handleSetupFailure(strictWebSetup,
                            "Skip web module " + name + ", provider returned null WebSetup", null);
                }
            } catch (Throwable e) {
                unavailableModules.add(name);
                handleSetupFailure(strictWebSetup, "Setup web module " + name + " failed", e);
            }
        }
        LOGGER.info("Loaded web modules: " + loadedModules);
        return webSetups;
    }

    static Set<String> parseDisableModules(String disableModulesEnv) {
        String value = Objects.requireNonNullElse(disableModulesEnv, "");
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    static List<WebSetupProvider> normalizeWebSetupProviders(List<WebSetupProvider> discoveredProviders) {
        return normalizeWebSetupProviders(discoveredProviders, false);
    }

    static List<WebSetupProvider> normalizeWebSetupProviders(List<WebSetupProvider> discoveredProviders, boolean strictWebSetup) {
        Map<String, WebSetupProvider> webSetupProviderMap = new LinkedHashMap<>();
        discoveredProviders.stream()
                .sorted(Comparator.comparingInt(WebSetupProvider::order).thenComparing(WebSetupLoader::providerName))
                .forEach(webSetupProvider -> {
                    String name = providerName(webSetupProvider);
                    if (name.isEmpty()) {
                        handleSetupFailure(strictWebSetup,
                                "Skip unnamed web module provider: " + webSetupProvider.getClass().getName(), null);
                        return;
                    }
                    if (webSetupProviderMap.containsKey(name)) {
                        handleSetupFailure(strictWebSetup,
                                "Skip duplicated web module provider " + webSetupProvider.getClass().getName()
                                        + ", module name: " + name + ", used provider: " + webSetupProviderMap.get(name).getClass().getName(),
                                null);
                        return;
                    }
                    webSetupProviderMap.put(name, webSetupProvider);
                });
        return new ArrayList<>(webSetupProviderMap.values());
    }

    private static List<WebSetupProvider> loadWebSetupProviders(boolean strictWebSetup) {
        List<WebSetupProvider> discoveredProviders = new ArrayList<>();
        ServiceLoader.load(WebSetupProvider.class).stream().forEach(provider -> {
            try {
                discoveredProviders.add(provider.get());
            } catch (Throwable e) {
                handleSetupFailure(strictWebSetup, "Load web module provider " + provider.type().getName() + " failed", e);
            }
        });
        return normalizeWebSetupProviders(discoveredProviders, strictWebSetup);
    }

    static boolean parseStrictMode(String value) {
        String flag = Objects.requireNonNullElse(value, "").trim();
        return "true".equalsIgnoreCase(flag) || "1".equals(flag) || "yes".equalsIgnoreCase(flag);
    }

    private static void handleSetupFailure(boolean strictWebSetup, String message, Throwable e) {
        if (strictWebSetup) {
            throw new IllegalStateException(message, e);
        }
        if (Objects.nonNull(e)) {
            LOGGER.log(Level.WARNING, message, e);
            return;
        }
        LOGGER.warning(message);
    }

    private static List<String> names(List<WebSetupProvider> webSetupProviders) {
        List<String> names = new ArrayList<>();
        for (WebSetupProvider webSetupProvider : webSetupProviders) {
            names.add(providerName(webSetupProvider));
        }
        return names;
    }

    private static String providerName(WebSetupProvider webSetupProvider) {
        return Objects.requireNonNullElse(webSetupProvider.name(), "").trim();
    }
}
