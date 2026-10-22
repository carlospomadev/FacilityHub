SET search_path TO facilityhub;

INSERT INTO usuarios (nombre, apellidos, email, password_hash, activo, rol_id, inquilino_id)
VALUES
('Admin', 'FacilityHub', 'admin@facilityhub.local', '$2a$10$H98AEOL8a.0tInXi.3aL5.PrMu.RbnjIA7ewI7RVaOyPBW9/kvD.e', TRUE,
 (SELECT id FROM roles WHERE codigo='ADMIN'), NULL),
('Responsable', 'Explotacion', 'explotacion@facilityhub.local', '$2a$10$H98AEOL8a.0tInXi.3aL5.PrMu.RbnjIA7ewI7RVaOyPBW9/kvD.e', TRUE,
 (SELECT id FROM roles WHERE codigo='EXPLOTACION'), NULL),
('Usuario', 'Inquilino Demo', 'inquilino@facilityhub.local', '$2a$10$H98AEOL8a.0tInXi.3aL5.PrMu.RbnjIA7ewI7RVaOyPBW9/kvD.e', TRUE,
 (SELECT id FROM roles WHERE codigo='INQUILINO'),
 (SELECT id FROM inquilinos WHERE nombre_comercial='Restaurante Demo'));
