package com.zrlog.admin.web.token;

import com.zrlog.common.vo.AdminTokenVO;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class AdminTokenThreadLocalTest {

    private static AdminTokenVO token(int userId, String protocol) {
        AdminTokenVO token = new AdminTokenVO();
        token.setUserId(userId);
        token.setProtocol(protocol);
        return token;
    }

    @After
    public void tearDown() {
        AdminTokenThreadLocal.remove();
    }

    @Test
    public void shouldReturnDefaultsWhenNoTokenIsBound() {
        assertNull(AdminTokenThreadLocal.getUser());
        assertEquals(-1, AdminTokenThreadLocal.getUserId());
        assertEquals("http", AdminTokenThreadLocal.getUserProtocol());
    }

    @Test
    public void shouldBindAdminTokenOnlyWhenThreadLocalIsEmpty() {
        AdminTokenVO first = token(1, "https");
        AdminTokenVO second = token(2, "http");

        AdminTokenThreadLocal.setAdminToken(first);
        AdminTokenThreadLocal.setAdminToken(second);

        assertSame(first, AdminTokenThreadLocal.getUser());
        assertEquals(1, AdminTokenThreadLocal.getUserId());
        assertEquals("https", AdminTokenThreadLocal.getUserProtocol());
    }

    @Test
    public void shouldRemoveBoundAdminToken() {
        AdminTokenThreadLocal.setAdminToken(token(1, "https"));

        AdminTokenThreadLocal.remove();

        assertNull(AdminTokenThreadLocal.getUser());
        assertEquals(-1, AdminTokenThreadLocal.getUserId());
    }
}
