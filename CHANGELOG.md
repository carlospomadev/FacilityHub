# Bitácora de desarrollo - FacilityHub

## 22/10/2026 - Hito de integración y avance 50 %

### Infraestructura
- Instalación y configuración de PostgreSQL 18.
- Creación de la base de datos `facilityhub`.
- Creación del usuario específico de aplicación.
- Configuración segura de credenciales mediante variables de entorno.

### Backend
- Compilación correcta del proyecto Spring Boot.
- Integración de Flyway.
- Ejecución de migraciones de base de datos.
- Validación de Hibernate y JPA.
- Backend disponible en puerto 8080.
- Health check validado mediante Spring Boot Actuator.

### Seguridad
- Autenticación mediante usuario y contraseña.
- Generación de token JWT.
- Protección de endpoints.
- Roles de aplicación.
- Corrección de carga de relaciones de usuario mediante EntityGraph.

### Centros y locales
- Consulta de centros.
- Consulta de locales por centro.
- Consulta de inquilino actual.
- Consulta de instalaciones técnicas.

### Mantenimientos
- Consulta de obligaciones por local.
- Consulta de mantenimientos.
- Creación de nuevos mantenimientos.
- Cambio de estado PROGRAMADO a EN_PROCESO.
- Finalización de mantenimientos.
- Registro de fecha real de ejecución.
- Recalculo automático de la próxima fecha según periodicidad.

### Gestión documental
- Subida de documentos mediante multipart/form-data.
- Asociación del documento a un mantenimiento.
- Consulta de documentos.
- Revisión documental.
- Cambio de estado PENDIENTE a VALIDO.
- Descarga de archivos almacenados.

### Pruebas realizadas
- Login correcto.
- JWT válido.
- Lectura desde PostgreSQL.
- Escritura desde API.
- Persistencia de nuevos registros.
- Actualización de estados.
- Ejecución de reglas de negocio.
- Persistencia documental.
- Descarga documental.

### Control de versiones
- Preparación de estructura limpia para GitHub.
- Creación de `.gitignore`.
- Exclusión de archivos generados, temporales y secretos.
- Creación de `.env.example`.

## Próximo hito (planificado el 22/10/2026; completado en las fases siguientes)

- Validar aplicación Android.
- Integrar Android con API REST.
- Login móvil.
- Visualización de centros.
- Visualización de locales.
- Continuar segundo 50 % del proyecto.

## 23/10/2026 - Validación integración Android end-to-end

### Compatibilidad Android
- Generación e incorporación del Gradle Wrapper.
- Ajuste del proyecto a Android Gradle Plugin 9.2.1.
- Configuración de Gradle 9.4.1.
- Adaptación al soporte Kotlin integrado de AGP 9.
- Eliminación de configuración Kotlin obsoleta.
- Compilación Android validada mediante `assembleDebug`.

### Conectividad
- Configuración del emulador Android para acceder al backend mediante `10.0.2.2:8080`.
- Validación del permiso INTERNET.
- Incorporación de `ACCESS_LOCAL_NETWORK` para Android 17 / API 37.
- Solicitud del permiso de red local en tiempo de ejecución.
- Health check del backend validado desde el propio emulador.

### Integración Android - Backend
- Login desde Android validado correctamente.
- Autenticación JWT integrada.
- Identificación de usuario y rol EXPLOTACION.
- Consulta de centros comerciales.
- Consulta de locales por centro.
- Consulta del inquilino actual.
- Consulta de instalaciones técnicas.
- Consulta de obligaciones de mantenimiento.
- Consulta del histórico de mantenimientos.
- Creación de mantenimiento desde Android.
- Persistencia del nuevo mantenimiento en PostgreSQL.
- Refresco y visualización del mantenimiento creado en la aplicación.

### Flujo end-to-end validado
Android App
-> API REST
-> Spring Security / JWT
-> Servicios Spring Boot
-> PostgreSQL
-> Respuesta API
-> Actualización de interfaz Android

### Resultado
La integración móvil principal de FacilityHub queda funcional y validada para el hito de avance del proyecto.

## 03/11 – 16/11/2026 – Ciclo de mantenimiento y documentos en Android (FH-001 a FH-004)
- Se corrige el doble envío al crear mantenimientos desde Android: guarda síncrona de `loading`.
- Android permite iniciar y finalizar mantenimientos.
- Android permite listar y adjuntar documentos de mantenimiento (máximo 20 MB).

## 16/11 – 19/11/2026 – Seguridad documental y aislamiento por inquilino (FH-005)
- **V4:** visibilidad de documentos (INTERNO o COMPARTIDO). La autorización documental se aplica por recurso.
- **Aislamiento del inquilino** en centros, locales, obligaciones y mantenimientos, según su ocupación vigente. Lo ajeno responde 404 uniforme.
- **Endurecimiento del health check de Actuator** (sin detalles).
- **Pruebas:** primeras pruebas de integración (P4/A2) con barrera sobre una base de datos aislada.
- **Repositorio:**
  - `gradle-wrapper.jar` versionado;
  - valores sensibles retirados de la documentación y de `.env.example`.
- **Base principal:** migrada a V4 con copia de seguridad y restauración ensayada.

## 19/11 – 21/11/2026 – Proveedores, intervenciones y cumplimiento (FH-006, fase P6)
- **P6-B (V5):**
  - proveedores y técnicos (rol PROVEEDOR);
  - responsabilidad contractual NHOOD o INQUILINO;
  - obligaciones de zona común;
  - asignación de proveedor a mantenimientos;
  - «Mis trabajos» en Android.
- **P6-C1 (V6):**
  - intervenciones con evidencias;
  - envío a validación y aprobación, solicitud de corrección o cierre sin cumplimiento;
  - auditoría de eventos;
  - recálculo de la próxima fecha al aprobar;
  - un mantenimiento FINALIZADO significa «ejecutado y enviado»; solo cuenta como cumplido con una intervención APROBADO.
- **P6-C2 (V7):**
  - obligaciones del inquilino;
  - declaración de proveedor, que Explotación revisa;
  - solicitud de alta de proveedor nuevo;
  - fecha prevista;
  - protección del historial ante cambios de ocupación.
- **P6-C3:** circuito de intervenciones, validación y área del inquilino en Android.
- **P6-D1 (V8):** alertas internas por eventos y bandeja «Mis notificaciones».
- **P6-D2 (V9):**
  - recordatorios de vencimiento (próximo, vencido y validación pendiente vencida);
  - desactivados por defecto;
  - ejecución manual por ADMIN, en simulación salvo que se indique lo contrario.
- **P6-D3:**
  - panel de Explotación (`/api/gestion/resumen` y `/api/gestion/trabajos`) y pantalla Android;
  - historial del local (`/api/locales/{id}/historial`) para gestión e inquilino, con certificados compartidos y «Histórico P5 — sin validación»;
  - revisión final: solo los P5 cerrados cuentan como historial, los registros P5 del inquilino aparecen marcados y el login queda limpio tras un 401.
- **Pruebas de la fase:**
  - 147 pruebas unitarias;
  - pruebas de integración por bloque sobre la base de pruebas;
  - validación HTTP en bases desechables (incluida la descarga real de certificados);
  - validación en emulador.
- **Publicación:** cinco *pull requests* fusionados (#1 a #5); `main` = `5643db4`.

## 21/11/2026 – Documentación y reproducibilidad (FH-008A)
- **Documentación actualizada** al estado real: README principal, del backend y de Android, plan de pruebas y bitácora.
- **`scripts/validar-api.ps1` adaptado al circuito vigente:** mantenimiento → asignación → intervención → evidencia → envío → aprobación → recálculo → historial.
  - Exige `-ConfirmarEntornoDesechable` y solo admite un backend local en el puerto de pruebas 18080 (rechaza el 8080 habitual).
  - Toma las credenciales de variables de entorno.
  - Es compatible con Windows PowerShell 5.1.
- **Arranque reproducible validado** por la vía nativa, desde cero en una base desechable: Flyway V1–V9, backend, health check, login y conexión Android ↔ API.
- **Docker Compose:** solo revisión estática. **`mvn spring-boot:run`:** no validado en el equipo de desarrollo.

