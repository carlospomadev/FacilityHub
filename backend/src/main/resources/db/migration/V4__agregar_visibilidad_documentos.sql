-- FacilityHub - Visibilidad de documentos de mantenimiento (FH-005A1)
-- Migracion aditiva: los documentos existentes quedan INTERNO mediante el valor por defecto.
-- No modifica ni elimina filas.

SET search_path TO facilityhub;

ALTER TABLE documentos_mantenimiento
    ADD COLUMN visibilidad VARCHAR(20) NOT NULL DEFAULT 'INTERNO';

ALTER TABLE documentos_mantenimiento
    ADD CONSTRAINT ck_documento_visibilidad CHECK (visibilidad IN ('INTERNO', 'COMPARTIDO'));
