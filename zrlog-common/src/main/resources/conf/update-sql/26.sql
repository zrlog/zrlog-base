ALTER TABLE `user` ADD COLUMN `passkeyUserHandle` varchar(64) DEFAULT NULL;
CREATE UNIQUE INDEX `user_passkey_handle` ON `user` (`passkeyUserHandle`);

CREATE TABLE IF NOT EXISTS `user_passkey`
(
    `id`                 int(11) NOT NULL AUTO_INCREMENT,
    `userId`             int(11)      NOT NULL,
    `credentialIdHash`   varchar(64)  NOT NULL,
    `credentialId`       longtext     NOT NULL,
    `publicKeyCose`      longtext     NOT NULL,
    `signatureCount`     bigint       NOT NULL DEFAULT 0,
    `transports`         varchar(255) DEFAULT NULL,
    `name`               varchar(128) DEFAULT NULL,
    `aaguid`             varchar(64)  DEFAULT NULL,
    `backupEligible`     bit(1)       NOT NULL DEFAULT false,
    `backupState`        bit(1)       NOT NULL DEFAULT false,
    `origin`             varchar(512) NOT NULL,
    `rpId`               varchar(255) NOT NULL,
    `createdAt`          bigint       NOT NULL,
    `lastUsedAt`         bigint       DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `user_passkey_credential_hash`
    ON `user_passkey` (`credentialIdHash`);
CREATE INDEX `user_passkey_user`
    ON `user_passkey` (`userId`);

CREATE TABLE IF NOT EXISTS `user_passkey_challenge`
(
    `id`          int(11) NOT NULL AUTO_INCREMENT,
    `requestId`   varchar(64) NOT NULL,
    `ceremony`    varchar(32) NOT NULL,
    `userId`      int(11) DEFAULT NULL,
    `requestJson` longtext    NOT NULL,
    `createdAt`   bigint      NOT NULL,
    `expiresAt`   bigint      NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `user_passkey_challenge_request`
    ON `user_passkey_challenge` (`requestId`);
CREATE INDEX `user_passkey_challenge_expiry`
    ON `user_passkey_challenge` (`expiresAt`);
CREATE INDEX `user_passkey_challenge_user_ceremony`
    ON `user_passkey_challenge` (`userId`, `ceremony`);
