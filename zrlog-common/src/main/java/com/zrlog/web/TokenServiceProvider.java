package com.zrlog.web;

import com.zrlog.common.TokenService;

/**
 * Optional session capability contributed by the admin module for application assembly.
 */
public interface TokenServiceProvider {
    TokenService getTokenService();
}
