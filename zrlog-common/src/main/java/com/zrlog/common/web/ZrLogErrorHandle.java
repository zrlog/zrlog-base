package com.zrlog.common.web;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpErrorHandle;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.common.Constants;
import com.zrlog.common.exception.AbstractBusinessException;
import com.zrlog.common.exception.NotFindDbEntryException;
import com.zrlog.common.rest.response.ApiStandardResponse;
import com.zrlog.util.I18nUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局异常处理
 */
public class ZrLogErrorHandle implements HttpErrorHandle {

    private static final Logger LOGGER = LoggerUtil.getLogger(ZrLogErrorHandle.class);

    private final int httpStatueCode;

    public ZrLogErrorHandle(int httpStatueCode) {
        this.httpStatueCode = httpStatueCode;
    }

    /**
     * @param request
     * @param response
     * @param e
     */
    @Override
    public void doHandle(HttpRequest request, HttpResponse response, Throwable e) {
        if (Constants.debugLoggerPrintAble()) {
            LOGGER.log(Level.SEVERE, "handle " + request.getUri() + " error", e);
        } else {
            LOGGER.log(Level.WARNING, "handle " + request.getUri() + " error " + e.getMessage());
        }
        if (request.getUri().startsWith("/api")) {
            if (e instanceof AbstractBusinessException) {
                AbstractBusinessException ee = (AbstractBusinessException) e;
                ApiStandardResponse<Void> error = new ApiStandardResponse<>();
                error.setError(ee.getError());
                error.setMessage(getResponseMessage(e));
                response.renderJson(error);
            } else if (e instanceof NotFindResourceException) {
                ApiStandardResponse<Void> error = new ApiStandardResponse<>();
                error.setError(9404);
                error.setMessage(getResponseMessage(e));
                response.renderJson(error);
            } else {
                ApiStandardResponse<Void> error = new ApiStandardResponse<>();
                error.setError(9999);
                error.setMessage(getResponseMessage(e));
                response.renderJson(error);
            }
            return;
        }
        if (request.getUri().startsWith(Constants.ADMIN_URI_BASE_PATH)) {
            String message = URLEncoder.encode(getResponseMessage(e), StandardCharsets.UTF_8);
            if (e instanceof NotFindResourceException || e instanceof NotFindDbEntryException) {
                response.redirect(Constants.ADMIN_URI_BASE_PATH + "/404?queryString=" + request.getQueryStr() + "&uriPath=" + request.getUri() + "&message=" + message);
                return;
            }
            response.redirect(Constants.ADMIN_URI_BASE_PATH + "/500?message=" + message);
            return;
        }
        InputStream errorInputStream = getErrorInputStream(e, httpStatueCode);
        response.addHeader("Content-Type", "text/html;charset=utf-8");
        if (Objects.isNull(errorInputStream)) {
            response.renderCode(500);
            return;
        }
        response.write(errorInputStream, httpStatueCode);
    }

    private String getResponseMessage(Throwable error) {
        if (error instanceof AbstractBusinessException) {
            String message = error.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        if (error instanceof NotFindResourceException) {
            return I18nUtil.getBackendStringFromRes("request.error.notFound");
        }
        return I18nUtil.getBackendStringFromRes("unknownError");
    }

    private InputStream getErrorInputStream(Throwable e, int httpStatueCode) {
        if (Constants.debugLoggerPrintAble()) {
            try {
                InputStream htmlInputStream = PathUtil.getConfInputStream("/error/" + httpStatueCode + ".html");
                if (Objects.isNull(htmlInputStream)) {
                    htmlInputStream = PathUtil.getConfInputStream("/error/" + 500 + ".html");
                }
                String body = "<pre style='color:red;white-space:pre-wrap'>" + WebTools.htmlEncode(LoggerUtil.recordStackTraceMsg(e)) + "</pre>";
                Document document = Jsoup.parse(htmlInputStream, "utf-8", "/");
                document.body().append(body);
                return new ByteArrayInputStream(document.html().getBytes());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        InputStream inputStream = PathUtil.getConfInputStream("/error/" + httpStatueCode + ".html");
        if (Objects.nonNull(inputStream)) {
            return inputStream;
        }
        return PathUtil.getConfInputStream("/error/500.html");
    }
}
