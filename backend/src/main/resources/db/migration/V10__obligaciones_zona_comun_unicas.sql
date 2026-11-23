-- FacilityHub - FH-010B / RF-09 (decision D3): una sola obligacion ACTIVA por centro, instalacion y tipo en zona comun.
--
-- uq_obligacion_activa (V1) usa (local_id, COALESCE(instalacion_id, 0), tipo_mantenimiento_id) WHERE activo. En zona comun
-- local_id es NULL y PostgreSQL considera distintos los NULL, asi que ese indice no impide duplicados de zona comun.
-- Este indice parcial los cubre. Es ADITIVO: no modifica tablas, datos ni V1-V9.
--
-- Valor centinela 0: instalaciones.id es BIGSERIAL (empieza en 1) y la FK impide ids inexistentes, asi que 0 nunca es una
-- instalacion valida (mismo convenio que uq_obligacion_activa).
--
-- SEGURIDAD: el backend NO aplica esta migracion salvo que se pida expresamente (spring.flyway.target, por defecto 9;
-- ver application.yml). No ejecutarla en la base principal sin autorizacion.

-- 1. Comprobacion previa: si ya existen duplicados activos de zona comun, la migracion se detiene sin tocar nada.
--    No se hace ninguna limpieza automatica: requieren revision manual.
DO $$
DECLARE
    v_conflictos INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_conflictos FROM (
        SELECT 1
          FROM obligaciones_mantenimiento
         WHERE activo AND local_id IS NULL
         GROUP BY centro_id, COALESCE(instalacion_id, 0), tipo_mantenimiento_id
        HAVING COUNT(*) > 1
    ) d;
    IF v_conflictos > 0 THEN
        RAISE EXCEPTION 'V10 detenida: % grupos de obligaciones activas de zona comun duplicadas; requieren revision manual', v_conflictos;
    END IF;
END
$$;

-- 2. Indice unico parcial (solo obligaciones activas de zona comun).
CREATE UNIQUE INDEX uq_obligacion_activa_zona_comun
    ON obligaciones_mantenimiento (centro_id, COALESCE(instalacion_id, 0), tipo_mantenimiento_id)
    WHERE activo AND local_id IS NULL;
