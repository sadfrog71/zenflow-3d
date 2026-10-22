-- Versioned book sources, translations, reader progress and bookmarks.
-- Apply after P10_note_pinning.sql. This migration is intentionally not auto-run.
CREATE TABLE zf_book (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  author VARCHAR(200) NOT NULL DEFAULT '',
  publisher VARCHAR(200) NOT NULL DEFAULT '',
  source_language VARCHAR(16) NOT NULL,
  source_version_label VARCHAR(128) NOT NULL,
  source_url VARCHAR(1024) NOT NULL DEFAULT '',
  category VARCHAR(64) NOT NULL DEFAULT 'classics',
  summary VARCHAR(2000) NOT NULL DEFAULT '',
  rights_holder VARCHAR(200) NOT NULL DEFAULT '',
  license_name VARCHAR(200) NOT NULL DEFAULT '',
  license_evidence VARCHAR(2000) NOT NULL DEFAULT '',
  license_territory VARCHAR(200) NOT NULL DEFAULT '',
  license_term VARCHAR(200) NOT NULL DEFAULT '',
  license_expires_on DATE NULL,
  permission_display TINYINT(1) NOT NULL DEFAULT 0,
  permission_translate TINYINT(1) NOT NULL DEFAULT 0,
  permission_adapt TINYINT(1) NOT NULL DEFAULT 0,
  permission_ai TINYINT(1) NOT NULL DEFAULT 0,
  authorization_verified TINYINT(1) NOT NULL DEFAULT 0,
  state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  version INT NOT NULL DEFAULT 1,
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_book_tenant_id (tenant_id, id),
  KEY ix_zf_book_tenant_state (tenant_id, state, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_book_source_asset (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  file_name VARCHAR(200) NOT NULL,
  file_type VARCHAR(16) NOT NULL,
  file_hash CHAR(64) NOT NULL,
  file_blob LONGBLOB NOT NULL,
  create_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_book_source_asset_tenant_id (tenant_id, id),
  KEY ix_zf_book_source_asset_book (tenant_id, book_id, id),
  CONSTRAINT fk_zf_book_source_asset_book FOREIGN KEY (tenant_id, book_id)
    REFERENCES zf_book (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_book_source_version (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  extracted_hash CHAR(64) NOT NULL,
  source_reviewed TINYINT(1) NOT NULL DEFAULT 0,
  reviewed_by VARCHAR(64) NULL,
  reviewed_at DATETIME(3) NULL,
  create_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_book_source_version (tenant_id, book_id, version_no),
  UNIQUE KEY uk_zf_book_source_tenant_id (tenant_id, id),
  KEY ix_zf_book_source_book (tenant_id, book_id, id),
  CONSTRAINT fk_zf_book_source_asset FOREIGN KEY (tenant_id, asset_id)
    REFERENCES zf_book_source_asset (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE zf_book
  ADD COLUMN current_source_version_id BIGINT NULL AFTER state,
  ADD KEY ix_zf_book_current_source (tenant_id, current_source_version_id);

CREATE TABLE zf_book_source_chapter (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  source_version_id BIGINT NOT NULL,
  chapter_no INT NOT NULL,
  title VARCHAR(300) NOT NULL,
  source_text MEDIUMTEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_book_source_chapter (tenant_id, source_version_id, chapter_no),
  UNIQUE KEY uk_zf_book_source_chapter_tenant_id (tenant_id, id),
  KEY ix_zf_book_source_chapters (tenant_id, book_id, source_version_id, chapter_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_book_translation (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  source_version_id BIGINT NOT NULL,
  target_language VARCHAR(16) NOT NULL,
  revision_no INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  version INT NOT NULL DEFAULT 1,
  is_excerpt TINYINT(1) NOT NULL DEFAULT 0,
  excerpt_label VARCHAR(200) NOT NULL DEFAULT '',
  translation_origin VARCHAR(24) NOT NULL DEFAULT 'SERVER_MODEL',
  translation_model_note VARCHAR(200) NOT NULL DEFAULT '',
  translator_note MEDIUMTEXT NULL,
  qa_report JSON NULL,
  qa_hard_issues INT NOT NULL DEFAULT 0,
  rights_snapshot JSON NULL,
  translated_by VARCHAR(64) NULL,
  reviewed_by VARCHAR(64) NULL,
  reviewed_at DATETIME(3) NULL,
  published_by VARCHAR(64) NULL,
  published_at DATETIME(3) NULL,
  create_by VARCHAR(64) NOT NULL,
  update_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_book_translation_revision (tenant_id, book_id, source_version_id, target_language, revision_no),
  UNIQUE KEY uk_zf_book_translation_tenant_id (tenant_id, id),
  KEY ix_zf_book_translation_public (tenant_id, book_id, target_language, status, published_at),
  KEY ix_zf_book_translation_source (tenant_id, source_version_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_book_translation_asset (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  translation_id BIGINT NOT NULL,
  file_name VARCHAR(200) NOT NULL,
  file_type VARCHAR(16) NOT NULL,
  file_hash CHAR(64) NOT NULL,
  file_blob LONGBLOB NOT NULL,
  create_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_book_translation_asset_tenant_id (tenant_id, id),
  KEY ix_zf_book_translation_asset (tenant_id, book_id, translation_id),
  CONSTRAINT fk_zf_book_translation_asset_book FOREIGN KEY (tenant_id, book_id)
    REFERENCES zf_book (tenant_id, id),
  CONSTRAINT fk_zf_book_translation_asset_translation FOREIGN KEY (tenant_id, translation_id)
    REFERENCES zf_book_translation (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_book_translation_chapter (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  translation_id BIGINT NOT NULL,
  source_chapter_id BIGINT NOT NULL,
  chapter_no INT NOT NULL,
  included TINYINT(1) NOT NULL DEFAULT 1,
  translation_text MEDIUMTEXT NOT NULL,
  commentary MEDIUMTEXT NOT NULL,
  annotations JSON NULL,
  translation_state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  review_state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  failure_reason VARCHAR(500) NOT NULL DEFAULT '',
  qa_report JSON NULL,
  updated_by VARCHAR(64) NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_book_translation_chapter (tenant_id, translation_id, source_chapter_id),
  KEY ix_zf_book_translation_chapter_order (tenant_id, translation_id, chapter_no),
  CONSTRAINT fk_zf_book_translation_chapter_translation FOREIGN KEY (tenant_id, translation_id)
    REFERENCES zf_book_translation (tenant_id, id),
  CONSTRAINT fk_zf_book_translation_chapter_source FOREIGN KEY (tenant_id, source_chapter_id)
    REFERENCES zf_book_source_chapter (tenant_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_book_reader_preference (
  tenant_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  sync_enabled TINYINT(1) NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (tenant_id, owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_book_reader_progress (
  tenant_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  translation_id BIGINT NOT NULL,
  language VARCHAR(16) NOT NULL,
  chapter_no INT NOT NULL,
  paragraph_no INT NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (tenant_id, owner_user_id, translation_id, language),
  KEY ix_zf_book_progress_owner (tenant_id, owner_user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zf_book_bookmark (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  translation_id BIGINT NOT NULL,
  language VARCHAR(16) NOT NULL,
  chapter_no INT NOT NULL,
  paragraph_no INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_zf_book_bookmark_anchor (tenant_id, owner_user_id, translation_id, language, chapter_no, paragraph_no),
  KEY ix_zf_book_bookmark_owner (tenant_id, owner_user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
