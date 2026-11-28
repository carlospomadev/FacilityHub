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


## 22/11/2026 – Demostración reproducible (FH-008B)
Para poder presentar la aplicación sin preparar nada a mano, he creado los scripts de `scripts/demo/`.
- **Qué preparan:**
  - un PostgreSQL propio para la demo (puerto 55440, base `facilityhub_demo`);
  - dos backends (18080 y 8080) y los datos de ejemplo.
- **Protección de la base principal:** los scripts comprueban que el backend nunca se conecta a la base principal ni al puerto 5432.
- **Preparar la presentación:** `demo.ps1 -Accion PrepararPresentacion` deja todo en el estado inicial, con la app abierta en el login.
- **Comprobaciones automáticas:**
  - 65 en la API (66 con el ciclo completo de intervención);
  - 26 en Android, con UI Automator.
- Publicado en el PR #7.

## 22/11 – 23/11/2026 – Mejoras de diseño y navegación en Android (FH-009, R1–R7)
He rediseñado la app en siete partes pequeñas para que fuera más clara y cómoda de usar:
- **R1:** tema claro y oscuro con un color principal propio (`#0F5C6E`), componentes comunes y soporte para fuente grande.
- **R2:** pantalla de login más clara: sin cuenta precargada, botón «Mostrar contraseña», teclas de acción y mensajes distintos para credenciales, conexión y servidor.
- **R3:** barra de navegación inferior según el rol, menú de cuenta y vuelta al login cuando la sesión caduca.
- **R4:** panel de Explotación con indicadores, bandejas y deslizar para actualizar.
- **R5:** validación de intervenciones con los botones siempre visibles abajo y confirmación antes de aprobar o rechazar.
- **R6:** «Mis trabajos» del proveedor separado por fases, «Mi local» del inquilino e historial en forma de línea de tiempo.
- **R7:**
  - cada pantalla gestiona su propia carga y sus errores, y se evitan recargas innecesarias;
  - he añadido un selector de fecha en español y he adaptado la barra inferior a la fuente al 200 %.
- **Pruebas:** en cada parte hice pruebas dirigidas en el emulador y repetí la regresión Android (26/26) y la preparación de la demo (14/14).
- Publicado en los PR #8 a #14. El último merge es `400bcc4`.

## 23/11/2026 – Registro de instalaciones (FH-010A, RF-07)
Hasta ahora las instalaciones solo se podían consultar. He añadido su alta desde Android.
- **Backend:**
  - `POST /api/centros/{id}/instalaciones` registra una instalación en un local o en la zona común del centro;
  - `GET /api/centros/{id}/instalaciones` lista las del centro;
  - solo pueden usarlos ADMIN y EXPLOTACION.
- **Validaciones:**
  - el local tiene que ser del centro;
  - el centro y el local deben estar activos;
  - no se admiten dos instalaciones con el mismo nombre en el mismo sitio, aunque se envíen a la vez.
- **Android:** pantalla «Instalaciones» del centro y formulario «Nueva instalación», con errores junto a cada campo, protección contra el doble toque y mensaje de confirmación.
- **Pruebas:**

  | Batería | Resultado |
  |---|---|
  | Unitarias | 154 |
  | API | 27/27 |
  | Android | 18/18 |
  | Integración con barrera | 9/9 |
  | Regresión Android | 26/26 |
  | Preparación de la demo | 14/14 |

- Publicado en el PR #15. El merge es `714dcb4`.

## 23/11/2026 – Registro de obligaciones de mantenimiento (FH-010B, RF-09)
He añadido el alta de obligaciones de mantenimiento desde Android.
- **Backend:**
  - `POST /api/centros/{id}/obligaciones` registra una obligación;
  - `GET /api/centros/{id}/obligaciones` lista las del centro;
  - `GET /api/tipos-mantenimiento` devuelve el catálogo de tipos (solo lectura).
- **Reglas que he aplicado:**
  - el inquilino solo puede ser responsable si el local tiene una ocupación vigente, y nunca en zona común;
  - al registrar la obligación **no** se crea ningún mantenimiento: el primero se programa después;
  - si no se indica la próxima fecha, se calcula con la periodicidad del tipo;
  - no se admiten dos obligaciones activas iguales.
- **Migración V10:** añade un índice para evitar obligaciones duplicadas en la zona común.
  - Para no aplicarla por error en la base principal, el backend se queda en **V9** salvo que se indique `FACILITYHUB_FLYWAY_TARGET=10`.
  - Solo la he aplicado en bases de prueba y en la demo. **La base principal sigue en V9.**
- **Android:** formulario «Nueva obligación» con el tipo del catálogo, la periodicidad visible, el responsable, las fechas y una explicación de cómo se calcula la próxima fecha.
- **Pruebas:**

  | Batería | Resultado |
  |---|---|
  | Unitarias | 164 |
  | V10 en bases de prueba | 12/12 |
  | API | 43/43 |
  | Android | 21/21 |
  | API con ciclo completo | 66/66 |
  | Regresión Android | 26/26 |
  | Preparación de la demo | 14/14 |

- Publicado en el PR #16. El merge es `1b0c3ab`.

## 23/11/2026 – Gestión de los tipos de mantenimiento (FH-012, RF-08)
Hasta ahora el catálogo de tipos solo se podía consultar. He añadido su gestión desde Android para el rol ADMIN.
- **Backend:**
  - `GET /api/tipos-mantenimiento/catalogo` y `GET /api/tipos-mantenimiento/{id}` para consultar todos los tipos (también los inactivos) con su uso;
  - `POST /api/tipos-mantenimiento` para dar de alta;
  - `PATCH /api/tipos-mantenimiento/{id}` para editar;
  - `PATCH /api/tipos-mantenimiento/{id}/activo` para desactivar o reactivar.
  - `GET /api/tipos-mantenimiento` sigue igual: solo los activos, para el formulario de obligaciones.
- **Reglas:**
  - el código se fija al crear el tipo y no cambia; no se admiten códigos ni nombres repetidos;
  - la periodicidad va de 1 a 120 meses y solo hay las dos reglas de cálculo que ya existían;
  - si el tipo ya se usa, los cambios de periodicidad, regla o documento piden confirmación y se aplican a partir de ahora, sin cambiar las fechas guardadas;
  - no se puede pasar a exigir documento mientras haya intervenciones abiertas sin evidencias;
  - desactivar un tipo no borra ni cancela nada: solo deja de ofrecerse para obligaciones nuevas.
- **Concurrencia:**
  - las escrituras del catálogo se hacen de una en una con un bloqueo de PostgreSQL;
  - una «huella» de los datos detecta si otro administrador ha cambiado el tipo mientras lo editabas.
- **Auditoría:** todos los cambios quedan registrados en la tabla de auditoría que ya existía.
- **Android:** pantalla «Tipos de mantenimiento» (EXPLOTACION solo consulta) y formulario de alta y edición, con avisos de impacto y confirmaciones.
- **Base de datos:** no hay migraciones nuevas; la base principal sigue en V9.
- **Pruebas:**

  | Batería | Resultado |
  |---|---|
  | Unitarias | 172 |
  | API | 26/26 |
  | Android | 22/22 |
  | API con ciclo completo | 66/66 |
  | Regresión Android | 26/26 |
  | Preparación de la demo | 14/14 |

- **Corrección encontrada en las pruebas:** el campo Código desordenaba lo que se escribía al pasarlo a mayúsculas en cada pulsación; ahora se convierte al guardar.

## 28/11/2026 – Gestión de centros (FH-013, RF-03)
Hasta ahora los centros solo se podían consultar. He añadido su gestión desde Android para el rol ADMIN.
- **Backend:**
  - `GET /api/centros/gestion` y `GET /api/centros/gestion/{id}` para consultar todos los centros (también los inactivos) con su uso: locales, instalaciones, obligaciones, ocupaciones vigentes y mantenimientos abiertos;
  - `POST /api/centros` para dar de alta;
  - `PATCH /api/centros/{id}` para editar;
  - `PATCH /api/centros/{id}/activo` para desactivar o reactivar.
  - `GET /api/centros` sigue igual: solo los activos.
- **Reglas:**
  - código obligatorio, único y que no cambia (letras, números y guiones, de 2 a 30); nombre y ciudad obligatorios;
  - no puede haber dos centros con el mismo nombre en la misma ciudad (sin distinguir mayúsculas ni espacios);
  - no se puede desactivar un centro con ocupaciones vigentes, ocupaciones futuras activas o mantenimientos abiertos; si no los tiene, hay que confirmar viendo un resumen de lo que contiene;
  - desactivar no borra ni cambia nada más; el centro inactivo no admite instalaciones, obligaciones ni mantenimientos nuevos, y se puede reactivar.
- **Concurrencia:** igual que en los tipos (bloqueo de PostgreSQL, bloqueo de la fila y huella de los datos). El alta de mantenimientos bloquea también la fila del centro, así no se cruza con una desactivación.
- **Auditoría:** alta, edición, desactivación y reactivación quedan registradas.
- **Android:** acceso «Gestionar centros» desde la pestaña Centros (EXPLOTACION solo consulta), formulario de alta y edición y diálogo de desactivación con el resumen. La pestaña Centros ahora se recarga cuando cambian los datos.
- **Base de datos:** no hay migraciones nuevas; la base principal sigue en V9.
- **Pruebas:**

  | Batería | Resultado |
  |---|---|
  | Unitarias | 183 |
  | API | 34/34 |
  | Android | 22/22 |
  | API con ciclo completo | 66/66 |
  | Regresión Android | 26/26 |
  | Preparación de la demo | 14/14 |

## 28/11/2026 – Gestión de locales (FH-014, RF-04)
Hasta ahora los locales solo se podían consultar. He añadido su gestión desde Android para el rol ADMIN.
- **Backend:**
  - `GET /api/centros/{id}/locales/gestion` y `GET /api/locales/gestion/{id}` para consultar los locales de un centro (también los inactivos) con su uso: instalaciones, obligaciones, mantenimientos, ocupaciones vigentes y futuras y mantenimientos abiertos;
  - `POST /api/centros/{id}/locales` para dar de alta;
  - `PATCH /api/locales/{id}` para editar;
  - `PATCH /api/locales/{id}/activo` para desactivar o reactivar.
  - `GET /api/centros/{id}/locales` sigue igual: solo los activos.
- **Reglas:**
  - código obligatorio (hasta 40 caracteres), en mayúsculas, único dentro del centro y que no cambia; nombre obligatorio y que puede repetirse; planta y ubicación opcionales;
  - un local no puede cambiar de centro;
  - no se puede desactivar un local con ocupaciones vigentes, ocupaciones futuras activas o mantenimientos abiertos; si no los tiene, hay que confirmar viendo un resumen;
  - desactivar no borra ni cambia nada más; el local inactivo no admite instalaciones, obligaciones ni mantenimientos nuevos (los de zona común no cambian);
  - en un centro inactivo no se pueden crear ni reactivar locales (sí editarlos o desactivarlos).
- **Concurrencia:** bloqueo de la fila del centro y después la del local, siempre en ese orden, y huella de los datos. Las altas de instalaciones, obligaciones y mantenimientos de un local también bloquean su fila, así no se cruzan con una desactivación.
- **Auditoría:** alta, edición, desactivación y reactivación quedan registradas.
- **Android:** acceso «Gestionar locales» desde la lista de locales de un centro y desde la ficha del centro (EXPLOTACION solo consulta), formulario de alta y edición con el centro como dato fijo y diálogo de desactivación con el resumen.
- **Limitación:** las obligaciones activas de un local inactivo pueden seguir generando avisos de vencimiento.
- **Base de datos:** no hay migraciones nuevas; la base principal sigue en V9.
- **Pruebas:**

  | Batería | Resultado |
  |---|---|
  | Unitarias | 193 |
  | API | 38/38 |
  | Android | 23/23 |
  | API con ciclo completo | 66/66 |
  | Regresión Android | 26/26 |
  | Preparación de la demo | 14/14 |
