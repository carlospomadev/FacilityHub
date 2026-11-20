-- FacilityHub - FH-006 / P6-C1: auditoria, intervenciones, historial de validaciones, evidencias y regla de recalculo.
-- Migracion aditiva: no elimina filas ni columnas. La auditoria se crea aqui, antes de cualquier uso (corrige la
-- inconsistencia V6/V7 del diseno P6-A). Las tablas del flujo del inquilino (P6-C2) iran en V7 y las alertas en V8.
-- No crea usuarios ni contrasenas.

SET search_path TO facilityhub;

-- 1. Auditoria de acciones (inmutable).
CREATE TABLE auditoria_eventos (
    id BIGSERIAL PRIMARY KEY,
    entidad VARCHAR(40) NOT NULL,
    entidad_id BIGINT NOT NULL,
    accion VARCHAR(40) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detalle JSONB
);
CREATE INDEX idx_auditoria_entidad ON auditoria_eventos(entidad, entidad_id);
CREATE INDEX idx_auditoria_usuario ON auditoria_eventos(usuario_id);

CREATE FUNCTION fn_registro_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Registro inmutable: % no admite %', TG_TABLE_NAME, TG_OP USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_inmutable
    BEFORE UPDATE OR DELETE ON auditoria_eventos
    FOR EACH ROW EXECUTE FUNCTION fn_registro_inmutable();

-- 2. Regla de recalculo de la proxima fecha por tipo de mantenimiento (D-7). Hoy se usan DESDE_EJECUCION (por
--    defecto) y DESDE_PROGRAMADA; nuevas reglas (fechas fijas, requisitos reglamentarios) se anadiran con su migracion.
ALTER TABLE tipos_mantenimiento ADD COLUMN regla_recalculo VARCHAR(30) NOT NULL DEFAULT 'DESDE_EJECUCION';
ALTER TABLE tipos_mantenimiento ADD CONSTRAINT ck_tipo_regla_recalculo
    CHECK (regla_recalculo IN ('DESDE_EJECUCION', 'DESDE_PROGRAMADA'));

-- 3. Intervenciones: registro de la ejecucion de un mantenimiento y de su validacion.
CREATE TABLE intervenciones (
    id BIGSERIAL PRIMARY KEY,
    mantenimiento_id BIGINT NOT NULL REFERENCES mantenimientos(id),
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id),
    tecnico_usuario_id BIGINT,
    origen VARCHAR(20) NOT NULL,
    registrada_por_usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    numero_envio INTEGER NOT NULL DEFAULT 0,
    fecha_ejecucion DATE,
    actuaciones TEXT,
    resultado VARCHAR(20),
    incidencias TEXT,
    estado_validacion VARCHAR(25) NOT NULL DEFAULT 'BORRADOR',
    ejecucion_anticipada BOOLEAN NOT NULL DEFAULT FALSE,
    enviada_at TIMESTAMP,
    enviada_por_usuario_id BIGINT REFERENCES usuarios(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- El tecnico, si consta, pertenece al proveedor de la intervencion.
    CONSTRAINT fk_intervencion_tecnico_proveedor
        FOREIGN KEY (tecnico_usuario_id, proveedor_id) REFERENCES usuarios(id, proveedor_id),
    CONSTRAINT ck_intervencion_origen CHECK (origen IN ('PROVEEDOR', 'REGISTRO_GESTION')),
    CONSTRAINT ck_intervencion_resultado CHECK (resultado IS NULL OR resultado IN ('CONFORME', 'CON_INCIDENCIAS', 'NO_REALIZADO')),
    CONSTRAINT ck_intervencion_estado CHECK (estado_validacion IN ('BORRADOR', 'PENDIENTE_VALIDACION', 'REQUIERE_CORRECCION',
        'APROBADO', 'CERRADO_SIN_CUMPLIMIENTO')),
    -- Una intervencion NO_REALIZADO nunca es cumplimiento valido.
    CONSTRAINT ck_intervencion_no_realizado CHECK (NOT (resultado = 'NO_REALIZADO' AND estado_validacion = 'APROBADO')),
    -- El cierre sin cumplimiento solo corresponde a intentos NO_REALIZADO.
    CONSTRAINT ck_intervencion_cierre_sin_cumplimiento CHECK (
        estado_validacion <> 'CERRADO_SIN_CUMPLIMIENTO' OR resultado = 'NO_REALIZADO'),
    -- Fuera de BORRADOR deben constar los datos de la ejecucion y el envio.
    CONSTRAINT ck_intervencion_enviada CHECK (
        estado_validacion = 'BORRADOR'
        OR (fecha_ejecucion IS NOT NULL AND resultado IS NOT NULL AND enviada_at IS NOT NULL
            AND enviada_por_usuario_id IS NOT NULL AND numero_envio > 0)
    )
);
-- Como maximo una intervencion abierta y una aprobada por mantenimiento. Los intentos cerrados sin cumplimiento no
-- cuentan: se admiten varios, incluso el mismo dia (visitas reales justificadas). El doble envio accidental se evita
-- con la version (409) y el bloqueo de fila del servicio, no por fecha.
CREATE UNIQUE INDEX uq_intervencion_abierta ON intervenciones(mantenimiento_id)
    WHERE estado_validacion IN ('BORRADOR', 'PENDIENTE_VALIDACION', 'REQUIERE_CORRECCION');
CREATE UNIQUE INDEX uq_intervencion_aprobada ON intervenciones(mantenimiento_id)
    WHERE estado_validacion = 'APROBADO';
CREATE INDEX idx_intervenciones_proveedor ON intervenciones(proveedor_id);
CREATE INDEX idx_intervenciones_estado ON intervenciones(estado_validacion);

-- 4. Historial de validaciones (inmutable): quien valida, cuando, que decide y sobre que envio.
CREATE TABLE validaciones_intervencion (
    id BIGSERIAL PRIMARY KEY,
    intervencion_id BIGINT NOT NULL REFERENCES intervenciones(id),
    numero_envio INTEGER NOT NULL,
    accion VARCHAR(25) NOT NULL,
    comentario TEXT,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_validacion_accion CHECK (accion IN ('APROBAR', 'SOLICITAR_CORRECCION', 'CERRAR_SIN_CUMPLIMIENTO')),
    CONSTRAINT ck_validacion_comentario CHECK (accion = 'APROBAR' OR (comentario IS NOT NULL AND length(trim(comentario)) > 0))
);
CREATE INDEX idx_validaciones_intervencion ON validaciones_intervencion(intervencion_id);

CREATE TRIGGER trg_validacion_inmutable
    BEFORE UPDATE OR DELETE ON validaciones_intervencion
    FOR EACH ROW EXECUTE FUNCTION fn_registro_inmutable();

-- 5. Evidencias y certificados: vinculo opcional con la intervencion, autor y categoria (documentos P5 sin cambios).
ALTER TABLE documentos_mantenimiento ADD COLUMN intervencion_id BIGINT REFERENCES intervenciones(id);
ALTER TABLE documentos_mantenimiento ADD COLUMN subido_por_usuario_id BIGINT REFERENCES usuarios(id);
ALTER TABLE documentos_mantenimiento ADD COLUMN categoria VARCHAR(20);
ALTER TABLE documentos_mantenimiento ADD CONSTRAINT ck_documento_categoria
    CHECK (categoria IS NULL OR categoria IN ('EVIDENCIA', 'CERTIFICADO', 'OTRO'));
CREATE INDEX idx_documentos_intervencion ON documentos_mantenimiento(intervencion_id);
