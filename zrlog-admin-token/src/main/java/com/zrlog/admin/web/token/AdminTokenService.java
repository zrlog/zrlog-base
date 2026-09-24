package com.zrlog.admin.web.token;

import com.google.gson.Gson;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.SecurityUtils;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.cookie.Cookie;
import com.zrlog.admin.web.util.ByteUtils;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.model.User;
import com.zrlog.data.security.AccountAccess;
import java.sql.SQLException;
import com.zrlog.util.CrossUtils;
import com.zrlog.util.ParseUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AdminTokenService implements TokenService {

    private static final String ADMIN_TOKEN_KEY_IN_COOKIE = "admin-token";
    public static final String ADMIN_TOKEN_KEY_IN_REQUEST_HEADER = "X-ZrLog-Admin-Token";
    private static final Logger LOGGER = LoggerUtil.getLogger(AdminTokenService.class);
    private final String TOKEN_SPLIT_CHAR = "#";
    private final IvParameterSpec iv;
    private long sessionTimeout;
    private final Map<Integer, String> userSecretKeyCacheMap = new ConcurrentHashMap<>();

    public AdminTokenService(long sessionTimeoutInMinutes) {
        //字符长度必须要大于16个字符
        this.iv = new IvParameterSpec("_BLOG_BLOG_BLOG_".getBytes(StandardCharsets.UTF_8));
        //*60000， Cookie过期时间单位为分钟
        this.sessionTimeout = sessionTimeoutInMinutes * 60 * 1000L;
    }

    protected AccountAccess loadAccount(int userId) throws SQLException {
        return AccountAccess.load(userId);
    }

    @Override
    public void updateSessionTimeout(long sessionTimeoutInMinutes) {
        this.sessionTimeout = sessionTimeoutInMinutes * 60 * 1000L;
    }

    private byte[] encrypt(String secretKey, byte[] value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        //必须要16位
        String newSecretKey = SecurityUtils.md5(secretKey).substring(8, 24);
        SecretKeySpec secretKeySpec = new SecretKeySpec(newSecretKey.getBytes(StandardCharsets.UTF_8), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, iv);
        return cipher.doFinal(value);
    }

    private byte[] decrypt(String secretKey, byte[] encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        //必须要16位
        String newSecretKey = SecurityUtils.md5(secretKey).substring(8, 24);
        SecretKeySpec secretKeySpec = new SecretKeySpec(newSecretKey.getBytes(StandardCharsets.UTF_8), "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv);
        return cipher.doFinal(encrypted);
    }

    @Override
    public AdminFullTokenVO getAdminTokenVO(HttpRequest request) {
        if (!Constants.zrLogConfig.isInstalled()) {
            return null;
        }
        //header first, seconds parse cookie
        String tokenInHeader = request.getHeader(ADMIN_TOKEN_KEY_IN_REQUEST_HEADER);
        if (StringUtils.isNotEmpty(tokenInHeader)) {
            AdminFullTokenVO adminTokenVO = parseAdminTokenByStr(tokenInHeader);
            if (Objects.nonNull(adminTokenVO)) {
                return adminTokenVO;
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(ADMIN_TOKEN_KEY_IN_COOKIE)) {
                AdminFullTokenVO adminFullTokenVO = parseAdminTokenByStr(cookie.getValue());
                if (Objects.nonNull(adminFullTokenVO)) {
                    return adminFullTokenVO;
                }
            }
        }
        return null;
    }

    private AdminFullTokenVO parseAdminTokenByStr(String tokenString) {
        if (!tokenString.contains(TOKEN_SPLIT_CHAR)) {
            LOGGER.warning("Malformed admin token");
            return null;
        }
        String userIdStr = tokenString.substring(0, tokenString.indexOf(TOKEN_SPLIT_CHAR));
        if (!ParseUtil.isNumeric(userIdStr)) {
            LOGGER.warning("Malformed admin token identity");
            return null;
        }
        int userId = Integer.parseInt(userIdStr);
        try {
            AccountAccess account = loadAccount(userId);
            if (!account.isEnabled()) return null;
            String sk = userSecretKeyCacheMap.get(userId);
            if (Objects.isNull(sk)) {
                sk = (String) new User().set("userId", userId).queryFirst("secretKey");
                if (Objects.isNull(sk)) {
                    return null;
                }
                if (Constants.debugLoggerPrintAble()) {
                    LOGGER.info("Missing secretKey cache " + userId);
                }
            }
            userSecretKeyCacheMap.put(userId, sk);
            byte[] adminTokenEncryptAfter = ByteUtils.hexString2Bytes(tokenString.substring(tokenString.indexOf(TOKEN_SPLIT_CHAR) + 1));
            String base64Encode = new String(decrypt(sk, Base64.getDecoder().decode(adminTokenEncryptAfter)));
            AdminFullTokenVO adminTokenVO = new Gson().fromJson(base64Encode, AdminFullTokenVO.class);
            if (adminTokenVO.getUserId() != userId || adminTokenVO.getAuthVersion() != account.getAuthVersion()) return null;
            adminTokenVO.setSecretKey(sk);
            if (adminTokenVO.getCreatedDate() + sessionTimeout > System.currentTimeMillis()) {
                return adminTokenVO;
            }
        } catch (BadPaddingException e) {
            //ignore 发生数据库 secretKey 无法解析 token 的情况下
            userSecretKeyCacheMap.remove(userId);
        } catch (Exception e) {
            LOGGER.warning("Parse token error " + e.getMessage());
        }
        return null;
    }

    @Override
    public void removeAdminToken(HttpRequest request, HttpResponse response) {
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (ADMIN_TOKEN_KEY_IN_COOKIE.equals(cookie.getName())) {
                cookie.setValue("");
                cookie.setExpireDate(new Date(0));
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                response.addCookie(cookie);
            }
        }
        String referer = request.getHeader("Referer");
        if (Objects.isNull(referer)) {
            response.redirect(ADMIN_LOGIN_URI_PATH);
            return;
        }
        URI uri = URI.create(referer);
        if (!CrossUtils.isEnableOrigin(request)) {
            response.redirect(ADMIN_LOGIN_URI_PATH);
            return;
        }
        String ext = Objects.equals(request.getParaToStr("sp"), "true") ? ".html" : "";
        response.redirect(uri.getScheme() + "://" + uri.getRawAuthority() + WebTools.buildEncodedUrl(request, ADMIN_LOGIN_URI_PATH + ext));

    }

    @Override
    public void setAdminToken(Integer userId, String secretKey, String sessionId, String protocol, HttpRequest request, HttpResponse response) throws Exception {
        AccountAccess account = loadAccount(userId);
        if (!account.isEnabled()) throw new IllegalStateException("Account disabled");
        AdminTokenVO adminTokenVO = new AdminTokenVO();
        adminTokenVO.setAuthVersion(account.getAuthVersion());
        adminTokenVO.setUserId(userId);
        adminTokenVO.setSessionId(sessionId);
        adminTokenVO.setProtocol(protocol);
        long loginTime = System.currentTimeMillis();
        adminTokenVO.setCreatedDate(loginTime);
        AdminTokenThreadLocal.setAdminToken(adminTokenVO);
        String encryptBeforeString = new Gson().toJson(adminTokenVO);
        byte[] base64Bytes = Base64.getEncoder().encode(encrypt(secretKey, encryptBeforeString.getBytes()));
        String encryptAfterString = ByteUtils.bytesToHexString(base64Bytes);
        String tokenString = adminTokenVO.getUserId() + TOKEN_SPLIT_CHAR + encryptAfterString;
        Cookie cookie = new Cookie();
        cookie.setName(ADMIN_TOKEN_KEY_IN_COOKIE);
        cookie.setValue(tokenString);
        cookie.setExpireDate(new Date(System.currentTimeMillis() + sessionTimeout));
        if (Objects.equals(protocol, "https")) {
            if (CrossUtils.isEnableOrigin(request) && !EnvKit.isDevMode()) {
                cookie.setSameSite("None");
            }
            cookie.setSecure(true);
        }
        cookie.setHttpOnly(true);
        cookie.setPath(WebTools.getHomeUrl(request));
        response.addCookie(cookie);

    }

}
