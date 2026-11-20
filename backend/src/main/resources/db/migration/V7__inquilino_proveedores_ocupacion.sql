-- FacilityHub - FH-006 / P6-C2: responsabilidades del INQUILINO en obligaciones de responsabilidad INQUILINO.
-- Migracion aditiva: no elimina ni modifica filas existentes. No crea usuarios, empresas ni contrasenas.
--   1. mantenimientos.ocupacion_id (ocupacion responsable, protege el historial ante cambios de ocupacion) y
--      fecha_prevista_inquilino.
--   2. obligacion_proveedor: proveedor declarado por el inquilino para una obligacion, ligado a SU ocupacion, y su
--      revision (aceptacion o rechazo) por gestion.
--   3. solicitudes_proveedor: peticion de incorporacion de un proveedor nuevo; la atiende ADMIN (D-1).

SET search_path TO facilityhub;

-- 1. Mantenimientos.
ALTER TABLE mantenimientos ADD COLUMN ocupacion_id BIGINT REFERENCES ocupaciones_local(id);
ALTER TABLE mantenimientos ADD COLUMN fecha_prevista_inquilino DATE;
CREATE INDEX idx_mantenimientos_ocupacion ON mantenimientos(ocupacion_id);

-- La ocupacion de un mantenimiento debe ser del mismo local que su obligacion, y solo en obligaciones INQUILINO.
CREATE FUNCTION fn_mantenimiento_ocupacion_coherente() RETURNS trigger AS $$
DECLARE
    v_local_obligacion BIGINT;
    v_responsable VARCHAR(20);
    v_local_ocupacion BIGINT;
BEGIN
    IF NEW.ocupacion_id IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT local_id, responsable INTO v_local_obligacion, v_responsable
      FROM facilityhub.obligaciones_mantenimiento WHERE id = NEW.obligacion_id;
    SELECT local_id INTO v_local_ocupacion FROM facilityhub.ocupaciones_local WHERE id = NEW.ocupacion_id;
    IF v_responsable <> 'INQUILINO' OR v_local_obligacion IS DISTINCT FROM v_local_ocupacion THEN
        RAISE EXCEPTION 'La ocupacion del mantenimiento no corresponde al local de una obligacion del inquilino'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mantenimiento_ocupacion
    BEFORE INSERT OR UPDATE OF ocupacion_id, obligacion_id ON mantenimientos
    FOR EACH ROW EXECUTE FUNCTION fn_mantenimiento_ocupacion_coherente();

-- 2. Proveedor declarado por el inquilino (una declaracion activa por obligacion).
CREATE TABLE obligacion_proveedor (
    id BIGSERIAL PRIMARY KEY,
    obligacion_id BIGINT NOT NULL REFERENCES obligaciones_mantenimiento(id),
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id),
    ocupacion_id BIGINT NOT NULL REFERENCES ocupaciones_local(id),
    estado VARCHAR(20) NOT NULL DEFAULT 'PROPUESTO',
    declarado_por_usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    declarado_at TIMESTAMP NOT NULL,
    revisado_por_usuario_id BIGINT REFERENCES usuarios(id),
    revisado_at TIMESTAMP,
    motivo_revision TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    finalizado_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_obligacion_proveedor_estado CHECK (estado IN ('PROPUESTO', 'ACEPTADO', 'RECHAZADO')),
    CONSTRAINT ck_obligacion_proveedor_revision CHECK (
        estado = 'PROPUESTO' OR (revisado_por_usuario_id IS NOT NULL AND revisado_at IS NOT NULL)),
    CONSTRAINT ck_obligacion_proveedor_rechazo CHECK (
        estado <> 'RECHAZADO' OR (motivo_revision IS NOT NULL AND length(trim(motivo_revision)) > 0)),
    -- Un rechazo cierra la declaracion: solo PROPUESTO o ACEPTADO pueden estar activas.
    CONSTRAINT ck_obligacion_proveedor_activa CHECK (NOT (activo AND estado = 'RECHAZADO'))
);
CREATE UNIQUE INDEX uq_obligacion_proveedor_activa ON obligacion_proveedor(obligacion_id) WHERE activo;
CREATE INDEX idx_obligacion_proveedor_estado ON obligacion_proveedor(estado) WHERE activo;

-- Solo obligaciones INQUILINO, y la ocupacion declarante debe ser del local de la obligacion.
CREATE FUNCTION fn_obligacion_proveedor_coherente() RETURNS trigger AS $$
DECLARE
    v_local_obligacion BIGINT;
    v_responsable VARCHAR(20);
    v_local_ocupacion BIGINT;
BEGIN
    SELECT local_id, responsable INTO v_local_obligacion, v_responsable
      FROM facilityhub.obligaciones_mantenimiento WHERE id = NEW.obligacion_id;
    SELECT local_id INTO v_local_ocupacion FROM facilityhub.ocupaciones_local WHERE id = NEW.ocupacion_id;
    IF v_responsable <> 'INQUILINO' OR v_local_obligacion IS DISTINCT FROM v_local_ocupacion THEN
        RAISE EXCEPTION 'La declaracion de proveedor exige una obligacion del inquilino y una ocupacion de su local'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_obligacion_proveedor_coherente
    BEFORE INSERT OR UPDATE OF obligacion_id, ocupacion_id ON obligacion_proveedor
    FOR EACH ROW EXECUTE FUNCTION fn_obligacion_proveedor_coherente();

-- 3. Solicitudes de incorporacion de un proveedor nuevo (el inquilino no crea empresas ni usuarios).
CREATE TABLE solicitudes_proveedor (
    id BIGSERIAL PRIMARY KEY,
    inquilino_id BIGINT NOT NULL REFERENCES inquilinos(id),
    obligacion_id BIGINT REFERENCES obligaciones_mantenimiento(id),
    solicitado_por_usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    razon_social VARCHAR(150) NOT NULL,
    cif VARCHAR(20),
    contacto VARCHAR(300),
    observaciones TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    proveedor_id BIGINT REFERENCES proveedores(id),
    atendida_por_usuario_id BIGINT REFERENCES usuarios(id),
    atendida_at TIMESTAMP,
    respuesta TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_solicitud_estado CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'RECHAZADA')),
    CONSTRAINT ck_solicitud_atendida CHECK (estado <> 'ATENDIDA' OR proveedor_id IS NOT NULL),
    CONSTRAINT ck_solicitud_resuelta CHECK (
        estado = 'PENDIENTE' OR (atendida_por_usuario_id IS NOT NULL AND atendida_at IS NOT NULL)),
    CONSTRAINT ck_solicitud_rechazo CHECK (
        estado <> 'RECHAZADA' OR (respuesta IS NOT NULL AND length(trim(respuesta)) > 0)),
    CONSTRAINT ck_solicitud_razon_social CHECK (length(trim(razon_social)) > 0)
);
-- Evita la doble solicitud accidental de la misma empresa mientras esta pendiente.
CREATE UNIQUE INDEX uq_solicitud_pendiente ON solicitudes_proveedor(inquilino_id, lower(trim(razon_social)))
    WHERE estado = 'PENDIENTE';
CREATE INDEX idx_solicitudes_inquilino ON solicitudes_proveedor(inquilino_id);
CREATE INDEX idx_solicitudes_estado ON solicitudes_proveedor(estado);
