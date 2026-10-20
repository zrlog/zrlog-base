package com.zrlog.common.controller;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.Controller;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.common.exception.MissingRequestBodyException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class BaseControllerTest {

    private static BaseController controller(Map<String, String> params, String body) throws Exception {
        BaseController controller = new BaseController();
        setControllerField(controller, "request", request(params, body));
        setControllerField(controller, "response", response());
        return controller;
    }

    private static void setControllerField(BaseController controller, String name, Object value) throws Exception {
        Field field = Controller.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static HttpRequest request(Map<String, String> params, String body) {
        return (HttpRequest) Proxy.newProxyInstance(
                BaseControllerTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getParaToStr".equals(method.getName())) {
                        return params.get(args[0].toString());
                    }
                    if ("getInputStream".equals(method.getName())) {
                        if (body == null) {
                            return null;
                        }
                        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(
                BaseControllerTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> {
                    if ("write".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof InputStream) {
                        ((InputStream) args[0]).readAllBytes();
                    }
                    return null;
                });
    }

    @Test
    public void shouldReadRequiredParamOrThrowArgsException() throws Exception {
        BaseController controller = controller(Map.of("id", "42"), null);

        assertEquals("42", controller.getParamWithEmptyCheck("id"));

        ArgsException exception = assertThrows(ArgsException.class,
                () -> controller.getParamWithEmptyCheck("missing"));
        assertTrue(exception.getMessage().contains("missing"));
    }

    @Test
    public void shouldSupportRequestResponseConstructor() {
        BaseController controller = new BaseController(request(Map.of("id", "7"), null), response());

        assertEquals("7", controller.getParamWithEmptyCheck("id"));
    }

    @Test
    public void shouldRejectMissingRequestBody() throws Exception {
        BaseController nullBodyController = controller(new HashMap<>(), null);
        assertThrows(MissingRequestBodyException.class,
                () -> nullBodyController.getRequestBodyWithNullCheck(SampleRequest.class));

        BaseController emptyBodyController = controller(new HashMap<>(), "");
        assertThrows(MissingRequestBodyException.class,
                () -> emptyBodyController.getRequestBodyWithNullCheck(SampleRequest.class));
    }

    @Test
    public void shouldConvertValidateAndCleanRequestBody() throws Exception {
        SampleRequest request = BaseController.convertWithValid("{\"name\":\" Alice \",\"required\":true}",
                SampleRequest.class);

        assertEquals("Alice", request.name);
        assertTrue(request.validated);
        assertTrue(request.cleaned);
    }

    @Test
    public void shouldPropagateValidatorErrors() {
        ArgsException exception = assertThrows(ArgsException.class,
                () -> BaseController.convertWithValid("{\"name\":\"\",\"required\":true}", SampleRequest.class));

        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    public void shouldRejectJsonNullRequestBody() {
        assertThrows(NullPointerException.class,
                () -> BaseController.convertWithValid("null", SampleRequest.class));
    }

    public static class SampleRequest implements Validator {

        private String name;
        private boolean required;
        private transient boolean validated;
        private transient boolean cleaned;

        @Override
        public void doValid() {
            validated = true;
            if (name == null || name.isBlank() || !required) {
                throw new ArgsException("name");
            }
        }

        @Override
        public void doClean() {
            cleaned = true;
            name = name.trim();
        }
    }
}
