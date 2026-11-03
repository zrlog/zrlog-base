package com.zrlog.data.util;

import com.hibegin.common.dao.ResultValueConvertUtils;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class DateValueFormatUtils {

    private DateValueFormatUtils() {
        throw new UnsupportedOperationException();
    }

    public static String format(Object value, String pattern) {
        try {
            return ResultValueConvertUtils.formatDate(value, pattern);
        } catch (RuntimeException e) {
            Long parsed = parseLocalizedDateString(value);
            if (parsed != null) {
                return new SimpleDateFormat(pattern).format(new Date(parsed));
            }
            if (Objects.isNull(value)) {
                return "";
            }
            return value.toString();
        }
    }

    private static Long parseLocalizedDateString(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).replace('\u202f', ' ').replace('\u00a0', ' ');
        for (DateFormat format :
                Arrays.asList(
                        DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault()),
                        DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.ENGLISH),
                        new SimpleDateFormat("MMM d, yyyy, h:mm:ss a", Locale.ENGLISH))) {
            try {
                return format.parse(text).getTime();
            } catch (ParseException ignored) {
            }
        }
        return null;
    }
}
