package com.zrlog.common.web;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpRequestListener;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.common.Constants;
import com.zrlog.util.I18nUtil;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

public class ZrLogHttpRequestListener implements HttpRequestListener {

    private static final Logger LOGGER = LoggerUtil.getLogger(HttpRequestListener.class);

    private final LongAdder totalTime = new LongAdder();

    @Override
    public void onHandled(HttpRequest request, HttpResponse httpResponse) {
        try {
            I18nUtil.removeI18n();
        } finally {
            long used = System.currentTimeMillis() - request.getCreateTime();
            totalTime.add(used);
            if (used > 5000) {
                LOGGER.info(
                        "Slow request ["
                                + request.getMethod()
                                + "] "
                                + request.getUri()
                                + " used time "
                                + used
                                + "ms");
            } else if (Constants.debugLoggerPrintAble()) {
                LOGGER.info(
                        "Request ["
                                + request.getMethod()
                                + "] "
                                + request.getUri()
                                + " used time "
                                + used
                                + "ms");
            }
        }
    }

    public long getTotalHandleTime() {
        return totalTime.longValue();
    }
}
