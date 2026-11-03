package com.zrlog.blog.web.template;

import com.zrlog.util.I18nUtil;
import java.util.ArrayList;
import java.util.List;

public class PagerUtil {

    /**
     * 生成分页对象
     *
     * @param currentUri
     * @param currentPage
     * @param totalPage
     * @return
     */
    public static PagerVO generatorPager(String currentUri, long currentPage, long totalPage) {
        PagerVO pager = new PagerVO();
        List<PagerVO.PageEntry> pageList = new ArrayList<>();
        if (currentPage != 1) {
            PagerVO.PageEntry prevPage =
                    pageEntity(
                            currentUri,
                            currentPage,
                            I18nUtil.getBlogStringFromRes("prevPage"),
                            currentPage - 1,
                            totalPage);
            prevPage.setPrev(true);
            pageList.add(prevPage);
        }
        if (totalPage > 10) {
            if (currentPage < 3 || totalPage - 4 < currentPage) {
                for (int i = 1; i <= 4; i++) {
                    pageList.add(pageEntity(currentUri, currentPage, i, totalPage));
                }
            } else {
                if (currentPage + 1 == totalPage - 3) {
                    pageList.add(pageEntity(currentUri, currentPage, currentPage - 3, totalPage));
                }
                for (long i = currentPage - 2; i <= currentPage; i++) {
                    pageList.add(pageEntity(currentUri, currentPage, i, totalPage));
                }
                if (currentPage + 1 != totalPage - 3) {
                    pageList.add(pageEntity(currentUri, currentPage, currentPage + 1, totalPage));
                }
            }
            for (long i = totalPage - 3; i <= totalPage; i++) {
                pageList.add(pageEntity(currentUri, currentPage, i, totalPage));
            }
        } else {
            for (int i = 1; i <= totalPage; i++) {
                pageList.add(pageEntity(currentUri, currentPage, i, totalPage));
            }
        }
        if (currentPage != totalPage) {
            PagerVO.PageEntry nextPage =
                    pageEntity(
                            currentUri,
                            currentPage,
                            I18nUtil.getBlogStringFromRes("nextPage"),
                            currentPage + 1,
                            totalPage);
            nextPage.setNext(true);
            pageList.add(nextPage);
        }
        pager.setPageList(pageList);
        pager.setPageStartUrl(currentUri + 1);
        pager.setPageEndUrl(currentUri + totalPage);
        pager.setStartPage(currentPage == 1);
        pager.setEndPage(currentPage == totalPage);
        return pager;
    }

    private static PagerVO.PageEntry pageEntity(
            String url, long currentPage, String desc, long page, long totalPage) {
        PagerVO.PageEntry map = new PagerVO.PageEntry();
        map.setUrl(url + page);
        map.setDesc(desc);
        map.setCurrent(currentPage == page);
        map.setNumber(page);
        if (map.getCurrent()) {
            map.setNext(page < totalPage);
            map.setPrev(page > 1 && totalPage > 1);
        }
        return map;
    }

    private static PagerVO.PageEntry pageEntity(
            String url, long currentPage, long page, long totalPage) {
        return pageEntity(url, currentPage, page + "", page, totalPage);
    }
}
