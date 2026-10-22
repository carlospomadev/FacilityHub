# FacilityHub

FacilityHub es una aplicación desarrollada como Proyecto Intermodular del ciclo de Desarrollo de Aplicaciones Multiplataforma (DAM).

El proyecto nace de una necesidad real observada en un departamento de Explotación encargado de gestionar los mantenimientos y el cumplimiento documental de distintos centros comerciales.

## Objetivo

Centralizar la gestión de:

- Centros comerciales.
- Locales.
- Inquilinos.
- Instalaciones técnicas.
- Obligaciones de mantenimiento.
- Planificación y seguimiento de mantenimientos.
- Documentación y certificados.
- Validación documental.
- Próximas fechas de mantenimiento.

## Tecnologías

### Backend
- Java 21
- Spring Boot
- Spring Security
- JWT
- Spring Data JPA
- Flyway
- Maven

### Base de datos
- PostgreSQL

### Aplicación móvil
- Android
- Kotlin

### Control de versiones
- Git
- GitHub

## Arquitectura

Android App
    |
    | REST API / JWT
    v
Spring Boot Backend
    |
    | JPA / Hibernate
    v
PostgreSQL

## Funcionalidades implementadas

- Autenticación mediante JWT.
- Gestión de roles.
- Consulta de centros y locales.
- Consulta del inquilino actual.
- Gestión de instalaciones.
- Gestión de obligaciones de mantenimiento.
- Creación de mantenimientos.
- Cambio de estado de mantenimientos.
- Finalización de mantenimientos.
- Cálculo automático de la próxima fecha de mantenimiento.
- Subida de documentación.
- Listado de documentos.
- Revisión y validación documental.
- Descarga de documentos.

## Estado del proyecto

Avance aproximado: 50 %

El backend y la base de datos se encuentran integrados y validados mediante pruebas funcionales.

La siguiente fase se centra principalmente en:

- Validación e integración de la aplicación Android.
- Mejora de la interfaz móvil.
- Dashboard.
- Notificaciones.
- Pruebas adicionales.
- Documentación final.

## Configuración local

El proyecto utiliza variables de entorno para información sensible.

Consultar:

`.env.example`

Ejemplo de variables:

- DB_URL
- DB_USERNAME
- DB_PASSWORD
- JWT_SECRET
- JWT_EXPIRATION_MINUTES
- SERVER_PORT
- FACILITYHUB_STORAGE_ROOT

## Estructura

FacilityHub/
├── backend/
├── android/
├── docs/
│   └── evidencias/
├── scripts/
├── README.md
├── CHANGELOG.md
├── .env.example
└── .gitignore

## Proyecto académico

Proyecto Final / Proyecto Intermodular  
Desarrollo de Aplicaciones Multiplataforma (DAM)
