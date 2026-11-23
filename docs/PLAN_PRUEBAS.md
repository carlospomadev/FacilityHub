# FacilityHub — Plan de pruebas

## Objetivo
Comprobar de forma reproducible que FacilityHub funciona de extremo a extremo (PostgreSQL, backend Spring Boot y app Android) con el circuito vigente: **mantenimiento → asignación → intervención → envío → validación**.

> **Base de datos desechable:** las pruebas P-02 y P-03 crean datos (mantenimientos, proveedores, técnicos, intervenciones y documentos). Deben ejecutarse **solo en una base desechable o de pruebas**, nunca sobre datos reales.

## P-01 Arranque reproducible
1. Crear un usuario y una base vacía en PostgreSQL 18 (ver `backend/README.md`).
2. Definir `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET` (al menos 32 bytes) y, opcionalmente, `FACILITYHUB_STORAGE_ROOT`.
3. En `backend/`, ejecutar `mvn spring-boot:run`. Alternativa: `docker compose up --build -d` (ver las advertencias de puerto en `backend/README.md`).

**Estado:**
- el arranque nativo está **validado** (FH-008A, desde las clases compiladas);
- `mvn spring-boot:run` **no se ha validado** en el equipo de desarrollo;
- Docker Compose **solo se ha revisado estáticamente**, sin ejecutarlo.

**Resultado esperado:**
- el log muestra `Successfully applied 9 migrations … now at version v9`;
- `flyway_schema_history` contiene las versiones 1 a 9, todas correctas;
- V10 aparece como pendiente y **no se aplica**: está fuera del destino por defecto, `FACILITYHUB_FLYWAY_TARGET=9`. Solo se aplica en una base desechable autorizada, con `FACILITYHUB_FLYWAY_TARGET=10` (ver `backend/README.md`);
- `GET http://localhost:8080/actuator/health` devuelve `{"status":"UP"}`.

## P-02 Validación automática de la API
Arrancar un backend **aparte**, contra una base desechable y con `SERVER_PORT=18080`. El script rechaza cualquier otro destino: solo admite `http://localhost:18080` o `http://127.0.0.1:18080`, nunca el 8080 habitual.

Después, desde PowerShell (Windows PowerShell 5.1 o PowerShell 7), con las contraseñas de los usuarios demo en variables de entorno:

```powershell
$env:FACILITYHUB_TEST_PASSWORD  = "<contraseña de explotacion@facilityhub.local>"
$env:FACILITYHUB_ADMIN_PASSWORD = "<contraseña de admin@facilityhub.local>"
.\scripts\validar-api.ps1 -ConfirmarEntornoDesechable
```

**Qué valida:**
- salud del backend;
- login JWT de EXPLOTACION y ADMIN;
- centros, locales, inquilino actual, instalaciones y obligaciones;
- creación de un mantenimiento;
- alta de un proveedor y un técnico de prueba (ADMIN);
- asignación (EXPLOTACION);
- registro de la intervención por el técnico;
- evidencia adjunta y envío a validación;
- aprobación por EXPLOTACION;
- recálculo de la próxima fecha (fecha de ejecución + periodicidad);
- presencia del cumplimiento en el historial del local.

**Resultado esperado:** el mensaje `FACILITYHUB - VALIDACION API SUPERADA` y código de salida 0.

**Barreras:** sin `-ConfirmarEntornoDesechable`, o con un destino distinto de `localhost`/`127.0.0.1:18080`, el script se niega a ejecutarse (código 1) antes de enviar ninguna petición.

**Repetición:** el script puede ejecutarse varias veces el mismo día; cada vez elige una obligación NHOOD sin ejecución aprobada ese día.

## P-03 Aplicación Android
1. Abrir `android/` en Android Studio y ejecutar la app en un emulador. El backend se alcanza en `http://10.0.2.2:8080/`.
2. **EXPLOTACION:**
   - Centros → Locales → Detalle del local → Historial;
   - Detalle del local → **Registrar instalación** y **Registrar obligación** (tipo del catálogo, periodicidad y próxima fecha calculada);
   - Centros → Locales → **Instalaciones del centro** → alta de una instalación o una obligación de zona común;
   - Panel de Explotación con sus filtros;
   - Validaciones → Intervención (Aprobar o Pedir corrección).
3. **INQUILINO:** Mi local (obligaciones) → Mantenimientos (certificados compartidos) → Historial del local y descarga de un certificado. No ve accesos de alta.
4. **PROVEEDOR:** técnico creado por ADMIN → Mis trabajos → registrar una intervención, adjuntar una evidencia y enviarla.

**Resultado esperado:**
- los datos proceden del backend;
- cada rol ve solo sus pantallas y acciones;
- un 401 devuelve al login y los errores de conexión muestran «Reintentar».

## P-04 Pruebas automatizadas del backend
- **`mvn test`:** 164 pruebas unitarias sin base de datos. Resultado esperado: `BUILD SUCCESS`.
- **Pruebas de integración (`*IT`):** solo contra la base de pruebas dedicada y protegida por la barrera `P4Entorno` (ver `backend/README.md`).

## P-05 Evidencias para la memoria
Capturar como mínimo:
- el arranque con las migraciones V1–V9 y el health check;
- el resultado verde de `validar-api.ps1`;
- en Android:
  - el panel de Explotación;
  - una intervención pendiente de validar;
  - el historial de gestión con «Histórico P5 — sin validación»;
  - el historial del inquilino con un certificado descargado;
  - «Mis trabajos» del proveedor.

No incluir en las capturas contraseñas, tokens ni datos personales reales.

## P-06 Demostración reproducible
Ver [`scripts/demo/README.md`](../scripts/demo/README.md). Todo se ejecuta sobre la base desechable `facilityhub_demo`, en un PostgreSQL propio; los scripts rechazan la base principal `facilityhub`.

- **`demo.ps1 -Accion PrepararPresentacion`:** reconstruye `facilityhub_demo` (V1–V10) y deja la app en el login. Resultado esperado: 14/14 comprobaciones.
- **`demo.ps1 -Accion Validar -ConCicloCompleto`:** API de los cuatro roles y ciclo de intervención. Resultado esperado: 66/66.
- **`demo.ps1 -Accion Android`:** recorridos de la app por rol. Resultado esperado: 26/26.

Validar y Android modifican datos: después hay que ejecutar `PrepararPresentacion`.

**Últimos resultados comprobados** (23/11/2026, con el código de `main` hasta FH-010B):

| Prueba | Resultado |
|---|---|
| `PrepararPresentacion` | 14/14 |
| `Validar -ConCicloCompleto` | 66/66 |
| `Android` | 26/26 |
| Unitarias del backend | 164/164 |
| Integración con barrera (`InstalacionesFH010AP6IT`) | 9/9 |
