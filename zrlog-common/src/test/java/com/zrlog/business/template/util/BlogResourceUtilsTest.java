package com.zrlog.business.template.util;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class BlogResourceUtilsTest {

    @Test
    public void shouldExposeResourceLookupsFromClasspathIndex() {
        BlogResourceUtils utils = BlogResourceUtils.getInstance();
        List<String> resources = utils.getResources();

        assertEquals(resources.contains("missing"), utils.existsResource("missing"));
        assertEquals(
                resources.stream().filter(e -> e.startsWith("/")).collect(Collectors.toList()),
                utils.searchResources("/"));
    }
}
