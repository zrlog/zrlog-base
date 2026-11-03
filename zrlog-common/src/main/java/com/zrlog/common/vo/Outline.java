package com.zrlog.common.vo;

import java.util.ArrayList;
import java.util.List;

/** 文章内容的大纲结构 */
public class Outline {

    private String text;
    private int level;

    private List<Outline> children = new ArrayList<>();

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<Outline> getChildren() {
        return children;
    }

    public void setChildren(List<Outline> children) {
        this.children = children;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}
