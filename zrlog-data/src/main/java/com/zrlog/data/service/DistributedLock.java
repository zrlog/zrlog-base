package com.zrlog.data.service;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.LoggerUtil;
import com.zrlog.data.exception.DAOException;
import com.zrlog.model.WebSite;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.sql.SQLException;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.logging.Logger;

/**
 * 简洁的分布式锁，解决一些特殊情况下的并发问题（非高效的方式）
 */
public class DistributedLock implements Lock {

    public static final String LOCK_PREFIX = "distributed_lock_";

    private static final Logger LOGGER = LoggerUtil.getLogger(DistributedLock.class);
    private final String lockKey;
    private final String rawLockKey;

    private final jakarta.inject.Provider<WebSite> websites;

    @AssistedInject
    public DistributedLock(jakarta.inject.Provider<WebSite> websites, @Assisted String lockKey) {
        this.websites = Objects.requireNonNull(websites);
        this.lockKey = LOCK_PREFIX + lockKey;
        this.rawLockKey = lockKey;
    }

    @AssistedFactory
    public interface Factory {
        DistributedLock create(String lockKey);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long totalWaitTime = unit.toMillis(time);
        if (totalWaitTime > 600 * 1000) {
            throw new IllegalArgumentException("waitTime too long");
        }
        if (totalWaitTime <= 0) {
            throw new IllegalArgumentException("waitTime must be a positive number");
        }
        //简单数据库锁，不宜时间太短
        int seek = 200;
        while (true) {
            if (tryLock()) {
                return true;
            }
            totalWaitTime -= seek;
            if (totalWaitTime <= 0) {
                return false;
            }
            Thread.sleep(seek);
        }
    }

    @Override
    public void lock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void lockInterruptibly() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock() {
        try {
            return websites.get()
                    .set("name", lockKey)
                    .set(
                            "remark",
                            "Created at "
                                    + ResultValueConvertUtils.formatDate(
                                            new Date(), "yyyy-MM-dd HH:mm:ss"))
                    .set("value", "locked")
                    .save();
        } catch (SQLException e) {
            LOGGER.warning("tryLock " + rawLockKey + " error " + e.getMessage());
            //throw new RuntimeException(e);
            return false;
        }
    }

    @Override
    public void unlock() {
        try {
            websites.get().set("name", lockKey).delete();
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}
