package com.zrlog.data.dto;

import com.hibegin.common.dao.ResultBeanUtils;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArticleExtensionsAdapterTest {

    @Test
    public void shouldReadDatabaseJsonTextAsExtensionObject() {
        Map<String, Object> row = new HashMap<>();
        row.put("extensions", "{\"catalog\":{\"featured\":true,\"labels\":[\"a\",\"b\"]}}");

        ArticleBasicDTO article = ResultBeanUtils.convert(row, ArticleBasicDTO.class);

        Map<String, Object> catalog = (Map<String, Object>) article.getExtensions().get("catalog");
        assertEquals(Boolean.TRUE, catalog.get("featured"));
        assertEquals(2, ((java.util.List<?>) catalog.get("labels")).size());
    }

    @Test
    public void shouldTreatMissingOrMalformedExtensionsAsEmpty() {
        ArticleBasicDTO missing = ResultBeanUtils.convert(Collections.emptyMap(), ArticleBasicDTO.class);
        ArticleBasicDTO malformed = ResultBeanUtils.convert(
                Collections.singletonMap("extensions", "{invalid"), ArticleBasicDTO.class);

        assertTrue(missing.getExtensions().isEmpty());
        assertTrue(malformed.getExtensions().isEmpty());
    }
}
