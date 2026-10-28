-- Migración manual para bases FIXTECH existentes.
-- El proyecto mantiene Flyway deshabilitado; ejecutar una sola vez:
--   mysql -h 127.0.0.1 -u root fixtech_db < db/migraciones/001_recuperacion_otp.sql
--
-- Convierte la tabla token_recuperacion (sin uso antes de esta fase) al contrato OTP.

ALTER TABLE token_recuperacion
  CHANGE COLUMN token_hash codigo_hash VARCHAR(100) NOT NULL
    COMMENT 'BCrypt del código OTP, nunca texto plano',
  ADD COLUMN ticket_hash VARCHAR(64) NULL
    COMMENT 'SHA-256 del ticket temporal' AFTER codigo_hash,
  ADD COLUMN ticket_expira_en DATETIME NULL
    COMMENT 'Vigencia del ticket posterior a la verificación' AFTER expira_en,
  ADD COLUMN intentos TINYINT NOT NULL DEFAULT 0 AFTER ticket_expira_en,
  ADD COLUMN ticket_usado TINYINT(1) NOT NULL DEFAULT 0
    COMMENT 'Ticket consumido' AFTER usado;

CREATE INDEX idx_tokrec_usuario ON token_recuperacion (usuario_id, creado_en);
CREATE INDEX idx_tokrec_ticket ON token_recuperacion (ticket_hash);
