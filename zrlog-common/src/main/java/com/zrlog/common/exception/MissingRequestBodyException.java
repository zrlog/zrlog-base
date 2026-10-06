package com.zrlog.common.exception;

import com.zrlog.util.I18nUtil;

public class MissingRequestBodyException extends AbstractBusinessException {
    @Override
    public int getError() {
        return 9030;
    }

    @Override
    public String getMessage() {
        return I18nUtil.getBackendStringFromRes("request.validation.bodyRequired");
    }
}
