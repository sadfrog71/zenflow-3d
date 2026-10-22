-- Product-only rolling history summaries, scoped by the existing authorization lineage.
ALTER TABLE zf_companion_run_context
 ADD COLUMN history_summary MEDIUMTEXT NULL,
 ADD COLUMN summary_end_seq BIGINT NULL,
 ADD COLUMN summary_lineage_digest VARCHAR(64) NULL;
