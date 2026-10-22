-- Overseas consumer login; execute after B00, B01 and product seed on isolated product DB.
CREATE TABLE zf_external_identity (
 id bigint NOT NULL PRIMARY KEY,
 tenant_id bigint NOT NULL,
 user_id bigint NOT NULL,
 provider varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 issuer varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_app_scope varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 subject varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_email varchar(254) NULL,
 provider_email_verified tinyint NOT NULL DEFAULT 0,
 created_at datetime(3) NOT NULL,
 last_login_at datetime(3) NOT NULL,
 UNIQUE KEY uk_zf_external_subject(tenant_id,provider,provider_app_scope,subject),
 UNIQUE KEY uk_zf_user_provider(tenant_id,user_id,provider),
 KEY idx_zf_external_user(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
