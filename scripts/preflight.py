from pathlib import Path
import re, sys
root = Path(__file__).resolve().parents[1]
backend = root / "backend"
android = root / "android"
checks = []

def check(name, cond, detail=""):
    checks.append((name, bool(cond), detail))

# Archivos esenciales
for rel in [
    "pom.xml", "Dockerfile", "docker-compose.yml",
    "src/main/resources/application.yml",
    "src/main/resources/db/migration/V1__crear_modelo_inicial.sql",
    "src/main/resources/db/migration/V2__cargar_datos_demo.sql",
    "src/main/resources/db/migration/V3__crear_usuarios_demo.sql",
]:
    check(f"backend/{rel}", (backend / rel).exists())
for rel in [
    "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/facilityhub/app/data/FacilityApi.kt",
]:
    check(f"android/{rel}", (android / rel).exists())

pom = (backend / "pom.xml").read_text(encoding="utf-8")
app_yml = (backend / "src/main/resources/application.yml").read_text(encoding="utf-8")
security = (backend / "src/main/java/com/facilityhub/security/SecurityConfig.java").read_text(encoding="utf-8")
auth = (backend / "src/main/java/com/facilityhub/api/AuthController.java").read_text(encoding="utf-8")
api = (android / "app/src/main/java/com/facilityhub/app/data/FacilityApi.kt").read_text(encoding="utf-8")
network = (android / "app/src/main/java/com/facilityhub/app/data/NetworkProvider.kt").read_text(encoding="utf-8")
manifest = (android / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
compose = (backend / "docker-compose.yml").read_text(encoding="utf-8")

check("Java 21 configurado", "<java.version>21</java.version>" in pom)
check("Spring Boot 4.1.1 configurado", "<version>4.1.1</version>" in pom)
check("PostgreSQL configurado", "jdbc:postgresql://" in app_yml and "postgresql" in pom)
check("Flyway configurado", "flyway" in pom and "flyway:" in app_yml)
check("JWT configurado", "JwtDecoder" in security and "JwtEncoder" in security)
check("Login publicado", '@PostMapping("/login")' in auth)
check("Rol EXPLOTACION", '"ADMIN", "EXPLOTACION"' in security)
check("Android usa localhost del host via emulador", '10.0.2.2:8080' in network)
check("Android permite trafico HTTP local", 'usesCleartextTraffic="true"' in manifest)
check("Docker contiene backend y postgres", "backend:" in compose and "postgres:" in compose)

required_routes = [
    'api/auth/login', 'api/centros', 'api/centros/{id}/locales',
    'api/locales/{id}/inquilino-actual', 'api/locales/{id}/instalaciones',
    'api/locales/{id}/obligaciones', 'api/obligaciones/{id}/mantenimientos'
]
for route in required_routes:
    check(f"Ruta Android {route}", route in api)

# Comprobar que los endpoints equivalentes existen en controladores (por fragmentos estables)
controller_text = "\n".join(p.read_text(encoding="utf-8") for p in (backend / "src/main/java/com/facilityhub/api").glob("*Controller.java"))
for frag in [
    '/api/auth', '/api/centros', '/api/locales', '/api/obligaciones', '/api/mantenimientos'
]:
    check(f"Backend publica {frag}", frag in controller_text)

failed = [c for c in checks if not c[1]]
lines = ["FacilityHub Hito 7 - Preflight", "="*34]
for name, ok, detail in checks:
    lines.append(f"[{'OK' if ok else 'FAIL'}] {name}" + (f" - {detail}" if detail else ""))
lines += ["", f"Resultado: {len(checks)-len(failed)}/{len(checks)} comprobaciones superadas."]
report = "\n".join(lines) + "\n"
(root / "docs/RESULTADO_PREFLIGHT.txt").write_text(report, encoding="utf-8")
print(report)
sys.exit(1 if failed else 0)
