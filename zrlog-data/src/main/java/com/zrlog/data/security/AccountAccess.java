package com.zrlog.data.security;

import com.zrlog.model.User;
import java.sql.SQLException;
import java.util.*;

/** Current database permissions; never trust roles supplied by a client or an old token. */
public final class AccountAccess {
    public static final Set<String> ROLES = Set.of("owner", "admin", "editor", "author", "contributor");
    private final int userId;
    private final String role;
    private final boolean enabled;
    private final int authVersion;

    private AccountAccess(int userId, String role, boolean enabled, int authVersion) {
        this.userId = userId;
        this.role = role;
        this.enabled = enabled && ROLES.contains(role);
        this.authVersion = authVersion;
    }

    public static AccountAccess load(int userId) throws SQLException {
        return from(new User().loadById(userId));
    }

    public static AccountAccess from(Map<String, Object> row) {
        if (row == null || row.isEmpty()) return new AccountAccess(-1, "", false, -1);
        return new AccountAccess(((Number) row.get("userId")).intValue(),
                Objects.toString(row.get("role"), ""), truth(row.get("enabled")),
                row.get("authVersion") instanceof Number ? ((Number) row.get("authVersion")).intValue() : -1);
    }

    public static boolean truth(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof byte[]) return ((byte[]) value).length > 0 && ((byte[]) value)[0] != 0;
        return "true".equalsIgnoreCase(Objects.toString(value, "")) || "1".equals(Objects.toString(value, ""));
    }

    public int getUserId() { return userId; }
    public String getRole() { return role; }
    public int getAuthVersion() { return authVersion; }
    public boolean isEnabled() { return enabled; }
    public boolean isOwner() { return enabled && "owner".equals(role); }
    public boolean isAdministrator() { return enabled && ("owner".equals(role) || "admin".equals(role)); }
    public boolean managesAllArticles() { return isAdministrator() || enabled && "editor".equals(role); }
    public boolean canPublish() { return AccountAction.ARTICLE_PUBLISH.allowed(this); }
    public boolean canAccessArticle(int authorId) { return enabled && (managesAllArticles() || authorId == userId); }
    public boolean canAccessArticle(int authorId, boolean privateArticle) {
        return canAccessArticle(authorId) && (!privateArticle || isAdministrator() || authorId == userId);
    }
    public Set<String> scopes() {
        if (!enabled) return Collections.emptySet();
        Set<String> scopes = new LinkedHashSet<>();
        for (AccountAction action : AccountAction.values()) {
            if (action.getScope() != null && action.allowed(this)) scopes.add(action.getScope());
        }
        scopes.add("articles:read_drafts");
        scopes.add("articles:read_private");
        if (managesAllArticles()) scopes.add("articles:all");
        return Collections.unmodifiableSet(scopes);
    }
}
