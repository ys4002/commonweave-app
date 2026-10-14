-- Add these columns only when they are missing from an existing commonweave_users table.
SET @commonweave_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'commonweave_users' AND column_name = 'platform_role') = 0,
  'ALTER TABLE commonweave_users ADD COLUMN platform_role VARCHAR(10) NOT NULL DEFAULT ''USER''',
  'SELECT 1'
);
PREPARE commonweave_stmt FROM @commonweave_sql;
EXECUTE commonweave_stmt;
DEALLOCATE PREPARE commonweave_stmt;

SET @commonweave_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'commonweave_users' AND column_name = 'enabled') = 0,
  'ALTER TABLE commonweave_users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE',
  'SELECT 1'
);
PREPARE commonweave_stmt FROM @commonweave_sql;
EXECUTE commonweave_stmt;
DEALLOCATE PREPARE commonweave_stmt;

SET @commonweave_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'commonweave_users' AND column_name = 'last_login_at') = 0,
  'ALTER TABLE commonweave_users ADD COLUMN last_login_at DATETIME(6) NULL',
  'SELECT 1'
);
PREPARE commonweave_stmt FROM @commonweave_sql;
EXECUTE commonweave_stmt;
DEALLOCATE PREPARE commonweave_stmt;

CREATE TABLE IF NOT EXISTS admin_actions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  admin_id BIGINT NOT NULL,
  action VARCHAR(40) NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_admin_actions_admin FOREIGN KEY (admin_id) REFERENCES commonweave_users (id),
  KEY ix_admin_actions_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
