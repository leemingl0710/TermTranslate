-- Brings the existing Neon DB (original db/schema.sql) up to the current db/schema.sql.
-- Run once in the Neon SQL Editor as the owner account (app_user* cannot ALTER tables).
-- Runs in one transaction: if any statement fails, nothing is changed.
-- Team members' logins, passwords and DB_URL are not affected.

BEGIN;

-- users: case-insensitive unique username
ALTER TABLE users DROP CONSTRAINT users_username_key;
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));

-- terms: no duplicate Korean terms
ALTER TABLE terms ADD CONSTRAINT terms_korean_term_key UNIQUE (korean_term);

-- analysis_results: new columns
ALTER TABLE analysis_results ADD COLUMN source_text    JSONB;
ALTER TABLE analysis_results ADD COLUMN exam_questions TEXT;

-- Foreign keys: add ON DELETE rules
ALTER TABLE documents DROP CONSTRAINT documents_user_id_fkey;
ALTER TABLE documents ADD CONSTRAINT documents_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE api_logs DROP CONSTRAINT api_logs_user_id_fkey;
ALTER TABLE api_logs ADD CONSTRAINT api_logs_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE SET NULL;

ALTER TABLE analysis_results DROP CONSTRAINT analysis_results_document_id_fkey;
ALTER TABLE analysis_results ADD CONSTRAINT analysis_results_document_id_fkey
  FOREIGN KEY (document_id) REFERENCES documents (document_id) ON DELETE CASCADE;

-- New table: terms found in each analysis
CREATE TABLE analysis_terms (
  result_id INT NOT NULL REFERENCES analysis_results (result_id) ON DELETE CASCADE,
  term_id   INT NOT NULL REFERENCES terms (term_id) ON DELETE CASCADE,
  PRIMARY KEY (result_id, term_id)
);
CREATE INDEX idx_analysis_terms_term_id ON analysis_terms (term_id);

-- Make sure team roles can use the new table (safe to re-run).
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rw;

COMMIT;
