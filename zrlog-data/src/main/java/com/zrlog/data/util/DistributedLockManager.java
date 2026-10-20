package com.zrlog.data.util;

import com.zrlog.common.vo.LockVO;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.model.WebSite;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对分布式锁进行管理
 */
public class DistributedLockManager {

    private final jakarta.inject.Provider<WebSite> websites;
    private final DistributedLock.Factory locks;

    @Inject
    public DistributedLockManager(
            jakarta.inject.Provider<WebSite> websites, DistributedLock.Factory locks) {
        this.websites = Objects.requireNonNull(websites);
        this.locks = Objects.requireNonNull(locks);
    }

    public List<LockVO> getLocks() throws Exception {
        List<LockVO> lockList = new ArrayList<>();
        for (Map<String, Object> stringObjectMap :
                websites.get()
                        .queryListWithParams(
                                "select * from website where name like ?",
                                DistributedLock.LOCK_PREFIX + "%")) {
            LockVO lockVO = new LockVO();
            lockVO.setName(((String) stringObjectMap.get("name")).replaceFirst(DistributedLock.LOCK_PREFIX, ""));
            lockVO.setRemark((String) stringObjectMap.get("remark"));
            lockList.add(lockVO);
        }
        return lockList;
    }

    public void releaseLock(String lockKey) {
        locks.create(lockKey).unlock();
    }
}
