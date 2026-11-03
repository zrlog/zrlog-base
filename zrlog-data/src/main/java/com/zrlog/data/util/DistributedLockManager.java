package com.zrlog.data.util;

import com.zrlog.common.vo.LockVO;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.data.repository.WebSiteRepository;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        List<LockVO> lockList = new ArrayList<>();
        for (com.zrlog.model.WebSite row :
                websites.get()
                        .select(
                                "WHERE name LIKE ?",
                                DistributedLock.LOCK_PREFIX + "%")) {
            LockVO lockVO = new LockVO();
            lockVO.setName(row.getName().replaceFirst(DistributedLock.LOCK_PREFIX, ""));
            lockVO.setRemark(row.getRemark());
            lockList.add(lockVO);
        }
        return lockList;
    }

    public void releaseLock(String lockKey) {
        locks.create(lockKey).unlock();
    }
}
