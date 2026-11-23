# FacilityHub - Demo (FH-008B): funciones comunes. Se carga con ". .\lib-demo.ps1".
# Todo el estado de la demo vive en %LOCALAPPDATA%\FacilityHubDemo (fuera del repositorio). Nunca se imprimen contrasenas.

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ---------------- Configuracion ----------------
$script:Demo = [ordered]@{
    Raiz          = Join-Path $env:LOCALAPPDATA "FacilityHubDemo"
    BaseDatos     = "facilityhub_demo"
    PuertoBD      = 55440
    PuertoApi     = 18080          # validaciones (mismo puerto reservado que scripts/validar-api.ps1)
    PuertoAndroid = 8080           # el emulador usa 10.0.2.2:8080
    PgBin         = "C:\Program Files\PostgreSQL\18\bin"
    Repo          = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    Adb           = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    Emulador      = Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe"
    Avd           = "Pixel_6"
}
$script:Demo.PgData       = Join-Path $script:Demo.Raiz "pgdata"
$script:Demo.Logs         = Join-Path $script:Demo.Raiz "logs"
$script:Demo.Storage      = Join-Path $script:Demo.Raiz "storage"
$script:Demo.Documentos   = Join-Path $script:Demo.Raiz "documentos"
$script:Demo.Resultados   = Join-Path $script:Demo.Raiz "resultados"
$script:Demo.Capturas     = Join-Path $script:Demo.Raiz "capturas"
$script:Demo.Marcador     = Join-Path $script:Demo.Raiz ".facilityhub-demo"
$script:Demo.Estado       = Join-Path $script:Demo.Raiz "estado.json"
$script:Demo.Credenciales = Join-Path $script:Demo.Raiz "credenciales-demo.json"
$script:Demo.Manifiesto   = Join-Path $script:Demo.Raiz "manifiesto-demo.json"
$script:Demo.Classpath    = Join-Path $script:Demo.Raiz "classpath.txt"

# Bases que nunca puede usar la demo (produccion y pruebas existentes).
$script:BasesProhibidas = @("facilityhub", "facilityhub_fh005_p3", "postgres", "template0", "template1")

# ---------------- Mensajes ----------------
function Write-Paso($m) { Write-Host "[PASO] $m" -ForegroundColor Cyan }
function Write-Ok($m)   { Write-Host "[OK]   $m" -ForegroundColor Green }
function Write-Aviso($m){ Write-Host "[AVISO] $m" -ForegroundColor Yellow }
function Stop-Demo($m, [int]$codigo = 1) { Write-Host "[ERROR] $m" -ForegroundColor Red; exit $codigo }

# ---------------- Ejecucion de programas nativos ----------------
# Ejecuta un programa nativo con su salida redirigida a un archivo (o descartada) y devuelve el codigo de salida, sin que
# el texto en stderr se convierta en excepcion (comportamiento de Windows PowerShell 5.1 con ErrorActionPreference=Stop).
function Invoke-Nativo([string]$exe, [string[]]$argumentos, [string]$log = $null) {
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($log) { & $exe @argumentos *> $log } else { & $exe @argumentos *> $null }
        return $LASTEXITCODE
    } finally { $ErrorActionPreference = $anterior }
}

# ---------------- Salvaguardas ----------------
function Assert-BaseDemo([string]$nombre) {
    if ($script:BasesProhibidas -contains $nombre.ToLowerInvariant()) { Stop-Demo "Base '$nombre' prohibida para la demo." }
    if ($nombre -notmatch '^facilityhub_demo$') { Stop-Demo "La demo solo admite la base 'facilityhub_demo' (recibido '$nombre')." }
}

function Test-PuertoLibre([int]$puerto) {
    $c = @(Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue)
    return ($c.Count -eq 0)
}

function Test-RaizPropia {
    # La raiz solo se considera de la demo si contiene el marcador creado por esta automatizacion.
    return (Test-Path $script:Demo.Marcador)
}

function Initialize-Raiz {
    if (Test-Path $script:Demo.Raiz) {
        if (-not (Test-RaizPropia)) { Stop-Demo "$($script:Demo.Raiz) existe pero no la creo esta automatizacion (sin marcador). No se toca." }
    } else {
        New-Item -ItemType Directory -Path $script:Demo.Raiz | Out-Null
        Set-Content -Path $script:Demo.Marcador -Value ("Creado por scripts/demo de FacilityHub el " + (Get-Date -Format s)) -Encoding ASCII
    }
    foreach ($d in @($script:Demo.Logs, $script:Demo.Storage, $script:Demo.Documentos, $script:Demo.Resultados, $script:Demo.Capturas)) {
        if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d | Out-Null }
    }
}

function Assert-Herramientas {
    foreach ($exe in @("initdb.exe", "pg_ctl.exe", "psql.exe")) {
        if (-not (Test-Path (Join-Path $script:Demo.PgBin $exe))) { Stop-Demo "No se encuentra $exe en $($script:Demo.PgBin) (PostgreSQL 18)." }
    }
    foreach ($cmd in @("java", "mvn")) {
        if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) { Stop-Demo "No se encuentra '$cmd' en el PATH." }
    }
}

# ---------------- Estado (PIDs de procesos creados por la demo) ----------------
function Read-Estado {
    if (Test-Path $script:Demo.Estado) { return (Get-Content -Raw $script:Demo.Estado | ConvertFrom-Json) }
    return [pscustomobject]@{ procesos = @() ; emuladorIniciadoPorDemo = $false }
}
function Save-Estado($estado) { $estado | ConvertTo-Json -Depth 5 | Set-Content -Path $script:Demo.Estado -Encoding UTF8 }

function Add-ProcesoEstado([string]$nombre, [System.Diagnostics.Process]$p) {
    $e = Read-Estado
    $lista = @($e.procesos | Where-Object { $_.nombre -ne $nombre })
    $lista += [pscustomobject]@{ nombre = $nombre; pid = $p.Id; inicio = $p.StartTime.ToString("o") }
    $e.procesos = $lista
    Save-Estado $e
}

# Devuelve el proceso registrado por la demo si sigue vivo y es EXACTAMENTE el mismo (PID e instante de inicio);
# un PID reutilizado por otro programa no coincide en el instante de inicio y no se toca.
function Get-ProcesoDemoVivo([string]$nombre) {
    $reg = @((Read-Estado).procesos | Where-Object { $_.nombre -eq $nombre }) | Select-Object -First 1
    if (-not $reg) { return $null }
    $p = Get-Process -Id $reg.pid -ErrorAction SilentlyContinue
    if ($p -and $p.StartTime.ToString("o") -eq $reg.inicio) { return $p }
    return $null
}

function Remove-ProcesoEstado([string]$nombre) {
    $e = Read-Estado
    $e.procesos = @($e.procesos | Where-Object { $_.nombre -ne $nombre })
    Save-Estado $e
}

function Stop-ProcesoDemo([string]$nombre) {
    $p = Get-ProcesoDemoVivo $nombre
    if ($p) {
        Stop-Process -Id $p.Id -Force
        Write-Ok "Proceso '$nombre' (PID $($p.Id)) detenido"
    }
    Remove-ProcesoEstado $nombre
}

# ---------------- PostgreSQL ----------------
function Invoke-Psql([string]$sql, [string]$base = $script:Demo.BaseDatos) {
    # El SQL entra por la entrada estandar (no queda en la linea de comandos).
    # El exito se decide por el codigo de salida: en Windows PowerShell 5.1 cualquier texto en stderr (p. ej. NOTICE)
    # se convertiria en excepcion con ErrorActionPreference=Stop.
    $psql = Join-Path $script:Demo.PgBin "psql.exe"
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $env:PGOPTIONS = "-c client_min_messages=warning"
    try {
        $salida = ("SET client_min_messages = warning;`n" + $sql) | & $psql -h 127.0.0.1 -p $script:Demo.PuertoBD -U postgres -d $base -X -q -A -t -v ON_ERROR_STOP=1 2>&1
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $anterior
        Remove-Item Env:PGOPTIONS -ErrorAction SilentlyContinue
    }
    $texto = (($salida | ForEach-Object { "$_" }) -join "`n").Trim()
    if ($codigo -ne 0) { Stop-Demo ("Fallo de SQL: " + ($texto -split "`n")[0]) }
    return $texto
}

function Test-ClusterActivo {
    $pgctl = Join-Path $script:Demo.PgBin "pg_ctl.exe"
    return ((Invoke-Nativo $pgctl @("-D", $script:Demo.PgData, "status")) -eq 0)
}

function Start-ClusterDemo {
    $pgctl = Join-Path $script:Demo.PgBin "pg_ctl.exe"
    if (-not (Test-Path (Join-Path $script:Demo.PgData "PG_VERSION"))) {
        if (-not (Test-PuertoLibre $script:Demo.PuertoBD)) { Stop-Demo "El puerto $($script:Demo.PuertoBD) esta ocupado por otro servicio." }
        Write-Paso "Creando cluster PostgreSQL desechable en $($script:Demo.PgData)"
        $rc = Invoke-Nativo (Join-Path $script:Demo.PgBin "initdb.exe") @("-A", "trust", "-U", "postgres", "-E", "UTF8", "--locale=C", "-D", $script:Demo.PgData) (Join-Path $script:Demo.Logs "initdb.log")
        if ($rc -ne 0) { Stop-Demo "initdb fallo (ver logs\initdb.log)" }
        Set-Content -Path (Join-Path $script:Demo.PgData ".facilityhub-demo") -Value "cluster de la demo" -Encoding ASCII
    } elseif (-not (Test-Path (Join-Path $script:Demo.PgData ".facilityhub-demo"))) {
        Stop-Demo "El directorio de datos existe sin el marcador de la demo. No se toca."
    }
    if (Test-ClusterActivo) { Write-Ok "Cluster demo ya activo (puerto $($script:Demo.PuertoBD))"; return }
    if (-not (Test-PuertoLibre $script:Demo.PuertoBD)) { Stop-Demo "El puerto $($script:Demo.PuertoBD) esta ocupado por otro servicio." }
    # pg_ctl se lanza con su propia consola oculta y se espera SOLO a pg_ctl: si se redirige su salida, el proceso
    # postgres hereda esos descriptores y PowerShell se queda esperando indefinidamente.
    $argumentos = @("-D", "`"$($script:Demo.PgData)`"", "-o", "`"-p $($script:Demo.PuertoBD) -h 127.0.0.1`"",
                    "-l", "`"$(Join-Path $script:Demo.Logs 'postgres.log')`"", "-w", "start")
    $p = Start-Process -FilePath $pgctl -ArgumentList $argumentos -WindowStyle Hidden -PassThru
    $p.WaitForExit()
    if (-not (Test-ClusterActivo)) { Stop-Demo "No se pudo arrancar el cluster demo (ver logs\postgres.log)" }
    Write-Ok "Cluster demo activo en 127.0.0.1:$($script:Demo.PuertoBD)"
}

function Stop-ClusterDemo {
    if (-not (Test-Path (Join-Path $script:Demo.PgData ".facilityhub-demo"))) { return }
    if (Test-ClusterActivo) {
        Invoke-Nativo (Join-Path $script:Demo.PgBin "pg_ctl.exe") @("-D", $script:Demo.PgData, "-m", "fast", "-w", "stop") | Out-Null
        Write-Ok "Cluster demo detenido"
    }
}

# El servidor que escucha en el puerto de la demo debe ser exactamente el cluster de la demo.
function Assert-ClusterPropio {
    $dir = Invoke-Psql "SHOW data_directory;" "postgres"
    # GetFullPath normaliza '/' a '\' en Windows; -ne no distingue mayusculas. [char]92 = '\'.
    $esperado = [System.IO.Path]::GetFullPath($script:Demo.PgData).TrimEnd([char]92)
    $real = [System.IO.Path]::GetFullPath($dir).TrimEnd([char]92)
    if (-not $dir -or $real -ne $esperado) { Stop-Demo "El servidor en el puerto $($script:Demo.PuertoBD) no es el cluster de la demo. No se toca." }
}

function Reset-BaseDemo {
    Assert-BaseDemo $script:Demo.BaseDatos
    Assert-ClusterPropio
    Write-Paso "Recreando la base $($script:Demo.BaseDatos) (solo en el cluster demo)"
    Invoke-Psql "DROP DATABASE IF EXISTS $($script:Demo.BaseDatos) WITH (FORCE);" "postgres" | Out-Null
    Invoke-Psql "DO `$`$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'facilityhub') THEN CREATE ROLE facilityhub LOGIN; END IF; END `$`$;" "postgres" | Out-Null
    Invoke-Psql "CREATE DATABASE $($script:Demo.BaseDatos) OWNER facilityhub;" "postgres" | Out-Null
    Write-Ok "Base $($script:Demo.BaseDatos) vacia creada"
}

# ---------------- Backend ----------------
function Initialize-Backend {
    $backend = Join-Path $script:Demo.Repo "backend"
    Write-Paso "Compilando el backend (Maven, modo offline)"
    Push-Location $backend
    try {
        $rc = Invoke-Nativo "mvn.cmd" @("-q", "-o", "compile") (Join-Path $script:Demo.Logs "maven-compile.log")
        if ($rc -ne 0) { Stop-Demo "mvn compile fallo (ver logs\maven-compile.log). Hace falta la cache de Maven con las dependencias." }
        $rc = Invoke-Nativo "mvn.cmd" @("-q", "-o", "dependency:build-classpath", "-Dmdep.includeScope=runtime", "-Dmdep.outputFile=$($script:Demo.Classpath)") (Join-Path $script:Demo.Logs "maven-classpath.log")
        if ($rc -ne 0) { Stop-Demo "No se pudo calcular el classpath (ver logs\maven-classpath.log)" }
    } finally { Pop-Location }
    Write-Ok "Backend compilado"
}

function Get-SecretoJwtDemo {
    $b = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    return [Convert]::ToBase64String($b)
}

function Start-BackendDemo([int]$puerto) {
    $nombre = "backend-$puerto"
    Assert-BaseDemo $script:Demo.BaseDatos
    $url = "http://127.0.0.1:$puerto"
    $h = $null
    try { $h = Invoke-RestMethod -Uri "$url/actuator/health" -TimeoutSec 2; $responde = $true } catch { $responde = $false }
    $vivo = Get-ProcesoDemoVivo $nombre
    if ($responde) {
        if ($vivo) {
            Assert-AislamientoBackend $puerto $vivo.Id
            Write-Ok "Backend demo ya activo en $puerto"; return
        }
        Stop-Demo "El puerto $puerto lo usa un servicio que no creo la demo. No se toca."
    }
    if (-not (Test-PuertoLibre $puerto)) { Stop-Demo "El puerto $puerto esta ocupado por otro servicio. No se toca." }
    $cp = "$(Join-Path $script:Demo.Repo 'backend\target\classes');" + (Get-Content -Raw $script:Demo.Classpath).Trim()
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$($script:Demo.PuertoBD)/$($script:Demo.BaseDatos)?ApplicationName=facilityhub-demo-$puerto"
    $env:DB_USERNAME = "facilityhub"
    $env:DB_PASSWORD = "demo-local-trust"   # NO es un secreto: el cluster desechable usa autenticacion 'trust' solo en 127.0.0.1
    $env:JWT_SECRET = Get-SecretoJwtDemo
    $env:SERVER_PORT = "$puerto"
    $env:SERVER_ADDRESS = "127.0.0.1"   # solo loopback (el emulador llega por 10.0.2.2 -> 127.0.0.1 del anfitrion)
    $env:FACILITYHUB_STORAGE_ROOT = $script:Demo.Storage
    $env:FACILITYHUB_RECORDATORIOS_ENABLED = "false"
    # FH-010B: la base demo es desechable y Assert-BaseDemo ya ha verificado su nombre; solo aqui se pide V10 de forma
    # explicita (por defecto el backend se queda en V9 para no migrar por accidente la base principal).
    $env:FACILITYHUB_FLYWAY_TARGET = "10"
    $log = Join-Path $script:Demo.Logs "$nombre.log"
    Write-Paso "Arrancando backend demo en el puerto $puerto"
    $p = Start-Process -FilePath "java" -ArgumentList @("-cp", "`"$cp`"", "com.facilityhub.FacilityHubApplication") `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden -PassThru
    foreach ($v in @("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "SERVER_PORT", "SERVER_ADDRESS", "FACILITYHUB_STORAGE_ROOT", "FACILITYHUB_RECORDATORIOS_ENABLED")) { Remove-Item "Env:$v" -ErrorAction SilentlyContinue }
    Add-ProcesoEstado $nombre $p
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        if ($p.HasExited) { Stop-Demo "El backend ($puerto) termino al arrancar (ver logs\$nombre.log)" }
        try { $h = Invoke-RestMethod -Uri "$url/actuator/health" -TimeoutSec 2; if ($h.status -eq "UP") { break } } catch { }
    }
    if (-not $h -or $h.status -ne "UP") { Stop-Demo "El backend ($puerto) no respondio UP a tiempo" }
    Assert-AislamientoBackend $puerto $p.Id
    Write-Ok "Backend demo UP en $url"
}

# Comprueba la conexion EFECTIVA del proceso backend: solo con el cluster demo y la base demo, nunca con 5432.
function Assert-AislamientoBackend([int]$puerto, [int]$procId) {
    $conDemo = @(Get-NetTCPConnection -OwningProcess $procId -RemotePort $script:Demo.PuertoBD -State Established -ErrorAction SilentlyContinue)
    $conPrincipal = @(Get-NetTCPConnection -OwningProcess $procId -RemotePort 5432 -ErrorAction SilentlyContinue)
    if ($conDemo.Count -lt 1) { Stop-ProcesoDemo "backend-$puerto"; Stop-Demo "El backend ($puerto) no tiene conexiones con el cluster demo. Detenido por seguridad." }
    if ($conPrincipal.Count -gt 0) { Stop-ProcesoDemo "backend-$puerto"; Stop-Demo "El backend ($puerto) esta conectado al puerto 5432. Detenido por seguridad." }
    $sesiones = Invoke-Psql "SELECT count(*) FROM pg_stat_activity WHERE application_name = 'facilityhub-demo-$puerto' AND datname = '$($script:Demo.BaseDatos)';" "postgres"
    if ([int]$sesiones -lt 1) { Stop-ProcesoDemo "backend-$puerto"; Stop-Demo "No hay sesiones del backend ($puerto) en la base demo. Detenido por seguridad." }
    Write-Ok "Aislamiento verificado: PID $procId -> 127.0.0.1:$($script:Demo.PuertoBD)/$($script:Demo.BaseDatos) ($sesiones sesiones; 0 conexiones a 5432)"
}

# ---------------- Credenciales (archivo local protegido) ----------------
function New-Contrasena([int]$longitud = 20) {
    $alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".ToCharArray()
    $b = New-Object byte[] $longitud
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    return (-join ($b | ForEach-Object { $alfabeto[$_ % $alfabeto.Length] }))
}

function Protect-ArchivoUsuario([string]$ruta) {
    $usuario = "$env:USERDOMAIN\$env:USERNAME"
    # Sin herencia, solo el usuario actual (se retiran SYSTEM *S-1-5-18 y Administradores *S-1-5-32-544).
    $rc = Invoke-Nativo "icacls.exe" @($ruta, "/inheritance:r", "/grant:r", "${usuario}:(R,W)", "/remove:g", "*S-1-5-18", "*S-1-5-32-544")
    if ($rc -ne 0) { Stop-Demo "No se pudo restringir el acceso a $ruta" }
}

function Get-CredencialesDemo([switch]$Regenerar) {
    if ((Test-Path $script:Demo.Credenciales) -and -not $Regenerar) {
        return (Get-Content -Raw $script:Demo.Credenciales | ConvertFrom-Json)
    }
    $cuentas = @(
        [pscustomobject]@{ clave = "admin";       rol = "ADMIN";       email = "admin@facilityhub.local";             descripcion = "Administrador de demostracion" },
        [pscustomobject]@{ clave = "explotacion"; rol = "EXPLOTACION"; email = "explotacion@facilityhub.local";       descripcion = "Responsable de Explotacion" },
        [pscustomobject]@{ clave = "inquilinoA";  rol = "INQUILINO";   email = "inquilino@facilityhub.local";         descripcion = "Inquilino A - Restaurante Demo (L-101)" },
        [pscustomobject]@{ clave = "inquilinoB";  rol = "INQUILINO";   email = "inquilino.b@demo.facilityhub.test";   descripcion = "Inquilino B - Tienda Demo (L-205)" },
        [pscustomobject]@{ clave = "proveedor";   rol = "PROVEEDOR";   email = "proveedor@demo.facilityhub.test";     descripcion = "Responsable de la empresa Mantenimientos Demo S.L." },
        [pscustomobject]@{ clave = "tecnico";     rol = "PROVEEDOR";   email = "tecnico@demo.facilityhub.test";       descripcion = "Tecnico de Mantenimientos Demo S.L." }
    )
    foreach ($c in $cuentas) { $c | Add-Member -NotePropertyName password -NotePropertyValue (New-Contrasena) }
    $doc = [pscustomobject]@{ generado = (Get-Date -Format s); aviso = "Credenciales SOLO para la demo desechable. No compartir ni versionar."; cuentas = $cuentas }
    if (Test-Path $script:Demo.Credenciales) { Remove-Item $script:Demo.Credenciales -Force }
    New-Item -ItemType File -Path $script:Demo.Credenciales | Out-Null
    Protect-ArchivoUsuario $script:Demo.Credenciales
    $doc | ConvertTo-Json -Depth 4 | Set-Content -Path $script:Demo.Credenciales -Encoding UTF8
    Write-Ok "Credenciales nuevas generadas en $($script:Demo.Credenciales) (solo lectura del usuario actual)"
    return $doc
}

function Get-Cuenta($cred, [string]$clave) { return @($cred.cuentas | Where-Object { $_.clave -eq $clave })[0] }

# ---------------- API ----------------
function Invoke-Api([string]$metodo, [string]$ruta, [string]$token, $cuerpo = $null, [int]$puerto = $script:Demo.PuertoApi) {
    $params = @{ Uri = "http://127.0.0.1:$puerto$ruta"; Method = $metodo; Headers = @{} }
    if ($token) { $params.Headers.Authorization = "Bearer $token" }
    if ($null -ne $cuerpo) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 6))
    }
    $r = Invoke-RestMethod @params
    return $r   # asignar y devolver: desenrolla los arrays JSON en Windows PowerShell 5.1
}

# Devuelve el codigo HTTP (y el cuerpo, si lo hay) sin lanzar excepciones.
function Invoke-ApiCodigo([string]$metodo, [string]$ruta, [string]$token, $cuerpo = $null, [int]$puerto = $script:Demo.PuertoApi) {
    Add-Type -AssemblyName System.Net.Http
    $c = New-Object System.Net.Http.HttpClient
    try {
        $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::new($metodo), "http://127.0.0.1:$puerto$ruta")
        if ($token) { $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token) }
        if ($null -ne $cuerpo) { $req.Content = New-Object System.Net.Http.StringContent(($cuerpo | ConvertTo-Json -Depth 6), [System.Text.Encoding]::UTF8, "application/json") }
        $resp = $c.SendAsync($req).Result
        $bytes = $resp.Content.ReadAsByteArrayAsync().Result
        return [pscustomobject]@{ codigo = [int]$resp.StatusCode; bytes = $bytes }
    } finally { $c.Dispose() }
}

function Get-TokenDemo($cred, [string]$clave, [int]$puerto = $script:Demo.PuertoApi) {
    $c = Get-Cuenta $cred $clave
    $r = Invoke-Api POST "/api/auth/login" $null @{ email = $c.email; password = $c.password } $puerto
    if (-not $r.token) { Stop-Demo "Login sin token para la cuenta '$clave'" }
    return $r.token
}

function Send-Multipart([string]$ruta, [string]$token, [string]$archivo, [int]$puerto = $script:Demo.PuertoApi) {
    Add-Type -AssemblyName System.Net.Http
    $c = New-Object System.Net.Http.HttpClient
    try {
        $c.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)
        $mp = New-Object System.Net.Http.MultipartFormDataContent
        $contenido = New-Object System.Net.Http.ByteArrayContent(, [System.IO.File]::ReadAllBytes($archivo))
        $contenido.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("application/pdf")
        $mp.Add($contenido, "file", [System.IO.Path]::GetFileName($archivo))
        $resp = $c.PostAsync("http://127.0.0.1:$puerto$ruta", $mp).Result
        if (-not $resp.IsSuccessStatusCode) { Stop-Demo "Subida fallida $ruta (HTTP $([int]$resp.StatusCode))" }
        return ($resp.Content.ReadAsStringAsync().Result | ConvertFrom-Json)
    } finally { $c.Dispose() }
}

# ---------------- Fechas (Europe/Madrid) y PDF sinteticos ----------------
function Get-HoyMadrid {
    $tz = [System.TimeZoneInfo]::FindSystemTimeZoneById("Romance Standard Time")
    return [System.TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $tz).Date
}
function Format-Fecha([datetime]$d) { return $d.ToString("yyyy-MM-dd") }

# PDF minimo valido de una pagina con varias lineas de texto (ASCII).
function New-PdfSintetico([string]$ruta, [string[]]$lineas) {
    $texto = "BT /F1 12 Tf 50 780 Td 16 TL "
    foreach ($l in $lineas) { $texto += "(" + ($l -replace '([\\()])', '\$1') + ") Tj T* " }
    $texto += "ET"
    $objs = @(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        "<< /Length $($texto.Length) >>`nstream`n$texto`nendstream",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    )
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("%PDF-1.4`n")
    $offsets = @()
    for ($i = 0; $i -lt $objs.Count; $i++) {
        $offsets += $sb.Length
        [void]$sb.Append("$($i + 1) 0 obj`n$($objs[$i])`nendobj`n")
    }
    $xref = $sb.Length
    [void]$sb.Append("xref`n0 $($objs.Count + 1)`n0000000000 65535 f `n")
    foreach ($o in $offsets) { [void]$sb.Append(("{0:D10} 00000 n `n" -f $o)) }
    [void]$sb.Append("trailer`n<< /Size $($objs.Count + 1) /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n")
    [System.IO.File]::WriteAllBytes($ruta, [System.Text.Encoding]::ASCII.GetBytes($sb.ToString()))
    return (Get-FileHash -Algorithm SHA256 -Path $ruta).Hash.ToLowerInvariant()
}
