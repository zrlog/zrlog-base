ALTER TABLE `user` ADD COLUMN `role` varchar(24) NOT NULL DEFAULT 'contributor';
ALTER TABLE `user` ADD COLUMN `enabled` bit(1) NOT NULL DEFAULT true;
ALTER TABLE `user` ADD COLUMN `authVersion` int(11) NOT NULL DEFAULT 0;
UPDATE `user` SET `role`='admin';
UPDATE `user` SET `role`='owner' WHERE `userId`=(SELECT ownerId FROM (SELECT MIN(`userId`) AS ownerId FROM `user`) AS initial_owner);

CREATE TABLE IF NOT EXISTS `oauth_client` (
    `clientId` varchar(64) NOT NULL,
    `name` varchar(128) NOT NULL,
    `redirectUris` longtext NOT NULL,
    `enabled` bit(1) NOT NULL DEFAULT true,
    PRIMARY KEY (`clientId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `oauth_grant` (
    `id` varchar(64) NOT NULL,
    `userId` int(11) NOT NULL,
    `clientId` varchar(64) NOT NULL,
    `scope` varchar(512) NOT NULL,
    `resource` varchar(1024) NOT NULL,
    `authVersion` int(11) NOT NULL,
    `createdAt` bigint NOT NULL,
    `revoked` bit(1) NOT NULL DEFAULT false,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX `oauth_grant_user` ON `oauth_grant` (`userId`);

CREATE TABLE IF NOT EXISTS `oauth_credential` (
    `hash` varchar(64) NOT NULL,
    `kind` varchar(16) NOT NULL,
    `grantId` varchar(64) DEFAULT NULL,
    `payload` longtext NOT NULL,
    `expiresAt` bigint NOT NULL,
    `used` bit(1) NOT NULL DEFAULT false,
    PRIMARY KEY (`hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX `oauth_credential_grant` ON `oauth_credential` (`grantId`);
CREATE INDEX `oauth_credential_expiry` ON `oauth_credential` (`expiresAt`);

CREATE UNIQUE INDEX `oauth_client_identifier` ON `oauth_client` (`clientId`);
CREATE UNIQUE INDEX `oauth_grant_identifier` ON `oauth_grant` (`id`);
CREATE UNIQUE INDEX `oauth_credential_hash` ON `oauth_credential` (`hash`);
