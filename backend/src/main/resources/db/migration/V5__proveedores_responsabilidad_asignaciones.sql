-- FacilityHub - FH-006 / P6-B: proveedores, responsabilidad contractual, coherencia centro/instalacion/local
-- y asignaciones de mantenimiento.
-- Migracion aditiva: no elimina filas ni columnas. Los datos existentes quedan validos:
--   * obligaciones existentes (datos demo de V2) -> responsable NHOOD (decision D-3);
--   * centro_id se rellena desde el local;
--   * mantenimientos existentes (P5) -> validacion_requerida = FALSE (historico sin validacion P6).
-- No crea usuarios ni contrasenas.

SET search_path TO facilityhub;

-- 1. Rol PROVEEDOR
INSERT INTO roles (codigo, nombre, descripcion)
VALUES ('PROVEEDOR', 'Proveedor / tecnico', 'Empresa externa: acceso exclusivo a trabajos asignados');

-- 2. Empresas proveedoras
CREATE TABLE proveedores (
    id BIGSERIAL PRIMARY KEY,
    cif VARCHAR(20),
    razon_social VARCHAR(150) NOT NULL,
    email_contacto VARCHAR(150),
    telefono VARCHAR(30),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_proveedor_cif ON proveedores(cif) WHERE cif IS NOT NULL;

-- 3. Usuarios tecnicos: vinculo con su empresa. Un usuario no puede ser a la vez inquilino y proveedor.
ALTER TABLE usuarios ADD COLUMN proveedor_id BIGINT REFERENCES proveedores(id);
ALTER TABLE usuarios ADD CONSTRAINT ck_usuario_un_vinculo CHECK (proveedor_id IS NULL OR inquilino_id IS NULL);
-- Clave candidata para exigir que el tecnico asignado pertenezca al proveedor de la asignacion.
ALTER TABLE usuarios ADD CONSTRAINT uq_usuario_id_proveedor UNIQUE (id, proveedor_id);
CREATE INDEX idx_usuarios_proveedor ON usuarios(proveedor_id);

-- Rol PROVEEDOR <=> proveedor_id informado.
CREATE FUNCTION fn_usuario_vinculo_proveedor() RETURNS trigger AS $$
DECLARE
    codigo_rol VARCHAR(30);
BEGIN
    SELECT codigo INTO codigo_rol FROM facilityhub.roles WHERE id = NEW.rol_id;
    IF (codigo_rol = 'PROVEEDOR') <> (NEW.proveedor_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Un usuario PROVEEDOR debe tener proveedor_id y ningun otro rol puede tenerlo'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_usuario_vinculo_proveedor
    BEFORE INSERT OR UPDATE OF rol_id, proveedor_id ON usuarios
    FOR EACH ROW EXECUTE FUNCTION fn_usuario_vinculo_proveedor();

-- 4. Coherencia centro / instalacion / local
ALTER TABLE locales ADD CONSTRAINT uq_local_id_centro UNIQUE (id, centro_id);

-- 4.1 Instalaciones: pertenecen a un centro; el local es opcional (NULL = zona comun).
ALTER TABLE instalaciones ADD COLUMN centro_id BIGINT REFERENCES centros(id);
UPDATE instalaciones i SET centro_id = l.centro_id FROM locales l WHERE l.id = i.local_id;
ALTER TABLE instalaciones ALTER COLUMN centro_id SET NOT NULL;
ALTER TABLE instalaciones ALTER COLUMN local_id DROP NOT NULL;
ALTER TABLE instalaciones ADD CONSTRAINT fk_instalacion_local_centro
    FOREIGN KEY (local_id, centro_id) REFERENCES locales(id, centro_id);
ALTER TABLE instalaciones ADD CONSTRAINT uq_instalacion_id_centro UNIQUE (id, centro_id);
CREATE INDEX idx_instalaciones_centro ON instalaciones(centro_id);

-- 4.2 Obligaciones: centro obligatorio, local opcional (zona comun), responsable contractual explicito.
ALTER TABLE obligaciones_mantenimiento ADD COLUMN centro_id BIGINT REFERENCES centros(id);
UPDATE obligaciones_mantenimiento o SET centro_id = l.centro_id FROM locales l WHERE l.id = o.local_id;
ALTER TABLE obligaciones_mantenimiento ALTER COLUMN centro_id SET NOT NULL;
ALTER TABLE obligaciones_mantenimiento ALTER COLUMN local_id DROP NOT NULL;

ALTER TABLE obligaciones_mantenimiento ADD COLUMN responsable VARCHAR(20);
UPDATE obligaciones_mantenimiento SET responsable = 'NHOOD';
ALTER TABLE obligaciones_mantenimiento ALTER COLUMN responsable SET NOT NULL;
ALTER TABLE obligaciones_mantenimiento ADD CONSTRAINT ck_obligacion_responsable
    CHECK (responsable IN ('NHOOD', 'INQUILINO'));
-- Una obligacion de zona comun nunca puede ser responsabilidad de un inquilino.
ALTER TABLE obligaciones_mantenimiento ADD CONSTRAINT ck_obligacion_responsable_local
    CHECK (responsable = 'NHOOD' OR local_id IS NOT NULL);
ALTER TABLE obligaciones_mantenimiento ADD CONSTRAINT fk_obligacion_local_centro
    FOREIGN KEY (local_id, centro_id) REFERENCES locales(id, centro_id);
ALTER TABLE obligaciones_mantenimiento ADD CONSTRAINT fk_obligacion_instalacion_centro
    FOREIGN KEY (instalacion_id, centro_id) REFERENCES instalaciones(id, centro_id);
CREATE INDEX idx_obligaciones_centro ON obligaciones_mantenimiento(centro_id);

-- 4.3 Derivacion de centro_id desde el local cuando no se informa y coherencia instalacion/local de la obligacion.
CREATE FUNCTION fn_instalacion_centro() RETURNS trigger AS $$
BEGIN
    IF NEW.centro_id IS NULL AND NEW.local_id IS NOT NULL THEN
        SELECT centro_id INTO NEW.centro_id FROM facilityhub.locales WHERE id = NEW.local_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_instalacion_centro
    BEFORE INSERT OR UPDATE OF local_id, centro_id ON instalaciones
    FOR EACH ROW EXECUTE FUNCTION fn_instalacion_centro();

CREATE FUNCTION fn_obligacion_coherencia() RETURNS trigger AS $$
DECLARE
    local_instalacion BIGINT;
BEGIN
    IF NEW.centro_id IS NULL AND NEW.local_id IS NOT NULL THEN
        SELECT centro_id INTO NEW.centro_id FROM facilityhub.locales WHERE id = NEW.local_id;
    END IF;
    IF NEW.instalacion_id IS NOT NULL THEN
        SELECT local_id INTO local_instalacion FROM facilityhub.instalaciones WHERE id = NEW.instalacion_id;
        -- Instalacion de un local: la obligacion debe ser del mismo local.
        -- Instalacion de zona comun: la obligacion no puede tener local.
        IF local_instalacion IS DISTINCT FROM NEW.local_id THEN
            RAISE EXCEPTION 'La instalacion no pertenece al local de la obligacion'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_obligacion_coherencia
    BEFORE INSERT OR UPDATE OF local_id, centro_id, instalacion_id ON obligaciones_mantenimiento
    FOR EACH ROW EXECUTE FUNCTION fn_obligacion_coherencia();

-- 5. Mantenimientos: bloqueo optimista y marca de validacion P6.
ALTER TABLE mantenimientos ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE mantenimientos ADD COLUMN validacion_requerida BOOLEAN NOT NULL DEFAULT TRUE;
-- Registros P5: historico sin validacion P6 (no se les atribuye ninguna aprobacion).
UPDATE mantenimientos SET validacion_requerida = FALSE;

-- 6. Asignaciones de mantenimiento a proveedores (una activa por mantenimiento).
CREATE TABLE asignaciones_mantenimiento (
    id BIGSERIAL PRIMARY KEY,
    mantenimiento_id BIGINT NOT NULL REFERENCES mantenimientos(id),
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id),
    tecnico_usuario_id BIGINT,
    asignado_por_usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    asignado_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    finalizada_at TIMESTAMP,
    finalizada_por_usuario_id BIGINT REFERENCES usuarios(id),
    motivo_fin VARCHAR(20),
    CONSTRAINT fk_asignacion_tecnico_proveedor
        FOREIGN KEY (tecnico_usuario_id, proveedor_id) REFERENCES usuarios(id, proveedor_id),
    CONSTRAINT ck_asignacion_motivo
        CHECK (motivo_fin IS NULL OR motivo_fin IN ('REASIGNADA', 'RETIRADA', 'CANCELADA', 'PROVEEDOR_BAJA', 'COMPLETADA')),
    CONSTRAINT ck_asignacion_cierre CHECK (
        (activa AND finalizada_at IS NULL AND motivo_fin IS NULL)
        OR (NOT activa AND finalizada_at IS NOT NULL AND motivo_fin IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uq_asignacion_activa ON asignaciones_mantenimiento(mantenimiento_id) WHERE activa;
CREATE INDEX idx_asignaciones_proveedor_activa ON asignaciones_mantenimiento(proveedor_id) WHERE activa;
CREATE INDEX idx_asignaciones_mantenimiento ON asignaciones_mantenimiento(mantenimiento_id);
