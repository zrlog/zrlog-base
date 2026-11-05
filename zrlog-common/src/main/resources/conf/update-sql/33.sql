-- Public article pagination and counts. Trailing join keys keep ID/count scans covered.
CREATE INDEX `log_public_order`
    ON `log` (`rubbish`, `privacy`, `logId`, `typeId`, `userId`);
CREATE INDEX `log_public_sticky`
    ON `log` (`rubbish`, `privacy`, `sticky`, `logId`, `typeId`, `userId`);
CREATE INDEX `log_public_type`
    ON `log` (`typeId`, `rubbish`, `privacy`, `logId`, `userId`);
-- MySQL already used this name; explicit DDL also preserves the index on SQLite/D1.
CREATE INDEX `logId` ON `comment` (`logId`);
