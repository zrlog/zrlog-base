package com.zrlog.business.util;

import com.hibegin.common.dao.dto.Direction;
import com.hibegin.common.dao.dto.OrderBy;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.web.Controller;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ControllerUtilTest {

    private static Controller controller(Map<String, String[]> params) {
        return new Controller(request(params), null);
    }

    private static HttpRequest request(Map<String, String[]> params) {
        return (HttpRequest) Proxy.newProxyInstance(
                ControllerUtilTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("decodeParamMap".equals(method.getName())) {
                        return params;
                    }
                    if ("getParaToInt".equals(method.getName())) {
                        String[] values = params.get(args[0].toString());
                        if (values == null || values.length == 0) {
                            return args[1];
                        }
                        return Integer.parseInt(values[0]);
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    @Test
    public void shouldDefaultToIdDescendingWhenSortIsMissing() throws Exception {
        PageRequest pageRequest = ControllerUtil.getPageRequest(controller(new HashMap<>()));
        OrderBy orderBy = pageRequest.getSorts().get(0);

        assertEquals(1, pageRequest.getSorts().size());
        assertEquals("id", orderBy.getSortKey());
        assertEquals(Direction.DESC, orderBy.getDirection());
    }

    @Test
    public void shouldParseSortAndLegacySidxParams() throws Exception {
        Map<String, String[]> sortParams = new HashMap<>();
        sortParams.put("sort", new String[]{"title,asc", "created"});

        PageRequest sortPageRequest = ControllerUtil.getPageRequest(controller(sortParams));
        OrderBy titleOrder = sortPageRequest.getSorts().get(0);
        OrderBy createdOrder = sortPageRequest.getSorts().get(1);

        assertEquals("title", titleOrder.getSortKey());
        assertEquals(Direction.ASC, titleOrder.getDirection());
        assertEquals("created", createdOrder.getSortKey());
        assertEquals(Direction.DESC, createdOrder.getDirection());

        Map<String, String[]> legacyParams = new HashMap<>();
        legacyParams.put("sidx", new String[]{"last_update_date,desc?page=2"});

        PageRequest legacyPageRequest = ControllerUtil.getPageRequest(controller(legacyParams));
        OrderBy legacyOrder = legacyPageRequest.getSorts().get(0);

        assertEquals("last_update_date", legacyOrder.getSortKey());
        assertEquals(Direction.DESC, legacyOrder.getDirection());
    }

    @Test
    public void shouldExposeUnpagedRequest() {
        PageRequest pageRequest = ControllerUtil.unPageRequest();

        assertEquals(Long.valueOf(1), pageRequest.getPage());
        assertEquals(Long.valueOf(Long.MAX_VALUE), pageRequest.getSize());
    }

    @Test
    public void shouldBuildPageRequestFromControllerParams() {
        Map<String, String[]> params = new HashMap<>();
        params.put("page", new String[]{"3"});
        params.put("size", new String[]{"25"});
        params.put("sort", new String[]{"title,asc", "id,desc"});

        PageRequest pageRequest = ControllerUtil.toPageRequest(controller(params), 10);

        assertEquals(Long.valueOf(3), pageRequest.getPage());
        assertEquals(Long.valueOf(25), pageRequest.getSize());
        assertEquals(2, pageRequest.getSorts().size());
        assertEquals("title", pageRequest.getSorts().get(0).getSortKey());
        assertEquals(Direction.ASC, pageRequest.getSorts().get(0).getDirection());
        assertEquals("id", pageRequest.getSorts().get(1).getSortKey());
        assertEquals(Direction.DESC, pageRequest.getSorts().get(1).getDirection());
    }

    @Test
    public void shouldFallbackInvalidPageAndSizeToDefaults() {
        Map<String, String[]> params = new HashMap<>();
        params.put("page", new String[]{"0"});
        params.put("size", new String[]{"-1"});

        PageRequest pageRequest = ControllerUtil.getPageRequest(controller(params));

        assertEquals(Long.valueOf(1), pageRequest.getPage());
        assertEquals(Long.valueOf(10), pageRequest.getSize());
        assertEquals("id", pageRequest.getSorts().get(0).getSortKey());
        assertEquals(Direction.DESC, pageRequest.getSorts().get(0).getDirection());
    }
}
