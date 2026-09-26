package com.zrlog.data.security;

import org.junit.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

public class AccountAccessTest {
    private AccountAccess account(String role) {
        return AccountAccess.from(Map.of("userId", 1, "role", role, "enabled", true, "authVersion", 0));
    }
    @Test public void delegationIntersectsRolesAndCannotBeWidened() {
        AccountAccess owner = account("owner");
        AccountAccess reader = owner.restrictActions(Set.of("article.read"));
        assertTrue(AccountAction.ARTICLE_READ.allowed(reader));
        assertFalse(reader.canPublish());
        assertFalse(AccountAction.ADMIN_APPOINT.allowed(reader));
        assertFalse(AccountAction.ARTICLE_PUBLISH.allowed(reader.restrictActions(Set.of("article.publish"))));
        assertTrue(owner.canPublish());
        AccountAccess author = account("author").restrictActions(Set.of("site.configure", "article.publish"));
        assertFalse(AccountAction.SITE_CONFIGURE.allowed(author));
        assertTrue(author.canPublish());
        assertFalse(author.canAccessArticle(2));
    }
    @Test public void scopeAdapterReflectsDelegationAndPreservesAccountContentBoundaries() {
        AccountAccess editor = account("editor").restrictActions(Set.of("article.read"));
        assertTrue(editor.scopes().containsAll(Set.of("articles:read", "articles:read_drafts", "articles:all")));
        assertFalse(editor.scopes().contains("articles:write"));
        assertFalse(editor.canAccessArticle(2, true));
        assertFalse(AccountAction.NOTIFICATION_CREATE.allowed(editor));
        assertTrue(AccountAction.NOTIFICATION_CREATE.allowed(account("admin")));
    }
}
