-- FacilityHub - Datos de prueba
SET search_path TO facilityhub;

INSERT INTO roles (codigo, nombre, descripcion) VALUES
('ADMIN', 'Administrador', 'Configuracion general y gestion de usuarios'),
('EXPLOTACION', 'Responsable de Explotacion', 'Seguimiento de centros, locales y mantenimientos'),
('INQUILINO', 'Inquilino', 'Consulta de obligaciones y aportacion de documentacion');

INSERT INTO centros (codigo, nombre, direccion, ciudad, descripcion) VALUES
('CC-MAD-01', 'Centro Comercial Demo Madrid', 'Avenida de Ejemplo 100', 'Madrid', 'Centro ficticio utilizado para pruebas academicas');

INSERT INTO inquilinos (cif, razon_social, nombre_comercial, email_contacto, telefono) VALUES
('B00000001', 'Restauracion Demo SL', 'Restaurante Demo', 'operaciones@demo.local', '+34 600 000 001'),
('B00000002', 'Retail Demo SL', 'Tienda Demo', 'facility@retaildemo.local', '+34 600 000 002');

INSERT INTO locales (centro_id, codigo, nombre, planta, ubicacion) VALUES
((SELECT id FROM centros WHERE codigo='CC-MAD-01'), 'L-101', 'Restaurante Demo', 'Planta 1', 'Zona restauracion'),
((SELECT id FROM centros WHERE codigo='CC-MAD-01'), 'L-205', 'Tienda Demo', 'Planta 2', 'Galeria norte');

INSERT INTO ocupaciones_local (local_id, inquilino_id, fecha_inicio, activo) VALUES
((SELECT id FROM locales WHERE codigo='L-101'), (SELECT id FROM inquilinos WHERE nombre_comercial='Restaurante Demo'), DATE '2026-01-01', TRUE),
((SELECT id FROM locales WHERE codigo='L-205'), (SELECT id FROM inquilinos WHERE nombre_comercial='Tienda Demo'), DATE '2026-02-01', TRUE);

INSERT INTO instalaciones (local_id, nombre, tipo, descripcion) VALUES
((SELECT id FROM locales WHERE codigo='L-101'), 'Sistema PCI local 101', 'PCI', 'Elementos de proteccion contra incendios del local'),
((SELECT id FROM locales WHERE codigo='L-101'), 'Climatizacion local 101', 'CLIMATIZACION', 'Sistema de climatizacion del establecimiento'),
((SELECT id FROM locales WHERE codigo='L-205'), 'Cuadro electrico local 205', 'BAJA_TENSION', 'Cuadro electrico principal del local');

INSERT INTO tipos_mantenimiento (codigo, nombre, descripcion, periodicidad_meses, requiere_documento) VALUES
('PCI-TRIM', 'Revision PCI trimestral', 'Revision periodica de proteccion contra incendios', 3, TRUE),
('CLIMA-SEM', 'Mantenimiento de climatizacion semestral', 'Revision preventiva del sistema de climatizacion', 6, TRUE),
('BT-ANUAL', 'Revision electrica anual', 'Revision preventiva de baja tension', 12, TRUE);

INSERT INTO obligaciones_mantenimiento (
    local_id, instalacion_id, tipo_mantenimiento_id, fecha_inicio, proxima_fecha, observaciones
) VALUES
(
    (SELECT id FROM locales WHERE codigo='L-101'),
    (SELECT id FROM instalaciones WHERE nombre='Sistema PCI local 101'),
    (SELECT id FROM tipos_mantenimiento WHERE codigo='PCI-TRIM'),
    DATE '2026-09-15', DATE '2026-12-15', 'Obligacion de prueba para la demo'
),
(
    (SELECT id FROM locales WHERE codigo='L-101'),
    (SELECT id FROM instalaciones WHERE nombre='Climatizacion local 101'),
    (SELECT id FROM tipos_mantenimiento WHERE codigo='CLIMA-SEM'),
    DATE '2026-07-15', DATE '2027-01-15', NULL
),
(
    (SELECT id FROM locales WHERE codigo='L-205'),
    (SELECT id FROM instalaciones WHERE nombre='Cuadro electrico local 205'),
    (SELECT id FROM tipos_mantenimiento WHERE codigo='BT-ANUAL'),
    DATE '2026-05-10', DATE '2027-05-10', NULL
);

INSERT INTO mantenimientos (obligacion_id, fecha_programada, estado, descripcion) VALUES
((SELECT id FROM obligaciones_mantenimiento WHERE proxima_fecha=DATE '2026-12-15'), DATE '2026-12-15', 'PROGRAMADO', 'Revision PCI trimestral'),
((SELECT id FROM obligaciones_mantenimiento WHERE proxima_fecha=DATE '2027-01-15'), DATE '2027-01-15', 'PROGRAMADO', 'Mantenimiento semestral de climatizacion'),
((SELECT id FROM obligaciones_mantenimiento WHERE proxima_fecha=DATE '2027-05-10'), DATE '2027-05-10', 'PROGRAMADO', 'Revision electrica anual');
