package com.zrlog.web;

import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class WebSetupLoaderTest {

    @Test
    public void disablingHostCascadesButDisablingAiLeavesAccessAvailable() {
        FakeProvider admin = new FakeProvider("admin", 100);
        FakeProvider ai = dependent("admin-ai", 140, "admin");
        FakeProvider access = dependent("admin-access", 150, "admin");
        FakeProvider mcp = dependent("admin-mcp", 160, "admin-access");
        List<WebSetupProvider> providers = List.of(admin, ai, access, mcp);
        assertTrue(WebSetupLoader.create(null, providers, Set.of("admin"), true).isEmpty());
        assertEquals(3, WebSetupLoader.create(null, providers, Set.of("admin-ai"), true).size());
        assertEquals(2, WebSetupLoader.create(null, providers, Set.of("admin-access"), true).size());
        assertEquals(3, WebSetupLoader.create(null, providers, Set.of("admin-mcp"), true).size());
    }

    @Test
    public void missingOrMisorderedDependencyCannotStartDependentRoutes() {
        FakeProvider child = dependent("admin-ai", 140, "admin");
        assertTrue(WebSetupLoader.create(null, List.of(child), Set.of(), false).isEmpty());
        assertThrows(IllegalStateException.class, () -> WebSetupLoader.create(null, List.of(child), Set.of(), true));
        assertThrows(IllegalStateException.class, () -> WebSetupLoader.create(null,
                List.of(child, new FakeProvider("admin", 150)), Set.of(), true));
    }

    private static FakeProvider dependent(String name, int order, String dependency) {
        return new FakeProvider(name, order) {
            @Override public Set<String> requiredModules() { return Set.of(dependency); }
        };
    }

    @Test
    public void disabledProviderIsNeverCreatedAndNamesAreExact() {
        WebSetup enabled = () -> { };
        WebSetupProvider disabled = new FakeProvider("admin-ai", 10) {
            @Override public WebSetup create(WebSetupContext context) {
                throw new AssertionError("Disabled feature must not initialize");
            }
        };
        WebSetupProvider mcp = new FakeProvider("admin-mcp", 20) {
            @Override public WebSetup create(WebSetupContext context) { return enabled; }
        };
        assertEquals(List.of(enabled), WebSetupLoader.create(null, List.of(disabled, mcp), Set.of("admin-ai"), true));
        assertEquals(List.of(enabled), WebSetupLoader.create(null, List.of(mcp), Set.of("admin"), true));
    }

    @Test
    public void providerCreationFailureHonorsStrictMode() {
        WebSetupProvider broken = new FakeProvider("broken", 10) {
            @Override public WebSetup create(WebSetupContext context) { throw new IllegalArgumentException("broken"); }
        };
        WebSetupProvider empty = new FakeProvider("empty", 20) {
            @Override public WebSetup create(WebSetupContext context) { return null; }
        };
        assertTrue(WebSetupLoader.create(null, List.of(broken, empty), Set.of(), false).isEmpty());
        assertThrows(IllegalStateException.class, () -> WebSetupLoader.create(null, List.of(broken), Set.of(), true));
        assertThrows(IllegalStateException.class, () -> WebSetupLoader.create(null, List.of(empty), Set.of(), true));
    }

    @Test
    public void shouldParseDisableModules() {
        Set<String> modules = WebSetupLoader.parseDisableModules(" admin,blog,, install ");

        assertEquals(new HashSet<>(Arrays.asList("admin", "blog", "install")), modules);
        assertTrue(WebSetupLoader.parseDisableModules(null).isEmpty());
    }

    @Test
    public void shouldSortAndDeduplicateWebSetupProviders() {
        FakeProvider lateAdmin = new FakeProvider("admin", 20);
        FakeProvider earlyAdmin = new FakeProvider("admin", 5);
        FakeProvider blog = new FakeProvider("blog", 10);
        FakeProvider unnamed = new FakeProvider(" ", 1);

        List<WebSetupProvider> providers = WebSetupLoader.normalizeWebSetupProviders(
                Arrays.asList(lateAdmin, blog, unnamed, earlyAdmin));

        assertEquals(2, providers.size());
        assertSame(earlyAdmin, providers.get(0));
        assertSame(blog, providers.get(1));
    }

    @Test
    public void shouldSortProvidersByOrderThenName() {
        FakeProvider blog = new FakeProvider("blog", 10);
        FakeProvider admin = new FakeProvider("admin", 10);
        FakeProvider install = new FakeProvider("install", 20);

        List<WebSetupProvider> providers = WebSetupLoader.normalizeWebSetupProviders(Arrays.asList(install, blog, admin));

        assertEquals(Arrays.asList(admin, blog, install), providers);
    }

    @Test
    public void shouldSkipNullProviderNameWhenNormalizing() {
        List<WebSetupProvider> providers = WebSetupLoader.normalizeWebSetupProviders(Arrays.asList(
                new FakeProvider(null, 10),
                new FakeProvider("admin", 20)));

        assertEquals(1, providers.size());
        assertEquals("admin", providers.get(0).name());
    }

    @Test
    public void shouldParseStrictMode() {
        assertTrue(WebSetupLoader.parseStrictMode("true"));
        assertTrue(WebSetupLoader.parseStrictMode(" TRUE "));
        assertTrue(WebSetupLoader.parseStrictMode("1"));
        assertTrue(WebSetupLoader.parseStrictMode("yes"));
        assertTrue(WebSetupLoader.parseStrictMode(" Yes "));
        assertFalse(WebSetupLoader.parseStrictMode("false"));
        assertFalse(WebSetupLoader.parseStrictMode(null));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailStrictModeOnDuplicatedWebSetupProvider() {
        WebSetupLoader.normalizeWebSetupProviders(Arrays.asList(
                new FakeProvider("admin", 10),
                new FakeProvider("admin", 20)), true);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldFailStrictModeOnUnnamedWebSetupProvider() {
        WebSetupLoader.normalizeWebSetupProviders(Arrays.asList(
                new FakeProvider(" ", 10),
                new FakeProvider("admin", 20)), true);
    }

    private static class FakeProvider implements WebSetupProvider {

        private final String name;
        private final int order;

        FakeProvider(String name, int order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public WebSetup create(WebSetupContext context) {
            return () -> {
            };
        }
    }
}
