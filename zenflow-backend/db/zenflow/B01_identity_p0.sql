-- 此间正式开发数据库结构设计 v0.3 / 2026-10-04
-- DESIGN ONLY：未连接数据库、未执行。不是可直接用于生产的迁移包。
-- 语法目标：锁定参考源码 db/Dockerfile 中 MySQL 8.0.32；部署版本另行验证，不推荐升级或复制旧凭据。
-- 前置：完成经审查的独立 schema 基线，包括胶囊、助手、Skill、CMS、账号表及相关增量。
-- 没有 USE / CREATE DATABASE / DROP / 示例账号 / 真实密钥；执行库由正式迁移流程明确指定。
-- ID 由应用分配；新表 tenant_id 为 BIGINT，调用既有字符串 ID 模块时使用十进制规范字符串。
-- 跨模块引用是逻辑外键：同事务校验租户、主体与存在性，不跨旧 varchar/bigint 列强加外键。
-- 新表统一 UTC DATETIME(3)、utf8mb4_bin、显式业务状态；JSON 中不存凭据、音频正文或未授权私人数据。

-- B01：在已核准的独立空库基线后执行的结构变更草案。
-- 这些 ALTER 不是幂等迁移；正式实现时由迁移版本记录保证一次执行，并先检查实际列与索引。
ALTER TABLE `app_user`
  MODIFY COLUMN `email` VARCHAR(254) NULL,
  ADD COLUMN `email_normalized` VARCHAR(254) COLLATE utf8mb4_bin NULL COMMENT '此间邮箱身份规范值',
  ADD COLUMN `email_verified_at` DATETIME(3) NULL,
  ADD COLUMN `auth_version` BIGINT NOT NULL DEFAULT 1,
  ADD UNIQUE KEY `uk_zf_app_email` (`tenant_id`, `email_normalized`);
ALTER TABLE `ai_capsule`
  ADD COLUMN `owner_subject_type` VARCHAR(16) NOT NULL DEFAULT 'ADMIN_USER',
  ADD KEY `ix_zf_capsule_owner` (`tenant_id`, `owner_subject_type`, `owner_user_id`, `status`, `update_time`, `id`);
ALTER TABLE `ai_capsule_revision`
  ADD COLUMN `owner_subject_type` VARCHAR(16) NOT NULL DEFAULT 'ADMIN_USER',
  ADD KEY `ix_zf_capsule_revision_owner` (`tenant_id`, `owner_subject_type`, `owner_user_id`, `capsule_id`, `revision_no`);
ALTER TABLE `ai_assistant_session`
  ADD COLUMN `owner_subject_type` VARCHAR(16) NOT NULL DEFAULT 'ADMIN_USER',
  ADD KEY `ix_zf_session_owner` (`tenant_id`, `owner_subject_type`, `owner_user_id`, `status`, `last_message_at`, `id`);
ALTER TABLE `ai_skill_run`
  ADD COLUMN `actor_type` VARCHAR(16) NOT NULL DEFAULT 'ADMIN_USER',
  ADD COLUMN `subject_type` VARCHAR(16) NOT NULL DEFAULT 'ADMIN_USER';
-- 运行幂等摘要与 Identity 契约同时纳入 actor_type / subject_type；不重写既有历史摘要。
-- App 显式写 APP_USER；上述 ADMIN_USER 默认值仅保护原框架语义，不能作为此间消费者的默认身份。
ALTER TABLE `app_article`
  MODIFY COLUMN `content` LONGTEXT NULL,
  ADD KEY `ix_zf_article_tenant` (`tenant_id`, `id`);

-- P0 / 消费者产品偏好
-- 账号密码、邮箱、封禁仍在 app_user；此表不是另一套账号。
CREATE TABLE `zf_profile` (
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '消费者 app_user.user_id；仅 APP_USER',
  `public_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '社区公开身份，随机生成，不暴露内部数字 ID',
  `locale` VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT 'zh-CN / en-US',
  `companion_preferences` JSON NOT NULL COMMENT '用户明确设置的表达偏好',
  `memory_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '以后参考总开关',
  `context_epoch` BIGINT NOT NULL DEFAULT 1 COMMENT '个人授权变更代次，撤回时递增',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`tenant_id`, `owner_user_id`),
  UNIQUE KEY `uk_profile_public` (`tenant_id`, `public_id`),
  CONSTRAINT `ck_profile_1` CHECK (memory_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='消费者产品偏好';

-- P0 / 业务动作幂等凭据
-- 仅包装数据库内的短事务；同事务写业务结果和凭据，回滚则一起回滚；长运行用各自任务表。
CREATE TABLE `zf_command_receipt` (
  `id` BIGINT NOT NULL COMMENT '应用生成的主键',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `actor_type` VARCHAR(16) NOT NULL COMMENT 'APP_USER / ADMIN_USER / SERVICE',
  `actor_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '受信主体 ID',
  `operation` VARCHAR(64) NOT NULL COMMENT '已登记操作，例如 CONSENT_GRANT',
  `idempotency_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端稳定动作 ID',
  `request_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化输入摘要',
  `state` VARCHAR(16) NOT NULL COMMENT 'PROCESSING / SUCCEEDED',
  `resource_type` VARCHAR(48) NULL COMMENT '结果资源类型',
  `resource_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '结果资源 ID',
  `result_meta` JSON NULL COMMENT '最少结果元数据，不存正文',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_command_scope` (`tenant_id`, `actor_type`, `actor_id`, `operation`, `idempotency_key`),
  CONSTRAINT `ck_command_receipt_1` CHECK (state IN ('PROCESSING','SUCCEEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='业务动作幂等凭据';

-- P0 / 事务提交后的可恢复动作
-- 复用现有调度与日志设施执行；不是新的任务编排平台。失败保留可重试事件，访问禁用在业务事务内立即生效。
CREATE TABLE `zf_outbox` (
  `id` BIGINT NOT NULL COMMENT '应用生成的主键',
  `tenant_id` BIGINT NOT NULL COMMENT '受信产品租户 ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '缓存失效、来源撤回或资源清理处理器',
  `aggregate_type` VARCHAR(48) NOT NULL COMMENT '资源类别',
  `aggregate_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源 ID',
  `dedupe_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '生产方生成唯一事件键',
  `payload_json` JSON NOT NULL COMMENT '来源 ID、版本与操作者；不得含密钥或私人正文',
  `state` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCEEDED / DEAD',
  `attempts` BIGINT NOT NULL DEFAULT 0 COMMENT '尝试次数',
  `lease_epoch` BIGINT NOT NULL DEFAULT 0 COMMENT '防止旧工作进程写回',
  `lease_owner` VARCHAR(128) NULL COMMENT '当前执行者',
  `lease_until` DATETIME(3) NULL COMMENT '租约到期',
  `next_attempt_at` DATETIME(3) NOT NULL COMMENT '下一次重试时间',
  `error_code` VARCHAR(64) NULL COMMENT '安全错误码',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'UTC 修改时间，由服务更新',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_dedupe` (`tenant_id`, `dedupe_key`),
  KEY `ix_outbox_claim` (`state`, `next_attempt_at`, `lease_until`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='事务提交后的可恢复动作';

