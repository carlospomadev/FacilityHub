-- FacilityHub - FH-006 / P6-D2: recordatorios de vencimiento sobre las alertas de V8 (canal APP).
-- Migracion aditiva: amplia los tipos admitidos y anade un indice de consulta. No modifica ni crea filas.
-- Los recordatorios los genera una ejecucion controlada (programada desactivada por defecto o manual por ADMIN);
-- el arranque no escribe nada. La deduplicacion reutiliza uq_alerta_evento con un evento_ref por periodo de aviso.

SET search_path TO facilityhub;

ALTER TABLE alertas DROP CONSTRAINT ck_alerta_tipo;
ALTER TABLE alertas ADD CONSTRAINT ck_alerta_tipo CHECK (tipo IN ('ASIGNACION', 'PROVEEDOR_DECLARADO',
    'EVIDENCIAS_PRESENTADAS', 'CORRECCION_SOLICITADA', 'APROBADO',
    'VENCIMIENTO_PROXIMO', 'MANTENIMIENTO_VENCIDO', 'VALIDACION_PENDIENTE_VENCIDA'));

-- Busqueda de mantenimientos abiertos por fecha programada (ventana de preaviso y vencidos).
CREATE INDEX idx_mantenimientos_abiertos_fecha ON mantenimientos(fecha_programada)
    WHERE estado NOT IN ('CANCELADO', 'FINALIZADO');
