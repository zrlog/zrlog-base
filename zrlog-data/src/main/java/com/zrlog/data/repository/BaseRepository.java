package com.zrlog.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.entity.BaseEntity;

/** Compatibility base for repositories compiled against the ZrLog package. */
public abstract class BaseRepository<E extends BaseEntity<ID>, ID>
        extends com.hibegin.common.dao.entity.BaseRepository<E, ID> {

    protected BaseRepository(Class<E> entityClass, DataSourceWrapper source) {
        super(entityClass, source);
    }
}
