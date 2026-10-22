-- P1 preview query support; apply once after P03 and schema preflight.
CREATE INDEX `ix_preview_active_limit` ON `zf_companion_preview` (`tenant_id`,`administrator_id`,`status`,`expires_at`);
