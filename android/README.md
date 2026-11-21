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
| `ui/` | Pantallas Compose y ViewModels: `FacilityViewModel`, `FlujoViewModel`, `AlertasViewModel`, `PanelViewModel` y `HistorialViewModel` |
| `MainActivity.kt` | Navegación y pantalla inicial según el rol |

## Pantallas por rol
| Rol | Pantallas |
|---|---|
| ADMIN y EXPLOTACION | Centros → Locales → Detalle del local (inquilino, instalaciones, obligaciones, **Historial**) → Mantenimientos y documentos. **Panel de Explotación** (indicadores y listados por filtro), Validaciones, Proveedores, Declarados y Solicitudes. Gestión del mantenimiento (asignación) e Intervención (aprobar, pedir corrección o cerrar) |
| INQUILINO | Mis obligaciones (declarar o solicitar proveedor), Mantenimientos (fecha prevista, asignar proveedor aceptado, certificados), **Historial del local** con descarga de certificados compartidos y Mis solicitudes |
| PROVEEDOR | Mis trabajos → detalle del trabajo → Intervención (registrar, adjuntar evidencias y enviar a validación) |
| Todos | Mis notificaciones (alertas) |

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
La app se ha validado en un emulador Pixel_6 contra bases de datos desechables, en cada fase: panel de Explotación, historial, descarga de certificados, permisos por rol y respuestas 401/403/404 y de conexión. Las evidencias (capturas e informes) están fuera del repositorio. No hay pruebas Android automatizadas.
