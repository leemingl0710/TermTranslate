-- Adds the "verified" flag to the shared term dictionary.
--   true  = added or checked by a person (all existing rows)
--   false = found automatically by the LLM during analysis, waiting for a person to approve it
-- Run once in the Neon SQL Editor as the owner account (app_user* cannot ALTER tables).
-- Safe for older code: the column has a default, so existing INSERTs keep working.

BEGIN;

ALTER TABLE terms ADD COLUMN verified BOOLEAN NOT NULL DEFAULT true;

COMMIT;
