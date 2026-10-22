# FacilityHub Backend - Hito 5: API funcional del nucleo

Esta version reúne el modelo de datos, autenticacion/roles y el primer recorrido funcional completo del backend.

## Recorrido soportado

`Login -> Centro -> Local -> Inquilino -> Instalaciones -> Obligaciones -> Mantenimientos -> Documentos`

## Funciones principales

- Login JWT y control por roles.
- Consulta de centros y locales.
- Consulta del inquilino activo de un local.
- Consulta de instalaciones de un local.
- Consulta de obligaciones y periodicidad.
- Creacion y cambio de estado de mantenimientos.
- Calculo de la siguiente fecha al finalizar un mantenimiento.
- Subida de certificados/documentos.
- Listado y descarga de documentos.
- Revision documental: PENDIENTE, VALIDO, RECHAZADO o CADUCADO.

## Endpoints

- `POST /api/auth/login`
- `GET /api/centros`
- `GET /api/centros/{id}`
- `GET /api/centros/{id}/locales`
- `GET /api/locales/{id}/inquilino-actual`
- `GET /api/locales/{id}/instalaciones`
- `GET /api/locales/{id}/obligaciones`
- `GET /api/obligaciones/{id}/mantenimientos`
- `POST /api/obligaciones/{id}/mantenimientos`
- `PATCH /api/mantenimientos/{id}/estado`
- `GET /api/mantenimientos/{id}/documentos`
- `POST /api/mantenimientos/{id}/documentos`
- `GET /api/documentos/{id}/archivo`
- `PATCH /api/documentos/{id}/revision`

## Usuarios demo

Clave para todos: `TU_PASSWORD_DE_PRUEBA`

- `admin@facilityhub.local`
- `explotacion@facilityhub.local`
- `inquilino@facilityhub.local`

## Estado de validacion

La estructura de codigo y configuracion ha sido revisada estaticamente. La prueba real con Maven + PostgreSQL + Docker se mantiene pendiente y debe realizarse antes de considerar este backend completamente validado.
