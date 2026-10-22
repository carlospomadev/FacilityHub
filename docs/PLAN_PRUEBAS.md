# FacilityHub - Plan de pruebas Hito 7

## Objetivo
Demostrar que el núcleo del proyecto funciona de extremo a extremo y dejar evidencia suficiente para considerar el 50 % como funcional y validado.

## P-01 Arranque de infraestructura
1. Abrir Docker Desktop.
2. En `backend`, ejecutar `docker compose up --build -d`.
3. Verificar `http://localhost:8080/actuator/health`.

**Esperado:** PostgreSQL y backend aparecen saludables y el estado de la API es `UP`.

## P-02 Flujo automatizado de API
Desde PowerShell, ejecutar `scripts/validar-api.ps1`.

Valida:
- login con JWT;
- listado de centros;
- locales del centro;
- inquilino actual;
- instalaciones;
- obligaciones de mantenimiento;
- creación de un mantenimiento;
- finalización del mantenimiento;
- recálculo automático de la próxima fecha según periodicidad.

**Esperado:** mensaje `FACILITYHUB - VALIDACION API HITO 7 SUPERADA`.

## P-03 Aplicación Android
1. Abrir la carpeta `android` en Android Studio.
2. Esperar la sincronización Gradle.
3. Crear/empezar un emulador Android.
4. Ejecutar la app.
5. Iniciar sesión con:
   - `explotacion@facilityhub.local`
   - `TU_PASSWORD_DE_PRUEBA`
6. Abrir Centro Comercial Demo Madrid.
7. Abrir un local.
8. Comprobar inquilino, instalaciones y obligaciones.
9. Entrar en una obligación y crear un mantenimiento.

**Esperado:** la información mostrada procede del backend y el mantenimiento nuevo aparece en el histórico.

## P-04 Evidencias para la memoria
Capturar como mínimo:
1. Docker con ambos servicios activos.
2. Resultado verde del script `validar-api.ps1`.
3. Pantalla de login Android.
4. Listado de centros.
5. Detalle de un local con obligaciones.
6. Histórico de mantenimientos tras crear uno.

## Criterio para cerrar el 50 %
El hito se considera validado cuando P-01, P-02 y P-03 están superadas. P-04 documenta la evidencia para la entrega académica.
