package com.zrlog.common;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.common.vo.AdminFullTokenVO;

/**
 * 验证 token 相关相关逻辑
 */
public interface TokenService {


    String ADMIN_LOGIN_URI_PATH = Constants.ADMIN_URI_BASE_PATH + "/login";

    /**
     * 更新生成和比对 token 的有效时间
     *
     * @param sessionTimeoutInMinutes 单位分钟
     */
    void updateSessionTimeout(long sessionTimeoutInMinutes);

    /**
     * 从 HTTP 请求头里面获得登录信息，
     * 支持从 Header (X-Admin-Token) 和 Cookie 里面读取
     * 优先读取 Header 里面的
     *
     * @param request http 请求
     * @return 完整的 token 信息
     */
    AdminFullTokenVO getAdminTokenVO(HttpRequest request);

    /** Renew an authenticated browser session without changing its chosen lifetime. */
    default void refreshAdminToken(AdminFullTokenVO token, HttpRequest request, HttpResponse response) throws Exception {
        setAdminToken(token.getUserId(), token.getSecretKey(), token.getSessionId(), token.getProtocol(), request, response);
    }

    void removeAdminToken(HttpRequest request, HttpResponse response);

    void setAdminToken(Integer userId, String secretKey, String sessionId, String protocol, HttpRequest request, HttpResponse response) throws Exception;
}
