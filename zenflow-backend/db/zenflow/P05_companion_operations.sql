-- Product-only publication history. Apply once after rehearsal and backup.
-- Actor comes from authenticated SYS_USER; no private chat, model keys or authored text.
CREATE TABLE `zf_companion_operation` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `config_id` BIGINT NOT NULL,
  `administrator_id` BIGINT NOT NULL,
  `administrator_name` VARCHAR(64) NOT NULL,
  `action` VARCHAR(16) NOT NULL COMMENT 'PUBLISH/ROLLBACK/DISABLE',
  `previous_revision_id` BIGINT NULL,
  `target_revision_id` BIGINT NULL,
  `binding_revision_id` BIGINT NOT NULL,
  `changes_json` JSON NOT NULL COMMENT 'Changed field labels only',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `ix_companion_operation_history` (`tenant_id`,`config_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
