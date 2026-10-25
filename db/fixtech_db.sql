-- =====================================================================
--  FIXTECH - Base de datos completa (MySQL 8 / MariaDB 10.4+ de XAMPP)
--  Plataforma web de servicios de soporte y mantenimiento tecnologico
--  Piura, 2026
--
--  COMO USARLO (phpMyAdmin): pestana "Importar" -> elegir este archivo -> Continuar.
--  ADVERTENCIA: el script BORRA y vuelve a crear las tablas de fixtech_db.
--               No lo ejecutes de nuevo si ya tienes datos que quieres conservar.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS fixtech_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE fixtech_db;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS auditoria, token_recuperacion, parametro_sistema, notificacion,
  reclamo_foto, reclamo, mensaje, resena, pago_comision_detalle, pago_comision, comision,
  ajuste_precio, avance_servicio, constancia_foto, constancia, historial_estado_servicio,
  servicio, contraoferta, oferta, imagen_solicitud, solicitud, tipo_servicio, tipo_equipo,
  documento_tecnico, tecnico_distrito, tecnico_especialidad, distrito, especialidad,
  direccion_cliente, tecnico, cliente, administrador, usuario;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 1. USUARIOS Y ROLES
-- =====================================================================

CREATE TABLE usuario (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  correo                VARCHAR(120) NOT NULL,
  password_hash         VARCHAR(100) NOT NULL COMMENT 'BCrypt, nunca texto plano',
  rol                   VARCHAR(10)  NOT NULL,
  estado                VARCHAR(12)  NOT NULL DEFAULT 'ACTIVO',
  motivo_suspension     VARCHAR(300) NULL,
  intentos_fallidos     INT          NOT NULL DEFAULT 0,
  bloqueado_hasta       DATETIME     NULL,
  acepto_terminos       TINYINT(1)   NOT NULL DEFAULT 0,
  fecha_acepto_terminos DATETIME     NULL,
  creado_en             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_usuario_correo UNIQUE (correo),
  CONSTRAINT ck_usuario_rol    CHECK (rol IN ('CLIENTE','TECNICO','ADMIN')),
  CONSTRAINT ck_usuario_estado CHECK (estado IN ('ACTIVO','SUSPENDIDO','INACTIVO'))
) ENGINE=InnoDB;

CREATE TABLE administrador (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id BIGINT       NOT NULL,
  nombres    VARCHAR(80)  NOT NULL,
  apellidos  VARCHAR(80)  NOT NULL,
  CONSTRAINT uq_admin_usuario UNIQUE (usuario_id),
  CONSTRAINT fk_admin_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE cliente (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id BIGINT       NOT NULL,
  nombres    VARCHAR(80)  NOT NULL,
  apellidos  VARCHAR(80)  NOT NULL,
  dni        VARCHAR(8)   NULL,
  celular    VARCHAR(15)  NOT NULL,
  foto_url   VARCHAR(255) NULL,
  CONSTRAINT uq_cliente_usuario UNIQUE (usuario_id),
  CONSTRAINT fk_cliente_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================================
-- 2. CATALOGOS
-- =====================================================================

CREATE TABLE distrito (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  nombre         VARCHAR(60)  NOT NULL,
  provincia      VARCHAR(60)  NOT NULL DEFAULT 'Piura',
  tope_movilidad DECIMAL(8,2) NOT NULL DEFAULT 15.00 COMMENT 'Maximo de movilidad permitido en la zona (S/)',
  activo         TINYINT(1)   NOT NULL DEFAULT 1,
  CONSTRAINT uq_distrito_nombre UNIQUE (nombre, provincia),
  CONSTRAINT ck_distrito_tope CHECK (tope_movilidad >= 0)
) ENGINE=InnoDB;

CREATE TABLE especialidad (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  nombre      VARCHAR(80)  NOT NULL,
  descripcion VARCHAR(200) NULL,
  activo      TINYINT(1)   NOT NULL DEFAULT 1,
  CONSTRAINT uq_especialidad_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE tipo_equipo (
  id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(60) NOT NULL,
  activo TINYINT(1)  NOT NULL DEFAULT 1,
  CONSTRAINT uq_tipo_equipo_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE tipo_servicio (
  id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(80) NOT NULL,
  activo TINYINT(1)  NOT NULL DEFAULT 1,
  CONSTRAINT uq_tipo_servicio_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE direccion_cliente (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  cliente_id  BIGINT       NOT NULL,
  alias       VARCHAR(40)  NOT NULL DEFAULT 'Casa',
  direccion   VARCHAR(200) NOT NULL,
  referencia  VARCHAR(200) NULL,
  distrito_id BIGINT       NOT NULL,
  latitud     DECIMAL(9,6) NULL,
  longitud    DECIMAL(9,6) NULL,
  principal   TINYINT(1)   NOT NULL DEFAULT 0,
  CONSTRAINT fk_dircli_cliente  FOREIGN KEY (cliente_id)  REFERENCES cliente(id)  ON DELETE CASCADE,
  CONSTRAINT fk_dircli_distrito FOREIGN KEY (distrito_id) REFERENCES distrito(id)
) ENGINE=InnoDB;

-- =====================================================================
-- 3. TECNICOS
-- =====================================================================

CREATE TABLE tecnico (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id               BIGINT        NOT NULL,
  nombres                  VARCHAR(80)   NOT NULL,
  apellidos                VARCHAR(80)   NOT NULL,
  dni                      VARCHAR(8)    NOT NULL,
  celular                  VARCHAR(15)   NOT NULL,
  foto_url                 VARCHAR(255)  NULL,
  descripcion              VARCHAR(1000) NULL,
  anios_experiencia        TINYINT       NOT NULL DEFAULT 0,
  ofrece_domicilio         TINYINT(1)    NOT NULL DEFAULT 0,
  ofrece_taller            TINYINT(1)    NOT NULL DEFAULT 0,
  direccion_taller         VARCHAR(200)  NULL,
  referencia_taller        VARCHAR(200)  NULL,
  horario_taller           VARCHAR(120)  NULL,
  garantia_dias_defecto    INT           NOT NULL DEFAULT 7,
  estado_verificacion      VARCHAR(12)   NOT NULL DEFAULT 'PENDIENTE',
  motivo_verificacion      VARCHAR(300)  NULL,
  verificado_por           BIGINT        NULL,
  fecha_verificacion       DATETIME      NULL,
  bloqueado_por_deuda      TINYINT(1)    NOT NULL DEFAULT 0,
  saldo_comision_pendiente DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  calificacion_promedio    DECIMAL(3,2)  NOT NULL DEFAULT 0.00,
  total_resenas            INT           NOT NULL DEFAULT 0,
  servicios_completados    INT           NOT NULL DEFAULT 0,
  destacado                TINYINT(1)    NOT NULL DEFAULT 0,
  creado_en                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_tecnico_usuario UNIQUE (usuario_id),
  CONSTRAINT uq_tecnico_dni     UNIQUE (dni),
  CONSTRAINT fk_tecnico_usuario  FOREIGN KEY (usuario_id)     REFERENCES usuario(id) ON DELETE CASCADE,
  CONSTRAINT fk_tecnico_verifico FOREIGN KEY (verificado_por) REFERENCES usuario(id),
  CONSTRAINT ck_tecnico_estado CHECK (estado_verificacion IN ('PENDIENTE','APROBADO','OBSERVADO','RECHAZADO')),
  CONSTRAINT ck_tecnico_modalidad CHECK (ofrece_domicilio = 1 OR ofrece_taller = 1),
  CONSTRAINT ck_tecnico_garantia CHECK (garantia_dias_defecto >= 0),
  CONSTRAINT ck_tecnico_saldo CHECK (saldo_comision_pendiente >= 0),
  CONSTRAINT ck_tecnico_calif CHECK (calificacion_promedio BETWEEN 0 AND 5)
) ENGINE=InnoDB;

CREATE TABLE tecnico_especialidad (
  tecnico_id      BIGINT NOT NULL,
  especialidad_id BIGINT NOT NULL,
  PRIMARY KEY (tecnico_id, especialidad_id),
  CONSTRAINT fk_te_tecnico      FOREIGN KEY (tecnico_id)      REFERENCES tecnico(id)      ON DELETE CASCADE,
  CONSTRAINT fk_te_especialidad FOREIGN KEY (especialidad_id) REFERENCES especialidad(id)
) ENGINE=InnoDB;

CREATE TABLE tecnico_distrito (
  tecnico_id  BIGINT NOT NULL,
  distrito_id BIGINT NOT NULL,
  PRIMARY KEY (tecnico_id, distrito_id),
  CONSTRAINT fk_td_tecnico  FOREIGN KEY (tecnico_id)  REFERENCES tecnico(id)  ON DELETE CASCADE,
  CONSTRAINT fk_td_distrito FOREIGN KEY (distrito_id) REFERENCES distrito(id)
) ENGINE=InnoDB;

CREATE TABLE documento_tecnico (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  tecnico_id      BIGINT       NOT NULL,
  tipo            VARCHAR(20)  NOT NULL,
  ruta_archivo    VARCHAR(255) NOT NULL,
  nombre_original VARCHAR(150) NULL,
  estado_revision VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE',
  observacion     VARCHAR(300) NULL,
  creado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_doctec_tecnico FOREIGN KEY (tecnico_id) REFERENCES tecnico(id) ON DELETE CASCADE,
  CONSTRAINT ck_doctec_tipo   CHECK (tipo IN ('DNI_FRONTAL','DNI_POSTERIOR','CERTIFICADO','OTRO')),
  CONSTRAINT ck_doctec_estado CHECK (estado_revision IN ('PENDIENTE','APROBADO','RECHAZADO'))
) ENGINE=InnoDB;

-- =====================================================================
-- 4. SOLICITUDES Y OFERTAS
-- =====================================================================

CREATE TABLE solicitud (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  cliente_id              BIGINT        NOT NULL,
  tipo_equipo_id          BIGINT        NOT NULL,
  tipo_servicio_id        BIGINT        NOT NULL,
  marca                   VARCHAR(60)   NULL,
  modelo                  VARCHAR(80)   NULL,
  descripcion             VARCHAR(2000) NOT NULL,
  urgencia                VARCHAR(6)    NOT NULL DEFAULT 'MEDIA',
  modalidad               VARCHAR(10)   NOT NULL,
  distrito_id             BIGINT        NOT NULL,
  direccion               VARCHAR(200)  NULL COMMENT 'Solo visible al tecnico tras aceptar la oferta',
  referencia              VARCHAR(200)  NULL,
  fecha_preferida         DATE          NULL,
  horario_preferido       VARCHAR(40)   NULL,
  presupuesto_referencial DECIMAL(10,2) NULL,
  estado                  VARCHAR(12)   NOT NULL DEFAULT 'PUBLICADA',
  fecha_vencimiento       DATETIME      NOT NULL,
  motivo_cancelacion      VARCHAR(300)  NULL,
  creado_en               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_sol_cliente  FOREIGN KEY (cliente_id)       REFERENCES cliente(id),
  CONSTRAINT fk_sol_equipo   FOREIGN KEY (tipo_equipo_id)   REFERENCES tipo_equipo(id),
  CONSTRAINT fk_sol_servicio FOREIGN KEY (tipo_servicio_id) REFERENCES tipo_servicio(id),
  CONSTRAINT fk_sol_distrito FOREIGN KEY (distrito_id)      REFERENCES distrito(id),
  CONSTRAINT ck_sol_urgencia  CHECK (urgencia IN ('BAJA','MEDIA','ALTA')),
  CONSTRAINT ck_sol_modalidad CHECK (modalidad IN ('DOMICILIO','TALLER','INDISTINTO')),
  CONSTRAINT ck_sol_estado    CHECK (estado IN ('PUBLICADA','CON_OFERTAS','ACEPTADA','VENCIDA','CANCELADA')),
  CONSTRAINT ck_sol_presupuesto CHECK (presupuesto_referencial IS NULL OR presupuesto_referencial > 0)
) ENGINE=InnoDB;
CREATE INDEX idx_sol_estado_distrito ON solicitud (estado, distrito_id);
CREATE INDEX idx_sol_cliente         ON solicitud (cliente_id, estado);
CREATE INDEX idx_sol_vencimiento     ON solicitud (estado, fecha_vencimiento);

CREATE TABLE imagen_solicitud (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  solicitud_id BIGINT       NOT NULL,
  ruta_archivo VARCHAR(255) NOT NULL,
  orden        TINYINT      NOT NULL DEFAULT 1,
  CONSTRAINT uq_imgsol_orden UNIQUE (solicitud_id, orden),
  CONSTRAINT fk_imgsol_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud(id) ON DELETE CASCADE,
  CONSTRAINT ck_imgsol_orden CHECK (orden BETWEEN 1 AND 5)
) ENGINE=InnoDB;

CREATE TABLE oferta (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  solicitud_id          BIGINT        NOT NULL,
  tecnico_id            BIGINT        NOT NULL,
  modalidad             VARCHAR(10)   NOT NULL,
  precio_servicio       DECIMAL(10,2) NOT NULL,
  costo_movilidad       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  costo_diagnostico     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  tiempo_estimado_horas INT           NOT NULL,
  garantia_dias         INT           NOT NULL DEFAULT 7,
  mensaje               VARCHAR(500)  NULL,
  estado                VARCHAR(14)   NOT NULL DEFAULT 'ENVIADA',
  creado_en             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_oferta_sol_tec UNIQUE (solicitud_id, tecnico_id) COMMENT 'RN-05: una oferta por tecnico y solicitud',
  CONSTRAINT fk_oferta_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud(id) ON DELETE CASCADE,
  CONSTRAINT fk_oferta_tecnico   FOREIGN KEY (tecnico_id)   REFERENCES tecnico(id),
  CONSTRAINT ck_oferta_modalidad CHECK (modalidad IN ('DOMICILIO','TALLER')),
  CONSTRAINT ck_oferta_estado    CHECK (estado IN ('ENVIADA','CONTRAOFERTADA','ACEPTADA','RECHAZADA','RETIRADA','EXPIRADA')),
  CONSTRAINT ck_oferta_precio    CHECK (precio_servicio > 0),
  CONSTRAINT ck_oferta_movilidad CHECK (costo_movilidad >= 0 AND (modalidad = 'DOMICILIO' OR costo_movilidad = 0)),
  CONSTRAINT ck_oferta_diag      CHECK (costo_diagnostico >= 0),
  CONSTRAINT ck_oferta_tiempo    CHECK (tiempo_estimado_horas > 0),
  CONSTRAINT ck_oferta_garantia  CHECK (garantia_dias >= 0)
) ENGINE=InnoDB;
CREATE INDEX idx_oferta_tecnico ON oferta (tecnico_id, estado);

CREATE TABLE contraoferta (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  oferta_id       BIGINT        NOT NULL,
  emisor          VARCHAR(7)    NOT NULL,
  ronda           TINYINT       NOT NULL,
  monto_propuesto DECIMAL(10,2) NOT NULL COMMENT 'Precio del servicio propuesto (sin movilidad)',
  mensaje         VARCHAR(300)  NULL,
  estado          VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE',
  creado_en       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_contra_ronda UNIQUE (oferta_id, ronda, emisor),
  CONSTRAINT fk_contra_oferta FOREIGN KEY (oferta_id) REFERENCES oferta(id) ON DELETE CASCADE,
  CONSTRAINT ck_contra_emisor CHECK (emisor IN ('CLIENTE','TECNICO')),
  CONSTRAINT ck_contra_ronda  CHECK (ronda BETWEEN 1 AND 3),
  CONSTRAINT ck_contra_monto  CHECK (monto_propuesto > 0),
  CONSTRAINT ck_contra_estado CHECK (estado IN ('PENDIENTE','ACEPTADA','RECHAZADA','SUPERADA'))
) ENGINE=InnoDB;

-- =====================================================================
-- 5. SERVICIO (EJECUCION)
-- =====================================================================

CREATE TABLE servicio (
  id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
  solicitud_id              BIGINT        NOT NULL,
  oferta_id                 BIGINT        NOT NULL,
  cliente_id                BIGINT        NOT NULL,
  tecnico_id                BIGINT        NOT NULL,
  modalidad                 VARCHAR(10)   NOT NULL,
  estado                    VARCHAR(20)   NOT NULL DEFAULT 'PROGRAMADO',
  precio_servicio           DECIMAL(10,2) NOT NULL,
  costo_movilidad           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  costo_diagnostico         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  monto_total               DECIMAL(10,2) NOT NULL COMMENT 'Servicio + movilidad vigente (cambia solo con ajuste aprobado)',
  monto_final               DECIMAL(10,2) NULL     COMMENT 'Monto cobrado al finalizar; base de la comision',
  metodo_pago               VARCHAR(13)   NULL,
  pin_inicio                CHAR(4)       NULL     COMMENT 'PIN que el cliente da al tecnico (domicilio)',
  pin_validado              TINYINT(1)    NOT NULL DEFAULT 0,
  garantia_dias             INT           NOT NULL DEFAULT 7,
  fecha_programada          DATETIME      NULL,
  fecha_inicio              DATETIME      NULL,
  fecha_finalizacion        DATETIME      NULL,
  fecha_limite_conformidad  DATETIME      NULL COMMENT 'finalizacion + 48 h',
  fecha_confirmacion        DATETIME      NULL,
  confirmado_automaticamente TINYINT(1)   NOT NULL DEFAULT 0,
  cancelado_por             VARCHAR(7)    NULL,
  motivo_cancelacion        VARCHAR(300)  NULL,
  creado_en                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_servicio_solicitud UNIQUE (solicitud_id),
  CONSTRAINT uq_servicio_oferta    UNIQUE (oferta_id),
  CONSTRAINT fk_serv_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud(id),
  CONSTRAINT fk_serv_oferta    FOREIGN KEY (oferta_id)    REFERENCES oferta(id),
  CONSTRAINT fk_serv_cliente   FOREIGN KEY (cliente_id)   REFERENCES cliente(id),
  CONSTRAINT fk_serv_tecnico   FOREIGN KEY (tecnico_id)   REFERENCES tecnico(id),
  CONSTRAINT ck_serv_modalidad CHECK (modalidad IN ('DOMICILIO','TALLER')),
  CONSTRAINT ck_serv_estado CHECK (estado IN ('PROGRAMADO','TECNICO_EN_CAMINO','EQUIPO_RECIBIDO','EN_DIAGNOSTICO',
        'PENDIENTE_AJUSTE','EN_PROCESO','EN_REPARACION','LISTO_PARA_RECOGER','FINALIZADO','CONFIRMADO','CALIFICADO','CANCELADO')),
  CONSTRAINT ck_serv_pago   CHECK (metodo_pago IS NULL OR metodo_pago IN ('EFECTIVO','YAPE','PLIN','TRANSFERENCIA')),
  CONSTRAINT ck_serv_cancel CHECK (cancelado_por IS NULL OR cancelado_por IN ('CLIENTE','TECNICO','SISTEMA')),
  CONSTRAINT ck_serv_montos CHECK (precio_servicio >= 0 AND costo_movilidad >= 0 AND costo_diagnostico >= 0 AND monto_total >= 0)
) ENGINE=InnoDB;
CREATE INDEX idx_serv_cliente     ON servicio (cliente_id, estado);
CREATE INDEX idx_serv_tecnico     ON servicio (tecnico_id, estado);
CREATE INDEX idx_serv_conformidad ON servicio (estado, fecha_limite_conformidad);

CREATE TABLE historial_estado_servicio (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  servicio_id     BIGINT       NOT NULL,
  estado_anterior VARCHAR(20)  NULL,
  estado_nuevo    VARCHAR(20)  NOT NULL,
  cambiado_por    BIGINT       NULL COMMENT 'NULL = cambio automatico del sistema',
  comentario      VARCHAR(300) NULL,
  creado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_hist_servicio FOREIGN KEY (servicio_id)  REFERENCES servicio(id) ON DELETE CASCADE,
  CONSTRAINT fk_hist_usuario  FOREIGN KEY (cambiado_por) REFERENCES usuario(id)
) ENGINE=InnoDB;
CREATE INDEX idx_hist_servicio ON historial_estado_servicio (servicio_id, creado_en);

CREATE TABLE constancia (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  servicio_id           BIGINT        NOT NULL,
  tipo                  VARCHAR(10)   NOT NULL,
  observaciones         VARCHAR(1000) NULL,
  accesorios            VARCHAR(300)  NULL,
  confirmada_por_cliente TINYINT(1)   NOT NULL DEFAULT 0,
  fecha_confirmacion    DATETIME      NULL,
  creado_en             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_constancia_tipo UNIQUE (servicio_id, tipo),
  CONSTRAINT fk_const_servicio FOREIGN KEY (servicio_id) REFERENCES servicio(id) ON DELETE CASCADE,
  CONSTRAINT ck_const_tipo CHECK (tipo IN ('RECEPCION','ENTREGA'))
) ENGINE=InnoDB;

CREATE TABLE constancia_foto (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  constancia_id BIGINT       NOT NULL,
  ruta_archivo VARCHAR(255) NOT NULL,
  CONSTRAINT fk_constfoto_const FOREIGN KEY (constancia_id) REFERENCES constancia(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE avance_servicio (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  servicio_id BIGINT        NOT NULL,
  tipo        VARCHAR(12)   NOT NULL,
  descripcion VARCHAR(1000) NOT NULL,
  ruta_foto   VARCHAR(255)  NULL,
  creado_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_avance_servicio FOREIGN KEY (servicio_id) REFERENCES servicio(id) ON DELETE CASCADE,
  CONSTRAINT ck_avance_tipo CHECK (tipo IN ('DIAGNOSTICO','AVANCE'))
) ENGINE=InnoDB;

CREATE TABLE ajuste_precio (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  servicio_id     BIGINT        NOT NULL,
  monto_anterior  DECIMAL(10,2) NOT NULL COMMENT 'Precio del servicio antes del ajuste',
  monto_nuevo     DECIMAL(10,2) NOT NULL COMMENT 'Precio del servicio propuesto',
  justificacion   VARCHAR(500)  NOT NULL,
  ruta_evidencia  VARCHAR(255)  NULL,
  estado          VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE',
  creado_en       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_respuesta DATETIME      NULL,
  CONSTRAINT fk_ajuste_servicio FOREIGN KEY (servicio_id) REFERENCES servicio(id) ON DELETE CASCADE,
  CONSTRAINT ck_ajuste_estado CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO')),
  CONSTRAINT ck_ajuste_montos CHECK (monto_anterior > 0 AND monto_nuevo > 0)
) ENGINE=InnoDB;

-- =====================================================================
-- 6. COMISIONES Y PAGOS
-- =====================================================================

CREATE TABLE comision (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  servicio_id        BIGINT        NOT NULL,
  tecnico_id         BIGINT        NOT NULL,
  monto_base         DECIMAL(10,2) NOT NULL COMMENT 'Monto total aprobado (servicio + movilidad)',
  porcentaje         DECIMAL(5,4)  NOT NULL COMMENT '0.0600 = 6 %',
  monto_comision     DECIMAL(10,2) NOT NULL,
  monto_neto_tecnico DECIMAL(10,2) NOT NULL,
  estado             VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE',
  fecha_limite_pago  DATE          NOT NULL,
  fecha_pago         DATETIME      NULL,
  creado_en          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_comision_servicio UNIQUE (servicio_id),
  CONSTRAINT fk_com_servicio FOREIGN KEY (servicio_id) REFERENCES servicio(id),
  CONSTRAINT fk_com_tecnico  FOREIGN KEY (tecnico_id)  REFERENCES tecnico(id),
  CONSTRAINT ck_com_estado CHECK (estado IN ('PENDIENTE','PAGADA','ANULADA')),
  CONSTRAINT ck_com_porcentaje CHECK (porcentaje >= 0 AND porcentaje <= 1),
  CONSTRAINT ck_com_montos CHECK (monto_base >= 0 AND monto_comision >= 0 AND monto_neto_tecnico >= 0
        AND monto_comision + monto_neto_tecnico = monto_base)
) ENGINE=InnoDB;
CREATE INDEX idx_com_tecnico_estado ON comision (tecnico_id, estado);
CREATE INDEX idx_com_vencimiento    ON comision (estado, fecha_limite_pago);

CREATE TABLE pago_comision (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  tecnico_id       BIGINT        NOT NULL,
  monto            DECIMAL(10,2) NOT NULL,
  metodo           VARCHAR(13)   NOT NULL,
  numero_operacion VARCHAR(40)   NOT NULL,
  ruta_comprobante VARCHAR(255)  NOT NULL,
  estado           VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE',
  validado_por     BIGINT        NULL,
  fecha_validacion DATETIME      NULL,
  observacion      VARCHAR(300)  NULL,
  creado_en        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_pagocom_tecnico FOREIGN KEY (tecnico_id)   REFERENCES tecnico(id),
  CONSTRAINT fk_pagocom_admin   FOREIGN KEY (validado_por) REFERENCES usuario(id),
  CONSTRAINT ck_pagocom_metodo CHECK (metodo IN ('YAPE','PLIN','TRANSFERENCIA')),
  CONSTRAINT ck_pagocom_estado CHECK (estado IN ('PENDIENTE','VALIDADO','RECHAZADO')),
  CONSTRAINT ck_pagocom_monto  CHECK (monto > 0)
) ENGINE=InnoDB;
CREATE INDEX idx_pagocom_estado ON pago_comision (estado, creado_en);

CREATE TABLE pago_comision_detalle (
  pago_id     BIGINT NOT NULL,
  comision_id BIGINT NOT NULL,
  PRIMARY KEY (pago_id, comision_id),
  CONSTRAINT fk_pcd_pago     FOREIGN KEY (pago_id)     REFERENCES pago_comision(id) ON DELETE CASCADE,
  CONSTRAINT fk_pcd_comision FOREIGN KEY (comision_id) REFERENCES comision(id)
) ENGINE=InnoDB;

-- =====================================================================
-- 7. RESENAS, MENSAJES, RECLAMOS, NOTIFICACIONES
-- =====================================================================

CREATE TABLE resena (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  servicio_id       BIGINT        NOT NULL,
  cliente_id        BIGINT        NOT NULL,
  tecnico_id        BIGINT        NOT NULL,
  estrellas         TINYINT       NOT NULL,
  puntualidad       TINYINT       NULL,
  trato             TINYINT       NULL,
  calidad           TINYINT       NULL,
  precio_justo      TINYINT       NULL,
  comentario        VARCHAR(1000) NULL,
  respuesta_tecnico VARCHAR(1000) NULL,
  fecha_respuesta   DATETIME      NULL,
  estado            VARCHAR(10)   NOT NULL DEFAULT 'VISIBLE',
  motivo_reporte    VARCHAR(300)  NULL,
  creado_en         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_resena_servicio UNIQUE (servicio_id) COMMENT 'Una resena por servicio',
  CONSTRAINT fk_resena_servicio FOREIGN KEY (servicio_id) REFERENCES servicio(id),
  CONSTRAINT fk_resena_cliente  FOREIGN KEY (cliente_id)  REFERENCES cliente(id),
  CONSTRAINT fk_resena_tecnico  FOREIGN KEY (tecnico_id)  REFERENCES tecnico(id),
  CONSTRAINT ck_resena_estrellas CHECK (estrellas BETWEEN 1 AND 5),
  CONSTRAINT ck_resena_criterios CHECK (
        (puntualidad  IS NULL OR puntualidad  BETWEEN 1 AND 5) AND
        (trato        IS NULL OR trato        BETWEEN 1 AND 5) AND
        (calidad      IS NULL OR calidad      BETWEEN 1 AND 5) AND
        (precio_justo IS NULL OR precio_justo BETWEEN 1 AND 5)),
  CONSTRAINT ck_resena_estado CHECK (estado IN ('VISIBLE','OCULTA','REPORTADA'))
) ENGINE=InnoDB;
CREATE INDEX idx_resena_tecnico ON resena (tecnico_id, estado);

CREATE TABLE mensaje (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  servicio_id BIGINT        NOT NULL,
  emisor_id   BIGINT        NOT NULL,
  contenido   VARCHAR(1000) NOT NULL,
  leido       TINYINT(1)    NOT NULL DEFAULT 0,
  creado_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_msg_servicio FOREIGN KEY (servicio_id) REFERENCES servicio(id) ON DELETE CASCADE,
  CONSTRAINT fk_msg_emisor   FOREIGN KEY (emisor_id)   REFERENCES usuario(id)
) ENGINE=InnoDB;
CREATE INDEX idx_msg_servicio ON mensaje (servicio_id, creado_en);

CREATE TABLE reclamo (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  servicio_id      BIGINT        NOT NULL,
  cliente_id       BIGINT        NOT NULL,
  descripcion      VARCHAR(2000) NOT NULL,
  estado           VARCHAR(12)   NOT NULL DEFAULT 'ABIERTO',
  respuesta_tecnico VARCHAR(1000) NULL,
  resolucion       VARCHAR(1000) NULL,
  resuelto_por     BIGINT        NULL,
  creado_en        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_resolucion DATETIME      NULL,
  CONSTRAINT fk_recl_servicio FOREIGN KEY (servicio_id)  REFERENCES servicio(id),
  CONSTRAINT fk_recl_cliente  FOREIGN KEY (cliente_id)   REFERENCES cliente(id),
  CONSTRAINT fk_recl_admin    FOREIGN KEY (resuelto_por) REFERENCES usuario(id),
  CONSTRAINT ck_recl_estado CHECK (estado IN ('ABIERTO','EN_REVISION','RESUELTO','CERRADO'))
) ENGINE=InnoDB;
CREATE INDEX idx_recl_estado ON reclamo (estado, creado_en);

CREATE TABLE reclamo_foto (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  reclamo_id   BIGINT       NOT NULL,
  ruta_archivo VARCHAR(255) NOT NULL,
  CONSTRAINT fk_reclfoto_reclamo FOREIGN KEY (reclamo_id) REFERENCES reclamo(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE notificacion (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id      BIGINT       NOT NULL,
  tipo            VARCHAR(40)  NOT NULL,
  titulo          VARCHAR(120) NOT NULL,
  mensaje         VARCHAR(300) NOT NULL,
  referencia_tipo VARCHAR(30)  NULL,
  referencia_id   BIGINT       NULL,
  leida           TINYINT(1)   NOT NULL DEFAULT 0,
  creado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notif_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE INDEX idx_notif_usuario ON notificacion (usuario_id, leida, creado_en);

-- =====================================================================
-- 8. SISTEMA: PARAMETROS, AUDITORIA, RECUPERACION DE CLAVE
-- =====================================================================

CREATE TABLE parametro_sistema (
  clave          VARCHAR(60)  NOT NULL PRIMARY KEY,
  valor          VARCHAR(200) NOT NULL,
  descripcion    VARCHAR(200) NULL,
  actualizado_en DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  actualizado_por BIGINT      NULL,
  CONSTRAINT fk_param_usuario FOREIGN KEY (actualizado_por) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE auditoria (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id BIGINT       NULL,
  accion     VARCHAR(60)  NOT NULL,
  entidad    VARCHAR(60)  NOT NULL,
  entidad_id BIGINT       NULL,
  detalle    VARCHAR(1000) NULL,
  ip         VARCHAR(45)  NULL,
  creado_en  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE SET NULL
) ENGINE=InnoDB;
CREATE INDEX idx_audit_entidad ON auditoria (entidad, entidad_id);
CREATE INDEX idx_audit_fecha   ON auditoria (creado_en);

CREATE TABLE token_recuperacion (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id BIGINT       NOT NULL,
  token_hash VARCHAR(100) NOT NULL,
  expira_en  DATETIME     NOT NULL,
  usado      TINYINT(1)   NOT NULL DEFAULT 0,
  creado_en  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tokrec_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE INDEX idx_tokrec_hash ON token_recuperacion (token_hash);

-- =====================================================================
-- 9. VISTAS UTILES
-- =====================================================================

CREATE OR REPLACE VIEW v_oferta_detalle AS
SELECT o.id AS oferta_id, o.solicitud_id, o.tecnico_id,
       CONCAT(t.nombres, ' ', t.apellidos) AS tecnico,
       t.calificacion_promedio, t.total_resenas, t.servicios_completados,
       o.modalidad, o.precio_servicio, o.costo_movilidad,
       (o.precio_servicio + o.costo_movilidad) AS monto_total,
       o.tiempo_estimado_horas, o.garantia_dias, o.estado
FROM oferta o JOIN tecnico t ON t.id = o.tecnico_id;

CREATE OR REPLACE VIEW v_resumen_comisiones_tecnico AS
SELECT t.id AS tecnico_id, CONCAT(t.nombres, ' ', t.apellidos) AS tecnico,
       COALESCE(SUM(CASE WHEN c.estado = 'PENDIENTE' THEN c.monto_comision END), 0) AS pendiente,
       COALESCE(SUM(CASE WHEN c.estado = 'PAGADA'    THEN c.monto_comision END), 0) AS pagado,
       MIN(CASE WHEN c.estado = 'PENDIENTE' THEN c.fecha_limite_pago END)           AS proximo_vencimiento
FROM tecnico t LEFT JOIN comision c ON c.tecnico_id = t.id
GROUP BY t.id, t.nombres, t.apellidos;

CREATE OR REPLACE VIEW v_ingresos_mensuales AS
SELECT DATE_FORMAT(s.fecha_confirmacion, '%Y-%m') AS mes,
       COUNT(*)                AS servicios,
       SUM(c.monto_base)       AS volumen_total,
       SUM(c.monto_comision)   AS ingreso_fixtech
FROM comision c JOIN servicio s ON s.id = c.servicio_id
WHERE c.estado <> 'ANULADA' AND s.fecha_confirmacion IS NOT NULL
GROUP BY DATE_FORMAT(s.fecha_confirmacion, '%Y-%m');

-- =====================================================================
-- 10. DATOS INICIALES
-- =====================================================================

INSERT INTO parametro_sistema (clave, valor, descripcion) VALUES
 ('comision_porcentaje',          '0.0600', 'Comision de FIXTECH (6 %)'),
 ('limite_deuda_comision',        '30.00',  'Deuda maxima de comisiones antes de bloquear al tecnico (S/)'),
 ('plazo_pago_comision_dias',     '7',      'Dias para pagar la comision desde el cierre del servicio'),
 ('vencimiento_solicitud_horas',  '72',     'Horas para vencer una solicitud sin oferta aceptada'),
 ('confirmacion_automatica_horas','48',     'Horas para confirmar automaticamente un servicio finalizado'),
 ('max_rondas_contraoferta',      '3',      'Rondas maximas de contraoferta por oferta'),
 ('garantia_minima_dias',         '7',      'Garantia minima sugerida (dias)'),
 ('max_imagenes_solicitud',       '5',      'Fotos maximas por solicitud'),
 ('dias_para_resenar',            '15',     'Dias para dejar resena tras confirmar el servicio'),
 ('max_cancelaciones_tecnico_30d','3',      'Cancelaciones del tecnico en 30 dias antes de revision'),
 ('calificacion_minima_revision', '2.50',   'Promedio bajo el cual se revisa al tecnico'),
 ('min_resenas_revision',         '5',      'Resenas minimas para aplicar la revision por promedio'),
 ('tope_movilidad_defecto',       '15.00',  'Tope de movilidad por defecto (S/)');

INSERT INTO distrito (nombre, provincia, tope_movilidad) VALUES
 ('Piura','Piura',15.00), ('Castilla','Piura',15.00), ('Veintiseis de Octubre','Piura',15.00),
 ('Catacaos','Piura',20.00), ('Cura Mori','Piura',20.00), ('El Tallan','Piura',20.00),
 ('La Arena','Piura',20.00), ('La Union','Piura',25.00), ('Las Lomas','Piura',30.00),
 ('Tambo Grande','Piura',30.00);

INSERT INTO especialidad (nombre, descripcion) VALUES
 ('Mantenimiento de laptops',        'Limpieza, cambio de pasta termica y optimizacion'),
 ('Mantenimiento de PC de escritorio','Limpieza interna y revision de componentes'),
 ('Reparacion de hardware',          'Placas, fuentes, discos y memorias'),
 ('Formateo e instalacion de sistemas','Windows, Linux y controladores'),
 ('Redes y WiFi',                    'Configuracion de routers, cableado y puntos de acceso'),
 ('Impresoras y multifuncionales',   'Mantenimiento, reseteo y configuracion'),
 ('Recuperacion de datos',           'Recuperacion de archivos de discos y USB'),
 ('Cambio de pantalla y teclado',    'Reemplazo de pantallas, teclados y bisagras'),
 ('Instalacion de software',         'Office, antivirus, programas especializados'),
 ('Soporte a empresas',              'Soporte tecnico para oficinas y negocios');

INSERT INTO tipo_equipo (nombre) VALUES
 ('Laptop'), ('PC de escritorio'), ('All in one'), ('Impresora'),
 ('Monitor'), ('Router o red'), ('Tablet'), ('Otro');

INSERT INTO tipo_servicio (nombre) VALUES
 ('Mantenimiento preventivo'), ('Reparacion de hardware'), ('Formateo e instalacion'),
 ('Instalacion de software'), ('Configuracion de redes'), ('Recuperacion de datos'),
 ('Diagnostico'), ('Otro');

-- Administrador inicial. Contrasena de desarrollo: Admin123!  (CAMBIALA antes de publicar el sistema)
INSERT INTO usuario (correo, password_hash, rol, estado, acepto_terminos, fecha_acepto_terminos) VALUES
 ('admin@fixtech.pe', '$2a$10$tvN5J7Gq9Og5i/5EA5HXqeLO.wHojC9cJLqSeRPKXNDeOtHBun94S', 'ADMIN', 'ACTIVO', 1, NOW());

INSERT INTO administrador (usuario_id, nombres, apellidos)
SELECT id, 'Administrador', 'FIXTECH' FROM usuario WHERE correo = 'admin@fixtech.pe';
