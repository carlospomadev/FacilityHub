-- FacilityHub - FH-006 / P6-D1: alertas internas por eventos (canal APP).
-- Migracion aditiva: crea una tabla nueva, no modifica filas existentes ni crea usuarios. Las alertas solo nacen de
-- eventos de negocio (asignacion, declaracion de proveedor, envio y validacion de intervenciones), en la misma
-- transaccion: no hay tareas programadas y el arranque no escribe nada. Recordatorios de plazo y canales externos: P6-D2/D3.

SET search_path TO facilityhub;

CREATE TABLE alertas (
    id BIGSERIAL PRIMARY KEY,
    destinatario_usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    tipo VARCHAR(40) NOT NULL,
    -- Identificador del hecho (p. ej. asignacion:{id}); junto con destinatario y tipo impide duplicados.
    evento_ref VARCHAR(120) NOT NULL,
    mantenimiento_id BIGINT REFERENCES mantenimientos(id),
    obligacion_id BIGINT REFERENCES obligaciones_mantenimiento(id),
    intervencion_id BIGINT REFERENCES intervenciones(id),
    -- Texto breve sin datos personales (tipo de mantenimiento, local o zona comun, fecha).
    mensaje VARCHAR(300) NOT NULL,
    canal VARCHAR(10) NOT NULL DEFAULT 'APP',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    leida_at TIMESTAMP,
    CONSTRAINT ck_alerta_tipo CHECK (tipo IN ('ASIGNACION', 'PROVEEDOR_DECLARADO', 'EVIDENCIAS_PRESENTADAS',
        'CORRECCION_SOLICITADA', 'APROBADO')),
    CONSTRAINT ck_alerta_canal CHECK (canal IN ('APP')),
    CONSTRAINT ck_alerta_mensaje CHECK (length(trim(mensaje)) > 0),
    CONSTRAINT uq_alerta_evento UNIQUE (destinatario_usuario_id, tipo, evento_ref)
);
CREATE INDEX idx_alertas_no_leidas ON alertas(destinatario_usuario_id, created_at DESC) WHERE leida_at IS NULL;
CREATE INDEX idx_alertas_destinatario ON alertas(destinatario_usuario_id, created_at DESC);
