package com.zrlog.blog.web.template.vo;

import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.data.dto.ArticleDetailDTO;

/** 文章详情页数据 */
public class ArticleDetailPageVO extends BasePageInfo {

    private ArticleDetailDTO log;

    public ArticleDetailPageVO(ArticleDetailDTO log, BaseDataInitVO init) {
        this.log = log;
        this.init = init;
    }

    public ArticleDetailPageVO() {}

    public ArticleDetailDTO getLog() {
        return log;
    }

    public void setLog(ArticleDetailDTO log) {
        this.log = log;
    }
}
