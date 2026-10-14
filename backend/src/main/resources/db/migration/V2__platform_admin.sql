ALTER TABLE users
  ADD COLUMN platform_role VARCHAR(10) NOT NULL DEFAULT 'USER',
  ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN last_login_at DATETIME(6) NULL;

CREATE TABLE admin_actions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  admin_id BIGINT NOT NULL,
  action VARCHAR(40) NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_admin_actions_admin FOREIGN KEY (admin_id) REFERENCES users (id),
  KEY ix_admin_actions_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
