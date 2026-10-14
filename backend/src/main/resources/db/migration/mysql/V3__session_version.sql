-- Add session_version only when it is missing from an existing commonweave_users table.
SET @commonweave_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'commonweave_users' AND column_name = 'session_version') = 0,
  'ALTER TABLE commonweave_users ADD COLUMN session_version INT NOT NULL DEFAULT 0',
  'SELECT 1'
);
PREPARE commonweave_stmt FROM @commonweave_sql;
EXECUTE commonweave_stmt;
DEALLOCATE PREPARE commonweave_stmt;
