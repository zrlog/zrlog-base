package com.zrlog.business.util;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Constants;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.util.ParseUtil;
import java.util.List;

/**
 * 与文章相关的业务代码
 */
public class ArticleHelpers {

    /**
     * 高亮用户检索的关键字
     */
    public static void wrapperSearchKeyword(PageData<ArticleBasicDTO> data, String keywords) {
        if (StringUtils.isEmpty(keywords)) {
            return;
        }
        List<ArticleBasicDTO> logs = data.getRows();
        if (logs == null || logs.isEmpty()) {
            return;
        }
        long digestLength = Constants.getPublicWebSiteInfo().getArticle_auto_digest_length();
        for (ArticleBasicDTO log : logs) {
            String title = log.getTitle();
            String content = log.getContent();
            String digest = log.getDigest();
            log.setTitle(ParseUtil.wrapperKeyword(title, keywords, digestLength));
            String tryWrapperDigest = ParseUtil.wrapperKeyword(digest, keywords, digestLength);
            if (tryWrapperDigest != null && tryWrapperDigest.length() != digest.length()) {
                log.setDigest(tryWrapperDigest);
            } else {
                log.setDigest(ParseUtil.wrapperKeyword(ParseUtil.removeHtmlElement(content), keywords, digestLength));
            }
        }
    }
}
