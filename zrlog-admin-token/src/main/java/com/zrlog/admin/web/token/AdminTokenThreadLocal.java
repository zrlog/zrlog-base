package com.zrlog.admin.web.token;

import com.zrlog.common.vo.AdminTokenVO;

import java.util.Objects;

/**
 * 使用 ThreadLocal 作多个类之间取值，和设值。而不是向传统方式需要获得 HttpRequest 对象才能取值，设值。
 */
public class AdminTokenThreadLocal {

    private AdminTokenThreadLocal() {
    }

    private static final ThreadLocal<AdminTokenVO> userThreadLocal = new ThreadLocal<>();

    public static AdminTokenVO getUser() {
        return userThreadLocal.get();
    }

    public static String getUserProtocol() {
        if (Objects.isNull(getUser())) {
            return "http";
        }
        return getUser().getProtocol();
    }

    static void setAdminToken(AdminTokenVO user) {
        if (userThreadLocal.get() == null) {
            userThreadLocal.set(user);
        }
    }

    /** Explicitly carries a request identity into a worker; restores any previous context afterwards. */
    public static <T> T withUser(AdminTokenVO user, java.util.concurrent.Callable<T> task) throws Exception {
        AdminTokenVO previous = userThreadLocal.get();
        if (user == null) userThreadLocal.remove(); else userThreadLocal.set(user);
        try { return task.call(); }
        finally {
            if (previous == null) userThreadLocal.remove(); else userThreadLocal.set(previous);
        }
    }

    public static int getUserId() {
        if (Objects.isNull(userThreadLocal.get())) {
            return -1;
        }
        return userThreadLocal.get().getUserId();
    }

    public static void remove() {
        userThreadLocal.remove();
    }
}
