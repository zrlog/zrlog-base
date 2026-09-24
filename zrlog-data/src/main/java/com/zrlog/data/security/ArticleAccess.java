package com.zrlog.data.security;

import java.util.Set;

/** Intersects current account ownership rules with one external connection's granted scopes. */
public final class ArticleAccess {
    private ArticleAccess() { }
    public static boolean canRead(AccountAccess account, Set<String> scopes, int authorId, boolean draft, boolean privateArticle) {
        if (!account.isEnabled() || !account.canAccessArticle(authorId, privateArticle)) return false;
        if (authorId != account.getUserId() && !scopes.contains("articles:all")) return false;
        if (!scopes.contains("articles:read")) return false;
        if (draft && !scopes.contains("articles:read_drafts")) return false;
        return !privateArticle || scopes.contains("articles:read_private");
    }
    public static boolean canWrite(AccountAccess account, Set<String> scopes, int authorId, boolean currentDraft, boolean currentPrivate, boolean nextDraft, boolean nextPrivate) {
        if (!account.isEnabled()) return false;
        if (!account.canAccessArticle(authorId, currentPrivate) || !scopes.contains("articles:write")) return false;
        if (authorId != account.getUserId() && !scopes.contains("articles:all")) return false;
        if ("contributor".equals(account.getRole()) && (!currentDraft || !nextDraft)) return false;
        boolean currentlyPublic = !currentDraft && !currentPrivate;
        boolean willBePublic = !nextDraft && !nextPrivate;
        return !(currentlyPublic || willBePublic) || account.canPublish() && scopes.contains("articles:publish");
    }
}
