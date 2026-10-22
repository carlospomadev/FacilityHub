-- FacilityHub - Esquema inicial de base de datos
-- Version: 0.2
-- Objetivo: cubrir el nucleo funcional del proyecto para la entrega del 50 %.

CREATE SCHEMA IF NOT EXISTS facilityhub;
SET search_path TO facilityhub;

-- 1. Roles de acceso
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    descripcion VARCHAR(255)
);

-- 2. Inquilinos / operadores de los locales
CREATE TABLE inquilinos (
    id BIGSERIAL PRIMARY KEY,
    cif VARCHAR(20),
    razon_social VARCHAR(150) NOT NULL,
    nombre_comercial VARCHAR(120),
    email_contacto VARCHAR(150),
    telefono VARCHAR(30),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Usuarios de la aplicacion
CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    apellidos VARCHAR(120),
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    rol_id BIGINT NOT NULL REFERENCES roles(id),
    inquilino_id BIGINT REFERENCES inquilinos(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Centros comerciales / edificios gestionados
CREATE TABLE centros (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(200),
    ciudad VARCHAR(100),
    descripcion TEXT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Locales de cada centro
CREATE TABLE locales (
    id BIGSERIAL PRIMARY KEY,
    centro_id BIGINT NOT NULL REFERENCES centros(id),
    codigo VARCHAR(40) NOT NULL,
    nombre VARCHAR(120),
    planta VARCHAR(40),
    ubicacion VARCHAR(150),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_local_centro_codigo UNIQUE (centro_id, codigo)
);

-- 6. Historico de ocupacion de los locales por los inquilinos
CREATE TABLE ocupaciones_local (
    id BIGSERIAL PRIMARY KEY,
    local_id BIGINT NOT NULL REFERENCES locales(id),
    inquilino_id BIGINT NOT NULL REFERENCES inquilinos(id),
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_ocupacion_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);

-- Solo puede existir una ocupacion activa por local.
CREATE UNIQUE INDEX uq_ocupacion_activa_por_local
    ON ocupaciones_local(local_id)
    WHERE activo = TRUE;

-- 7. Instalaciones o sistemas sujetos a mantenimiento dentro de un local
CREATE TABLE instalaciones (
    id BIGSERIAL PRIMARY KEY,
    local_id BIGINT NOT NULL REFERENCES locales(id),
    nombre VARCHAR(120) NOT NULL,
    tipo VARCHAR(80) NOT NULL,
    descripcion TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_instalacion_estado CHECK (estado IN ('ACTIVA', 'INACTIVA', 'FUERA_SERVICIO'))
);

-- 8. Catalogo de mantenimientos exigibles
CREATE TABLE tipos_mantenimiento (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    descripcion TEXT,
    periodicidad_meses INTEGER NOT NULL,
    requiere_documento BOOLEAN NOT NULL DEFAULT TRUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_periodicidad_positiva CHECK (periodicidad_meses > 0)
);

-- 9. Obligaciones de mantenimiento aplicables a un local/instalacion
CREATE TABLE obligaciones_mantenimiento (
    id BIGSERIAL PRIMARY KEY,
    local_id BIGINT NOT NULL REFERENCES locales(id),
    instalacion_id BIGINT REFERENCES instalaciones(id),
    tipo_mantenimiento_id BIGINT NOT NULL REFERENCES tipos_mantenimiento(id),
    fecha_inicio DATE NOT NULL,
    proxima_fecha DATE NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    observaciones TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_obligacion_estado CHECK (estado IN ('ACTIVA', 'PAUSADA', 'CANCELADA')),
    CONSTRAINT ck_proxima_fecha CHECK (proxima_fecha >= fecha_inicio)
);

-- Evita duplicar la misma obligacion activa sobre la misma instalacion/local.
CREATE UNIQUE INDEX uq_obligacion_activa
    ON obligaciones_mantenimiento(local_id, COALESCE(instalacion_id, 0), tipo_mantenimiento_id)
    WHERE activo = TRUE;

-- 10. Ejecuciones concretas de mantenimiento
CREATE TABLE mantenimientos (
    id BIGSERIAL PRIMARY KEY,
    obligacion_id BIGINT NOT NULL REFERENCES obligaciones_mantenimiento(id),
    fecha_programada DATE NOT NULL,
    fecha_realizacion DATE,
    estado VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADO',
    descripcion TEXT,
    observaciones TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mantenimiento_estado CHECK (
        estado IN ('PROGRAMADO', 'PENDIENTE', 'EN_PROCESO', 'FINALIZADO', 'CANCELADO')
    ),
    CONSTRAINT ck_finalizado_con_fecha CHECK (
        estado <> 'FINALIZADO' OR fecha_realizacion IS NOT NULL
    )
);

-- 11. Documentacion aportada para acreditar un mantenimiento
CREATE TABLE documentos_mantenimiento (
    id BIGSERIAL PRIMARY KEY,
    mantenimiento_id BIGINT NOT NULL REFERENCES mantenimientos(id),
    nombre_original VARCHAR(255) NOT NULL,
    ruta_archivo VARCHAR(500) NOT NULL,
    tipo_documento VARCHAR(80),
    fecha_documento DATE,
    fecha_caducidad DATE,
    estado_revision VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones TEXT,
    fecha_subida TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_documento_revision CHECK (
        estado_revision IN ('PENDIENTE', 'VALIDO', 'RECHAZADO', 'CADUCADO')
    ),
    CONSTRAINT ck_documento_fechas CHECK (
        fecha_caducidad IS NULL OR fecha_documento IS NULL OR fecha_caducidad >= fecha_documento
    )
);

-- Indices de consulta frecuentes
CREATE INDEX idx_locales_centro ON locales(centro_id);
CREATE INDEX idx_instalaciones_local ON instalaciones(local_id);
CREATE INDEX idx_obligaciones_local ON obligaciones_mantenimiento(local_id);
CREATE INDEX idx_obligaciones_proxima_fecha ON obligaciones_mantenimiento(proxima_fecha);
CREATE INDEX idx_mantenimientos_obligacion ON mantenimientos(obligacion_id);
CREATE INDEX idx_mantenimientos_fecha_programada ON mantenimientos(fecha_programada);
CREATE INDEX idx_documentos_mantenimiento ON documentos_mantenimiento(mantenimiento_id);
