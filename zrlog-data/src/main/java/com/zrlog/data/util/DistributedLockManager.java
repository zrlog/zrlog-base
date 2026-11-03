package com.zrlog.data.util;

import com.zrlog.common.vo.LockVO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.data.service.DistributedLock;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * 对分布式锁进行管理
 */
public class DistributedLockManager {

    private final jakarta.inject.Provider<WebSiteRepository> websites;
    private final DistributedLock.Factory locks;

    @Inject
    public DistributedLockManager(
            jakarta.inject.Provider<WebSiteRepository> websites, DistributedLock.Factory locks) {
        this.websites = Objects.requireNonNull(websites);
        this.locks = Objects.requireNonNull(locks);
    }

    public List<LockVO> getLocks() throws Exception {
        List<LockVO> lockList = websites.get().queryList(
                "SELECT name,remark FROM website WHERE name LIKE ? ESCAPE '!' OR name=?", LockVO.class,
                DistributedLock.LOCK_PREFIX.replace("_", "!_") + "%", DistributedLock.SCHEMA_UPGRADE_KEY);
        for (LockVO lock : lockList) {
            if (lock.getName().startsWith(DistributedLock.LOCK_PREFIX)) {
                lock.setName(lock.getName().substring(DistributedLock.LOCK_PREFIX.length()));
            }
        }
        return lockList;
    }

    /**
     * Acquire a closeable handle for lifecycle work such as startup migration.
     */
    public DistributedLock acquire(String lockKey, long time, TimeUnit unit, BooleanSupplier cancelled) throws SQLException {
        DistributedLock lock = locks.create(lockKey);
        try {
            if (lock.tryLock(time, unit, cancelled)) {
                return lock;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for database lock '" + lockKey + "'", interrupted);
        } catch (DAOException failure) {
            throw new SQLException("Cannot acquire database lock '" + lockKey + "'", failure.getCause());
        }
        if (cancelled.getAsBoolean()) {
            throw new SQLException("Database lock wait cancelled because the application is stopping");
        }
        throw new SQLException("Timed out waiting for database lock '" + lockKey + "' after " + unit.toMillis(time)
                + "ms. Another instance may still be running. After an abrupt termination, confirm all previous "
                + "instances have stopped before removing website row '" + DistributedLock.storageKey(lockKey) + "'.");
    }

    /**
     * Administrative force release: callers must first stop work protected by this lock.
     */
    public void releaseLock(String lockKey) {
        String storedKey = DistributedLock.storageKey(lockKey);
        try {
            String owner = websites.get().findValueByName(storedKey);
            if (owner != null) {
                websites.get().deleteLock(storedKey, owner);
            }
        } catch (SQLException failure) {
            throw new DAOException(failure);
        }
    }
}
