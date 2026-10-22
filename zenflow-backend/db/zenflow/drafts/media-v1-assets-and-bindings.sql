-- REVIEW DRAFT ONLY. No migration number; do not execute against any existing environment.
-- Reuses zf_client_operation from garden-v1-state-and-receipts.sql.
CREATE TABLE zf_media_asset (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tenant_id BIGINT NOT NULL,
  subject_type VARCHAR(16) NOT NULL DEFAULT 'APP_USER',
  owner_user_id BIGINT NOT NULL,
  purpose VARCHAR(24) NOT NULL,
  processing_state VARCHAR(24) NOT NULL DEFAULT 'RECEIVING',
  version INT NOT NULL DEFAULT 1,
  declared_mime VARCHAR(96) NOT NULL,
  declared_bytes BIGINT NOT NULL,
  declared_duration_seconds DOUBLE NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  max_bytes BIGINT NOT NULL,
  max_duration_seconds DOUBLE NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  storage_ref VARCHAR(64) NULL,
  quarantine_key VARCHAR(256) NULL,
  object_key VARCHAR(256) NULL,
  processing_request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  processing_base_version INT NULL,
  actual_mime VARCHAR(96) NULL,
  actual_bytes BIGINT NULL,
  actual_duration_seconds DOUBLE NULL,
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  KEY ix_zf_media_owner(tenant_id,subject_type,owner_user_id,processing_state,id),
  CONSTRAINT ck_zf_media_subject CHECK(subject_type='APP_USER'),
  CONSTRAINT ck_zf_media_purpose CHECK(purpose IN ('PRIVATE_NOTE','PUBLIC_SUBMISSION','FRIEND_LETTER')),
  CONSTRAINT ck_zf_media_state CHECK(processing_state IN ('RECEIVING','PROCESSING','READY','FAILED','EXPIRED','DELETE_REQUESTED','DELETED')),
  CONSTRAINT ck_zf_media_version CHECK(version>=1),
  CONSTRAINT ck_zf_media_ready CHECK(processing_state<>'READY' OR (object_key IS NOT NULL AND storage_ref IS NOT NULL AND quarantine_key IS NULL AND actual_mime IS NOT NULL AND actual_bytes IS NOT NULL AND actual_bytes>0 AND actual_duration_seconds IS NOT NULL AND actual_duration_seconds>0)),
  CONSTRAINT ck_zf_media_size CHECK(declared_bytes>0 AND declared_bytes<=max_bytes),
  CONSTRAINT ck_zf_media_duration CHECK(declared_duration_seconds>0 AND declared_duration_seconds<=max_duration_seconds)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_media_upload (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tenant_id BIGINT NOT NULL,
  subject_type VARCHAR(16) NOT NULL DEFAULT 'APP_USER',
  owner_user_id BIGINT NOT NULL,
  version INT NOT NULL DEFAULT 1,
  received_at DATETIME(3) NULL,
  lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  lease_until DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_zf_upload_media(media_id),
  KEY ix_zf_upload_owner(tenant_id,subject_type,owner_user_id,id),
  CONSTRAINT ck_zf_upload_subject CHECK(subject_type='APP_USER')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_media_binding (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  subject_type VARCHAR(16) NOT NULL DEFAULT 'APP_USER',
  owner_user_id BIGINT NOT NULL,
  media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  target_version INT NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL_AUDIO',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_zf_media_binding(media_id,target_type,target_id,target_version,role),
  KEY ix_zf_media_parent(tenant_id,target_type,target_id,target_version),
  CONSTRAINT ck_zf_binding_subject CHECK(subject_type='APP_USER'),
  CONSTRAINT ck_zf_binding_version CHECK(target_version>=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Purpose is immutable in service writes. Only READY assets can bind in the parent-content transaction.
-- Every play request must recheck the parent, friendship/block/withdrawal and current account.
-- No business success or public visibility is inferred from an upload registration or stored object.

-- MEDIA_STAGE_DELETE / MEDIA_STAGE events are inserted into existing zf_outbox in the receive-lease transaction.
-- One independent dedupe key per mediaId + stage token; payload is only owner/upload/media IDs, hash and bytes.
-- Successful stage-cleanup tombstones retain next_attempt_at for periodic late-write reconciliation.
-- Do not purge these event mappings as generic completed jobs before a bounded-write/retention policy is verified.

-- A completed immutable stage may be promoted by metadata only: object_key retains the opaque stage token.
-- The product adapter reconstructs the private provider key from mediaId/token/hash; no provider key is public.
-- READY object tokens are protected by current media references/bindings; expired unbound READY rows retire first.

-- NOTE bindings use target_id = capsule ID and target_version = immutable capsule revision_no.
-- Capsule metadata version/current_revision_id are checked by the note facade; title/favorite changes do not rebind.
-- PRIVATE_NOTE assets cannot bind to another note, a public submission or a friend letter in this adapter.
