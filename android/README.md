# FacilityHub — Aplicación Android

Cliente móvil de FacilityHub:
- Kotlin con Jetpack Compose y Material 3, Navigation Compose;
- MVVM con un ViewModel por área: sesión y navegación, flujos P6, alertas, panel e historial;
- Retrofit, OkHttp y Gson;
- `minSdk` 26, `targetSdk` y `compileSdk` 37.

## Estructura
| Carpeta o archivo | Contenido |
|---|---|
| `data/` | `FacilityApi` (Retrofit), `FacilityRepository`, modelos (`Models.kt`, `ModelsP6.kt`), `NetworkProvider` (URL base y token) y `TokenStore` |
| `ui/` | Pantallas Compose y ViewModels: `FacilityViewModel`, `FlujoViewModel`, `AlertasViewModel`, `PanelViewModel` y `HistorialViewModel`. Además: `Navegacion.kt` (barra inferior por rol), `EstadoCarga.kt` (estado de cada pantalla), `CampoFecha.kt` (selector de fecha), `InstalacionScreens.kt` y `ObligacionScreens.kt` (formularios de alta) `TiposScreens.kt` (catálogo de tipos) y `CentrosGestionScreens.kt` (gestión de centros) |
| `ui/theme/` | Tema Material 3 claro y oscuro, espaciados e iconos |
| `MainActivity.kt` | Navegación y pantalla inicial según el rol |

## Pantallas por rol
| Rol | Pantallas |
|---|---|
| ADMIN y EXPLOTACION | **Pestañas:** Panel, Centros, Validaciones (EXPLOTACION) o Proveedores (ADMIN) y Avisos. **Centros:** Centros → Locales → Detalle del local (inquilino, instalaciones, obligaciones e **Historial**) → Mantenimientos y documentos. **Panel de Explotación:** indicadores y bandejas por filtro. **Altas:** «Instalaciones» del centro, con **Nueva instalación** (en local o zona común) y **Nueva obligación** (desde el local o la zona común). **Otras pantallas:** Validaciones, Proveedores, Declarados, Solicitudes y **Tipos de mantenimiento** (ADMIN: alta, edición, desactivación y reactivación con avisos de impacto; EXPLOTACION: solo consulta) y **Gestionar centros** (ADMIN: alta, edición, desactivación con resumen de impacto —bloqueada si hay ocupaciones vigentes o futuras o mantenimientos abiertos— y reactivación; EXPLOTACION: consulta de todos, también los inactivos). La pestaña Centros solo muestra los centros activos. **Gestión:** asignación del mantenimiento e intervención (aprobar, pedir corrección o cerrar) |
| INQUILINO | **Pestañas:** Mi local, Solicitudes y Avisos. **Mi local:** obligaciones (declarar o solicitar proveedor) e historial del local, con descarga de certificados compartidos. **Mantenimientos:** fecha prevista, asignación del proveedor aceptado y certificados |
| PROVEEDOR | **Pestañas:** Trabajos y Avisos. **Mis trabajos** por fases (Por hacer, Corrección y Enviados) → detalle del trabajo → Intervención (registrar, adjuntar evidencias y enviar a validación) |
| Todos | Avisos (alertas) y menú de cuenta con «Cerrar sesión» |

**Diseño (FH-009):**
- Material 3 con tema claro y oscuro.
- Selector de fecha en español.
- Barra inferior adaptada a la fuente del sistema al 200 %.
- Cada pantalla tiene su propio estado de carga y error, y sus datos son reutilizables durante 60 s.

Los botones de acción salen solo de `accionesPermitidas`, calculadas por el backend. La app no duplica reglas de permisos.

**Comportamiento de la sesión:**
- un 401 devuelve al login;
- un 403 o un 404 muestran un mensaje claro;
- los errores de conexión se pueden reintentar.

## Ejecución
1. Arrancar el backend (ver [`../backend/README.md`](../backend/README.md)).
2. Abrir la carpeta `android/` en Android Studio y esperar a que Gradle sincronice. También se puede compilar con `gradlew :app:assembleDebug`.
3. Ejecutar la app en un emulador. La URL base está fijada en `data/NetworkProvider.kt` como `http://10.0.2.2:8080/`, que es el equipo anfitrión visto desde el emulador.
4. Iniciar sesión con un usuario de demostración. Las contraseñas las facilita el responsable del proyecto.

> **Dispositivo físico:** `10.0.2.2` solo funciona en el emulador. Para un móvil real hay que cambiar la URL base por la IP del equipo en la red local.

## Validación
La app se ha validado en un emulador Pixel_6 contra bases de datos desechables en cada fase: panel de Explotación, historial, descarga de certificados, altas de instalaciones y obligaciones, permisos por rol, respuestas 401/403/404 y de conexión, tema oscuro y fuente al 200 %.

**Recorridos automatizados** con ADB y UI Automator en [`scripts/demo/`](../scripts/demo/README.md) (`demo.ps1 -Accion Android`): 26 comprobaciones para los cuatro roles.

**Últimos resultados:**

| Prueba | Resultado |
|---|---|
| Regresión | 26/26 |
| Alta de instalaciones | 18/18 |
| Alta de obligaciones | 21/21 |
| Gestión de tipos | 22/22 |
| Gestión de centros | 22/22 |

No hay pruebas instrumentadas en el módulo Android. Las capturas y los informes de cada fase están fuera del repositorio.
