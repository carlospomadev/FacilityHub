# FacilityHub

FacilityHub es una aplicación para controlar el **mantenimiento preventivo y el cumplimiento documental** de los locales de centros comerciales. Se ha desarrollado como Proyecto Intermodular del ciclo de Desarrollo de Aplicaciones Multiplataforma (DAM), a partir de una necesidad real de un departamento de Explotación.

El sistema registra qué mantenimiento debe hacerse en cada local o zona común, quién es responsable, qué empresa lo ejecuta, qué evidencias y certificados se aportan, y quién valida que el mantenimiento se ha cumplido.

## Componentes

| Componente | Tecnología | Carpeta |
|---|---|---|
| API REST | Java 21, Spring Boot 4.1.1, Spring Security (JWT), Spring Data JPA, Flyway, Maven | [`backend/`](backend/README.md) |
| Base de datos | PostgreSQL 18, esquema `facilityhub`, migraciones Flyway V1–V10 (V10 solo con autorización expresa; ver [Migraciones](backend/README.md#base-de-datos)) | `backend/src/main/resources/db/migration` |
| Aplicación móvil | Android, Kotlin, Jetpack Compose, Material 3, MVVM, Retrofit | [`android/`](android/README.md) |
| Scripts | Validación automática de la API y demostración reproducible (PowerShell) | `scripts/`, [`scripts/demo/`](scripts/demo/README.md) |

```
App Android (Compose, MVVM) --REST + JWT--> Backend Spring Boot --JPA/Flyway--> PostgreSQL
                                                   |
                                                   +-- almacenamiento de documentos en disco
```

## Roles

| Rol | Uso principal |
|---|---|
| `ADMIN` | Administración: alta de proveedores y técnicos, catálogo de tipos de mantenimiento, solicitudes de proveedor, ejecución manual de recordatorios y todo lo de gestión |
| `EXPLOTACION` | Gestión: panel de trabajos, asignación de proveedores, registro y **validación** de intervenciones, documentos e historial |
| `INQUILINO` | Sus obligaciones (según su ocupación vigente): proveedor declarado, fecha prevista, solicitudes, historial y certificados compartidos |
| `PROVEEDOR` | Técnico de una empresa mantenedora: «Mis trabajos» asignados, registro de intervenciones, evidencias y envío a validación |

Los permisos se comprueban siempre en el backend. El rol se vuelve a leer de la base de datos y lo ajeno responde 404 uniforme. La app solo muestra las acciones que el backend devuelve en `accionesPermitidas`.

## Funcionalidades

- **Autenticación JWT y control de acceso** por rol y por recurso.
- **Consulta de centros, locales e inquilino actual.** Estos datos maestros se cargan mediante migraciones; no hay pantallas de alta (RF-03, RF-04 y RF-05 son solo de consulta).
- **Registro de instalaciones (RF-07, FH-010A):**
  - alta desde la app en un local o en la **zona común** del centro;
  - nombre, tipo, descripción y estado;
  - validación del centro y del local, y control de duplicados.
- **Registro de obligaciones de mantenimiento (RF-09, FH-010B):**
  - alta desde la app en un local, en una instalación o en la zona común;
  - tipo del catálogo con su periodicidad, que se consulta en `GET /api/tipos-mantenimiento`;
  - responsable contractual (NHOOD o INQUILINO; INQUILINO exige una ocupación vigente);
  - fecha de inicio y próxima fecha, que se calcula si no se indica;
  - el alta no programa ningún mantenimiento: el primero se crea después (RF-10).
- **Mantenimientos programados:**
  - asignación de proveedor y técnico;
  - fecha prevista indicada por el inquilino.
- **Intervenciones con validación:**
  - circuito borrador → envío → **aprobación**, corrección o cierre sin cumplimiento;
  - **recálculo automático de la próxima fecha** al aprobar;
  - un mantenimiento enviado no cuenta como cumplido hasta que se aprueba.
- **Documentación:**
  - evidencias y certificados;
  - revisión (pendiente, válido, rechazado o caducado);
  - visibilidad interna o compartida;
  - descarga controlada.
- **Proveedores:**
  - alta de empresas y técnicos;
  - el inquilino declara su proveedor y Explotación lo revisa;
  - solicitudes de alta de proveedor nuevo.
- **Alertas internas** («Mis notificaciones») y **recordatorios de vencimiento**. Los recordatorios están desactivados por defecto y solo se ejecutan manualmente desde ADMIN.
- **Panel de Explotación:** vencidos, próximos, pendientes de validación, en corrección, sin asignar, declaraciones y solicitudes.
- **Gestión de los tipos de mantenimiento (RF-08, FH-012):**
  - ADMIN puede dar de alta, editar, desactivar y reactivar tipos desde Android; EXPLOTACION solo los consulta;
  - el código no se puede cambiar y no se admiten códigos ni nombres repetidos;
  - si un tipo ya se usa en obligaciones activas, cambiar la periodicidad, la regla de cálculo o el documento obligatorio pide confirmación y se aplica a partir de ahora, sin tocar las fechas guardadas;
  - desactivar un tipo solo impide usarlo en obligaciones nuevas;
  - si otro administrador ha cambiado el tipo mientras lo editas, la app avisa y no se sobrescribe nada;
  - cada cambio queda registrado en la auditoría.
- **Mejoras de diseño en la app Android (FH-009):**
  - Material 3 con tema claro y oscuro, y navegación inferior por rol;
  - panel de Explotación y selector de fecha en español;
  - estado de carga y error propio de cada pantalla;
  - accesible con la fuente del sistema al 200 %.
- **Historial del local:**
  - gestión ve aprobados, cierres sin cumplimiento e históricos P5 («Histórico P5 — sin validación»);
  - el inquilino ve solo los cumplimientos aprobados de su ocupación, con certificados compartidos.

## Puesta en marcha rápida

1. **Base de datos:** PostgreSQL 18 con una base y un usuario para la aplicación (detalle en [`backend/README.md`](backend/README.md#base-de-datos)).
2. **Backend:** definir las variables de entorno y ejecutar `mvn spring-boot:run` en `backend/`. Flyway crea el esquema y los datos de demostración hasta **V9**. La migración V10 solo se aplica si se indica expresamente `FACILITYHUB_FLYWAY_TARGET=10` (ver [`backend/README.md`](backend/README.md#base-de-datos)).
3. **Comprobación:** `http://localhost:8080/actuator/health` → `{"status":"UP"}`.
4. **App:** abrir `android/` en Android Studio y ejecutarla en un emulador. El emulador accede al backend en `http://10.0.2.2:8080/`.
5. **Validación automática (opcional, solo en una base desechable):** arrancar un backend aparte con `SERVER_PORT=18080` contra una base desechable y ejecutar `scripts\validar-api.ps1 -ConfirmarEntornoDesechable`. El script rechaza cualquier destino que no sea `localhost`/`127.0.0.1` en el puerto 18080. Ver [`docs/PLAN_PRUEBAS.md`](docs/PLAN_PRUEBAS.md).

### Estado de validación de las vías de arranque
| Vía | Estado |
|---|---|
| **Arranque nativo** (PostgreSQL 18 + backend con variables de entorno + emulador Android) | **Validado** desde cero en una base desechable: Flyway V1–V9, health check, login, flujo completo de la API y conexión Android ↔ API (FH-008A). El backend se lanzó desde las clases compiladas (`mvn compile` + `java -cp`) |
| `mvn spring-boot:run` / `mvn package` | **No validado en el equipo de desarrollo**: el plugin de Spring Boot no estaba en la caché offline de Maven. En un equipo con acceso a internet, Maven lo descarga en la primera ejecución |
| **Docker Compose** (`backend/docker-compose.yml`) | **Solo revisión estática, sin ejecución** (Docker no estaba instalado). Ver las advertencias en `backend/README.md` |

### Configuración
Toda la información sensible va en variables de entorno, nunca en el repositorio. La plantilla es `.env.example`.

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexión JDBC a PostgreSQL |
| `JWT_SECRET` | Clave de firma de los tokens (**al menos 32 bytes**) |
| `JWT_EXPIRATION_MINUTES` | Caducidad del token (480 por defecto) |
| `SERVER_PORT` | Puerto HTTP (8080 por defecto) |
| `FACILITYHUB_STORAGE_ROOT` | Carpeta de los documentos subidos |
| `FACILITYHUB_RECORDATORIOS_ENABLED` | Tarea programada de recordatorios (`false` por defecto) |
| `FACILITYHUB_FLYWAY_TARGET` | Última migración que aplica Flyway (`9` por defecto). Solo un entorno autorizado debe fijar `10` |

**Usuarios de demostración** (creados por la migración V3): `admin@facilityhub.local`, `explotacion@facilityhub.local` e `inquilino@facilityhub.local`. Las contraseñas no se publican en el repositorio: las facilita el responsable del proyecto. Los usuarios PROVEEDOR los da de alta un ADMIN desde la API.

## Pruebas y validación
- `mvn test` en `backend/`: **172 pruebas unitarias** (sin base de datos).
- **Pruebas de integración (`*IT`):** se ejecutan contra una base de pruebas dedicada con una barrera de seguridad. Ver [`backend/README.md`](backend/README.md#pruebas).
- **`scripts/validar-api.ps1`:** recorrido completo de la API con el circuito vigente (mantenimiento → intervención → envío → validación).
- **Demostración reproducible** ([`scripts/demo/`](scripts/demo/README.md)): base desechable propia con comprobaciones automáticas.

Últimos resultados (FH-012, 23/11/2026; todas las pruebas que escriben datos se ejecutan en bases desechables):

| Batería | Resultado |
|---|---|
| Unitarias del backend | 172/172 |
| Integración con barrera (`InstalacionesFH010AP6IT`) | 9/9 |
| API de la demo con ciclo completo | 66/66 |
| Alta de instalaciones: API / Android | 27/27 / 18/18 |
| Alta de obligaciones: API / Android | 43/43 / 21/21 |
| Gestión de tipos: API / Android | 26/26 / 22/22 |
| Migración V10 en bases desechables | 12/12 |
| Regresión Android (los cuatro roles) | 26/26 |
| Preparación de la demo | 14/14 |

No hay CI configurada: cada fase se valida con pruebas locales e integradas.

## Estado de los requisitos (RF-01 a RF-12)
Requisitos funcionales del informe de avance v0.7 y su estado en `main`:

| Código | Requisito | Estado |
|---|---|---|
| RF-01 | Autenticación | Completo (JWT) |
| RF-02 | Roles | Completo (ADMIN, EXPLOTACION, INQUILINO y PROVEEDOR) |
| RF-03 | Centros | Consulta; sin alta ni edición |
| RF-04 | Locales | Consulta; sin alta ni edición |
| RF-05 | Inquilinos | Consulta del inquilino actual; sin alta |
| RF-06 | Ocupación e histórico | Completo |
| RF-07 | Instalaciones | **Completo:** alta desde la app (FH-010A); sin edición |
| RF-08 | Tipos de mantenimiento | **Completo:** alta, edición, desactivación y reactivación desde la app (FH-012) |
| RF-09 | Obligaciones | **Completo:** alta desde la app, responsable y próxima fecha (FH-010B) |
| RF-10 | Ejecuciones | Completo y ampliado (intervenciones con validación) |
| RF-11 | Documentación | Completo |
| RF-12 | Consulta de cumplimiento | Completo (historial del local y panel de Explotación) |

**Pendiente:**
- dar de alta y editar centros, locales e inquilinos (RF-03 a RF-05);
- editar instalaciones y obligaciones ya registradas;
- validar el arranque con Docker Compose y con `mvn spring-boot:run` (ver la tabla de vías de arranque).

## Estructura

```
FacilityHub/
├── backend/        API Spring Boot, migraciones Flyway, Dockerfile y docker-compose
├── android/        Aplicación Android (Kotlin + Compose)
├── docs/           Plan de pruebas e informe de avance v0.7 (histórico, hito del 50 %)
├── scripts/        Validación automática de la API y demostración reproducible (scripts/demo)
├── CHANGELOG.md    Bitácora de desarrollo
└── .env.example    Plantilla de variables de entorno (sin valores reales)
```

## Proyecto académico
Proyecto Intermodular — Desarrollo de Aplicaciones Multiplataforma (DAM). La evolución por fases se detalla en [`CHANGELOG.md`](CHANGELOG.md).

- [`docs/Informe_Avance_FacilityHub_v0.7.docx`](docs/Informe_Avance_FacilityHub_v0.7.docx): informe del hito del 50 % (22/10/2026). Es **histórico**: el estado actual es el que figura en este README y en la bitácora.
