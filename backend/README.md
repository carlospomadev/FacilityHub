# FacilityHub — Backend

API REST de FacilityHub. Usa Java 21, Spring Boot 4.1.1 (Web, Security con OAuth2 Resource Server y JWT, Data JPA, Validation y Actuator), PostgreSQL 18 y Flyway.

Paquetes en `com.facilityhub`:

| Paquete | Contenido |
|---|---|
| `api` | Controladores REST y DTO |
| `service` | Reglas de negocio y autorización por recurso |
| `repository` | Spring Data JPA |
| `domain` | Entidades |
| `security` | JWT y `SecurityConfig` |
| `config` | Reloj Europe/Madrid y recordatorios programados |

## Requisitos previos
- JDK 21.
- Maven 3.9 o superior. No hay `mvnw` versionado. La primera ejecución descarga dependencias y plugins.
- PostgreSQL 18 (local o Docker).

## Base de datos
Hay que crear un usuario y una base vacía; el esquema y los datos los crea Flyway al arrancar:

```sql
CREATE ROLE facilityhub LOGIN PASSWORD '<elige una contraseña>';
CREATE DATABASE facilityhub OWNER facilityhub;
```

Migraciones (`src/main/resources/db/migration`):

| Versión | Contenido |
|---|---|
| V1 | Modelo inicial |
| V2 | Datos de demostración: 1 centro, 2 locales, instalaciones y obligaciones |
| V3 | Usuarios demo |
| V4 | Visibilidad de documentos |
| V5 | Proveedores, responsabilidad y asignaciones |
| V6 | Intervenciones, validación y auditoría |
| V7 | Inquilino, declaración y solicitudes de proveedor |
| V8 | Alertas |
| V9 | Recordatorios de vencimiento |
| V10 | Índice único parcial: una sola obligación **activa** de zona común por centro, instalación y tipo (FH-010B). Es aditiva y se detiene, sin borrar nada, si ya existen duplicados |

> **V10 no se aplica por defecto.** `application.yml` fija `spring.flyway.target` en `${FACILITYHUB_FLYWAY_TARGET:9}`:
> - Sin esa variable, Flyway se queda en **V9** en cualquier base, incluida la base principal `facilityhub`, que permanece en V9.
> - El backend funciona igual en V9: la regla de duplicados también la aplica el servicio.
> - Solo un entorno autorizado debe fijar `FACILITYHUB_FLYWAY_TARGET=10`. Hoy lo hace únicamente la demo desechable (`scripts/demo`).
> - Antes de aplicarla en una base con datos, hay que comprobar que no existan obligaciones activas de zona común duplicadas.

Hibernate trabaja con `ddl-auto: validate`: no modifica el esquema. Los mantenimientos anteriores a la fase P6 quedan marcados como `validacion_requerida = FALSE` («Histórico P5 — sin validación»).

## Configuración
Toda la configuración se toma de variables de entorno (ver `src/main/resources/application.yml` y la tabla del [README principal](../README.md#configuración)). Mínimo imprescindible: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` (al menos 32 bytes).

Ejemplo en PowerShell, con valores propios:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/facilityhub"
$env:DB_USERNAME = "facilityhub"
$env:DB_PASSWORD = "<contraseña de la base>"
$env:JWT_SECRET = "<cadena aleatoria de 32 o más caracteres>"
$env:FACILITYHUB_STORAGE_ROOT = "C:\facilityhub\documentos"
```

## Ejecución
```powershell
cd backend
mvn spring-boot:run
```

> **Estado de validación:**
> - **Validado:** el arranque nativo, desde cero en una base desechable (Flyway V1–V9, health check, login y flujo completo de la API, FH-008A). El backend se lanzó desde las clases compiladas: `mvn compile` y después `java -cp target/classes;<classpath de dependencias> com.facilityhub.FacilityHubApplication`.
> - **No validado en el equipo de desarrollo:** `mvn spring-boot:run` y `mvn package`, porque el plugin de Spring Boot no estaba en la caché offline de Maven.

- **Salud:** `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`. No expone detalles.
- **Recordatorios automáticos:** desactivados (`FACILITYHUB_RECORDATORIOS_ENABLED=false`). ADMIN puede lanzarlos a mano con `POST /api/admin/recordatorios/ejecutar`, que por defecto es una simulación y no escribe nada.

### Docker (alternativa)
`docker-compose.yml` levanta PostgreSQL 18.6 y el backend. Necesita `DB_PASSWORD` y `JWT_SECRET` como variables de entorno.

```powershell
cd backend
docker compose up --build -d
```

> **Atención:**
> - Publica el puerto **5432**, que choca con un PostgreSQL local ya instalado.
> - Usa los volúmenes `facilityhub_pgdata` y `facilityhub_documents`.
> - **Solo revisión estática, sin ejecución:** Docker no estaba instalado en el equipo de desarrollo (FH-008A).
> - El healthcheck del backend usa `wget`; no está verificado que la imagen `eclipse-temurin:21-jre` lo incluya.
> - La vía validada es la ejecución nativa descrita arriba.

## Usuarios de demostración
| Rol | Usuario |
|---|---|
| ADMIN | `admin@facilityhub.local` |
| EXPLOTACION | `explotacion@facilityhub.local` |
| INQUILINO | `inquilino@facilityhub.local` (ocupante del local L-101) |

Las contraseñas no se publican en el repositorio: las facilita el responsable del proyecto. Los técnicos de proveedor (rol PROVEEDOR) los crea un ADMIN con `POST /api/proveedores/{id}/usuarios`.

## Endpoints
Todos requieren `Authorization: Bearer <token>`, salvo el login y `/actuator/health`. Gestión = ADMIN y EXPLOTACION.

| Área | Endpoints | Roles |
|---|---|---|
| Autenticación | `POST /api/auth/login` | público |
| Centros y locales | `GET /api/centros`, `/api/centros/{id}`, `/api/centros/{id}/locales`; `GET /api/locales/{id}/inquilino-actual`, `/api/locales/{id}/instalaciones` | gestión e INQUILINO (su local) |
| Instalaciones (RF-07) | `GET` y `POST /api/centros/{id}/instalaciones`: alta en un local o en la zona común (`localId` nulo); 201 con la instalación creada | gestión |
| Alta de obligaciones (RF-09) | `GET` y `POST /api/centros/{id}/obligaciones`: alta con `localId?`, `instalacionId?`, `tipoMantenimientoId`, `responsable`, `fechaInicio` y `proximaFecha?`; 201 con la obligación creada. No crea mantenimientos | gestión |
| Tipos de mantenimiento | `GET /api/tipos-mantenimiento`: tipos activos con su periodicidad, para el formulario de obligaciones | gestión |
| Catálogo de tipos (RF-08) | `GET /api/tipos-mantenimiento/catalogo` y `GET /api/tipos-mantenimiento/{id}` (activos e inactivos, con su uso y una `huella`); `POST /api/tipos-mantenimiento`; `PATCH /api/tipos-mantenimiento/{id}` (con `huella` y, si hay uso, `confirmarImpacto`); `PATCH /api/tipos-mantenimiento/{id}/activo` | consulta: gestión; escritura: ADMIN |
| Gestión de centros (RF-03) | `GET /api/centros/gestion` y `GET /api/centros/gestion/{id}` (activos e inactivos, con su uso y una `huella`); `POST /api/centros`; `PATCH /api/centros/{id}` (con `huella`); `PATCH /api/centros/{id}/activo` (con `huella` y, al desactivar, `confirmarImpacto`) | consulta: gestión; escritura: ADMIN |
| Gestión de locales (RF-04) | `GET /api/centros/{id}/locales/gestion` y `GET /api/locales/gestion/{id}` (activos e inactivos, con su centro, su uso y una `huella`); `POST /api/centros/{id}/locales`; `PATCH /api/locales/{id}` (con `huella`; `codigo` y `centroId`, si se envían, deben coincidir); `PATCH /api/locales/{id}/activo` (con `huella` y, al desactivar, `confirmarImpacto`). `GET /api/centros/{id}/locales` no cambia (solo activos) | consulta: gestión; escritura: ADMIN |
| Obligaciones | `GET /api/locales/{id}/obligaciones`; `GET` y `POST /api/obligaciones/{id}/mantenimientos`; `PATCH /api/obligaciones/{id}/responsable` | consulta: gestión e INQUILINO; escritura: gestión |
| Mantenimientos | `PATCH /api/mantenimientos/{id}/estado` (sin FINALIZADO); `GET`, `PUT` y `DELETE /api/mantenimientos/{id}/asignacion` | gestión |
| Intervenciones | `GET` y `POST /api/mantenimientos/{id}/intervenciones`; `GET` y `PATCH /api/intervenciones/{id}`; `POST …/{id}/documentos`, `POST …/{id}/envio`; `GET …/{id}/documentos/{docId}/archivo` | gestión y PROVEEDOR asignado |
| Validación | `GET /api/intervenciones/pendientes`; `POST /api/intervenciones/{id}/validacion` (APROBAR, SOLICITAR_CORRECCION o CERRAR_SIN_CUMPLIMIENTO) | gestión |
| Documentos | `GET` y `POST /api/mantenimientos/{id}/documentos`; `PATCH /api/documentos/{id}/revision`, `/visibilidad`; `GET /api/documentos/{id}/archivo` | gestión; INQUILINO solo descarga lo VÁLIDO y COMPARTIDO de su ocupación |
| Proveedores | `GET /api/proveedores`, `/{id}`; `POST`, `PATCH /api/proveedores…`, `POST` y `PATCH …/{id}/usuarios…` | consulta: gestión; altas: ADMIN |
| Área del proveedor | `GET /api/proveedor/trabajos`, `/api/proveedor/trabajos/{mantenimientoId}` | PROVEEDOR |
| Área del inquilino | `/api/inquilino/obligaciones`, `…/{id}/mantenimientos`, `…/{id}/proveedor`, `/api/inquilino/proveedores?cif=`, `/api/inquilino/solicitudes-proveedor`, `/api/inquilino/mantenimientos/{id}/fecha-prevista`, `…/asignacion` | INQUILINO |
| Revisión de proveedores | `GET /api/declaraciones-proveedor/pendientes`, `PATCH …/{id}/revision`; `GET /api/solicitudes-proveedor`, `PATCH …/{id}` | gestión (la resolución de solicitudes es solo de ADMIN) |
| Panel de Explotación | `GET /api/gestion/resumen`; `GET /api/gestion/trabajos?filtro=VENCIDOS\|PROXIMOS\|PENDIENTE_VALIDACION\|REQUIERE_CORRECCION\|SIN_ASIGNAR` | gestión |
| Historial del local | `GET /api/locales/{id}/historial` | gestión (completo) e INQUILINO (aprobados de su ocupación) |
| Alertas | `GET /api/alertas`, `/api/alertas/no-leidas`; `PATCH /api/alertas/{id}/leida` | todos (solo las propias) |
| Recordatorios | `POST /api/admin/recordatorios/ejecutar?simulacion=` | ADMIN |

**Respuestas de error:**
- 401: sin token o token no válido;
- 403: rol sin permiso;
- 404 uniforme: recurso inexistente o ajeno;
- 409: conflicto de versión o de estado, recurso inactivo, duplicado o, en obligaciones INQUILINO, local sin ocupación vigente.
  - En el catálogo de tipos, el 409 lleva además un campo `codigo`:

    | Código | Significado |
    |---|---|
    | `CODIGO_DUPLICADO` | Ya existe un tipo con ese código |
    | `NOMBRE_DUPLICADO` | Ya existe un tipo con ese nombre |
    | `DATOS_DESACTUALIZADOS` | Otro usuario ha cambiado el tipo |
    | `IMPACTO_NO_CONFIRMADO` | El cambio afecta a obligaciones activas y falta la confirmación |
    | `INTERVENCIONES_SIN_DOCUMENTO` | Hay intervenciones abiertas sin evidencias que quedarían bloqueadas |

  - En la gestión de centros (FH-013) se usan los mismos códigos `CODIGO_DUPLICADO`, `NOMBRE_DUPLICADO` (mismo nombre en la misma ciudad), `DATOS_DESACTUALIZADOS` e `IMPACTO_NO_CONFIRMADO`, y además `CENTRO_EN_USO` (no se puede desactivar: el 409 lleva `ocupacionesVigentes`, `ocupacionesFuturas` y `mantenimientosAbiertos`).

**Cómo se protege la gestión de locales (FH-014):**
- Códigos de error: `CODIGO_DUPLICADO` (mismo código en el mismo centro, sin distinguir mayúsculas), `CENTRO_INACTIVO` (alta o reactivación con el centro inactivo), `LOCAL_EN_USO` (no se puede desactivar: el 409 lleva `ocupacionesVigentes`, `ocupacionesFuturas` y `mantenimientosAbiertos`), `IMPACTO_NO_CONFIRMADO` (con el resumen) y `DATOS_DESACTUALIZADOS`. Cambiar el código o el centro devuelve 400.
- **Bloqueos, siempre en el mismo orden:** primero la fila del centro y después la del local. Lo hacen el alta, la edición y la (des)activación del local, y también las altas de instalaciones, obligaciones y mantenimientos de un local, que leen su estado después de bloquearlo. Así una desactivación y un alta relacionada no se cruzan. Además, el índice `uq_local_centro_codigo` es la última defensa contra códigos repetidos. Huella SHA-256 de los valores; sin migraciones nuevas.
- **Uso del local:** las mismas definiciones que en los centros (ocupación vigente o futura, mantenimiento abierto) aplicadas al local, con la fecha de Europe/Madrid. Bloquean la desactivación aunque se confirme.
- **Local inactivo:** no admite instalaciones, obligaciones ni mantenimientos nuevos (también cuando la obligación viene de una instalación del local). Las obligaciones de zona común no cambian. No se modifica ningún dato relacionado.
- **Límites:** las obligaciones activas de un local inactivo siguen apareciendo en el panel y pueden generar recordatorios y avisos de vencimiento (no se ha cambiado el módulo de alertas).

**Cómo se protege la gestión de centros (FH-013):**
- Mismo esquema que los tipos: bloqueo asesor propio del catálogo de centros y después bloqueo de la fila del centro; huella SHA-256 de sus valores; sin migraciones nuevas.
- **Trabajo abierto:** un mantenimiento cuenta como abierto si no está cancelado ni tiene una intervención aprobada, y está `PROGRAMADO`, `PENDIENTE` o `EN_PROCESO` o tiene una intervención en `BORRADOR`, `PENDIENTE_VALIDACION` o `REQUIERE_CORRECCION`. Una ocupación es vigente si está activa y la fecha de hoy (Europe/Madrid) está entre su inicio y su fin, y es futura si está activa, empieza después de hoy y no ha terminado. Las dos bloquean la desactivación, aunque se confirme.
- **Centro inactivo:** no admite instalaciones, obligaciones ni mantenimientos nuevos (`POST /api/obligaciones/{id}/mantenimientos` también lo comprueba). El alta de un mantenimiento bloquea la misma fila del centro que la desactivación, así que las dos operaciones no se cruzan.
- **Límites:** el nombre + ciudad repetido lo comprueba el servicio (no hay índice único, no se ha creado V11); el panel y los recordatorios no se han cambiado y siguen mostrando el trabajo pendiente que ya existía.

**Cómo se protege el catálogo de tipos (FH-012):**
- **Escrituras de una en una:** se serializan con un bloqueo asesor transaccional de PostgreSQL (`pg_advisory_xact_lock`) y después con el bloqueo de la fila del tipo, siempre en ese orden. Así no se crean dos tipos con el mismo nombre aunque haya varias instancias del backend.
- **Límite:** la regla de nombres repetidos la comprueba el servicio; un `INSERT` hecho por SQL fuera de la aplicación no queda protegido, porque no hay índice único de nombre (no se ha creado V11).
- **Huella:** al abrir un tipo, la API devuelve una huella (SHA-256 de sus valores). Al guardar se compara con la actual dentro de la misma transacción bloqueada.
- **Límite de la huella:** si otro usuario cambia un valor y lo vuelve a dejar igual, la huella vuelve a coincidir y ese cambio no se detecta.
- **Exigir documento:** solo se permite si no quedan intervenciones abiertas del tipo (`BORRADOR` o `REQUIERE_CORRECCION`, con resultado ejecutado o sin decidir) sin ninguna evidencia.

## Pruebas
- **Unitarias:** `mvn test` ejecuta 193 pruebas (Mockito) sin base de datos.
- **Integración (`*IT`):** MockMvc, JWT y JPQL reales con *rollback* por prueba.
  - Una barrera (`P4Entorno`) solo permite ejecutarlas contra la base de pruebas dedicada `facilityhub_fh005_p3`, con `-Dp4.autorizado=facilityhub_fh005_p3` y la variable `P4_DB_PASSWORD`.
  - No se lanzan con `mvn test` por defecto.
  - Las integradas no aplican migraciones: Flyway está desactivado en esa base, que no recibe V10.
- **API de extremo a extremo:** `scripts/validar-api.ps1`, solo contra un backend desechable en `127.0.0.1:18080` (ver `docs/PLAN_PRUEBAS.md`).
