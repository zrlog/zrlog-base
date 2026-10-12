package com.zrlog.business.exception;

import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.util.I18nUtil;

public class CheckUpgradeVersionException extends AbstractBusinessException {

    @Override
    public String getMessage() {
        return I18nUtil.getBackendStringFromRes("upgrade.check.failed");
    }

    @Override
    public int getError() {
        return 9038;
    }
}
