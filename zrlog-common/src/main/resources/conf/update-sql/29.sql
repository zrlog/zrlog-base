CREATE TABLE IF NOT EXISTS `user_access_token` (
    `id` varchar(64) NOT NULL,
    `userId` int(11) NOT NULL,
    `name` varchar(128) NOT NULL,
    `tokenHash` varchar(64) NOT NULL,
    `scope` varchar(512) NOT NULL,
    `resource` varchar(2048) NOT NULL,
    `authVersion` int(11) NOT NULL,
    `createdAt` bigint NOT NULL,
    `expiresAt` bigint NOT NULL,
    `revoked` bit(1) NOT NULL DEFAULT false,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE UNIQUE INDEX `user_access_token_hash` ON `user_access_token` (`tokenHash`);
CREATE INDEX `user_access_token_user` ON `user_access_token` (`userId`);
