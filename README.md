# FacilityHub

FacilityHub es una aplicación para controlar el **mantenimiento preventivo y el cumplimiento documental** de los locales de centros comerciales. Se ha desarrollado como Proyecto Intermodular del ciclo de Desarrollo de Aplicaciones Multiplataforma (DAM), a partir de una necesidad real de un departamento de Explotación.

El sistema registra qué mantenimiento debe hacerse en cada local o zona común, quién es responsable, qué empresa lo ejecuta, qué evidencias y certificados se aportan, y quién valida que el mantenimiento se ha cumplido.

## Componentes

| Componente | Tecnología | Carpeta |
|---|---|---|
| API REST | Java 21, Spring Boot 4.1.1, Spring Security (JWT), Spring Data JPA, Flyway, Maven | [`backend/`](backend/README.md) |
| Base de datos | PostgreSQL 18, esquema `facilityhub`, migraciones Flyway V1–V9 | `backend/src/main/resources/db/migration` |
| Aplicación móvil | Android, Kotlin, Jetpack Compose, Material 3, MVVM, Retrofit | [`android/`](android/README.md) |
| Scripts | Validación automática de la API (PowerShell) | `scripts/` |

```
App Android (Compose, MVVM) --REST + JWT--> Backend Spring Boot --JPA/Flyway--> PostgreSQL
                                                   |
                                                   +-- almacenamiento de documentos en disco
```

## Roles

| Rol | Uso principal |
|---|---|
| `ADMIN` | Administración: alta de proveedores y técnicos, solicitudes de proveedor, ejecución manual de recordatorios y todo lo de gestión |
| `EXPLOTACION` | Gestión: panel de trabajos, asignación de proveedores, registro y **validación** de intervenciones, documentos e historial |
| `INQUILINO` | Sus obligaciones (según su ocupación vigente): proveedor declarado, fecha prevista, solicitudes, historial y certificados compartidos |
| `PROVEEDOR` | Técnico de una empresa mantenedora: «Mis trabajos» asignados, registro de intervenciones, evidencias y envío a validación |

Los permisos se comprueban siempre en el backend. El rol se vuelve a leer de la base de datos y lo ajeno responde 404 uniforme. La app solo muestra las acciones que el backend devuelve en `accionesPermitidas`.

## Funcionalidades

- **Autenticación JWT y control de acceso** por rol y por recurso.
- **Consulta de centros, locales, inquilino actual e instalaciones**. Estos datos maestros se cargan mediante migraciones: no hay pantallas de alta ni de edición. Por eso los requisitos RF-03, RF-04, RF-05, RF-07, RF-08 y RF-09 del informe de avance, que piden registrar o gestionar estos datos, están **parcialmente implementados**: se consultan, pero no se dan de alta ni se editan desde la aplicación.
- **Obligaciones de mantenimiento:**
  - periodicidad y próxima fecha;
  - responsable contractual (NHOOD o INQUILINO);
  - obligaciones de local y de **zona común**.
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
- **Historial del local:**
  - gestión ve aprobados, cierres sin cumplimiento e históricos P5 («Histórico P5 — sin validación»);
  - el inquilino ve solo los cumplimientos aprobados de su ocupación, con certificados compartidos.

## Puesta en marcha rápida

1. **Base de datos:** PostgreSQL 18 con una base y un usuario para la aplicación (detalle en [`backend/README.md`](backend/README.md#base-de-datos)).
2. **Backend:** definir las variables de entorno y ejecutar `mvn spring-boot:run` en `backend/`. Flyway crea el esquema y los datos de demostración (V1–V9).
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

**Usuarios de demostración** (creados por la migración V3): `admin@facilityhub.local`, `explotacion@facilityhub.local` e `inquilino@facilityhub.local`. Las contraseñas no se publican en el repositorio: las facilita el responsable del proyecto. Los usuarios PROVEEDOR los da de alta un ADMIN desde la API.

## Pruebas y validación
- `mvn test` en `backend/`: **147 pruebas unitarias** (sin base de datos).
- **Pruebas de integración (`*IT`):** se ejecutan contra una base de pruebas dedicada con una barrera de seguridad. Ver [`backend/README.md`](backend/README.md#pruebas).
- **`scripts/validar-api.ps1`:** recorrido completo de la API con el circuito vigente (mantenimiento → intervención → envío → validación).
- **Sin CI configurada:** la validación de cada fase se documenta con pruebas locales e integradas.

## Estructura

```
FacilityHub/
├── backend/        API Spring Boot, migraciones Flyway, Dockerfile y docker-compose
├── android/        Aplicación Android (Kotlin + Compose)
├── docs/           Plan de pruebas e informe de avance
├── scripts/        Validación automática de la API
├── CHANGELOG.md    Bitácora de desarrollo
└── .env.example    Plantilla de variables de entorno (sin valores reales)
```

## Proyecto académico
Proyecto Intermodular — Desarrollo de Aplicaciones Multiplataforma (DAM). La evolución por fases se detalla en [`CHANGELOG.md`](CHANGELOG.md).
