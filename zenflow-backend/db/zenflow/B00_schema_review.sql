-- 此间 B00 独立空库结构提取稿。仅含参考表结构，不含示例账号或旧环境配置。
-- 需在一次性 MySQL 8 测试库按 B00 -> B01 演练后才作为正式迁移使用。
-- 来源锁定提交 3154567a；源文件及抽取脚本可审查。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Source: 0matrix_boot.sql, table: sys_system_config
CREATE TABLE `sys_system_config` (
  `id` bigint NOT NULL COMMENT '主键',
  `config_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置类型',
  `config_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置名称',
  `config_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置标识',
  `config_value` longtext COLLATE utf8mb4_general_ci COMMENT '配置值',
  `config_status` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开启状态',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置';

-- Source: 0matrix_boot.sql, table: sys_area
CREATE TABLE `sys_area` (
  `id` bigint unsigned NOT NULL COMMENT '主键ID',
  `pid` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '地区名称',
  `letter` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '地区字母',
  `adcode` bigint NOT NULL COMMENT '高德地区code',
  `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '经纬度',
  `area_sort` bigint DEFAULT NULL COMMENT '排序值',
  `area_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '0:未生效，1:生效',
  `area_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '0:国家,1:省,2:城市,3:区县',
  `hot` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '0:非热门，1:热门',
  `city_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '城市编码',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '创建人',
  `create_time` datetime COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='行政区划表';

-- Source: 0matrix_boot.sql, table: sys_audit_log
CREATE TABLE `sys_audit_log` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `audit_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '审计名称',
  `audit_field` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '字段名称',
  `before_val` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '变更前值',
  `after_val` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '变更后值',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人',
  `create_time` datetime COMMENT '操作时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '删除标记',
  `tenant_id` bigint(20) NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审计记录表';

-- Source: 0matrix_boot.sql, table: sys_dept
CREATE TABLE `sys_dept` (
  `dept_id` bigint(20) NOT NULL COMMENT '部门ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '部门名称',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父级部门ID',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`dept_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门管理';

-- Source: 0matrix_boot.sql, table: sys_dict
CREATE TABLE `sys_dict` (
  `id` bigint(20) NOT NULL COMMENT '编号',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字典类型',
  `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注信息',
  `system_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '系统标志',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `sys_dict_del_flag` (`del_flag`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典表';

-- Source: 0matrix_boot.sql, table: sys_dict_item
CREATE TABLE `sys_dict_item` (
  `id` bigint(20) NOT NULL COMMENT '编号',
  `dict_id` bigint(20) NOT NULL COMMENT '字典ID',
  `item_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字典项值',
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字典项名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字典类型',
  `description` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字典项描述',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序（升序）',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注信息',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `sys_dict_value` (`item_value`) USING BTREE,
  KEY `sys_dict_label` (`label`) USING BTREE,
  KEY `sys_dict_item_del_flag` (`del_flag`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典项';

-- Source: 0matrix_boot.sql, table: sys_file
CREATE TABLE `sys_file` (
  `id` bigint(20) NOT NULL COMMENT '编号',
  `group_id` bigint DEFAULT NULL COMMENT '文件组',
  `file_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件名',
  `bucket_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件存储桶名称',
  `dir` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件夹名称',
  `original` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '原始文件名',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件类型',
  `hash` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件hash',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '上传时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '所属租户',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件管理表';

-- Source: 0matrix_boot.sql, table: sys_file_group
CREATE TABLE `sys_file_group` (
  `id` bigint unsigned NOT NULL COMMENT '主键ID',
  `type` tinyint unsigned DEFAULT '10' COMMENT '类型: [10=图片, 20=视频]',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '分类名称',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户',
  `pid` bigint DEFAULT NULL COMMENT '父ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='文件分类表';

-- Source: 0matrix_boot.sql, table: sys_i18n
CREATE TABLE `sys_i18n` (
  `id` bigint(20) NOT NULL COMMENT 'id',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'name',
  `zh_cn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '中文',
  `en` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '英文',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统表-国际化';

-- Source: 0matrix_boot.sql, table: sys_log
CREATE TABLE `sys_log` (
  `id` bigint(20) NOT NULL COMMENT '编号',
  `log_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '日志类型',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日志标题',
  `service_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '服务ID',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remote_addr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '远程地址',
  `user_agent` text COLLATE utf8mb4_general_ci COMMENT '浏览器标识',
  `request_uri` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求URI',
  `method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求方法',
  `params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '请求参数',
  `time` bigint(20) DEFAULT NULL COMMENT '执行时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `exception` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '异常信息',
  `tenant_id` bigint(20) DEFAULT '0' COMMENT '所属租户',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `sys_log_request_uri` (`request_uri`) USING BTREE,
  KEY `sys_log_type` (`log_type`) USING BTREE,
  KEY `sys_log_create_date` (`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日志表';

-- Source: 0matrix_boot.sql, table: sys_sensitive_word
CREATE TABLE `sys_sensitive_word` (
  `sensitive_id` bigint NOT NULL COMMENT '主键',
  `sensitive_word` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '敏感词',
  `sensitive_type` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`sensitive_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='敏感词';

-- Source: 0matrix_boot.sql, table: sys_menu
CREATE TABLE `sys_menu` (
  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '菜单名称',
  `permission` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `path` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由路径',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父菜单ID',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '菜单图标',
  `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '是否可见，0隐藏，1显示',
  `sort_order` int(11) DEFAULT '1' COMMENT '排序值，越小越靠前',
  `keep_alive` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否缓存，0否，1是',
  `embedded` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否内嵌，0否，1是',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单类型，0:菜单 1:按钮',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志，0未删除，1已删除',
  `tenant_id` bigint(20) unsigned DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单权限表';

-- Source: 0matrix_boot.sql, table: sys_oauth_client_details
CREATE TABLE `sys_oauth_client_details` (
  `id` bigint(20) NOT NULL COMMENT 'ID',
  `client_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端ID',
  `resource_ids` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '资源ID集合',
  `client_secret` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端秘钥',
  `scope` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '授权范围',
  `authorized_grant_types` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '授权类型',
  `web_server_redirect_uri` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回调地址',
  `authorities` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限集合',
  `access_token_validity` int(11) DEFAULT NULL COMMENT '访问令牌有效期（秒）',
  `refresh_token_validity` int(11) DEFAULT NULL COMMENT '刷新令牌有效期（秒）',
  `additional_information` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '附加信息',
  `autoapprove` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '自动授权',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='终端信息表';

-- Source: 0matrix_boot.sql, table: sys_post
CREATE TABLE `sys_post` (
  `post_id` bigint(20) NOT NULL COMMENT '岗位ID',
  `post_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int(11) NOT NULL COMMENT '岗位排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '岗位描述',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '是否删除  -1：已删除  0：正常',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新人',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`post_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位信息表';

-- Source: 0matrix_boot.sql, table: sys_public_param
CREATE TABLE `sys_public_param` (
  `public_id` bigint(20) NOT NULL COMMENT '编号',
  `public_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名称',
  `public_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '键',
  `public_value` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '值',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态，0禁用，1启用',
  `validate_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '校验码',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `public_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '类型，0未知，1系统，2业务',
  `system_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '系统标识，0非系统，1系统',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`public_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共参数配置表';

-- Source: 0matrix_boot.sql, table: sys_role
CREATE TABLE `sys_role` (
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `role_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '角色名称',
  `role_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '角色编码',
  `role_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '角色描述',
  `ds_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '2' COMMENT '数据权限类型，0全部，1自定义，2本部门及以下，3本部门，4仅本人',
  `ds_scope` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据权限范围',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`role_id`) USING BTREE,
  KEY `role_idx1_role_code` (`role_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统角色表';

-- Source: 0matrix_boot.sql, table: sys_role_menu
CREATE TABLE `sys_role_menu` (
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色菜单表';

-- Source: 0matrix_boot.sql, table: sys_route_conf
CREATE TABLE `sys_route_conf` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `route_name` varchar(30) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  `route_id` varchar(30) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  `predicates` json DEFAULT NULL COMMENT '断言',
  `filters` json DEFAULT NULL COMMENT '过滤器',
  `uri` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  `sort_order` int(11) DEFAULT '0' COMMENT '排序',
  `metadata` json DEFAULT NULL COMMENT '路由元信息',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `del_flag` char(1) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路由配置表';

-- Source: 0matrix_boot.sql, table: sys_schedule
CREATE TABLE `sys_schedule` (
  `id` bigint(20) NOT NULL COMMENT 'id',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标题',
  `schedule_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日程类型',
  `schedule_state` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '状态',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '内容',
  `schedule_time` time DEFAULT NULL COMMENT '时间',
  `schedule_date` date DEFAULT NULL COMMENT '日期',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint(20) unsigned DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统日程管理表';

-- Source: 0matrix_boot.sql, table: sys_social_details
CREATE TABLE `sys_social_details` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '社交登录类型',
  `remark` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用ID',
  `app_secret` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用密钥',
  `redirect_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回调地址',
  `ext` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '拓展字段',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统社交登录账号表';

-- Source: 0matrix_boot.sql, table: sys_tenant
CREATE TABLE `sys_tenant` (
  `id` bigint(20) NOT NULL COMMENT '租户ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户名称',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户编码',
  `tenant_domain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '租户域名',
  `website_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '网站名称',
  `mini_qr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '移动端二维码',
  `background` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '登录页背景图',
  `footer` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '页脚信息',
  `logo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'logo',
  `start_time` datetime DEFAULT NULL COMMENT '租户开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '租户结束时间',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '租户状态，0正常，1停用',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `menu_id` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '租户菜单ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='租户表';

-- Source: 0matrix_boot.sql, table: sys_user
CREATE TABLE `sys_user` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码',
  `salt` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '盐值',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '电话号码',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像',
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '昵称',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '姓名',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱地址',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `lock_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '锁定标记，0未锁定，9已锁定',
  `password_expire_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '密码是否过期，0未过期，9已过期',
  `password_modify_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `wx_openid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信登录openId',
  `mini_openid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '小程序openId',
  `qq_openid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'QQ openId',
  `gitee_login` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '码云标识',
  `osc_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开源中国标识',
  `wx_cp_userid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '企业微信唯一ID',
  `wx_ding_userid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '钉钉唯一ID',
  `dept_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '主部门ID',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户ID',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- Source: 0matrix_boot.sql, table: sys_user_post
CREATE TABLE `sys_user_post` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `post_id` bigint(20) NOT NULL COMMENT '岗位ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`user_id`,`post_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户与岗位关联表';

-- Source: 0matrix_boot.sql, table: sys_user_role
CREATE TABLE `sys_user_role` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`user_id`,`role_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色表';

-- Source: 0matrix_boot.sql, table: sys_user_dept
CREATE TABLE `sys_user_dept` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `dept_id` bigint NOT NULL COMMENT '角色ID',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  PRIMARY KEY (`user_id`,`dept_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户部门表';

-- Source: 0matrix_boot.sql, table: sys_tenant_user
CREATE TABLE `sys_tenant_user` (
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  PRIMARY KEY (`tenant_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户用户表';

-- Source: 0matrix_boot.sql, table: sys_message
CREATE TABLE `sys_message` (
  `id` bigint NOT NULL COMMENT '主键',
  `category` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分类',
  `title` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标题',
  `content` text COLLATE utf8mb4_general_ci COMMENT '内容',
  `send_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否推送',
  `all_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '全部接受',
  `sort` int unsigned NOT NULL DEFAULT '0' COMMENT '排序 （越大越在前）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '删除时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内信息';

-- Source: 0matrix_boot.sql, table: sys_message_relation
CREATE TABLE `sys_message_relation` (
  `id` bigint NOT NULL COMMENT '主键',
  `msg_id` bigint DEFAULT NULL COMMENT '消息ID',
  `user_id` bigint DEFAULT NULL COMMENT '接收人ID',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '内容',
  `read_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '已读（0否，1是）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '删除时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统消息推送记录';

-- Source: 0matrix_boot.sql, table: process
CREATE TABLE `process` (
  `id` bigint NOT NULL COMMENT '用户id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `flow_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '表单ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表单名称',
  `logo` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '图标配置',
  `settings` json DEFAULT NULL COMMENT '设置项',
  `group_id` bigint NOT NULL COMMENT '分组ID',
  `form_config` json NOT NULL COMMENT '表单设置信息',
  `form_items` json NOT NULL COMMENT '表单设置内容',
  `process` json NOT NULL COMMENT '流程设置内容',
  `remark` varchar(125) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `sort` int NOT NULL,
  `is_hidden` char(1) NOT NULL COMMENT '0 正常 1=隐藏',
  `is_stop` char(1) NOT NULL COMMENT '0 正常 1=停用 ',
  `admin_id` bigint DEFAULT NULL COMMENT '流程管理员',
  `unique_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '唯一性id',
  `admin_list` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '管理员',
  `range_show` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '范围描述显示',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_form_id` (`flow_id`) USING BTREE,
  KEY `process_idx_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=182 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义数据';

-- Source: 0matrix_boot.sql, table: process_copy
CREATE TABLE `process_copy` (
  `id` bigint NOT NULL COMMENT '用户id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  `start_time` datetime NOT NULL COMMENT ' 流程发起时间',
  `node_time` datetime NOT NULL COMMENT '当前节点时间',
  `start_user_id` bigint NOT NULL COMMENT '发起人',
  `flow_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程id',
  `process_instance_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例id',
  `node_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点id',
  `group_id` bigint NOT NULL COMMENT '分组id',
  `group_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组名称',
  `process_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程名称',
  `node_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点 名称',
  `form_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表单数据',
  `user_id` bigint NOT NULL COMMENT '抄送人id',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `copy_idx_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程抄送数据';

-- Source: 0matrix_boot.sql, table: process_group
CREATE TABLE `process_group` (
  `id` bigint NOT NULL COMMENT '用户id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `group_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分组名',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `group_idx_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程分组';

-- Source: 0matrix_boot.sql, table: process_instance_record
CREATE TABLE `process_instance_record` (
  `id` bigint NOT NULL COMMENT '用户id',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程名字',
  `logo` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '头像',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `flow_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程id',
  `process_instance_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程实例id',
  `form_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '表单数据',
  `group_id` bigint DEFAULT NULL COMMENT '组id',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组名称',
  `status` int DEFAULT '1' COMMENT '状态',
  `finish_reason` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '结束原因',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `parent_process_instance_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上级流程实例id',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `record_idx_id` (`id`) USING BTREE,
  KEY `record_idx_dep_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=366 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='流程记录';

-- Source: 0matrix_boot.sql, table: process_node_data
CREATE TABLE `process_node_data` (
  `id` bigint NOT NULL COMMENT '用户id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  `flow_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程id',
  `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表单数据',
  `node_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `data_idx_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1195 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='流程节点数据';

-- Source: 0matrix_boot.sql, table: process_node_record
CREATE TABLE `process_node_record` (
  `id` bigint NOT NULL COMMENT '用户id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  `flow_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程id',
  `process_instance_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程实例id',
  `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '表单数据',
  `node_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `node_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '节点类型',
  `node_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点名字',
  `status` int NOT NULL COMMENT '节点状态',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `execution_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行id',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `node_idx_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1435 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='流程节点记录';

-- Source: 0matrix_boot.sql, table: process_node_record_assign_user
CREATE TABLE `process_node_record_assign_user` (
  `id` bigint NOT NULL COMMENT '用户id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  `flow_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程id',
  `process_instance_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程实例id',
  `data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '表单数据',
  `node_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL COMMENT ' 用户id',
  `status` int NOT NULL COMMENT '节点状态',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `execution_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执行id',
  `task_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ' 任务id',
  `approve_desc` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审批意见',
  `node_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT ' 节点名称',
  `task_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务类型',
  `local_data` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '表单本地数据',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `user_idx_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=597 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='流程节点记录-执行人';

-- Source: 0matrix_boot.sql, table: process_starter
CREATE TABLE `process_starter` (
  `id` bigint NOT NULL COMMENT '用户id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  `type_id` bigint NOT NULL COMMENT '用户id或者部门id',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT ' 类型 user dept',
  `process_id` bigint NOT NULL COMMENT '流程id',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `starter_idx_id` (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=217 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程发起人';

-- Source: 0matrix_boot.sql, table: bmp_oa_leave
CREATE TABLE `bmp_oa_leave` (
  `id` bigint NOT NULL COMMENT '请假表单主键',
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请人',
  `leave_type` int NOT NULL COMMENT '请假类型',
  `leave_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请假原因',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `leave_day` int NOT NULL COMMENT '请假天数',
  `leave_status` int DEFAULT NULL COMMENT '请假结果',
  `process_instance_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流程实例的编号',
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  `del_flag` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请假表单';

-- Source: 0matrix_boot.sql, table: qrtz_calendars
CREATE TABLE `qrtz_calendars` (
                                  `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                  `calendar_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                  `calendar` blob NOT NULL,
                                  PRIMARY KEY (`sched_name`,`calendar_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_fired_triggers
CREATE TABLE `qrtz_fired_triggers` (
                                       `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                       `entry_id` varchar(95) CHARACTER SET utf8  NOT NULL,
                                       `trigger_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                       `trigger_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                       `instance_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                       `fired_time` bigint NOT NULL,
                                       `sched_time` bigint NOT NULL,
                                       `priority` int NOT NULL,
                                       `state` varchar(16) CHARACTER SET utf8  NOT NULL,
                                       `job_name` varchar(200) CHARACTER SET utf8  DEFAULT NULL,
                                       `job_group` varchar(200) CHARACTER SET utf8  DEFAULT NULL,
                                       `is_nonconcurrent` varchar(1) CHARACTER SET utf8  DEFAULT NULL,
                                       `requests_recovery` varchar(1) CHARACTER SET utf8  DEFAULT NULL,
                                       PRIMARY KEY (`sched_name`,`entry_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_job_details
CREATE TABLE `qrtz_job_details` (
                                    `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                    `job_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                    `job_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                    `description` varchar(250) CHARACTER SET utf8  DEFAULT NULL,
                                    `job_class_name` varchar(250) CHARACTER SET utf8  NOT NULL,
                                    `is_durable` varchar(1) CHARACTER SET utf8  NOT NULL,
                                    `is_nonconcurrent` varchar(1) CHARACTER SET utf8  NOT NULL,
                                    `is_update_data` varchar(1) CHARACTER SET utf8  NOT NULL,
                                    `requests_recovery` varchar(1) CHARACTER SET utf8  NOT NULL,
                                    `job_data` blob,
                                    PRIMARY KEY (`sched_name`,`job_name`,`job_group`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_locks
CREATE TABLE `qrtz_locks` (
                              `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                              `lock_name` varchar(40) CHARACTER SET utf8  NOT NULL,
                              PRIMARY KEY (`sched_name`,`lock_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_paused_trigger_grps
CREATE TABLE `qrtz_paused_trigger_grps` (
                                            `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                            `trigger_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                            PRIMARY KEY (`sched_name`,`trigger_group`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_scheduler_state
CREATE TABLE `qrtz_scheduler_state` (
                                        `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                        `instance_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                        `last_checkin_time` bigint NOT NULL,
                                        `checkin_interval` bigint NOT NULL,
                                        PRIMARY KEY (`sched_name`,`instance_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: sys_job
CREATE TABLE `sys_job` (
                           `job_id` bigint NOT NULL COMMENT '任务id',
                           `job_name` varchar(64) CHARACTER SET utf8mb4 NOT NULL COMMENT '任务名称',
                           `job_group` varchar(64) CHARACTER SET utf8mb4 NOT NULL COMMENT '任务组名',
                           `job_order` char(1) CHARACTER SET utf8mb4 DEFAULT '1' COMMENT '组内执行顺利，值越大执行优先级越高，最大值9，最小值1',
                           `job_type` char(1) CHARACTER SET utf8mb4 NOT NULL DEFAULT '1' COMMENT '1、java类;2、spring bean名称;3、rest调用;4、jar调用;9其他',
                           `execute_path` varchar(500) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT 'job_type=3时，rest调用地址，仅支持rest get协议,需要增加String返回值，0成功，1失败;job_type=4时，jar路径;其它值为空',
                           `class_name` varchar(500) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT 'job_type=1时，类完整路径;job_type=2时，spring bean名称;其它值为空',
                           `method_name` varchar(500) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '任务方法',
                           `method_params_value` varchar(2000) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '参数值',
                           `cron_expression` varchar(255) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT 'cron执行表达式',
                           `misfire_policy` varchar(20) CHARACTER SET utf8mb4 DEFAULT '3' COMMENT '错失执行策略（1错失周期立即执行 2错失周期执行一次 3下周期执行）',
                           `job_tenant_type` char(1) CHARACTER SET utf8mb4 DEFAULT '1' COMMENT '1、多租户任务;2、非多租户任务',
                           `job_status` char(1) CHARACTER SET utf8mb4 DEFAULT '0' COMMENT '状态（1、未发布;2、运行中;3、暂停;4、删除;）',
                           `job_execute_status` char(1) CHARACTER SET utf8mb4 DEFAULT '0' COMMENT '状态（0正常 1异常）',
                           `create_by` varchar(64) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '创建者',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_by` varchar(64) CHARACTER SET utf8mb4 DEFAULT '' COMMENT '更新者',
                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                           `start_time` timestamp NULL DEFAULT NULL COMMENT '初次执行时间',
                           `previous_time` timestamp NULL DEFAULT NULL COMMENT '上次执行时间',
                           `next_time` timestamp NULL DEFAULT NULL COMMENT '下次执行时间',
                           `tenant_id` bigint DEFAULT '1' COMMENT '租户',
                           `remark` varchar(500) CHARACTER SET utf8mb4 DEFAULT '' COMMENT '备注信息',
                           PRIMARY KEY (`job_id`,`job_name`,`job_group`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度表';

-- Source: 0matrix_boot.sql, table: sys_job_log
CREATE TABLE `sys_job_log` (
                               `job_log_id` bigint NOT NULL COMMENT '任务日志ID',
                               `job_id` bigint NOT NULL COMMENT '任务id',
                               `job_name` varchar(64) CHARACTER SET utf8  DEFAULT NULL COMMENT '任务名称',
                               `job_group` varchar(64) CHARACTER SET utf8  DEFAULT NULL COMMENT '任务组名',
                               `job_order` char(1) CHARACTER SET utf8  DEFAULT NULL COMMENT '组内执行顺利，值越大执行优先级越高，最大值9，最小值1',
                               `job_type` char(1) CHARACTER SET utf8  NOT NULL DEFAULT '1' COMMENT '1、java类;2、spring bean名称;3、rest调用;4、jar调用;9其他',
                               `execute_path` varchar(500) CHARACTER SET utf8  DEFAULT NULL COMMENT 'job_type=3时，rest调用地址，仅支持post协议;job_type=4时，jar路径;其它值为空',
                               `class_name` varchar(500) CHARACTER SET utf8  DEFAULT NULL COMMENT 'job_type=1时，类完整路径;job_type=2时，spring bean名称;其它值为空',
                               `method_name` varchar(500) CHARACTER SET utf8  DEFAULT NULL COMMENT '任务方法',
                               `method_params_value` varchar(2000) CHARACTER SET utf8  DEFAULT NULL COMMENT '参数值',
                               `cron_expression` varchar(255) CHARACTER SET utf8  DEFAULT NULL COMMENT 'cron执行表达式',
                               `job_message` varchar(500) CHARACTER SET utf8  DEFAULT NULL COMMENT '日志信息',
                               `job_log_status` char(1) CHARACTER SET utf8  DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
                               `execute_time` varchar(30) CHARACTER SET utf8  DEFAULT NULL COMMENT '执行时间',
                               `exception_info` varchar(2000) CHARACTER SET utf8  DEFAULT '' COMMENT '异常信息',
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户id',
                               PRIMARY KEY (`job_log_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务执行日志表';

-- Source: 0matrix_boot.sql, table: wx_account
CREATE TABLE `wx_account` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名称',
  `account` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '账号',
  `appid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用ID',
  `appsecret` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用秘钥',
  `url` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'URL地址',
  `token` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Token令牌',
  `aeskey` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '消息加解密密钥',
  `qr_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '二维码URL地址',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公众号账户表';

-- Source: 0matrix_boot.sql, table: wx_account_fans
CREATE TABLE `wx_account_fans` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `openid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '粉丝openid',
  `subscribe_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '订阅状态，0未订阅，1已订阅',
  `subscribe_time` datetime DEFAULT NULL COMMENT '订阅时间',
  `nickname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '昵称',
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '性别',
  `language` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '语言',
  `country` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '国家',
  `province` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '省份',
  `city` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '城市',
  `tag_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分组ID',
  `headimg_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像URL地址',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注信息',
  `wx_account_id` bigint(20) DEFAULT NULL COMMENT '微信公众号ID',
  `wx_account_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信公众号名称',
  `wx_account_appid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信公众号AppID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `is_black` int(255) DEFAULT NULL COMMENT '是否在黑名单',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_1` (`openid`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='微信公众号粉丝表';

-- Source: 0matrix_boot.sql, table: wx_account_tag
CREATE TABLE `wx_account_tag` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `tag` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标签名称',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '修改人',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '修改时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `tenant_id` bigint(20) NOT NULL COMMENT '租户ID',
  `wx_account_id` bigint(20) NOT NULL COMMENT '微信公众号ID',
  `wx_account_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '微信公众号名称',
  `wx_account_appid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '微信公众号AppID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='微信公众号标签表';

-- Source: 0matrix_boot.sql, table: wx_auto_reply
CREATE TABLE `wx_auto_reply` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型（1、关注时回复；2、消息回复；3、关键词回复）',
  `req_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关键词',
  `req_type` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求消息类型（text：文本；image：图片；voice：语音；video：视频；shortvideo：小视频；location：地理位置）',
  `rep_type` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回复消息类型（text：文本；image：图片；voice：语音；video：视频；music：音乐；news：图文）',
  `rep_mate` char(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回复类型文本匹配类型（1、全匹配，2、半匹配）',
  `rep_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '回复类型文本保存文字',
  `rep_media_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回复类型imge、voice、news、video的mediaID或音乐缩略图的媒体id',
  `rep_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回复的素材名、视频和音乐的标题',
  `rep_desc` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '视频和音乐的描述',
  `rep_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '链接',
  `rep_hq_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高质量链接',
  `rep_thumb_media_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '缩略图的媒体id',
  `rep_thumb_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '缩略图url',
  `content` json DEFAULT NULL COMMENT '图文消息的内容',
  `app_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '公众号ID',
  `remark` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT '0' COMMENT '逻辑删除标记（0：显示；1：隐藏）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='微信自动回复';

-- Source: 0matrix_boot.sql, table: wx_mp_menu
CREATE TABLE `wx_mp_menu` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `menu` json DEFAULT NULL COMMENT '菜单',
  `wx_account_id` bigint(20) DEFAULT NULL COMMENT '公众号ID',
  `wx_account_appid` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '公众号APPID',
  `wx_account_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '公众号名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `pub_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '发布标志',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='微信菜单表';

-- Source: 0matrix_boot.sql, table: wx_msg
CREATE TABLE `wx_msg` (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `app_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '公众号名称',
  `app_logo` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '公众号logo',
  `wx_user_id` varchar(32) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '微信用户ID',
  `nick_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信用户昵称',
  `headimg_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信用户头像',
  `type` char(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '消息分类（1、用户发给公众号；2、公众号发给用户；）',
  `rep_type` char(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '消息类型（text：文本；image：图片；voice：语音；video：视频；shortvideo：小视频；location：地理位置；music：音乐；news：图文；event：推送事件）',
  `rep_event` char(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '事件类型（subscribe：关注；unsubscribe：取关；CLICK、VIEW：菜单事件）',
  `rep_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '回复类型文本保存文字、地理位置信息',
  `rep_media_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回复类型imge、voice、news、video的mediaID或音乐缩略图的媒体id',
  `rep_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回复的素材名、视频和音乐的标题',
  `rep_desc` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '视频和音乐的描述',
  `rep_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '链接',
  `rep_hq_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '高质量链接',
  `content` json DEFAULT NULL COMMENT '图文消息的内容',
  `rep_thumb_media_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '缩略图的媒体id',
  `rep_thumb_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '缩略图url',
  `rep_location_x` double DEFAULT NULL COMMENT '地理位置维度',
  `rep_location_y` double DEFAULT NULL COMMENT '地理位置经度',
  `rep_scale` double DEFAULT NULL COMMENT '地图缩放大小',
  `read_flag` char(2) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT '1' COMMENT '已读标记（1：是；0：否）',
  `app_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '公众号ID',
  `open_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信唯一标识',
  `remark` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT '0' COMMENT '逻辑删除标记（0：显示；1：隐藏）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='微信消息';

-- Source: 0matrix_boot.sql, table: pay_channel
CREATE TABLE `pay_channel` (
  `id` bigint(20) NOT NULL COMMENT '渠道主键ID',
  `mch_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '商户ID',
  `channel_id` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道ID',
  `channel_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道名称',
  `channel_mch_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道商户ID',
  `return_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '前端回调地址',
  `notify_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '后端回调地址',
  `state` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态',
  `param` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '参数',
  `remark` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付渠道表';

-- Source: 0matrix_boot.sql, table: pay_goods_order
CREATE TABLE `pay_goods_order` (
  `goods_order_id` bigint(20) NOT NULL COMMENT '商品订单ID',
  `goods_id` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '商品ID',
  `goods_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '商品名称',
  `amount` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '金额',
  `user_id` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户ID',
  `status` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '订单状态：订单生成(0)、支付成功(1)、处理完成(2)、处理失败(-1)',
  `pay_order_id` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '支付订单ID',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`goods_order_id`) USING BTREE,
  UNIQUE KEY `IDX_PayOrderId` (`pay_order_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品订单表';

-- Source: 0matrix_boot.sql, table: pay_notify_record
CREATE TABLE `pay_notify_record` (
  `id` bigint(20) NOT NULL COMMENT 'ID',
  `notify_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通知ID',
  `request` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '请求内容',
  `response` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '响应内容',
  `order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '订单号',
  `http_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'http状态',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知记录日志表';

-- Source: 0matrix_boot.sql, table: pay_refund_order
CREATE TABLE `pay_refund_order` (
  `refund_order_id` bigint(20) NOT NULL COMMENT '退款订单ID',
  `pay_order_id` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '支付订单号',
  `channel_pay_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道支付订单号',
  `mch_id` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '商户号',
  `mch_refund_no` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '商户退款单号',
  `channel_id` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道ID',
  `pay_amount` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '支付金额',
  `refund_amount` bigint(20) NOT NULL COMMENT '退款金额,单位分',
  `currency` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '币种',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '退款状态:0-订单生成,1-退款中,2-退款成功,3-退款失败,4-业务处理完成',
  `result` tinyint(4) NOT NULL DEFAULT '0' COMMENT '退款结果:0-不确认结果,1-等待手动处理,2-确认成功,3-确认失败',
  `client_ip` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端IP',
  `device` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '设备信息',
  `remark` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注信息',
  `channel_user` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道用户标识',
  `username` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  `channel_mch_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道商户号',
  `channel_order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道订单号',
  `channel_err_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道错误码',
  `channel_err_msg` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道错误信息',
  `extra` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '附加信息',
  `notify_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通知URL',
  `param1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展参数1',
  `param2` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展参数2',
  `expire_time` datetime DEFAULT NULL COMMENT '订单失效时间',
  `refund_succ_time` datetime DEFAULT NULL COMMENT '订单退款成功时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`refund_order_id`) USING BTREE,
  UNIQUE KEY `IDX_MchId_MchOrderNo` (`mch_id`,`mch_refund_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='退款订单表';

-- Source: 0matrix_boot.sql, table: pay_trade_order
CREATE TABLE `pay_trade_order` (
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `channel_id` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道ID',
  `amount` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '支付金额',
  `currency` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '币种',
  `status` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '支付状态:0-订单生成,1-支付中(目前未使用),2-支付成功,3-业务处理完成',
  `client_ip` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端IP',
  `device` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '设备信息',
  `subject` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标题',
  `body` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内容',
  `extra` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '附加信息',
  `channel_mch_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道商户号',
  `channel_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '渠道订单号',
  `err_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误码',
  `err_msg` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误信息',
  `param1` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展参数1',
  `param2` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '扩展参数2',
  `notify_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通知URL',
  `notify_count` tinyint(4) DEFAULT '0' COMMENT '通知次数',
  `last_notify_time` bigint(20) DEFAULT NULL COMMENT '最后一次通知时间',
  `expire_time` bigint(20) DEFAULT NULL COMMENT '订单失效时间',
  `pay_succ_time` datetime DEFAULT NULL COMMENT '订单支付成功时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `tenant_id` bigint(20) DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`order_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付订单表';

-- Source: 0matrix_boot.sql, table: gen_datasource_conf
CREATE TABLE `gen_datasource_conf` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '别名',
  `url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'jdbcurl',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  `password` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `ds_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库类型',
  `conf_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '配置类型',
  `ds_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库名称',
  `instance` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '实例',
  `port` int DEFAULT NULL COMMENT '端口',
  `host` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主机',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据源表';

-- Source: 0matrix_boot.sql, table: gen_field_type
CREATE TABLE `gen_field_type` (
  `id` bigint NOT NULL COMMENT 'id',
  `column_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段类型',
  `attr_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '属性类型',
  `package_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '属性包名',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `column_type` (`column_type`)
) ENGINE=InnoDB AUTO_INCREMENT=1634915190321451010 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字段类型管理';

-- Source: 0matrix_boot.sql, table: gen_form_conf
CREATE TABLE `gen_form_conf` (
  `id` bigint NOT NULL COMMENT 'ID',
  `ds_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库名称',
  `table_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表名称',
  `form_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表单信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `table_name` (`table_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单配置';

-- Source: 0matrix_boot.sql, table: gen_group
CREATE TABLE `gen_group` (
  `id` bigint NOT NULL,
  `group_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分组名称',
  `group_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分组描述',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '修改人',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='模板分组';

-- Source: 0matrix_boot.sql, table: gen_table
CREATE TABLE `gen_table` (
  `id` bigint NOT NULL COMMENT 'id',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表名',
  `class_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类名',
  `db_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库类型',
  `table_comment` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '说明',
  `author` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '作者',
  `email` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `package_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '项目包名',
  `version` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '项目版本号',
  `i18n` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否生成带有i18n 0 不带有 1带有',
  `style` bigint DEFAULT NULL COMMENT '代码风格',
  `sync_menu_id` bigint DEFAULT NULL COMMENT '所属菜单ID',
  `sync_route` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否自动同步路由',
  `child_table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表名称',
  `main_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主表关联键',
  `child_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表关联键',
  `parent_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '父表关联键',
  `name_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名称字段',
  `generator_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '生成方式  0：zip压缩包   1：自定义目录',
  `backend_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '后端生成路径',
  `frontend_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '前端生成路径',
  `module_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模块名',
  `function_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '功能名',
  `form_layout` tinyint DEFAULT NULL COMMENT '表单布局  1：一列   2：两列',
  `ds_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据源ID',
  `baseclass_id` bigint DEFAULT NULL COMMENT '基类ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `table_name_idx` (`table_name`,`ds_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成表';

-- Source: 0matrix_boot.sql, table: gen_table_column
CREATE TABLE `gen_table_column` (
  `id` bigint NOT NULL COMMENT 'id',
  `ds_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据源名称',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表名称',
  `field_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段名称',
  `field_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段类型',
  `field_comment` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段说明',
  `attr_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '属性名',
  `attr_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '属性类型',
  `package_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '属性包名',
  `sort` int DEFAULT NULL COMMENT '排序',
  `auto_fill` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '自动填充  DEFAULT、INSERT、UPDATE、INSERT_UPDATE',
  `primary_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '主键 0：否  1：是',
  `base_field` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '基类字段 0：否  1：是',
  `form_item` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '表单项 0：否  1：是',
  `form_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '表单必填 0：否  1：是',
  `form_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表单类型',
  `form_validator` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表单效验',
  `grid_item` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '列表项 0：否  1：是',
  `grid_sort` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '列表排序 0：否  1：是',
  `query_item` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '查询项 0：否  1：是',
  `query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '查询方式',
  `query_form_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '查询表单类型',
  `field_dict` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字典类型',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成表字段';

-- Source: 0matrix_boot.sql, table: gen_template
CREATE TABLE `gen_template` (
  `id` bigint NOT NULL COMMENT '主键',
  `template_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `generator_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板路径',
  `template_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板描述',
  `template_code` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板代码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `create_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT ' ' COMMENT '修改人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='模板';

-- Source: 0matrix_boot.sql, table: gen_template_group
CREATE TABLE `gen_template_group` (
  `group_id` bigint NOT NULL COMMENT '分组id',
  `template_id` bigint NOT NULL COMMENT '模板id',
  PRIMARY KEY (`group_id`,`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='模板分组关联表';

-- Source: 0matrix_boot.sql, table: gen_create_table
CREATE TABLE `gen_create_table` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `table_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '表名称',
  `ds_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据源',
  `comments` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '表注释',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL,
  `column_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '字段信息',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除标记',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='自动创建表管理';

-- Source: 0matrix_boot.sql, table: app_article
CREATE TABLE `app_article` (
  `id` bigint NOT NULL COMMENT '主键',
  `cid` bigint NOT NULL COMMENT '分类',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '标题',
  `intro` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '简介',
  `summary` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '摘要',
  `image` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '封面',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '内容',
  `author` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '作者',
  `visit` int unsigned NOT NULL DEFAULT '0' COMMENT '浏览',
  `sort` int unsigned NOT NULL DEFAULT '50' COMMENT '排序',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '删除时间',
    `tenant_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `cid_idx` (`cid`) USING BTREE COMMENT '分类索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='文章资讯表';

-- Source: 0matrix_boot.sql, table: app_article_category
CREATE TABLE `app_article_category` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `name` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '名称',
  `sort` smallint unsigned NOT NULL DEFAULT '50' COMMENT '排序',
  `is_show` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '是否显示: 0=否, 1=是',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否删除: 0=否, 1=是',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '创建人',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
    `tenant_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='文章分类表';

-- Source: 0matrix_boot.sql, table: app_article_collect
CREATE TABLE `app_article_collect` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `user_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '用户ID',
  `article_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '文章ID',
  `create_time` datetime COMMENT '创建时间',
  `update_time` datetime COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '是否删除',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
    `tenant_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='文章收藏表';

-- Source: 0matrix_boot.sql, table: app_contacts
CREATE TABLE `app_contacts` (
  `id` bigint NOT NULL COMMENT '主键',
  `contact_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通讯录表';

-- Source: 0matrix_boot.sql, table: app_page
CREATE TABLE `app_page` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `page_type` tinyint unsigned NOT NULL DEFAULT '10' COMMENT '页面类型',
  `page_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '页面名称',
  `page_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '页面数据',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '删除标记',
    `tenant_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='页面装修表';

-- Source: 0matrix_boot.sql, table: app_tabbar
CREATE TABLE `app_tabbar` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '导航名称',
  `selected` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '未选图标',
  `unselected` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '已选图标',
  `link` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '链接地址',
  `sort_order` int(11) NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '删除标记',
  `tenant_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='底部装修表';

-- Source: 0matrix_boot.sql, table: app_role
CREATE TABLE `app_role` (
  `role_id` bigint(20) NOT NULL,
  `role_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `role_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `role_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ' ' COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0',
  `tenant_id` bigint(20) DEFAULT NULL,
  `menu_id` varchar(20) NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`) USING BTREE,
  KEY `app_role_code_index` (`role_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='app角色表';

-- Source: 0matrix_boot.sql, table: app_social_details
CREATE TABLE `app_social_details` (
  `id` bigint(20) NOT NULL COMMENT '主键', -- 主键
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '社交类型', -- 社交类型
  `remark` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注', -- 备注
  `app_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用ID', -- 应用ID
  `app_secret` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '应用密钥', -- 应用密钥
  `redirect_url` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '重定向URL', -- 重定向URL
  `ext` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '拓展字段', -- 拓展字段
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ' ' COMMENT '创建人', -- 创建人
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ' ' COMMENT '修改人', -- 修改人
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', -- 创建时间
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', -- 更新时间
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志', -- 删除标志
PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
COMMENT='系统社交登录账号表';

-- Source: 0matrix_boot.sql, table: app_user
CREATE TABLE `app_user` (
  `user_id` bigint(20) NOT NULL COMMENT '用户id',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码',
  `salt` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '盐值',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号码',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像图片链接',
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '拓展字段:昵称',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '拓展字段:姓名',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '拓展字段:邮箱',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT ' ' COMMENT '修改人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '所属租户id',
  `last_modified_time` datetime DEFAULT NULL COMMENT '最后一次密码修改时间',
  `lock_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '锁定状态',
  `wx_openid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '微信登录openId',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='app用户表';

-- Source: 0matrix_boot.sql, table: app_user_role
CREATE TABLE `app_user_role` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户角色表';

-- Source: 0matrix_boot.sql, table: ai_model
CREATE TABLE `ai_model` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `model_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '类型: CHAT、Embedding、Image',
  `model_name` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模型名称',
  `provider` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '供应商',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '别名',
  `response_limit` int DEFAULT NULL COMMENT '响应长度',
  `temperature` double DEFAULT NULL COMMENT '温度',
  `top_p` double DEFAULT NULL,
  `api_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `base_url` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `secret_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `endpoint` varchar(100) COLLATE utf8mb4_bin DEFAULT NULL,
  `default_model` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '默认模型',
  `assistant_model` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '0' COMMENT '统一助手模型',
  `azure_deployment_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'azure模型参数',
  `gemini_project` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'gemini模型参数',
  `gemini_location` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'gemini模型参数',
  `image_size` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图片大小',
  `image_quality` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图片质量',
  `image_style` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图片风格',
  `dimensions` int DEFAULT NULL COMMENT '向量维数',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除标记',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '修改人',
  `ext_data` longtext COLLATE utf8mb4_bin COMMENT '扩展字段',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  `assistant_model_tenant` bigint GENERATED ALWAYS AS (
    (CASE
      WHEN `model_type` = 'Chat'
        AND `assistant_model` = '1'
        AND COALESCE(`del_flag`, '0') <> '1'
      THEN COALESCE(`tenant_id`, 1)
      ELSE NULL
    END)
  ) STORED COMMENT '租户统一助手模型唯一键',
  `default_voice_capability` varchar(190) COLLATE utf8mb4_bin GENERATED ALWAYS AS (
    (CASE
      WHEN `model_type` = 'Voice'
        AND `default_model` = '1'
        AND COALESCE(`del_flag`, '0') <> '1'
        AND JSON_VALID(`ext_data`) = 1
      THEN CONCAT(COALESCE(CAST(`tenant_id` AS CHAR), '1'), ':', JSON_UNQUOTE(JSON_EXTRACT(`ext_data`, '$.voiceType')))
      ELSE NULL
    END)
  ) STORED COMMENT '租户默认语音能力唯一键',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_model_assistant_tenant` (`assistant_model_tenant`),
  UNIQUE KEY `uk_ai_model_default_voice_capability` (`default_voice_capability`),
  CONSTRAINT `chk_ai_model_assistant_chat` CHECK ((`assistant_model` IN ('0', '1')) AND ((`assistant_model` <> '1') OR (COALESCE(`model_type`, '') = 'Chat'))),
  CONSTRAINT `chk_ai_model_default_voice_capability` CHECK ((NOT((COALESCE(`model_type`, '') = 'Voice') AND (COALESCE(`default_model`, '0') = '1') AND (COALESCE(`del_flag`, '0') <> '1'))) OR COALESCE(IF((JSON_VALID(`ext_data`) = 1), (NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(`ext_data`, '$.voiceType'))), '') IS NOT NULL), FALSE), FALSE))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='模型配置表';

-- Source: 0matrix_boot.sql, table: ai_bill
CREATE TABLE `ai_bill` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `message_key` bigint DEFAULT NULL COMMENT 'message key',
  `prompt_tokens` bigint DEFAULT NULL COMMENT '提示令牌数量',
  `completion_tokens` bigint DEFAULT NULL COMMENT '补全令牌数量',
  `model` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '模型名称',
  `reqid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '请求ID',
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'IP地址',
  `note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '备注',
  `token_id` bigint DEFAULT NULL COMMENT '令牌ID',
  `tokens` bigint DEFAULT NULL COMMENT '令牌数量',
  `token_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '令牌类型 0 系统  1 用户',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除标记',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '修改人',
  `use_time` bigint DEFAULT NULL COMMENT '使用时间,单位毫秒',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='账单';

-- Source: 0matrix_boot.sql, table: ai_chat_record
CREATE TABLE `ai_chat_record` (
  `record_id` bigint NOT NULL COMMENT '主键',
  `username` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户标识',
  `model_name` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模型名称',
  `question_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '问题',
  `answer_text` text COLLATE utf8mb4_general_ci COMMENT '答案',
  `dataset_id` bigint DEFAULT NULL COMMENT '所属知识库',
  `document_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联文档IDs',
  `suitability` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '匹配度',
  `ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端IP',
  `user_agent` text COLLATE utf8mb4_general_ci COMMENT '浏览器标识',
  `conversation_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话ID',
  `websearch_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '网络搜索',
  `standard_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标注',
  `qdrant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '向量ID',
  `inner_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '内部调用',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '删除标记',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `llm_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '大模型交互结果',
  `ext_details` text COLLATE utf8mb4_general_ci COMMENT '详细信息',
  `use_time` bigint DEFAULT NULL COMMENT '使用时间,单位毫秒',
  PRIMARY KEY (`record_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='聊天记录';

-- Source: 0matrix_boot.sql, table: ai_dataset
CREATE TABLE `ai_dataset` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '名称',
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '头像',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '描述',
  `collection_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '集合名称',
  `store_id` bigint DEFAULT NULL COMMENT '存储ID',
  `units` bigint DEFAULT NULL COMMENT '知识数量',
  `file_size` bigint DEFAULT NULL COMMENT '大小',
  `multi_round` int DEFAULT NULL COMMENT '多轮会话',
  `top_k` int DEFAULT NULL COMMENT '匹配条数',
  `score` int DEFAULT NULL COMMENT '匹配率',
  `sort_order` int DEFAULT NULL COMMENT '排序值',
  `fragment_size` int DEFAULT NULL COMMENT '切片数量',
  `empty_llm_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '空查询',
  `empty_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '未匹配提示',
  `sensitive_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '敏感词汇',
  `sensitive_msg` text COLLATE utf8mb4_bin COMMENT '敏感词命中消息',
  `welcome_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '欢迎语',
  `public_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '是否对外',
  `public_password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '访问密码',
  `embedding_model` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '嵌入模型',
  `summary_model` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '摘要模型',
  `reranker_model` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '排序模型',
  `pre_summary` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '预总结',
  `pre_compress` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '预压缩',
  `ai_ocr_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '是否使用AI OCR',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除标记',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '修改人',
  `footer` text COLLATE utf8mb4_bin COMMENT '底部信息',
  `standard_flag` char(1) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '标记数据',
  `ai_tag_flag` char(1) COLLATE utf8mb4_bin DEFAULT '1' COMMENT 'AI标签开关（0-关闭 1-开启）',
  `tags` text COLLATE utf8mb4_bin COMMENT '知识库标签（逗号分隔）',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='知识库';

-- Source: 0matrix_boot.sql, table: ai_embed_store
CREATE TABLE `ai_embed_store` (
  `store_id` bigint NOT NULL COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '名称',
  `store_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '类型',
  `uri` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '链接端点',
  `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Host',
  `port` int DEFAULT NULL COMMENT '端口',
  `use_tls` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否使用安全模式',
  `api_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密钥',
  `ext_data` longtext COLLATE utf8mb4_general_ci COMMENT '扩展参数',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '删除标记',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_by` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='向量库配置';

-- Source: 0matrix_boot.sql, table: ai_document
CREATE TABLE `ai_document` (
  `id` bigint NOT NULL COMMENT '主键',
  `dataset_id` bigint DEFAULT NULL COMMENT '知识库ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '名称',
  `file_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '文件类型',
  `file_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '文件链接',
  `file_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'MD5hash',
  `source_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '文件来源',
  `slice_count` bigint DEFAULT NULL COMMENT '切片数量',
  `hit_count` bigint DEFAULT NULL COMMENT '命中次数',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `file_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '状态',
  `slice_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '状态',
  `graph_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '图状态',
  `slice_fail_reason` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '总结摘要失败原因',
  `summary` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '总结摘要',
  `summary_fail_reason` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '总结摘要失败原因',
  `summary_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '总结状态',
  `document_config` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '文档配置元信息',
  `tags` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '文档标签（逗号分隔）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除标记',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '修改人',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='知识文档';

-- Source: 0matrix_boot.sql, table: ai_prompt
CREATE TABLE `ai_prompt` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `act` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '标题',
  `prompt` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '提示词',
  `prompt_sort` bigint DEFAULT NULL COMMENT '列表排序',
  `prompt_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '状态',
  `user_id` bigint DEFAULT NULL COMMENT '所属人',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除标记',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '修改人',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='提示词';

-- Source: 0matrix_boot.sql, table: ai_slice
CREATE TABLE `ai_slice` (
  `id` bigint NOT NULL COMMENT '主键',
  `document_id` bigint DEFAULT NULL COMMENT '文档ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '名称',
  `units` bigint DEFAULT NULL COMMENT '知识数量',
  `file_size` bigint DEFAULT NULL COMMENT '大小',
  `hit_count` bigint DEFAULT NULL COMMENT '命中次数',
  `char_count` bigint DEFAULT NULL COMMENT '字符数量',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '内容',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '删除标记',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '修改人',
  `slice_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '训练状态',
  `qdrant_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '向量ID',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='知识切片';

-- Source: 0matrix_boot.sql, table: ai_ocr_conf
CREATE TABLE `ai_ocr_conf` (
  `id` bigint NOT NULL COMMENT '主键',
  `ocr_title` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标题',
  `ocr_prompt` longtext COLLATE utf8mb4_general_ci COMMENT '描述提示词',
  `image_resource` longtext COLLATE utf8mb4_general_ci COMMENT '资源base64',
  `ocr_marked` longtext COLLATE utf8mb4_general_ci COMMENT '标注结果',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_by` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_by` datetime DEFAULT NULL,
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI OCR';

-- Source: 0matrix_boot.sql, table: ai_flow
CREATE TABLE `ai_flow` (
  `id` bigint NOT NULL COMMENT '流程主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程描述',
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程类型',
  `dsl` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '流程设计DSL',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程图标',
  `api_enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否启用api',
  `enabled` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '其否启用',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '状态',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否已删除',
  `result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '最终结果',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大模型流程表';

-- Source: 0matrix_boot.sql, table: ai_flow_log
CREATE TABLE `ai_flow_log` (
  `id` bigint NOT NULL COMMENT '流程日志主键',
  `flow_id` bigint NOT NULL COMMENT '流程主键',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程类型',
  `input_params` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '输入参数',
  `output_params` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '输出参数',
  `result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '最终结果',
  `executed` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行状态',
  `error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '错误信息',
  `run_time` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '运行时间',
  `api_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'api key',
  `total_tokens` bigint DEFAULT NULL COMMENT 'api tokens',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `status` int DEFAULT '1' COMMENT '状态',
  `is_deleted` int DEFAULT '0' COMMENT '是否已删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大模型流程日志表';

-- Source: 0matrix_boot.sql, table: ai_data
CREATE TABLE `ai_data` (
  `data_id` bigint NOT NULL COMMENT '主键ID',
  `dataset_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '数据集名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '数据集描述',
  `dataset_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据集类型 (1-明细表, 2-多指标周期表, 3-键值对表, 4-其他)',
  `ds_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联数据源名称',
  `table_name` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '关联数据表',
  `terminology_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '关联术语',
  `learning_status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '学习状态（0-未学习, 1-已学习）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '创建人',
  `create_time` datetime COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记 (0-正常, 1-删除)',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`data_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 数据集管理表';

-- Source: 0matrix_boot.sql, table: ai_data_field
CREATE TABLE `ai_data_field` (
  `field_id` bigint NOT NULL COMMENT '主键ID',
  `table_id` bigint NOT NULL COMMENT '关联数据表',
  `field_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段名称',
  `field_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '字段备注',
  `virtual_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '虚拟注释',
  `field_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '字段类型',
  `db_type` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据库类型',
  `ds_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联数据源名称',
  `modify_status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '修正状态 ',
  `modify_time` datetime DEFAULT NULL COMMENT '修正时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '创建人',
  `create_time` datetime COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记 (0-正常, 1-删除)',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`field_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 字段管理表';

-- Source: 0matrix_boot.sql, table: ai_data_table
CREATE TABLE `ai_data_table` (
  `table_id` bigint NOT NULL COMMENT '主键ID',
  `ds_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联数据源名称',
  `ds_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联数据源类型',
  `table_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '表名称',
  `table_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '物理表注释',
  `virtual_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '虚拟表注释',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '创建人',
  `create_time` datetime COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记 (0-正常, 1-删除)',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`table_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 数据表管理表';

-- Source: 0matrix_boot.sql, table: ai_mcp_config
CREATE TABLE `ai_mcp_config` (
  `mcp_id` bigint NOT NULL COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '服务器名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '描述',
  `mcp_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类型 (SSE: Server-Sent Events, STDIO: Standard Input/Output)',
  `command` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '命令',
  `sse_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'SSE URL',
  `parameters` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '参数',
  `environment_variables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '环境变量',
  `mcp_enabled` char(1) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '1' COMMENT '是否启用（1：启用，0：禁用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '创建人',
  `create_time` datetime COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`mcp_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MCP配置表';

-- Source: 0matrix_boot.sql, table: ai_poster
CREATE TABLE `ai_poster` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板名称',
  `template_code` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '模板代码',
  `template_css` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '模板CSS',
  `template_style` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '模板风格',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI海报模板表';

-- Source: 0matrix_boot.sql, table: ai_material_log
CREATE TABLE `ai_material_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint DEFAULT 1 COMMENT '租户ID',
  `material_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '资源类型',
  `prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '提示词',
  `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '模型',
  `original_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '原生链接',
  `local_url` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '本地链接',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `material_status` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '资源状态',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI资源记录';

-- Source: 0matrix_boot.sql, table: ai_report_conf
CREATE TABLE `ai_report_conf` (
  `id` bigint NOT NULL COMMENT '主键',
  `report_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '标题',
  `report_prompt` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '描述提示词',
  `report_resource` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '资源base64',
  `report_marked` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '报告字段',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_by` datetime DEFAULT NULL,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 报告';

-- Source: 0matrix_boot.sql, table: ai_dataset_user
CREATE TABLE `ai_dataset_user` (
  `dataset_id` bigint NOT NULL COMMENT '知识库ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  PRIMARY KEY (`dataset_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识库用户权限';

-- Source: 0matrix_boot.sql, table: ai_document_template
CREATE TABLE `ai_document_template` (
    `id` bigint auto_increment primary key,
    `name` varchar(100) null comment '模板名称',
    `content` longtext null comment '模板内容',
    `json` longtext null comment 'json配置',
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
    `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI公文模板';

-- Source: 0matrix_boot.sql, table: ai_datasource
CREATE TABLE `ai_datasource` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据源名称',
  `url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '连接URL',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名',
  `password` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '密码',
  `ds_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '数据源类型',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='数据源表';

-- Source: 0matrix_boot.sql, table: ai_storage_group
CREATE TABLE `ai_storage_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_name` varchar(100) NOT NULL COMMENT '分组名称',
  `parent_id` bigint DEFAULT 0 COMMENT '父分组ID',
  `icon` varchar(255) DEFAULT NULL COMMENT '分组图标',
  `sort_order` int DEFAULT 0 COMMENT '排序',
  `description` text COMMENT '分组描述',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人',
  `update_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记(0-正常,1-删除)',
  `tenant_id` bigint DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_storage_group_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI存储分组表';

-- Source: 0matrix_boot.sql, table: ai_storage_tag
CREATE TABLE `ai_storage_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tag_name` varchar(100) NOT NULL COMMENT '标签名称',
  `tag_type` varchar(20) DEFAULT 'custom' COMMENT '标签类型(ai/custom)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人',
  `update_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记(0-正常,1-删除)',
  `tenant_id` bigint DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件标签表';

-- Source: 0matrix_boot.sql, table: ai_storage_file
CREATE TABLE `ai_storage_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名称',
  `file_type` varchar(50) NOT NULL COMMENT '文件类型(image/video/document/audio)',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `file_url` varchar(500) NOT NULL COMMENT '文件URL',
  `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '缩略图URL',
  `group_id` bigint DEFAULT NULL COMMENT '所属分组ID',
  `storage_path` varchar(500) DEFAULT NULL COMMENT '存储路径',
  `md5` varchar(64) DEFAULT NULL COMMENT '文件MD5值',
  `width` int DEFAULT NULL COMMENT '宽度(图片/视频)',
  `height` int DEFAULT NULL COMMENT '高度(图片/视频)',
  `duration` int DEFAULT NULL COMMENT '时长(视频/音频,秒)',
  `ext_info` text COMMENT '扩展信息(EXIF等)',
  `ai_tags` text COMMENT 'AI识别标签',
  `ai_content` text COMMENT 'AI识别内容',
  `ai_analyzed` char(1) DEFAULT '0' COMMENT 'AI是否已分析(0-否,1-是)',
  `view_count` int DEFAULT 0 COMMENT '查看次数',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人',
  `update_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记(0-正常,1-删除)',
  `tenant_id` bigint DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  KEY `idx_storage_file_file_type` (`file_type`),
  KEY `idx_storage_file_group_id` (`group_id`),
  KEY `idx_storage_file_md5` (`md5`),
  FULLTEXT KEY `idx_storage_file_ai_content` (`ai_content`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI存储文件表';

-- Source: 0matrix_boot.sql, table: ai_storage_file_tag
CREATE TABLE `ai_storage_file_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_id` bigint NOT NULL COMMENT '文件ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `confidence` decimal(5,2) DEFAULT NULL COMMENT '置信度(AI标签)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人',
  `update_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记(0-正常,1-删除)',
  `tenant_id` bigint DEFAULT 0 COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_tag` (`file_id`,`tag_id`,`del_flag`),
  KEY `idx_file_tag_tag_id` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件标签关联表';

-- Source: 0matrix_boot.sql, table: ai_terminology
CREATE TABLE `ai_terminology` (
  `term_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pid` bigint DEFAULT NULL COMMENT '所属术语',
  `word` varchar(255) DEFAULT NULL COMMENT '词语',
  `description` text COMMENT '描述',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '修改人',
  `create_time` datetime(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `update_time` datetime(6) DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标记，0未删除，1已删除',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
  PRIMARY KEY (`term_id`),
  KEY `idx_terminology_pid` (`pid`),
  KEY `idx_terminology_word` (`word`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI术语和同义词';

-- Source: 14matrix_capsule.sql, table: ai_capsule
CREATE TABLE IF NOT EXISTS `ai_capsule` (
  `id` bigint NOT NULL COMMENT '胶囊ID', `tenant_id` bigint NOT NULL COMMENT '租户ID',
  `owner_user_id` bigint NOT NULL COMMENT '所有者用户ID', `record_type` varchar(32) NOT NULL,
  `title` varchar(120) NOT NULL, `current_revision_id` bigint NOT NULL,
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL', `source_label` varchar(255) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE', `favorite_flag` char(1) NOT NULL DEFAULT '0',
  `captured_at` datetime NOT NULL, `version` int NOT NULL DEFAULT 1,
  `create_by` varchar(64) DEFAULT NULL, `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL, `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NOT NULL DEFAULT '0', PRIMARY KEY (`id`),
  KEY `idx_capsule_owner_status_time` (`tenant_id`, `owner_user_id`, `status`, `update_time`),
  KEY `idx_capsule_owner_type_time` (`tenant_id`, `owner_user_id`, `record_type`, `captured_at`),
  KEY `idx_capsule_current_revision` (`tenant_id`, `owner_user_id`, `current_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闪念胶囊';

-- Source: 14matrix_capsule.sql, table: ai_capsule_revision
CREATE TABLE IF NOT EXISTS `ai_capsule_revision` (
  `id` bigint NOT NULL COMMENT '版本ID', `capsule_id` bigint NOT NULL, `tenant_id` bigint NOT NULL,
  `owner_user_id` bigint NOT NULL, `revision_no` int NOT NULL,
  `content_format` varchar(16) NOT NULL DEFAULT 'MARKDOWN', `content` longtext NOT NULL,
  `content_hash` char(64) NOT NULL, `change_reason` varchar(255) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL, `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_capsule_revision_owner_no` (`tenant_id`, `owner_user_id`, `capsule_id`, `revision_no`),
  KEY `idx_capsule_revision_owner_id` (`tenant_id`, `owner_user_id`, `capsule_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闪念胶囊不可变版本';

-- Source: 15matrix_assistant.sql, table: ai_assistant_session
CREATE TABLE IF NOT EXISTS `ai_assistant_session` (
  `id` varchar(64) NOT NULL, `tenant_id` varchar(64) NOT NULL, `owner_user_id` varchar(64) NOT NULL,
  `title` varchar(255) NOT NULL, `status` varchar(32) NOT NULL, `active_scopes` varchar(255) NOT NULL,
  `last_source_route` varchar(512) DEFAULT NULL, `version` bigint NOT NULL DEFAULT 1,
  `message_sequence` bigint NOT NULL DEFAULT 0, `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL, PRIMARY KEY (`id`),
  KEY `idx_assistant_session_owner` (`tenant_id`, `owner_user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: 15matrix_assistant.sql, table: ai_assistant_message
CREATE TABLE IF NOT EXISTS `ai_assistant_message` (
  `id` varchar(64) NOT NULL, `session_id` varchar(64) NOT NULL, `role` varchar(32) NOT NULL,
  `content` longtext NOT NULL, `status` varchar(32) NOT NULL, `sequence_no` bigint NOT NULL,
  `context_pack_id` varchar(64) DEFAULT NULL, `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_assistant_message_sequence` (`session_id`, `sequence_no`),
  KEY `idx_assistant_message_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: 15matrix_assistant.sql, table: ai_assistant_context_pack
CREATE TABLE IF NOT EXISTS `ai_assistant_context_pack` (
  `id` varchar(64) NOT NULL, `session_id` varchar(64) NOT NULL, `message_id` varchar(64) NOT NULL,
  `identity_fingerprint` char(64) NOT NULL, `payload_json` longtext NOT NULL,
  `created_at` datetime(6) NOT NULL, `expires_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_assistant_context_session` (`session_id`, `message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: 15matrix_assistant.sql, table: ai_assistant_citation
CREATE TABLE IF NOT EXISTS `ai_assistant_citation` (
  `id` varchar(128) NOT NULL, `context_pack_id` varchar(64) NOT NULL, `provider_code` varchar(32) NOT NULL,
  `source_id` varchar(128) NOT NULL, `source_version` varchar(255) DEFAULT NULL,
  `route_type` varchar(32) NOT NULL, `route_name` varchar(128) NOT NULL, `route_params_json` text NOT NULL,
  `created_at` datetime(6) NOT NULL, PRIMARY KEY (`context_pack_id`, `id`),
  KEY `idx_assistant_citation_pack` (`context_pack_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: 15matrix_assistant.sql, table: ai_assistant_exchange
CREATE TABLE IF NOT EXISTS `ai_assistant_exchange` (
  `session_id` varchar(64) NOT NULL, `client_message_id` varchar(64) NOT NULL,
  `payload_json` longtext NOT NULL, `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`session_id`, `client_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: 21matrix_skill_runtime.sql, table: ai_agent_skill_release
CREATE TABLE IF NOT EXISTS `ai_agent_skill_release` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `tenant_id` varchar(40) NOT NULL COMMENT '租户ID',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `release_version` varchar(32) NOT NULL DEFAULT 'v1' COMMENT 'Skill发布版本',
  `release_status` varchar(24) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/GRAY/DISABLED',
  `gray_config` json DEFAULT NULL COMMENT '灰度规则JSON',
  `skill_id` bigint NOT NULL COMMENT '技能ID',
  `is_default_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '默认开关',
  `skill_config` json DEFAULT NULL COMMENT '技能实例配置',
  `execution_strategy` varchar(48) NOT NULL DEFAULT 'PRIMARY_APP_INSTRUCTION' COMMENT 'Skill执行策略',
  `workflow_agent_id` bigint DEFAULT NULL COMMENT '专用Dify Workflow平台智能体ID',
  `input_mapping` json DEFAULT NULL COMMENT '输入变量映射',
  `output_mapping` json DEFAULT NULL COMMENT '输出变量映射',
  `result_mode` varchar(32) NOT NULL DEFAULT 'FINAL_RESPONSE' COMMENT '结果模式',
  `timeout_ms` int NOT NULL DEFAULT 90000 COMMENT '超时毫秒数',
  `retry_policy` varchar(24) NOT NULL DEFAULT 'NONE' COMMENT '重试策略',
  `side_effect_class` varchar(24) NOT NULL DEFAULT 'READ_ONLY' COMMENT '副作用分类',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `released_at` datetime(6) DEFAULT NULL COMMENT '发布时间',
  `disabled_at` datetime(6) DEFAULT NULL COMMENT '停用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_skill_release` (`agent_id`, `skill_id`),
  KEY `idx_agent_skill_release_tenant` (`tenant_id`),
  KEY `idx_agent_skill_release_workflow` (`workflow_agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体已发布Skill绑定投影';

-- Source: 21matrix_skill_runtime.sql, table: ai_agent_skill_release_history
CREATE TABLE IF NOT EXISTS `ai_agent_skill_release_history` (
  `id` bigint NOT NULL COMMENT '历史快照主键',
  `tenant_id` varchar(40) NOT NULL,
  `agent_id` bigint NOT NULL,
  `skill_id` bigint NOT NULL,
  `release_version` varchar(32) NOT NULL,
  `release_status` varchar(24) NOT NULL DEFAULT 'ACTIVE',
  `gray_config` json DEFAULT NULL,
  `is_default_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `skill_config` json DEFAULT NULL,
  `execution_strategy` varchar(48) NOT NULL DEFAULT 'PRIMARY_APP_INSTRUCTION',
  `workflow_agent_id` bigint DEFAULT NULL,
  `input_mapping` json DEFAULT NULL,
  `output_mapping` json DEFAULT NULL,
  `result_mode` varchar(32) NOT NULL DEFAULT 'FINAL_RESPONSE',
  `timeout_ms` int NOT NULL DEFAULT 90000,
  `retry_policy` varchar(24) NOT NULL DEFAULT 'NONE',
  `side_effect_class` varchar(24) NOT NULL DEFAULT 'READ_ONLY',
  `sort` int NOT NULL DEFAULT 0,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_skill_release_history_agent` (`tenant_id`, `agent_id`, `release_version`),
  KEY `idx_skill_release_history_skill` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill发布历史快照';

-- Source: 21matrix_skill_runtime.sql, table: ai_assistant_external_conversation
CREATE TABLE IF NOT EXISTS `ai_assistant_external_conversation` (
  `session_id` varchar(64) NOT NULL,
  `runtime_key` varchar(128) NOT NULL,
  `conversation_id` varchar(128) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`session_id`, `runtime_key`),
  KEY `idx_assistant_external_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: 21matrix_skill_runtime.sql, table: ai_assistant_execution_run
CREATE TABLE IF NOT EXISTS `ai_assistant_execution_run` (
  `id` varchar(64) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `message_id` varchar(64) NOT NULL,
  `client_message_id` varchar(64) DEFAULT NULL,
  `execution_plan` varchar(32) NOT NULL,
  `runtime_mode` varchar(32) DEFAULT NULL,
  `runtime_id` varchar(128) DEFAULT NULL,
  `task_id` varchar(128) DEFAULT NULL,
  `workflow_run_id` varchar(128) DEFAULT NULL,
  `conversation_id` varchar(128) DEFAULT NULL,
  `activated_skills_json` text NOT NULL,
  `status` varchar(32) NOT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `tenant_id` varchar(64) DEFAULT NULL,
  `agent_id` bigint DEFAULT NULL,
  `agent_version` varchar(32) DEFAULT NULL,
  `skill_versions_json` text DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `citations_json` text DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_assistant_execution_session` (`session_id`, `created_at`),
  KEY `idx_assistant_execution_workflow` (`workflow_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Source: 58matrix_managed_skill.sql, table: ai_skill_release
CREATE TABLE IF NOT EXISTS ai_skill_release (
  id bigint NOT NULL,
  tenant_id varchar(64) NOT NULL,
  skill_id bigint NOT NULL,
  package_id bigint NOT NULL,
  version varchar(64) NOT NULL,
  runtime_kind varchar(32) NOT NULL,
  content_snapshot longtext NOT NULL,
  input_schema json NOT NULL,
  output_schema json NOT NULL,
  executor_ref json NOT NULL,
  resource_refs json NOT NULL,
  limits_json json NOT NULL,
  allowed_parameters json NOT NULL,
  package_sha256 char(64) NOT NULL,
  release_digest char(64) NOT NULL,
  status varchar(16) NOT NULL COMMENT 'PUBLISHED/REVOKED; content cannot be updated',
  created_by varchar(64) NOT NULL,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  revoked_by varchar(64) DEFAULT NULL,
  revoked_at datetime(3) DEFAULT NULL,
  revocation_reason varchar(512) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_release_version (tenant_id, skill_id, version),
  KEY idx_skill_release_selection (tenant_id, skill_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Immutable managed Skill releases';

-- Source: 58matrix_managed_skill.sql, table: ai_skill_binding
CREATE TABLE IF NOT EXISTS ai_skill_binding (
  id bigint NOT NULL,
  tenant_id varchar(64) NOT NULL,
  consumer_type varchar(64) NOT NULL,
  consumer_id varchar(128) NOT NULL,
  slot_code varchar(64) NOT NULL,
  binding_key varchar(128) NOT NULL,
  active_revision_id bigint DEFAULT NULL,
  revision bigint NOT NULL DEFAULT 0 COMMENT 'Compare-and-set counter for pointer changes',
  created_by varchar(64) NOT NULL,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by varchar(64) NOT NULL,
  updated_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_binding_consumer (tenant_id, consumer_type, consumer_id, slot_code, binding_key),
  KEY idx_skill_binding_active (tenant_id, active_revision_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Managed consumer binding pointers';

-- Source: 58matrix_managed_skill.sql, table: ai_skill_binding_revision
CREATE TABLE IF NOT EXISTS ai_skill_binding_revision (
  id bigint NOT NULL,
  tenant_id varchar(64) NOT NULL,
  binding_id bigint NOT NULL,
  release_id bigint NOT NULL,
  enabled tinyint NOT NULL,
  audience_json json NOT NULL,
  activation_mode varchar(32) NOT NULL,
  parameter_json json NOT NULL,
  fallback_policy json NOT NULL,
  revision bigint NOT NULL,
  created_by varchar(64) NOT NULL,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_binding_revision (tenant_id, binding_id, revision),
  KEY idx_skill_revision_release (release_id, tenant_id, binding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Append-only binding revisions';

-- Source: 58matrix_managed_skill.sql, table: ai_skill_run
CREATE TABLE IF NOT EXISTS ai_skill_run (
  id bigint NOT NULL,
  tenant_id varchar(64) NOT NULL,
  binding_revision_id bigint NOT NULL,
  release_id bigint NOT NULL,
  actor_id varchar(64) NOT NULL,
  subject_id varchar(64) NOT NULL,
  consumer_type varchar(64) NOT NULL,
  consumer_id varchar(128) NOT NULL,
  slot_code varchar(64) NOT NULL,
  binding_key varchar(128) NOT NULL,
  parent_run_id varchar(128) DEFAULT NULL,
  business_run_id varchar(128) NOT NULL,
  correlation_id varchar(128) NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  idempotency_scope_digest char(64) NOT NULL COMMENT 'SHA256 of tenant, actor, subject, consumer identity and idempotency key',
  input_digest char(64) NOT NULL,
  request_digest char(64) NOT NULL COMMENT 'Detect same key with different input or frozen release',
  status varchar(16) NOT NULL COMMENT 'QUEUED/RUNNING/SUCCEEDED/DEGRADED/FAILED/CANCELLED/UNKNOWN',
  result_ref varchar(1024) DEFAULT NULL,
  artifact_refs json DEFAULT NULL,
  executor_version varchar(128) DEFAULT NULL,
  error_code varchar(64) DEFAULT NULL,
  timing json DEFAULT NULL,
  usage_json json DEFAULT NULL,
  revision bigint NOT NULL DEFAULT 0,
  created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  started_at datetime(3) DEFAULT NULL,
  finished_at datetime(3) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_run_idempotency (tenant_id, idempotency_scope_digest),
  KEY idx_skill_run_business (tenant_id, business_run_id),
  KEY idx_skill_run_subject (tenant_id, subject_id, created_at),
  KEY idx_skill_run_release (tenant_id, release_id, created_at),
  KEY idx_skill_run_parent (tenant_id, parent_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Managed Skill execution provenance; business results remain in original services';

-- Source: 0matrix_boot.sql, table: qrtz_triggers
CREATE TABLE `qrtz_triggers` (
                                 `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                 `trigger_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                 `trigger_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                 `job_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                 `job_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                 `description` varchar(250) CHARACTER SET utf8  DEFAULT NULL,
                                 `next_fire_time` bigint DEFAULT NULL,
                                 `prev_fire_time` bigint DEFAULT NULL,
                                 `priority` int DEFAULT NULL,
                                 `trigger_state` varchar(16) CHARACTER SET utf8  NOT NULL,
                                 `trigger_type` varchar(8) CHARACTER SET utf8  NOT NULL,
                                 `start_time` bigint NOT NULL,
                                 `end_time` bigint DEFAULT NULL,
                                 `calendar_name` varchar(200) CHARACTER SET utf8  DEFAULT NULL,
                                 `misfire_instr` smallint DEFAULT NULL,
                                 `job_data` blob,
                                 PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
                                 KEY `sched_name` (`sched_name`,`job_name`,`job_group`) USING BTREE,
                                 CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_blob_triggers
CREATE TABLE `qrtz_blob_triggers` (
                                      `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                      `trigger_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                      `trigger_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                      `blob_data` blob,
                                      PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
                                      CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_cron_triggers
CREATE TABLE `qrtz_cron_triggers` (
                                      `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                      `trigger_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                      `trigger_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                      `cron_expression` varchar(200) CHARACTER SET utf8  NOT NULL,
                                      `time_zone_id` varchar(80) CHARACTER SET utf8  DEFAULT NULL,
                                      PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
                                      CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_simple_triggers
CREATE TABLE `qrtz_simple_triggers` (
                                        `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                        `trigger_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                        `trigger_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                        `repeat_count` bigint NOT NULL,
                                        `repeat_interval` bigint NOT NULL,
                                        `times_triggered` bigint NOT NULL,
                                        PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
                                        CONSTRAINT `QRTZ_SIMPLE_TRIGGERS_IBFK_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Source: 0matrix_boot.sql, table: qrtz_simprop_triggers
CREATE TABLE `qrtz_simprop_triggers` (
                                         `sched_name` varchar(120) CHARACTER SET utf8  NOT NULL,
                                         `trigger_name` varchar(200) CHARACTER SET utf8  NOT NULL,
                                         `trigger_group` varchar(200) CHARACTER SET utf8  NOT NULL,
                                         `str_prop_1` varchar(512) CHARACTER SET utf8  DEFAULT NULL,
                                         `str_prop_2` varchar(512) CHARACTER SET utf8  DEFAULT NULL,
                                         `str_prop_3` varchar(512) CHARACTER SET utf8  DEFAULT NULL,
                                         `int_prop_1` int DEFAULT NULL,
                                         `int_prop_2` int DEFAULT NULL,
                                         `long_prop_1` bigint DEFAULT NULL,
                                         `long_prop_2` bigint DEFAULT NULL,
                                         `dec_prop_1` decimal(13,4) DEFAULT NULL,
                                         `dec_prop_2` decimal(13,4) DEFAULT NULL,
                                         `bool_prop_1` varchar(1) CHARACTER SET utf8  DEFAULT NULL,
                                         `bool_prop_2` varchar(1) CHARACTER SET utf8  DEFAULT NULL,
                                         PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`) USING BTREE,
                                         CONSTRAINT `QRTZ_SIMPROP_TRIGGERS_IBFK_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 原框架 44matrix_assistant_session_history.sql 补充的索引前置列。
ALTER TABLE ai_assistant_session ADD COLUMN last_message_at datetime(6) DEFAULT NULL;
-- Required by the locked SysTenant mapper; no inherited portal values.
ALTER TABLE sys_tenant ADD COLUMN portal_domain varchar(255) NULL;
