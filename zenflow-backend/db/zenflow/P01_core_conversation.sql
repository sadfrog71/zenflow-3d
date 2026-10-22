-- P1 approved v0.3 core tables. Rehearse on a dedicated empty schema; apply once after product backup.
-- No P2 media / P3 CMS / P4 community tables.

-- Existing capsule service requires reference migration 35matrix_capsule_tags.sql.
-- B00 extracted CREATE TABLE only; P0 never opened the capsule facade.
ALTER TABLE `ai_capsule` ADD COLUMN `tags` VARCHAR(1000) NULL AFTER `source_label`;
ALTER TABLE `ai_capsule` ADD KEY `idx_capsule_owner_tag` (`tenant_id`,`owner_user_id`,`tags`(128));

-- Existing managed publisher requires directory and audit archives omitted from P0.
-- Reuse PBI-2601 + 22matrix_skill_packages + 58matrix_managed_skill definitions, no legacy seeds.
CREATE TABLE IF NOT EXISTS `ai_skill` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID',
    `tenant_id`   VARCHAR(40)  NOT NULL DEFAULT '0' COMMENT '租户ID，0=系统内置技能（所有租户共享），非0=租户自定义技能（本期不开放）',
    `management_mode` VARCHAR(16) NOT NULL DEFAULT 'LEGACY' COMMENT 'LEGACY/MANAGED',
    `skill_code`  VARCHAR(50)  NOT NULL COMMENT '技能标识: artifact/search-engine/chat-db/text-to-image/text-to-mindmap',
    `skill_name`  VARCHAR(100) NOT NULL COMMENT '技能名称',
    `description` TEXT                  COMMENT '技能描述',
    `icon`        VARCHAR(500)          COMMENT '图标URL或emoji',
    `skill_type`  VARCHAR(20)  NOT NULL DEFAULT 'builtin' COMMENT '技能类型: builtin(内置)/custom(自定义，本期不开放创建)',
    `impl_type`   VARCHAR(30)  NOT NULL COMMENT '实现类型: prompt_injection/dify_plugin/matrix_plugin',
    `config`      JSON                  COMMENT '技能配置JSON，schema由impl_type决定',
    `is_enabled`  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否可用',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序权重',
    `create_by`   VARCHAR(64)           COMMENT '创建人',
    `update_by`   VARCHAR(64)           COMMENT '更新人',
    `create_time` DATETIME              COMMENT '创建时间',
    `update_time` DATETIME              COMMENT '更新时间',
    `del_flag`    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `skill_code`),
    INDEX `idx_tenant_enabled` (`tenant_id`, `is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='技能目录表';

CREATE TABLE IF NOT EXISTS `ai_skill_package` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(40) NOT NULL COMMENT '租户ID',
  `source_channel` VARCHAR(16) NOT NULL DEFAULT 'LEGACY' COMMENT 'LEGACY/MANAGED',
  `skill_id` bigint NOT NULL COMMENT '技能目录ID',
  `package_version` varchar(32) NOT NULL DEFAULT 'v1' COMMENT '包版本',
  `original_filename` varchar(255) NOT NULL COMMENT '原始文件名',
  `package_sha256` char(64) NOT NULL COMMENT '包内容SHA-256',
  `package_size` bigint NOT NULL COMMENT '包大小字节',
  `manifest_json` json DEFAULT NULL COMMENT '平台Manifest快照',
  `validation_report` json DEFAULT NULL COMMENT '导入校验报告',
  `package_status` varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW/APPROVED/REJECTED/ARCHIVED',
  `archive_data` longblob NOT NULL COMMENT '受限大小的原始ZIP归档',
  `create_by` varchar(64) DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_package_channel_hash` (`tenant_id`, `skill_id`, `source_channel`, `package_sha256`),
  KEY `idx_skill_package_status` (`tenant_id`, `skill_id`, `package_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill导入包与审核记录';

CREATE TABLE `zf_companion_config` (
  `id` BIGINT NOT NULL COMMENT '应用生成的主键',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `config_key` VARCHAR(64) NOT NULL COMMENT '产品内唯一键，例如 default',
  `display_name` VARCHAR(120) NOT NULL COMMENT '后台名称',
  `skill_binding_id` BIGINT NOT NULL COMMENT '唯一在线版本由该 ai_skill_binding 指针决定',
  `status` VARCHAR(16) NOT NULL DEFAULT 'DISABLED' COMMENT 'ENABLED / DISABLED',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_companion_key` (`tenant_id`, `config_key`),
  UNIQUE KEY `uk_companion_binding` (`tenant_id`, `skill_binding_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='陪伴配置入口';

CREATE TABLE `zf_companion_revision` (
  `id` BIGINT NOT NULL COMMENT '应用生成的主键',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `config_id` BIGINT NOT NULL COMMENT '陪伴配置 ID',
  `revision_no` BIGINT NOT NULL COMMENT '配置内顺序号',
  `authoring_json` JSON NOT NULL COMMENT '原则、核心提要、示例、引用展示规则等编辑材料',
  `retrieval_policy_json` JSON NOT NULL COMMENT '已核准参考范围、相关性、数量和预算',
  `skill_release_id` BIGINT NULL COMMENT '准备完成后的托管 Skill 发布 ID',
  `model_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '与 Skill executorRef.resourceId 一致的平台模型 ID',
  `model_config_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型标识与能力/外发配置摘要，不含密钥',
  `index_namespace` VARCHAR(160) NULL COMMENT '本修订的知识索引范围',
  `reference_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '排序后的已发布片段清单摘要',
  `release_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Skill、资料、检索规则与模型配置的总摘要',
  `state` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PREPARING / READY / FAILED / REVOKED',
  `sealed_at` DATETIME(3) NULL COMMENT 'READY 时冻结',
  `lease_epoch` BIGINT NOT NULL DEFAULT 0 COMMENT '准备任务工作者代次',
  `lease_owner` VARCHAR(128) NULL COMMENT '准备任务执行者',
  `lease_until` DATETIME(3) NULL COMMENT '准备租约到期',
  `attempts` BIGINT NOT NULL DEFAULT 0 COMMENT '准备尝试次数',
  `error_code` VARCHAR(64) NULL COMMENT '准备错误码',
  `created_by` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '管理员 ID',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_companion_revision` (`tenant_id`, `config_id`, `revision_no`),
  KEY `ix_companion_prepare` (`state`, `lease_until`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='陪伴配置的草稿与不可变发布修订';

CREATE TABLE `zf_reference_snapshot` (
  `id` BIGINT NOT NULL COMMENT '应用生成的主键',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `companion_revision_id` BIGINT NOT NULL COMMENT '所属陪伴配置修订',
  `dataset_id` BIGINT NOT NULL COMMENT '既有知识库 ID',
  `document_id` BIGINT NOT NULL COMMENT '既有文档 ID',
  `slice_id` BIGINT NULL COMMENT '既有切片 ID；完整短示例可为空',
  `source_version` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源版本或固定摘要',
  `source_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结正文 SHA-256',
  `reference_kind` VARCHAR(24) NOT NULL COMMENT 'MATERIAL / EXAMPLE',
  `title` VARCHAR(200) NOT NULL COMMENT '来源名称',
  `locale` VARCHAR(16) NOT NULL COMMENT '资料语言',
  `content` MEDIUMTEXT NOT NULL COMMENT '冻结的实际参考内容',
  `source_locator` VARCHAR(512) NULL COMMENT '页码、章节等可核对位置',
  `citation_visibility` VARCHAR(16) NOT NULL COMMENT 'PUBLIC / TITLE_ONLY / INTERNAL',
  `status` VARCHAR(16) NOT NULL DEFAULT 'VALID' COMMENT 'VALID / REVOKED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`id`),
  KEY `ix_reference_revision` (`tenant_id`, `companion_revision_id`, `status`, `id`),
  KEY `ix_reference_revoke` (`tenant_id`, `document_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='已发布参考片段快照';

CREATE TABLE `zf_conversation_binding` (
  `session_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '现有 ai_assistant_session.id',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '消费者 app_user.user_id；仅 APP_USER',
  `companion_config_id` BIGINT NOT NULL COMMENT '下轮从该配置解析生效修订',
  `locale` VARCHAR(16) NOT NULL COMMENT '本会话语言',
  `active_run_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '首版同会话最多一个未终止运行',
  `deleted_at` DATETIME(3) NULL COMMENT '会话删除标记',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`session_id`),
  KEY `ix_conversation_owner` (`tenant_id`, `owner_user_id`, `deleted_at`, `updated_at`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='此间消费者会话扩展';

CREATE TABLE `zf_companion_run_context` (
  `run_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '现有 ai_assistant_execution_run.id',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '消费者 app_user.user_id；仅 APP_USER',
  `session_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '现有会话 ID',
  `skill_run_id` BIGINT NULL COMMENT '托管 Skill 运行 ID',
  `binding_revision_id` BIGINT NOT NULL COMMENT '固定的 Skill 绑定修订',
  `companion_revision_id` BIGINT NOT NULL COMMENT '固定的此间配置修订',
  `model_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际固定的平台模型 ID',
  `model_config_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不含密钥的配置摘要',
  `history_end_seq` BIGINT NOT NULL COMMENT '本轮可见历史消息截止序号',
  `preferences_snapshot_json` JSON NOT NULL COMMENT '本轮语言与用户明确偏好的固定副本',
  `context_epoch` BIGINT NOT NULL COMMENT '开始时个人授权代次',
  `auth_version` BIGINT NOT NULL COMMENT '接收本轮时账号认证代次',
  `input_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本轮消息和显式来源请求摘要',
  `lineage_state` VARCHAR(16) NOT NULL DEFAULT 'VALID' COMMENT 'VALID / INVALIDATED',
  `response_message_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '最终回答消息 ID',
  `validated_result_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '已校验完整结果摘要，不复制回答正文',
  `finish_reason` VARCHAR(32) NULL COMMENT '供应商完成原因',
  `result_ready_at` DATETIME(3) NULL COMMENT '完整结果已保存时间',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`run_id`),
  KEY `ix_run_context_owner` (`tenant_id`, `owner_user_id`, `session_id`, `created_at`),
  KEY `ix_run_context_release` (`tenant_id`, `companion_revision_id`, `run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='当轮固定配置与来源边界';

CREATE TABLE `zf_run_source` (
  `id` BIGINT NOT NULL COMMENT '应用生成的主键',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '消费者 app_user.user_id；仅 APP_USER',
  `run_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '助手运行 ID',
  `source_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源类型、ID、版本与授权的稳定摘要',
  `source_kind` VARCHAR(24) NOT NULL COMMENT 'REFERENCE / CAPSULE / MEMORY',
  `reference_snapshot_id` BIGINT NULL COMMENT '参考快照 ID',
  `capsule_id` BIGINT NULL COMMENT '个人胶囊 ID；个人来源必填',
  `capsule_revision_id` BIGINT NULL COMMENT '被允许的正文版本',
  `memory_id` BIGINT NULL COMMENT '用户确认的摘要 ID',
  `consent_id` BIGINT NULL COMMENT '个人使用授权 ID',
  `consent_version` BIGINT NULL COMMENT '固定的授权版本',
  `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际来源内容摘要',
  `use_role` VARCHAR(24) NOT NULL COMMENT 'DIRECT / HISTORY_DERIVED',
  `derived_from_message_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '历史派生来源消息',
  `citation_key` VARCHAR(128) NOT NULL COMMENT '本次模型可用引用键',
  `cited_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '完整回答实际采用该引用',
  `status` VARCHAR(16) NOT NULL DEFAULT 'VALID' COMMENT 'VALID / INVALIDATED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_source` (`tenant_id`, `run_id`, `source_key`),
  KEY `ix_run_source_capsule` (`tenant_id`, `owner_user_id`, `capsule_id`, `status`),
  KEY `ix_run_source_reference` (`tenant_id`, `reference_snapshot_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='运行使用的来源与派生关系';

CREATE TABLE `zf_capsule_ext` (
  `capsule_id` BIGINT NOT NULL COMMENT '原 ai_capsule.id，同时为外部 noteId',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '消费者 app_user.user_id；仅 APP_USER',
  `kind` VARCHAR(24) NOT NULL COMMENT 'TEXT / VOICE / CHAT_EXCERPT',
  `text_state` VARCHAR(16) NOT NULL COMMENT 'EMPTY / CONFIRMED',
  `client_record_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本机生成的稳定记录 ID',
  `request_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '首次创建输入摘要',
  `delete_state` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETE_REQUESTED / DELETED',
  `deleted_at` DATETIME(3) NULL COMMENT '访问禁用时间',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`capsule_id`),
  UNIQUE KEY `uk_capsule_client` (`tenant_id`, `owner_user_id`, `client_record_id`),
  KEY `ix_capsule_ext_owner` (`tenant_id`, `owner_user_id`, `delete_state`, `kind`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='此间记录扩展';

CREATE TABLE `zf_capsule_revision_source` (
  `capsule_revision_id` BIGINT NOT NULL COMMENT '原 ai_capsule_revision.id',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '消费者 app_user.user_id；仅 APP_USER',
  `capsule_id` BIGINT NOT NULL COMMENT '原胶囊 ID',
  `source_kind` VARCHAR(24) NOT NULL COMMENT 'MANUAL / TRANSCRIPT / CHAT_EXCERPT / AUDIO_EMPTY',
  `transcription_job_id` BIGINT NULL COMMENT '手动转写任务 ID',
  `source_message_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '选中的聊天消息 ID',
  `media_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '产生转写的原音摘要',
  `author_role` VARCHAR(16) NOT NULL COMMENT 'USER / ASSISTANT / MIXED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`capsule_revision_id`),
  UNIQUE KEY `uk_revision_transcript` (`tenant_id`, `transcription_job_id`),
  KEY `ix_revision_source_capsule` (`tenant_id`, `owner_user_id`, `capsule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='胶囊文字版本的来源关系';

CREATE TABLE `zf_capsule_consent` (
  `id` BIGINT NOT NULL COMMENT '应用生成的主键',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '消费者 app_user.user_id；仅 APP_USER',
  `capsule_id` BIGINT NOT NULL COMMENT '胶囊 ID',
  `capsule_revision_id` BIGINT NOT NULL COMMENT '确定的文字版本 ID',
  `source_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所授权文本摘要',
  `scope_type` VARCHAR(16) NOT NULL COMMENT 'TURN / SESSION / PERSISTENT',
  `scope_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'TURN 为 runId，SESSION 为 sessionId，PERSISTENT 为 *',
  `state` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / REVOKED / STALE',
  `revoked_at` DATETIME(3) NULL COMMENT '撤回时间',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_capsule_consent_scope` (`tenant_id`, `owner_user_id`, `capsule_revision_id`, `scope_type`, `scope_key`),
  KEY `ix_capsule_consent_active` (`tenant_id`, `owner_user_id`, `state`, `scope_type`, `capsule_id`),
  CONSTRAINT `ck_capsule_consent_1` CHECK (scope_type IN ('TURN','SESSION','PERSISTENT')),
  CONSTRAINT `ck_capsule_consent_2` CHECK ((scope_type = 'PERSISTENT' AND scope_key = '*') OR (scope_type IN ('TURN','SESSION') AND scope_key <> '*'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='胶囊的模型使用授权';

CREATE TABLE `zf_memory` (
  `id` BIGINT NOT NULL COMMENT '应用生成的主键',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '消费者 app_user.user_id；仅 APP_USER',
  `consent_id` BIGINT NOT NULL COMMENT '有效 PERSISTENT 授权',
  `capsule_id` BIGINT NOT NULL COMMENT '来源胶囊',
  `capsule_revision_id` BIGINT NOT NULL COMMENT '来源正文版本',
  `summary` VARCHAR(2000) NOT NULL COMMENT '用户确认的摘要，不自动推断画像',
  `state` VARCHAR(16) NOT NULL COMMENT 'ACTIVE / PAUSED / STALE / REVOKED',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memory_consent` (`tenant_id`, `consent_id`),
  KEY `ix_memory_owner` (`tenant_id`, `owner_user_id`, `state`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='用户确认的简短记忆';

-- ai_skill_run.actor_type / subject_type are already supplied by B01; do not add again.

-- Existing native Assistant JDBC lifecycle columns from reference 31/40/42/44/45/51/61.
-- No enterprise seeds, billing changes, semantic executors or metric projection activation.
DROP PROCEDURE IF EXISTS _invelle_core_add_col;
DELIMITER //
CREATE PROCEDURE _invelle_core_add_col(IN t VARCHAR(64),IN c VARCHAR(64),IN definition TEXT)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) THEN
  SET @invelle_ddl=CONCAT('ALTER TABLE `',t,'` ADD COLUMN ',definition);
  PREPARE invelle_stmt FROM @invelle_ddl; EXECUTE invelle_stmt; DEALLOCATE PREPARE invelle_stmt;
 END IF;
END //
DELIMITER ;
CALL _invelle_core_add_col('ai_assistant_execution_run','provider','`provider` varchar(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','model','`model` varchar(255) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','prompt_tokens','`prompt_tokens` bigint DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','completion_tokens','`completion_tokens` bigint DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','total_tokens','`total_tokens` bigint DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','usage_source','`usage_source` varchar(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','request_id','`request_id` varchar(128) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','executed_skills_json','`executed_skills_json` text DEFAULT NULL COMMENT ''真实执行Skill列表；NULL表示历史证据未知''');
CALL _invelle_core_add_col('ai_assistant_execution_run','execution_status','`execution_status` varchar(32) DEFAULT NULL COMMENT ''执行状态''');
CALL _invelle_core_add_col('ai_assistant_execution_run','skill_release_id','`skill_release_id` bigint DEFAULT NULL COMMENT ''Skill发布投影ID''');
CALL _invelle_core_add_col('ai_assistant_execution_run','skill_release_version','`skill_release_version` varchar(32) DEFAULT NULL COMMENT ''Skill发布版本''');
CALL _invelle_core_add_col('ai_assistant_execution_run','skill_package_sha256','`skill_package_sha256` char(64) DEFAULT NULL COMMENT ''Skill包SHA-256''');
CALL _invelle_core_add_col('ai_assistant_execution_run','skill_instruction_digest','`skill_instruction_digest` char(64) DEFAULT NULL COMMENT ''Prompt指令SHA-256''');
CALL _invelle_core_add_col('ai_assistant_execution_run','context_pack_id','`context_pack_id` varchar(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','response_message_id','`response_message_id` varchar(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','user_message_id','`user_message_id` varchar(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','request_hash','`request_hash` char(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','dispatch_status','`dispatch_status` varchar(32) NOT NULL DEFAULT ''LEGACY''');
CALL _invelle_core_add_col('ai_assistant_execution_run','input_snapshot_json','`input_snapshot_json` longtext DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','draft_content','`draft_content` longtext DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','last_event_seq','`last_event_seq` bigint NOT NULL DEFAULT 0');
CALL _invelle_core_add_col('ai_assistant_execution_run','cancel_requested','`cancel_requested` tinyint NOT NULL DEFAULT 0');
CALL _invelle_core_add_col('ai_assistant_execution_run','owner_instance_id','`owner_instance_id` varchar(128) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','lease_expires_at','`lease_expires_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','model_invoked_at','`model_invoked_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','started_at','`started_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','completed_at','`completed_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','error_message_safe','`error_message_safe` varchar(512) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','source_mode','`source_mode` varchar(16) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','phase','`phase` varchar(32) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','catalog_snapshot_json','`catalog_snapshot_json` longtext DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','catalog_digest','`catalog_digest` char(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','plan_snapshot_json','`plan_snapshot_json` longtext DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','plan_digest','`plan_digest` char(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','analysis_result_json','`analysis_result_json` longtext DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','analysis_result_digest','`analysis_result_digest` char(64) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','route_intent','`route_intent` varchar(32) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','route_mode','`route_mode` varchar(32) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','route_trigger','`route_trigger` varchar(32) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','route_confidence','`route_confidence` decimal(5,4) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','route_planner_used','`route_planner_used` tinyint DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','route_decided_at','`route_decided_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','route_clarification_count','`route_clarification_count` int DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','model_first_chunk_at','`model_first_chunk_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_execution_run','first_visible_answer_at','`first_visible_answer_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_session','title_source','`title_source` varchar(16) NOT NULL DEFAULT ''DEFAULT''');
CALL _invelle_core_add_col('ai_assistant_session','active_scopes_json','`active_scopes_json` longtext DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_session','active_skill_code','`active_skill_code` varchar(128) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_session','last_message_at','`last_message_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_session','archived_at','`archived_at` datetime(6) DEFAULT NULL');
CALL _invelle_core_add_col('ai_assistant_message','attachments_json','`attachments_json` longtext DEFAULT NULL');
DROP PROCEDURE _invelle_core_add_col;
CREATE TABLE IF NOT EXISTS `ai_assistant_run_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) NOT NULL,
  `seq` bigint NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `event_version` int NOT NULL DEFAULT 1,
  `payload_json` longtext NOT NULL,
  `correlation_id` varchar(128) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assistant_run_event_seq` (`run_id`, `seq`),
  KEY `idx_assistant_run_event_replay` (`run_id`, `seq`, `created_at`),
  KEY `idx_assistant_run_event_cleanup` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE ai_assistant_execution_run ADD UNIQUE KEY uk_assistant_run_idempotency(session_id,client_message_id), ADD KEY idx_assistant_run_dispatch(dispatch_status,status,lease_expires_at,created_at), ADD KEY idx_assistant_run_response(response_message_id);
