package com.zrlog.data.security;

import java.util.*;

/** Fixed product roles. This catalog is the authoritative role/action matrix, not an editable RBAC model. */
public enum AccountAction {
    SESSION("account.session", null, "owner", "admin", "editor", "author", "contributor"),
    ACCOUNT_SELF("account.self", null, "owner", "admin", "editor", "author", "contributor"),
    ARTICLE_READ("article.read", "articles:read", "owner", "admin", "editor", "author", "contributor"),
    ARTICLE_CREATE("article.create", "articles:write", "owner", "admin", "editor", "author", "contributor"),
    ARTICLE_UPDATE("article.update", "articles:write", "owner", "admin", "editor", "author", "contributor"),
    ARTICLE_PUBLISH("article.publish", "articles:publish", "owner", "admin", "editor", "author"),
    ARTICLE_DELETE("article.delete", "articles:delete", "owner", "admin", "editor", "author"),
    ARTICLE_ASSIST("article.assist", null, "owner", "admin", "editor", "author", "contributor"),
    ARTICLE_PIN("article.pin", null, "owner", "admin", "editor"),
    TAXONOMY_READ("taxonomy.read", null, "owner", "admin", "editor", "author", "contributor"),
    TAXONOMY_MANAGE("taxonomy.manage", null, "owner", "admin", "editor"),
    COMMENT_MANAGE("comment.manage", null, "owner", "admin", "editor"),
    ASSET_UPLOAD("asset.upload", "assets:write", "owner", "admin", "editor", "author", "contributor"),
    FILE_MANAGE("file.manage", null, "owner", "admin"),
    DASHBOARD_READ("dashboard.read", null, "owner", "admin"),
    SITE_CONFIGURE("site.configure", null, "owner", "admin"),
    SYSTEM_MANAGE("system.manage", null, "owner", "admin"),
    PLUGIN_MANAGE("plugin.manage", null, "owner", "admin"),
    MEMBER_MANAGE("member.manage", null, "owner", "admin"),
    ADMIN_APPOINT("member.appoint_admin", null, "owner"),
    OWNERSHIP_TRANSFER("ownership.transfer", null, "owner"),
    OAUTH_CLIENT_MANAGE("oauth.client.manage", null, "owner", "admin"),
    OAUTH_GRANT_MANAGE("oauth.grant.manage", null, "owner", "admin", "editor", "author", "contributor"),
    PERMISSION_READ("permission.read", null, "owner", "admin", "editor", "author", "contributor");

    private final String id;
    private final String scope;
    private final Set<String> roles;
    AccountAction(String id, String scope, String... roles) {
        this.id = id; this.scope = scope; this.roles = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(roles)));
    }
    public String getId() { return id; }
    public String getScope() { return scope; }
    public Set<String> getRoles() { return roles; }
    public boolean allowed(AccountAccess account) { return account.isEnabled() && roles.contains(account.getRole()); }
}
