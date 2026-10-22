-- Persistent note pin state for owner-scoped private records.
-- Apply after P01_core_conversation.sql; existing records start unpinned.
ALTER TABLE `zf_capsule_ext`
  ADD COLUMN `pinned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶，仅用于本人记录列表排序' AFTER `deleted_at`,
  ADD KEY `ix_capsule_ext_pinned_owner` (`tenant_id`, `owner_user_id`, `delete_state`, `pinned`, `capsule_id`);
