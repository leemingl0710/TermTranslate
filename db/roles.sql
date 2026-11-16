-- Team member roles: data read/write only, no schema changes.
-- Already applied to Neon (neondb). Passwords are set separately with
--   ALTER ROLE app_userN WITH PASSWORD '...';
-- Never commit passwords to this file.

CREATE ROLE app_rw NOLOGIN;
GRANT CONNECT ON DATABASE neondb TO app_rw;
GRANT USAGE ON SCHEMA public TO app_rw;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_rw;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_rw;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO app_rw;

CREATE ROLE app_user1 LOGIN IN ROLE app_rw;
CREATE ROLE app_user2 LOGIN IN ROLE app_rw;
CREATE ROLE app_user3 LOGIN IN ROLE app_rw;
