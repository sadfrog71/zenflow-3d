-- Independent public submissions; private capsules are never copied implicitly.
CREATE TABLE zf_community_post (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL,
 author_name VARCHAR(120) NOT NULL, title VARCHAR(120) NOT NULL, content MEDIUMTEXT NOT NULL,
 category VARCHAR(16) NOT NULL, state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 featured BOOLEAN NOT NULL DEFAULT FALSE, version INT NOT NULL DEFAULT 1,
 review_note VARCHAR(500) NOT NULL DEFAULT '', reviewed_by BIGINT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_community_feed(tenant_id,state,id), INDEX ix_community_owner(tenant_id,owner_user_id,id)
);
CREATE TABLE zf_community_attachment (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, owner_user_id BIGINT NOT NULL,
 post_id BIGINT NULL, mime VARCHAR(20) NOT NULL, data MEDIUMBLOB NOT NULL,
 thumbnail MEDIUMTEXT NOT NULL, width INT NOT NULL, height INT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_community_attachment_owner(tenant_id,owner_user_id,post_id,created_at),
 INDEX ix_community_attachment_post(tenant_id,post_id)
);
