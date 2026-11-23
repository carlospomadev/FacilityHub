# FacilityHub — Demostración reproducible

Estos scripts construyen, validan y restauran una demostración completa de FacilityHub en un **entorno nativo aislado y desechable**:
- clúster PostgreSQL propio;
- base `facilityhub_demo`;
- backend local;
- emulador Android.

No se crean cuentas ni datos a mano, y no usan Docker.

> **Base principal protegida:** la demo solo trabaja con `facilityhub_demo`.
> - Nunca se conecta a la base principal `facilityhub` ni al puerto 5432.
> - La migración V10 solo se aplica a esta base de demo: el backend la pide con `FACILITYHUB_FLYWAY_TARGET=10`.
> - La base principal sigue en V9.

## Requisitos (ya instalados en el equipo de desarrollo)
- PostgreSQL 18 (`C:\Program Files\PostgreSQL\18\bin`).
- JDK 21 y Maven, con las dependencias en caché: los scripts compilan en modo offline.
- Android SDK con `adb`, el emulador y el AVD `Pixel_6`, para la parte Android.
- Windows PowerShell 5.1 o superior.

## Uso
Desde la raíz del repositorio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\demo\demo.ps1 -Accion PrepararPresentacion
```

| Acción | Qué hace |
|---|---|
| `PrepararPresentacion` | **Para presentar.** Reconstruye la demo en su estado inicial y arranca los backends 18080 y 8080. Comprueba la conectividad y el aislamiento, el login de las 6 cuentas, los estados iniciales de las intervenciones y los contadores del panel. Después arranca el emulador, instala la app y la deja **en la pantalla de login**, con un resumen de comprobaciones sin contraseñas. **No ejecuta recorridos automáticos ni modifica el escenario** |
| `Construir` | Crea o reutiliza el clúster demo (127.0.0.1:55440), **recrea** `facilityhub_demo`, arranca el backend en **18080** (Flyway V1–V10: solo esta base desechable pide V10, con `FACILITYHUB_FLYWAY_TARGET=10`), verifica el aislamiento y siembra las cuentas y los datos. Cada ejecución parte de cero: no duplica datos |
| `Validar` | Comprobaciones automáticas de la API, de solo lectura: autenticación, permisos, panel, alertas, historial y descarga de certificados con SHA-256. Con `-ConCicloCompleto` ejecuta además `scripts\validar-api.ps1`, que escribe datos |
| `Android` | Arranca un segundo backend demo en **8080** (la app usa `10.0.2.2:8080`) y el emulador si no está en marcha, compila e instala la app y ejecuta los recorridos de ADMIN, EXPLOTACION, PROVEEDOR e INQUILINO A y B. Tarda entre 45 y 90 minutos. Con `-Roles` se limita a algunos roles (por ejemplo, `-Roles ADMIN`). **EXPLOTACION y PROVEEDOR modifican datos.** Las animaciones del emulador se desactivan durante las pruebas y se restauran siempre |
| `Reiniciar` | Vuelve al estado inicial de la demo (equivale a `Construir`) |
| `Estado` | Muestra los recursos activos de la demo |
| `Detener` | Detiene **solo** lo que creó la demo: los backends, el emulador si lo arrancó la demo y el clúster demo. También restaura las animaciones del emulador si quedaron desactivadas |
| `Eliminar` | `Detener` y borra `%LOCALAPPDATA%\FacilityHubDemo`, solo si tiene el marcador de la demo |

Secuencia completa de ensayo:

```powershell
demo.ps1 -Accion Construir
demo.ps1 -Accion Validar -ConCicloCompleto
demo.ps1 -Accion Android
demo.ps1 -Accion Reiniciar
demo.ps1 -Accion Validar
```

## Salvaguardas
- **Base de datos:** solo `facilityhub_demo`; se rechazan `facilityhub`, `facilityhub_fh005_p3`, `postgres` y las plantillas.
- **Directorios y clúster:** todo vive en `%LOCALAPPDATA%\FacilityHubDemo`. Solo se toca si contiene el marcador `.facilityhub-demo`; el clúster tiene su propio marcador.
- **Puertos:** no se usa ningún puerto ocupado por otro servicio (55440, 18080 y 8080).
- **Aislamiento del backend, verificado antes de cualquier escritura por la API:**
  - el PID del backend solo tiene conexiones TCP con el clúster demo y ninguna con 5432;
  - `pg_stat_activity` registra sus sesiones (`ApplicationName=facilityhub-demo-<puerto>`) en la base demo.
- **Clúster:** antes de recrear la base se comprueba que el servidor del puerto 55440 es exactamente el clúster de la demo (`SHOW data_directory`).
- **Procesos:** solo se detienen los que creó la demo, comprobando el PID y el instante de inicio; un PID reutilizado por otro programa no se toca. El emulador solo se detiene si lo arrancó la demo.
- **Animaciones del emulador:**
  - se restauran en un `finally` al terminar o ante un error;
  - si el proceso muere, los valores originales quedan en `estado.json` y los restaura la siguiente ejecución o `Detener`.
- **Eliminar:** solo borra `%LOCALAPPDATA%\FacilityHubDemo`, con el marcador y el clúster detenido.
- **Recordatorios:** la tarea programada sigue desactivada. La demo solo hace una ejecución **manual** de recordatorios como ADMIN, para generar avisos de ejemplo.

## Credenciales
`Construir` genera contraseñas aleatorias para las seis cuentas de la demo (ADMIN, EXPLOTACION, INQUILINO A y B, y PROVEEDOR responsable y técnico):
- se guardan en `%LOCALAPPDATA%\FacilityHubDemo\credenciales-demo.json`, con permisos solo para el usuario actual;
- en la base se almacenan con BCrypt;
- **nunca se imprimen** ni se guardan en el repositorio;
- se reutilizan en cada reconstrucción, salvo que se pase `-RegenerarCredenciales`.

## Datos de la demo
Fechas relativas a hoy (Europe/Madrid). Hay un centro con dos locales:
- **L-101:** Restaurante Demo, inquilino A;
- **L-205:** Tienda Demo, inquilino B.

Cada local tiene sus instalaciones y hay además una **zona común**. Escenarios:
- mantenimientos aprobado, pendiente de validación, en corrección, asignado y próximo, vencido sin proveedor y de zona común;
- evidencias internas y certificados compartidos (PDF sintéticos);
- un registro «Histórico P5 — sin validación»;
- alertas.

Algunos datos de partida (usuarios del inquilino B, zona común y registro P5) se cargan por SQL, **solo** en la base demo. El resto pasa por los endpoints reales. El detalle está en `manifiesto-demo.json`, sin secretos.

## Últimos resultados comprobados
Resultados del 23/11/2026, con el código de `main` hasta FH-010B:

| Acción | Resultado |
|---|---|
| `PrepararPresentacion` | 14/14 |
| `Validar -ConCicloCompleto` | 66/66 |
| `Android` | 26/26 |

`Validar -ConCicloCompleto` y `Android` cambian datos de la demo; después hay que volver a ejecutar `PrepararPresentacion`.
