package com.zrlog.blog.web.template;

import com.zrlog.blog.web.template.vo.BasePageInfo;
import java.io.File;

/** 模板接口 */
public interface ZrLogTemplate {

    void init(File path) throws Exception;

    String render(String page, BasePageInfo pageInfo) throws Exception;

    void initClassTemplate(String template);
}
