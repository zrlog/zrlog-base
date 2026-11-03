package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Entity;

@Entity(table = "tag", idColumn = "tagId", generatedId = true)
public class Tag extends BaseEntity<Long> {
    private Integer count = 0;
    private String text;

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
