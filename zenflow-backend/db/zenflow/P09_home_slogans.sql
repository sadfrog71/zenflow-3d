-- Public home copy only; no consumer identity or private content.
CREATE TABLE zf_home_slogans (
 tenant_id BIGINT NOT NULL PRIMARY KEY,
 version BIGINT NOT NULL DEFAULT 0,
 items_json JSON NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
