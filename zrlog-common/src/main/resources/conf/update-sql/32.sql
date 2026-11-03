-- Assistant storage starts empty. Existing website data is neither copied nor removed.
CREATE TABLE IF NOT EXISTS `assistant_task` (
    `id` varchar(48) NOT NULL,
    `ownerId` int NOT NULL,
    `name` varchar(160) NOT NULL,
    `nameKey` varchar(160) DEFAULT NULL,
    `type` varchar(120) NOT NULL,
    `enabled` bit(1) NOT NULL,
    `revision` bigint NOT NULL,
    `updatedBy` int DEFAULT NULL,
    `schedule` longtext NOT NULL,
    `parameters` longtext NOT NULL,
    `executionActions` longtext NOT NULL,
    `nextRunAt` datetime(3) DEFAULT NULL,
    `lastRunAt` datetime(3) DEFAULT NULL,
    `lastStatus` varchar(32) DEFAULT NULL,
    `activeRunId` varchar(80) DEFAULT NULL,
    `activeRun` longtext DEFAULT NULL,
    `leaseUntil` datetime(3) DEFAULT NULL,
    `dueAt` datetime(3) DEFAULT NULL,
    `createdAt` datetime(3) DEFAULT NULL,
    `updatedAt` datetime(3) DEFAULT NULL,
    `version` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `assistant_task_id` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX `assistant_task_owner` ON `assistant_task` (`ownerId`, `id`);
CREATE INDEX `assistant_task_due` ON `assistant_task` (`dueAt`, `id`);
CREATE INDEX `assistant_task_active` ON `assistant_task` (`activeRunId`);

CREATE TABLE IF NOT EXISTS `assistant_task_run` (
    `id` varchar(80) NOT NULL,
    `taskId` varchar(48) NOT NULL,
    `ownerId` int NOT NULL,
    `taskName` varchar(160) NOT NULL,
    `taskNameKey` varchar(160) DEFAULT NULL,
    `taskType` varchar(120) NOT NULL,
    `taskRevision` bigint NOT NULL,
    `scheduledAt` datetime(3) NOT NULL,
    `startedAt` datetime(3) NOT NULL,
    `finishedAt` datetime(3) DEFAULT NULL,
    `trigger` varchar(16) NOT NULL,
    `triggeredBy` int DEFAULT NULL,
    `executionUserId` int DEFAULT NULL,
    `status` varchar(32) NOT NULL,
    `errorCode` varchar(160) DEFAULT NULL,
    `notifyOwner` bit(1) NOT NULL,
    `summary` text DEFAULT NULL,
    `planSnapshot` longtext DEFAULT NULL,
    `result` longtext DEFAULT NULL,
    `createdAt` datetime(3) DEFAULT NULL,
    `updatedAt` datetime(3) DEFAULT NULL,
    `version` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `assistant_task_run_id` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX `assistant_run_owner` ON `assistant_task_run` (`ownerId`, `startedAt`, `id`);
CREATE INDEX `assistant_run_task` ON `assistant_task_run` (`taskId`, `startedAt`, `id`);
CREATE INDEX `assistant_run_notice` ON `assistant_task_run` (`ownerId`, `notifyOwner`, `finishedAt`, `id`);
CREATE INDEX `assistant_run_retention` ON `assistant_task_run` (`status`, `finishedAt`, `id`);

CREATE TABLE IF NOT EXISTS `assistant_notice_read` (
    `id` varchar(100) NOT NULL,
    `runId` varchar(80) NOT NULL,
    `userId` int NOT NULL,
    `readAt` datetime(3) NOT NULL,
    `createdAt` datetime(3) DEFAULT NULL,
    `updatedAt` datetime(3) DEFAULT NULL,
    `version` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `assistant_notice_read_id` (`id`),
    UNIQUE KEY `assistant_notice_user_run` (`userId`, `runId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX `assistant_notice_run` ON `assistant_notice_read` (`runId`);
