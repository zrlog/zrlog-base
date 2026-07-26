ALTER TABLE log ADD COLUMN extensions longtext DEFAULT NULL;
CREATE TABLE IF NOT EXISTS `log_extension_index`
(
    `id`              bigint(20)   NOT NULL AUTO_INCREMENT,
    `log_id`          int(11)      NOT NULL,
    `namespace`       varchar(64)  NOT NULL,
    `extension_path`  varchar(191) NOT NULL,
    `extension_value` varchar(512) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY               `log_extension_article` (`log_id`, `namespace`),
    KEY               `log_extension_filter` (`namespace`, `extension_path`, `extension_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;
