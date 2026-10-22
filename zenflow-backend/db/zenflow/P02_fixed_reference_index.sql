-- Product-only extension of the reviewed P1 revision. Apply once after schema preflight and backup.
-- The complete public descriptor is pinned before external indexing; credentials are never copied here.
ALTER TABLE `zf_companion_revision`
  ADD COLUMN `index_configuration_json` JSON NULL COMMENT '固定向量模型和存储配置，不含密钥' AFTER `index_namespace`,
  ADD COLUMN `index_configuration_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '固定索引配置摘要' AFTER `index_configuration_json`;
