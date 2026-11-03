package com.zrlog.business.version;

import com.hibegin.common.util.LoggerUtil;
import com.zrlog.common.Constants;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UpgradeVersionHandlerHelpers {

    private static final Logger LOGGER = LoggerUtil.getLogger(UpgradeVersionHandlerHelpers.class);

    public static UpgradeVersionHandler getUpgradeVersionHandler(Integer version) {
        try {
            return (UpgradeVersionHandler)
                    Class.forName(
                                    "com.zrlog.business.version.V"
                                            + version
                                            + UpgradeVersionHandler.class.getSimpleName())
                            .getDeclaredConstructor()
                            .newInstance();
        } catch (ClassNotFoundException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException
                | NoSuchMethodException e) {
            if (Constants.debugLoggerPrintAble()) {
                LOGGER.log(Level.WARNING, "Try exec upgrade method error, " + e.getMessage());
            }
            return null;
        }
    }
}
