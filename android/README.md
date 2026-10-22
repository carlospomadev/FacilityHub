# FacilityHub Android - Hito 6

Primera version de la aplicacion Android que consumira el backend de FacilityHub.

## Tecnologia

- Kotlin 2.4.21
- Jetpack Compose (BOM 2026.09.00)
- Android Gradle Plugin 9.4.0
- Retrofit 3.0.0
- Navigation Compose
- ViewModel + coroutines

## Pantallas implementadas a nivel de codigo

1. Login.
2. Listado de centros.
3. Listado de locales de un centro.
4. Detalle del local con inquilino, instalaciones y obligaciones.
5. Listado de mantenimientos de una obligacion.
6. Formulario basico para crear una ejecucion de mantenimiento.

## Conexion

En emulador Android el backend se configura como `http://10.0.2.2:8080/`, que corresponde al equipo anfitrion.

## Usuario demo recomendado

- Usuario: `explotacion@facilityhub.local`
- Clave: `TU_PASSWORD_DE_PRUEBA`

## Estado

El codigo de la app esta preparado, pero no se marca como validado en dispositivo/emulador porque el entorno de generacion actual no dispone de Android SDK ni Gradle/Android Studio. La siguiente actividad de validacion consiste en abrir el proyecto en Android Studio, sincronizar Gradle y probar el flujo contra el backend ejecutado localmente.
