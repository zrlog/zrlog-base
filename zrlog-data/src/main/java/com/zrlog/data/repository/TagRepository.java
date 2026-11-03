package com.zrlog.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.model.Tag;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.*;

/** 统计文章中标签出现的次数，方便展示。文章的数据发生变化后，会自动更新记录，无需额外程序控制，对应数据库的 tag 表 */
public class TagRepository extends BaseRepository<Tag, Long> {

    private final DataSourceWrapper dataSource;

    @Inject
    public TagRepository(DataSourceWrapper dataSource) {
        super(Tag.class, dataSource);
        this.dataSource = dataSource;
    }

    public List<TagDTO> findAll() throws SQLException {
        List<TagDTO> result = new ArrayList<>();
        for (Tag row : select("")) {
            TagDTO item = new TagDTO();
            item.setId(row.getId());
            item.setText(row.getText());
            item.setCount(row.getCount() == null ? null : row.getCount().longValue());
            result.add(item);
        }
        return result;
    }

    private Set<String> strToSet(String str) {
        // A case variant in the same article still represents one tag.
        Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String tag : str.split(",")) {
            if (!tag.trim().isEmpty()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    public List<TagDTO> refreshTag() throws SQLException {
        deleteWhere("1=1");
        // Preserve the first spelling and merge case variants before the unique-index insert.
        Map<String, Integer> countMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<com.zrlog.data.dto.ArticleBasicDTO> logs =
                new LogRepository(dataSource)
                        .queryList(
                                "select keywords from "
                                        + LogRepository.TABLE_NAME
                                        + " where rubbish=? and privacy=? ",
                                com.zrlog.data.dto.ArticleBasicDTO.class,
                                false,
                                false);
        for (com.zrlog.data.dto.ArticleBasicDTO log : logs) {
            if (StringUtils.isNotEmpty((String) log.getKeywords())
                    && !((String) log.getKeywords()).trim().isEmpty()) {
                Set<String> tagSet = strToSet(log.getKeywords() + ",");
                for (String tag : tagSet) {
                    countMap.merge(tag, 1, Integer::sum);
                }
            }
        }
        List<TagDTO> all = new ArrayList<>();
        for (Map<String, Integer> t : splitList(countMap, 10)) {
            List<TagDTO> maps = batchInsert(t, all.size());
            all.addAll(maps);
        }
        return all;
    }

    private List<Map<String, Integer>> splitList(Map<String, Integer> countMap, int maxCount) {
        List<Map<String, Integer>> splitList = new ArrayList<>();
        Map<String, Integer> chunkMap = new LinkedHashMap<>(maxCount);
        for (Map.Entry<String, Integer> tag : countMap.entrySet()) {
            if (chunkMap.size() == maxCount) {
                splitList.add(new LinkedHashMap<>(chunkMap));
                chunkMap.clear();
            }
            chunkMap.put(tag.getKey(), tag.getValue());
        }
        if (!chunkMap.isEmpty()) {
            splitList.add(chunkMap);
        }
        return splitList;
    }

    private List<TagDTO> batchInsert(Map<String, Integer> countMap, int idBase)
            throws SQLException {
        StringBuilder insertSql =
                new StringBuilder(
                        "insert into "
                                + tableName
                                + " (tagId,text,count,createdAt,updatedAt,version) values ");
        List<Object> params = new ArrayList<>();
        int id = idBase;
        StringJoiner valueJoiner = new StringJoiner(",");
        List<TagDTO> all = new ArrayList<>();
        for (Map.Entry<String, Integer> tag : countMap.entrySet()) {
            id += 1;
            valueJoiner.add("(?,?,?,?,?,0)");
            params.add(id);
            params.add(tag.getKey());
            params.add(tag.getValue());
            String now = new java.sql.Timestamp(System.currentTimeMillis()).toString();
            params.add(now);
            params.add(now);
            TagDTO tagDO = new TagDTO();
            tagDO.setId((long) id);
            tagDO.setText(tag.getKey());
            tagDO.setCount(Long.valueOf(tag.getValue()));
            tagDO.setKeycode((long) tag.getKey().hashCode());
            all.add(tagDO);
        }
        insertSql.append(valueJoiner);
        execute(insertSql.toString(), params.toArray());
        return all;
    }

    public PageData<TagDTO> find(PageRequest page) {
        return queryPageData(
                "select tagId as id,text,count from " + tableName,
                page,
                new Object[0],
                TagDTO.class);
    }
}
