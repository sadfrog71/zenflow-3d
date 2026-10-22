-- Invelle operational metadata only. No private text, bearer tokens or payment rules.
CREATE TABLE zf_user_login_event (
 tenant_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL,
 authorization_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 logged_in_at DATETIME(3) NOT NULL,
 PRIMARY KEY(tenant_id,authorization_id), KEY ix_zf_login_owner(tenant_id,owner_user_id,logged_in_at)
) ENGINE=InnoDB;
CREATE TABLE zf_user_activity (
 tenant_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL,
 active_millis BIGINT NOT NULL DEFAULT 0, last_seen_at DATETIME(3) NULL,
 PRIMARY KEY(tenant_id,owner_user_id)
) ENGINE=InnoDB;
CREATE TABLE zf_user_activity_session (
 tenant_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL,
 session_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 sequence_no BIGINT NOT NULL, last_pulse_at DATETIME(3) NOT NULL, ended TINYINT NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,owner_user_id,session_key), KEY ix_zf_activity_pulse(last_pulse_at)
) ENGINE=InnoDB;
