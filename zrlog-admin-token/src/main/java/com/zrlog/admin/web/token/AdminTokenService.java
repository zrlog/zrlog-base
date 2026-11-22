package com.zrlog.admin.web.token;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.cookie.Cookie;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.repository.UserRepository;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.service.UserPreferenceStore;
import com.zrlog.model.User;
import com.zrlog.util.CrossUtils;
import com.zrlog.util.ParseUtil;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Singleton
public class AdminTokenService implements TokenService {

    public static final String ADMIN_TOKEN_KEY_IN_REQUEST_HEADER = "X-ZrLog-Admin-Token";
    private static final String ADMIN_TOKEN_KEY_IN_COOKIE = "admin-token";
    private static final Logger LOGGER = LoggerUtil.getLogger(AdminTokenService.class);
    private static final String TOKEN_SEPARATOR = "#";
    private static final String TOKEN_VERSION = "v2.";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] KEY_CONTEXT =
            "zrlog-admin-session-v2:".getBytes(StandardCharsets.UTF_8);
    private final SecureRandom nonceRandom = new SecureRandom();
    private final BooleanSupplier installed;
    private final jakarta.inject.Provider<UserRepository> users;
    private final jakarta.inject.Provider<UserPreferenceStore> preferences;
    private final BooleanSupplier staticHtml;
    private final Map<Integer, String> userSecretKeyCacheMap = new ConcurrentHashMap<>();
    private volatile long sessionTimeout;

    @Inject
    public AdminTokenService(
            @Named("adminSessionTimeoutMinutes") long sessionTimeoutInMinutes,
            BooleanSupplier installed,
            @Named("adminStaticHtml") BooleanSupplier staticHtml,
            jakarta.inject.Provider<UserRepository> users,
            jakarta.inject.Provider<UserPreferenceStore> preferences) {
        this.users = Objects.requireNonNull(users);
        this.preferences = Objects.requireNonNull(preferences);
        this.installed = Objects.requireNonNull(installed);
        this.staticHtml = Objects.requireNonNull(staticHtml);
        // *60000， Cookie过期时间单位为分钟
        this.sessionTimeout = sessionTimeoutInMinutes * 60 * 1000L;
    }

    protected AccountAccess loadAccount(int userId) throws SQLException {
        return AccountAccess.from(users.get().loadById((long) userId));
    }

    @Override
    public void updateSessionTimeout(long sessionTimeoutInMinutes) {
        this.sessionTimeout = sessionTimeoutInMinutes * 60 * 1000L;
    }

    protected long sessionTimeoutMinutes(int userId) throws SQLException {
        return preferences.get().sessionTimeoutMinutes(userId, sessionTimeout / 60000);
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private Cipher sessionCipher(int mode, int userId, String secretKey, byte[] nonce)
            throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(KEY_CONTEXT);
        SecretKeySpec key =
                new SecretKeySpec(digest.digest(secretKey.getBytes(StandardCharsets.UTF_8)), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(ByteBuffer.allocate(Integer.BYTES).putInt(userId).array());
        return cipher;
    }

    private String encodeToken(int userId, String secretKey, AdminTokenVO token)
            throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        nonceRandom.nextBytes(nonce);
        byte[] payload = new Gson().toJson(token).getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext =
                sessionCipher(Cipher.ENCRYPT_MODE, userId, secretKey, nonce).doFinal(payload);
        byte[] envelope =
                ByteBuffer.allocate(nonce.length + ciphertext.length)
                        .put(nonce)
                        .put(ciphertext)
                        .array();
        return userId
                + TOKEN_SEPARATOR
                + TOKEN_VERSION
                + Base64.getUrlEncoder().withoutPadding().encodeToString(envelope);
    }

    private AdminFullTokenVO decodeToken(int userId, String secretKey, String encoded)
            throws GeneralSecurityException {
        ByteBuffer envelope = ByteBuffer.wrap(Base64.getUrlDecoder().decode(encoded));
        if (envelope.remaining() <= NONCE_BYTES + TAG_BITS / Byte.SIZE) {
            throw new IllegalArgumentException("Incomplete admin session token");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        envelope.get(nonce);
        byte[] ciphertext = new byte[envelope.remaining()];
        envelope.get(ciphertext);
        byte[] payload =
                sessionCipher(Cipher.DECRYPT_MODE, userId, secretKey, nonce).doFinal(ciphertext);
        return new Gson()
                .fromJson(new String(payload, StandardCharsets.UTF_8), AdminFullTokenVO.class);
    }

    @Override
    public AdminFullTokenVO getAdminTokenVO(HttpRequest request) {
        try {
            return getAdminTokenVOForAdmin(request);
        } catch (DAOException e) {
            LOGGER.warning("Unable to load account for optional admin session");
            return null;
        }
    }

    @Override
    public AdminFullTokenVO getAdminTokenVOForAdmin(HttpRequest request) {
        if (!installed.getAsBoolean()) {
            return null;
        }
        // header first, seconds parse cookie
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
        int separator = tokenString.indexOf(TOKEN_SEPARATOR);
        if (separator <= 0 || !tokenString.startsWith(TOKEN_VERSION, separator + 1)) {
            return null;
        }
        String userIdStr = tokenString.substring(0, separator);
        if (!ParseUtil.isNumeric(userIdStr)) return null;
        int userId;
        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
        try {
            AccountAccess account = loadAccount(userId);
            if (!account.isEnabled()) return null;
            String sk = userSecretKeyCacheMap.get(userId);
            if (Objects.isNull(sk)) {
                User user = users.get().loadById((long) userId);
                sk = user == null ? null : user.getSecretKey();
                if (Objects.isNull(sk)) {
                    return null;
                }
                if (Constants.debugLoggerPrintAble()) {
                    LOGGER.info("Missing secretKey cache " + userId);
                }
            }
            userSecretKeyCacheMap.put(userId, sk);
            AdminFullTokenVO adminTokenVO =
                    decodeToken(
                            userId,
                            sk,
                            tokenString.substring(separator + 1 + TOKEN_VERSION.length()));
            if (adminTokenVO == null
                    || adminTokenVO.getUserId() != userId
                    || adminTokenVO.getAuthVersion() != account.getAuthVersion()) return null;
            adminTokenVO.setSecretKey(sk);
            long expiresAt =
                    adminTokenVO.getExpiresAt() == null
                            ? Math.addExact(adminTokenVO.getCreatedDate(), sessionTimeout)
                            : adminTokenVO.getExpiresAt();
            if (expiresAt > adminTokenVO.getCreatedDate() && expiresAt > currentTimeMillis()) {
                return adminTokenVO;
            }
        } catch (BadPaddingException e) {
            userSecretKeyCacheMap.remove(userId);
            LOGGER.warning("Admin token decryption failed; cached account key cleared");
        } catch (SQLException e) {
            throw new DAOException(e);
        } catch (DAOException e) {
            throw e;
        } catch (JsonParseException e) {
            LOGGER.warning(
                    () -> "Invalid admin token payload (" + e.getClass().getSimpleName() + ")");
        } catch (IllegalArgumentException e) {
            LOGGER.warning(
                    () -> "Invalid admin token encoding (" + e.getClass().getSimpleName() + ")");
        } catch (Exception e) {
            LOGGER.warning(
                    () -> "Admin token verification failed (" + e.getClass().getSimpleName() + ")");
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
        if (!CrossUtils.isEnableOrigin(request, staticHtml.getAsBoolean())) {
            response.redirect(ADMIN_LOGIN_URI_PATH);
            return;
        }
        String ext = Objects.equals(request.getParaToStr("sp"), "true") ? ".html" : "";
        response.redirect(
                uri.getScheme()
                        + "://"
                        + uri.getRawAuthority()
                        + WebTools.buildEncodedUrl(request, ADMIN_LOGIN_URI_PATH + ext));
    }

    @Override
    public void setAdminToken(
            Integer userId,
            String secretKey,
            String sessionId,
            String protocol,
            HttpRequest request,
            HttpResponse response)
            throws Exception {
        issueAdminToken(
                userId,
                secretKey,
                sessionId,
                protocol,
                request,
                response,
                Math.multiplyExact(sessionTimeoutMinutes(userId), 60000L));
    }

    @Override
    public void refreshAdminToken(
            AdminFullTokenVO token, HttpRequest request, HttpResponse response) throws Exception {
        long duration =
                token.getExpiresAt() == null
                        ? sessionTimeout
                        : Math.subtractExact(token.getExpiresAt(), token.getCreatedDate());
        issueAdminToken(
                token.getUserId(),
                token.getSecretKey(),
                token.getSessionId(),
                token.getProtocol(),
                request,
                response,
                duration);
    }

    private void issueAdminToken(
            Integer userId,
            String secretKey,
            String sessionId,
            String protocol,
            HttpRequest request,
            HttpResponse response,
            long duration)
            throws Exception {
        AccountAccess account;
        try {
            account = loadAccount(userId);
        } catch (SQLException e) {
            throw new DAOException(e);
        }
        if (!account.isEnabled()) throw new IllegalStateException("Account disabled");
        if (duration <= 0) throw new IllegalArgumentException("Invalid session lifetime");
        AdminTokenVO adminTokenVO = new AdminTokenVO();
        adminTokenVO.setAuthVersion(account.getAuthVersion());
        adminTokenVO.setUserId(userId);
        adminTokenVO.setSessionId(sessionId);
        adminTokenVO.setProtocol(protocol);
        long loginTime = currentTimeMillis();
        adminTokenVO.setCreatedDate(loginTime);
        adminTokenVO.setExpiresAt(Math.addExact(loginTime, duration));
        AdminTokenThreadLocal.setAdminToken(adminTokenVO);
        String tokenString = encodeToken(userId, secretKey, adminTokenVO);
        Cookie cookie = new Cookie();
        cookie.setName(ADMIN_TOKEN_KEY_IN_COOKIE);
        cookie.setValue(tokenString);
        cookie.setExpireDate(new Date(adminTokenVO.getExpiresAt()));
        if (Objects.equals(protocol, "https")) {
            if (CrossUtils.isEnableOrigin(request, staticHtml.getAsBoolean())
                    && !EnvKit.isDevMode()) {
                cookie.setSameSite("None");
            }
            cookie.setSecure(true);
        }
        cookie.setHttpOnly(true);
        cookie.setPath(WebTools.getHomeUrl(request));
        response.addCookie(cookie);
    }
}
