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

## Próximo hito

- Validar aplicación Android.
- Integrar Android con API REST.
- Login móvil.
- Visualización de centros.
- Visualización de locales.
- Continuar segundo 50 % del proyecto.
