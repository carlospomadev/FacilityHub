$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"

function Ok($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Fail($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red; exit 1 }

Info "Comprobando salud del backend..."
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "$base/actuator/health" -Method Get -TimeoutSec 3
        if ($health.status -eq "UP") { $healthy = $true; break }
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $healthy) { Fail "El backend no responde como UP" }
Ok "Backend disponible"

if (-not $env:FACILITYHUB_TEST_PASSWORD) {
    Fail "Falta la variable FACILITYHUB_TEST_PASSWORD"
}

$loginBody = @{
    email = "explotacion@facilityhub.local"
    password = $env:FACILITYHUB_TEST_PASSWORD
} | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
if (-not $login.token) { Fail "Login sin token" }
$headers = @{ Authorization = "Bearer $($login.token)" }
Ok "Login JWT como EXPLOTACION"

$centros = @(Invoke-RestMethod -Uri "$base/api/centros" -Headers $headers -Method Get)
if ($centros.Count -lt 1) { Fail "No hay centros" }
$centro = $centros[0]
Ok "Centros: $($centros.Count). Usando $($centro.nombre)"

$locales = @(Invoke-RestMethod -Uri "$base/api/centros/$($centro.id)/locales" -Headers $headers -Method Get)
if ($locales.Count -lt 1) { Fail "No hay locales" }
$local = $locales[0]
Ok "Locales: $($locales.Count). Usando $($local.codigo)"

$inquilino = Invoke-RestMethod -Uri "$base/api/locales/$($local.id)/inquilino-actual" -Headers $headers -Method Get
Ok "Inquilino actual: $($inquilino.nombreComercial)"

$instalaciones = @(Invoke-RestMethod -Uri "$base/api/locales/$($local.id)/instalaciones" -Headers $headers -Method Get)
if ($instalaciones.Count -lt 1) { Fail "No hay instalaciones" }
Ok "Instalaciones: $($instalaciones.Count)"

$obligaciones = @(Invoke-RestMethod -Uri "$base/api/locales/$($local.id)/obligaciones" -Headers $headers -Method Get)
if ($obligaciones.Count -lt 1) { Fail "No hay obligaciones" }
$obligacion = $obligaciones[0]
$proximaAntes = [datetime]::Parse($obligacion.proximaFecha)
Ok "Obligacion: $($obligacion.tipoMantenimiento), proxima $($obligacion.proximaFecha)"

$fecha = (Get-Date).ToString("yyyy-MM-dd")
$nuevoBody = @{ fechaProgramada = $fecha; descripcion = "Prueba automatizada Hito 7" } | ConvertTo-Json
$nuevo = Invoke-RestMethod -Uri "$base/api/obligaciones/$($obligacion.id)/mantenimientos" -Headers $headers -Method Post -ContentType "application/json" -Body $nuevoBody
if (-not $nuevo.id) { Fail "No se creo el mantenimiento" }
Ok "Mantenimiento creado: ID $($nuevo.id)"

$finalBody = @{ estado = "FINALIZADO"; fechaRealizacion = $fecha; observaciones = "Validacion automatizada" } | ConvertTo-Json
$finalizado = Invoke-RestMethod -Uri "$base/api/mantenimientos/$($nuevo.id)/estado" -Headers $headers -Method Patch -ContentType "application/json" -Body $finalBody
if ($finalizado.estado -ne "FINALIZADO") { Fail "No se finalizo el mantenimiento" }
Ok "Mantenimiento finalizado"

$obligacionesDespues = @(Invoke-RestMethod -Uri "$base/api/locales/$($local.id)/obligaciones" -Headers $headers -Method Get)
$actualizada = $obligacionesDespues | Where-Object { $_.id -eq $obligacion.id } | Select-Object -First 1
if (-not $actualizada) { Fail "No se encontro la obligacion despues de finalizar" }
$esperada = ([datetime]::Parse($fecha)).AddMonths([int]$obligacion.periodicidadMeses).ToString("yyyy-MM-dd")
if ($actualizada.proximaFecha -ne $esperada) {
    Fail "La proxima fecha no se recalculo correctamente. Esperada $esperada, recibida $($actualizada.proximaFecha)"
}
Ok "Regla de periodicidad validada: nueva proxima fecha $($actualizada.proximaFecha)"

Write-Host ""
Write-Host "FACILITYHUB - VALIDACION API HITO 7 SUPERADA" -ForegroundColor Green
Write-Host "Flujo validado: salud -> login -> centros -> locales -> inquilino -> instalaciones -> obligaciones -> crear mantenimiento -> finalizar -> recalcular fecha."
