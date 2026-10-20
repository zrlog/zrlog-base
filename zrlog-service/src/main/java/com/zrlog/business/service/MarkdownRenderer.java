package com.zrlog.business.service;

/**
 * Optional server-side Markdown conversion, supplied by a runtime provider.
 */
@FunctionalInterface
public interface MarkdownRenderer {

    /**
     * Converts Markdown using the provider's rendering rules.
     *
     * @param markdown Markdown source
     * @return rendered HTML, or {@code null} when rendering is unavailable
     */
    String render(String markdown);
}
