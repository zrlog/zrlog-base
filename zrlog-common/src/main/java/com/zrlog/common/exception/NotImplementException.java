package com.zrlog.common.exception;

import com.zrlog.util.I18nUtil;

public class NotImplementException extends AbstractBusinessException {
    @Override
    public int getError() {
        return 9088;
    }

    @Override
    public String getMessage() {
        return I18nUtil.getBackendStringFromRes("request.error.unsupportedOperation");
    }
}
