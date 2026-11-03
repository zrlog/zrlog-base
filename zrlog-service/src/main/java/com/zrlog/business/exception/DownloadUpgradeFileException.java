package com.zrlog.business.exception;

import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.util.I18nUtil;

public class DownloadUpgradeFileException extends AbstractBusinessException {

    public DownloadUpgradeFileException() {}

    public DownloadUpgradeFileException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        return I18nUtil.getBackendStringFromRes("upgrade.error.downloadFile");
    }

    @Override
    public int getError() {
        return 9026;
    }
}
