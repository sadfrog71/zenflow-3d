-- G01 REVIEW DRAFT ONLY. No migration number assigned; not loaded or executed.
-- Local highest observed batch is P11 (2026-10-22); recheck before promoting.
-- MySQL 8. Owner is always derived from tenant + APP_USER authenticated principal.
CREATE TABLE zf_garden_state (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  subject_type VARCHAR(16) NOT NULL DEFAULT 'APP_USER',
  owner_user_id BIGINT NOT NULL,
  scene_version VARCHAR(64) NOT NULL,
  state_version INT NOT NULL DEFAULT 1,
  fish_json JSON NOT NULL,
  placements_json JSON NOT NULL,
  environment_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_garden_owner (tenant_id, subject_type, owner_user_id),
  CONSTRAINT ck_zf_garden_subject CHECK (subject_type = 'APP_USER'),
  CONSTRAINT ck_zf_garden_version CHECK (state_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Separate from notification outbox and on-device sync queue; no private bodies here.
-- Key/hash are case-sensitive. Preserve tombstone mapping after target deletion.
CREATE TABLE zf_client_operation (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  subject_type VARCHAR(16) NOT NULL DEFAULT 'APP_USER',
  owner_user_id BIGINT NOT NULL,
  operation_type VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  client_request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  state VARCHAR(16) NOT NULL,
  result_type VARCHAR(48) NULL,
  result_id VARCHAR(64) NULL,
  result_version INT NULL,
  safe_error_code VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_client_operation (tenant_id, subject_type, owner_user_id, operation_type, client_request_id),
  KEY ix_zf_client_operation_owner (tenant_id, subject_type, owner_user_id, updated_at, id),
  CONSTRAINT ck_zf_operation_subject CHECK (subject_type = 'APP_USER'),
  CONSTRAINT ck_zf_operation_state CHECK (state IN ('PROCESSING','SUCCEEDED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Writers must lock/conditionally update expected version and persist success receipt in same transaction.
-- INIT only at expectedVersion=0. GET never creates. No DROP or seed data in this draft.
