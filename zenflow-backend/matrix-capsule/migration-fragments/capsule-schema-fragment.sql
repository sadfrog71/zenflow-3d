-- 闪念胶囊迁移片段。集成任务需分配正式迁移编号后再纳入发布链路。
-- 本片段只创建领域主数据，不写共享菜单、路由或权限数据。

CREATE TABLE IF NOT EXISTS `ai_capsule` (
  `id` bigint NOT NULL COMMENT '胶囊ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `owner_user_id` bigint NOT NULL COMMENT '所有者用户ID',
  `record_type` varchar(32) NOT NULL COMMENT 'IDEA/DATA_FRAGMENT/MEETING_POINT/DECISION/OTHER',
  `title` varchar(120) NOT NULL COMMENT '标题',
  `current_revision_id` bigint NOT NULL COMMENT '当前版本ID',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型',
  `source_label` varchar(255) DEFAULT NULL COMMENT '来源说明',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED/TRASHED',
  `favorite_flag` char(1) NOT NULL DEFAULT '0' COMMENT '是否收藏',
  `captured_at` datetime NOT NULL COMMENT '记录时间',
  `version` int NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志；MVP不提供删除接口',
  PRIMARY KEY (`id`),
  KEY `idx_capsule_owner_status_time` (`tenant_id`, `owner_user_id`, `status`, `update_time`),
  KEY `idx_capsule_owner_type_time` (`tenant_id`, `owner_user_id`, `record_type`, `captured_at`),
  KEY `idx_capsule_current_revision` (`tenant_id`, `owner_user_id`, `current_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闪念胶囊';

CREATE TABLE IF NOT EXISTS `ai_capsule_revision` (
  `id` bigint NOT NULL COMMENT '版本ID',
  `capsule_id` bigint NOT NULL COMMENT '胶囊ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `owner_user_id` bigint NOT NULL COMMENT '所有者用户ID',
  `revision_no` int NOT NULL COMMENT '版本序号',
  `content_format` varchar(16) NOT NULL DEFAULT 'MARKDOWN' COMMENT '内容格式',
  `content` longtext NOT NULL COMMENT '版本原文',
  `content_hash` char(64) NOT NULL COMMENT 'SHA-256',
  `change_reason` varchar(255) DEFAULT NULL COMMENT '变更原因',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_capsule_revision_owner_no` (`tenant_id`, `owner_user_id`, `capsule_id`, `revision_no`),
  KEY `idx_capsule_revision_owner_id` (`tenant_id`, `owner_user_id`, `capsule_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闪念胶囊不可变版本';

-- 为避免 ai_capsule.current_revision_id 与 revision.capsule_id 的循环建表依赖，
-- 片段不声明物理外键；Service 在同一事务内创建/切换版本，查询始终按 tenant_id + owner_user_id 连接。
