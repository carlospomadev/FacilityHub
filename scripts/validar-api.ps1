# FacilityHub - Validacion automatica de la API (circuito vigente P6)
#
# Flujo: salud -> login (EXPLOTACION y ADMIN) -> centros -> locales -> inquilino -> instalaciones -> obligaciones
#        -> crear mantenimiento -> alta de proveedor y tecnico de prueba -> asignacion -> intervencion del tecnico
#        -> evidencia -> envio a validacion -> aprobacion por EXPLOTACION -> recalculo de la proxima fecha -> historial.
#
# ESCRIBE DATOS (mantenimiento, proveedor, tecnico, intervencion y documento de prueba). Usar SOLO contra una base de
# datos desechable o de pruebas, nunca contra datos reales. Barreras independientes (se rechaza por defecto):
#   1. el parametro -ConfirmarEntornoDesechable;
#   2. la URL debe ser http://localhost o http://127.0.0.1 en el PUERTO RESERVADO PARA PRUEBAS 18080. El backend
#      habitual (8080, conectado a la base principal) se rechaza: el backend desechable se arranca con SERVER_PORT=18080.
#
# Requisitos: Windows PowerShell 5.1 o PowerShell 7; backend en marcha. Las contrasenas se leen de variables de
# entorno y nunca se escriben en pantalla:
#   FACILITYHUB_TEST_PASSWORD   contrasena de explotacion@facilityhub.local
#   FACILITYHUB_ADMIN_PASSWORD  contrasena de admin@facilityhub.local
# La contrasena del tecnico de prueba se genera al azar en cada ejecucion.
#
# Uso:  .\scripts\validar-api.ps1 -ConfirmarEntornoDesechable [-Base http://127.0.0.1:18080]
#
# Limitacion: la ejecucion debe ser posterior a la ultima aprobada de la obligacion; el script elige una obligacion
# NHOOD sin aprobacion hoy y avisa si no la encuentra (por ejemplo, tras varias ejecuciones el mismo dia).

param(
    [string]$Base = "http://127.0.0.1:18080",
    [switch]$ConfirmarEntornoDesechable
)

$ErrorActionPreference = "Stop"

function Ok($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Fail($msg) { Write-Host "[ERROR] $msg" -ForegroundColor Red; exit 1 }

$PuertoPruebas = 18080

if (-not $ConfirmarEntornoDesechable) {
    Fail "Este script crea datos de prueba. Ejecutalo solo contra una base desechable y con -ConfirmarEntornoDesechable."
}
# Barrera independiente: solo un backend local en el puerto reservado para pruebas (nunca el 8080 habitual).
$uri = $null
if (-not [System.Uri]::TryCreate($Base, [System.UriKind]::Absolute, [ref]$uri)) { Fail "URL no valida: $Base" }
if ($uri.Scheme -ne "http" -or @("localhost", "127.0.0.1") -notcontains $uri.Host -or $uri.Port -ne $PuertoPruebas -or $uri.AbsolutePath -ne "/") {
    Fail "Destino no autorizado ($Base). Solo se admite http://localhost:$PuertoPruebas o http://127.0.0.1:$PuertoPruebas (backend desechable arrancado con SERVER_PORT=$PuertoPruebas)."
}
$Base = "http://$($uri.Host):$PuertoPruebas"
if (-not $env:FACILITYHUB_TEST_PASSWORD) { Fail "Falta la variable FACILITYHUB_TEST_PASSWORD (EXPLOTACION)" }
if (-not $env:FACILITYHUB_ADMIN_PASSWORD) { Fail "Falta la variable FACILITYHUB_ADMIN_PASSWORD (ADMIN)" }

function Api($metodo, $ruta, $token, $cuerpo) {
    $params = @{ Uri = "$Base$ruta"; Method = $metodo; Headers = @{ Authorization = "Bearer $token" } }
    if ($null -ne $cuerpo) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 5))
    }
    # En Windows PowerShell 5.1 Invoke-RestMethod emite un array JSON como un unico objeto: se asigna y se devuelve
    # para que se desenrolle y @(...) reciba los elementos (en PowerShell 7 el resultado es el mismo).
    $respuesta = Invoke-RestMethod @params
    return $respuesta
}

function Login($email, $password) {
    $cuerpo = @{ email = $email; password = $password } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$Base/api/auth/login" -Method Post -ContentType "application/json" -Body $cuerpo
    if (-not $r.token) { Fail "Login sin token para $email" }
    return $r.token
}

# Subida multipart compatible con Windows PowerShell 5.1 (sin -Form).
function SubirEvidencia($intervencionId, $token) {
    Add-Type -AssemblyName System.Net.Http
    $cliente = New-Object System.Net.Http.HttpClient
    try {
        $cliente.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)
        $contenido = New-Object System.Net.Http.MultipartFormDataContent
        $bytes = [System.Text.Encoding]::ASCII.GetBytes("%PDF-1.4`n% FacilityHub validacion automatica`n%%EOF`n")
        $archivo = New-Object System.Net.Http.ByteArrayContent(, $bytes)
        $archivo.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("application/pdf")
        $contenido.Add($archivo, "file", "evidencia-validacion.pdf")
        $respuesta = $cliente.PostAsync("$Base/api/intervenciones/$intervencionId/documentos?categoria=EVIDENCIA", $contenido).Result
        if (-not $respuesta.IsSuccessStatusCode) { Fail "No se pudo adjuntar la evidencia (HTTP $([int]$respuesta.StatusCode))" }
        return $respuesta.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    } finally {
        $cliente.Dispose()
    }
}

# 1. Salud
Info "Comprobando salud del backend en $Base ..."
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "$Base/actuator/health" -Method Get -TimeoutSec 3
        if ($health.status -eq "UP") { $healthy = $true; break }
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $healthy) { Fail "El backend no responde como UP" }
Ok "Backend disponible"

# 2. Login
$tExpl = Login "explotacion@facilityhub.local" $env:FACILITYHUB_TEST_PASSWORD
Ok "Login JWT como EXPLOTACION"
$tAdmin = Login "admin@facilityhub.local" $env:FACILITYHUB_ADMIN_PASSWORD
Ok "Login JWT como ADMIN"

# 3. Recorrido de consulta
$centros = @(Api Get "/api/centros" $tExpl $null)
if ($centros.Count -lt 1) { Fail "No hay centros" }
$centro = $centros[0]
Ok "Centros: $($centros.Count). Usando $($centro.nombre)"

$locales = @(Api Get "/api/centros/$($centro.id)/locales" $tExpl $null)
if ($locales.Count -lt 1) { Fail "No hay locales" }
$hoy = (Get-Date).ToString("yyyy-MM-dd")

# Primera obligacion NHOOD (la asigna EXPLOTACION) sin una ejecucion aprobada hoy.
$local = $null; $obligacion = $null
foreach ($l in $locales) {
    $obls = @(Api Get "/api/locales/$($l.id)/obligaciones" $tExpl $null)
    foreach ($o in $obls) {
        if ($o.responsable -eq "NHOOD" -and $o.estado -eq "ACTIVA") {
            $mants = @(Api Get "/api/obligaciones/$($o.id)/mantenimientos" $tExpl $null)
            $aprobadaHoy = $mants | Where-Object { $_.fechaRealizacion -eq $hoy -and $_.estado -eq "FINALIZADO" }
            if (-not $aprobadaHoy) { $local = $l; $obligacion = $o; break }
        }
    }
    if ($obligacion) { break }
}
if (-not $obligacion) { Fail "No hay una obligacion NHOOD activa disponible hoy (ya validada hoy o sin datos)" }
Ok "Local $($local.codigo). Obligacion: $($obligacion.tipoMantenimiento), proxima $($obligacion.proximaFecha)"

$inquilino = Api Get "/api/locales/$($local.id)/inquilino-actual" $tExpl $null
Ok "Inquilino actual: $($inquilino.nombreComercial)"
$instalaciones = @(Api Get "/api/locales/$($local.id)/instalaciones" $tExpl $null)
Ok "Instalaciones: $($instalaciones.Count)"

# 4. Mantenimiento
$nuevo = Api Post "/api/obligaciones/$($obligacion.id)/mantenimientos" $tExpl @{ fechaProgramada = $hoy; descripcion = "Validacion automatica API" }
if (-not $nuevo.id) { Fail "No se creo el mantenimiento" }
Ok "Mantenimiento creado: ID $($nuevo.id) ($($nuevo.estado))"

# 5. Proveedor y tecnico de prueba (ADMIN)
$sufijo = (Get-Date).ToString("yyyyMMddHHmmss")
$proveedor = Api Post "/api/proveedores" $tAdmin @{ razonSocial = "Proveedor validacion $sufijo" }
$emailTecnico = "tecnico.$sufijo@validacion.test"
$pwTecnico = "Val-" + [guid]::NewGuid().ToString("N").Substring(0, 16)
Api Post "/api/proveedores/$($proveedor.id)/usuarios" $tAdmin @{ nombre = "Tecnico"; apellidos = "Validacion"; email = $emailTecnico; password = $pwTecnico } | Out-Null
Ok "Proveedor y tecnico de prueba creados (ID proveedor $($proveedor.id))"

# 6. Asignacion (EXPLOTACION, obligacion NHOOD)
Api Put "/api/mantenimientos/$($nuevo.id)/asignacion" $tExpl @{ proveedorId = $proveedor.id } | Out-Null
Ok "Proveedor asignado al mantenimiento"

# 7. Intervencion del tecnico, evidencia y envio
$tTec = Login $emailTecnico $pwTecnico
$intervencion = Api Post "/api/mantenimientos/$($nuevo.id)/intervenciones" $tTec @{ fechaEjecucion = $hoy; actuaciones = "Revision realizada en la validacion automatica"; resultado = "CONFORME" }
Ok "Intervencion registrada: ID $($intervencion.id) ($($intervencion.estadoValidacion))"
$conDocumento = SubirEvidencia $intervencion.id $tTec
Ok "Evidencia adjuntada ($(@($conDocumento.documentos).Count) documento)"
$enviada = Api Post "/api/intervenciones/$($intervencion.id)/envio" $tTec @{ version = $conDocumento.version }
if ($enviada.estadoValidacion -ne "PENDIENTE_VALIDACION") { Fail "La intervencion no quedo pendiente de validacion" }
Ok "Intervencion enviada a validacion"

# 8. Validacion (EXPLOTACION)
$aprobada = Api Post "/api/intervenciones/$($intervencion.id)/validacion" $tExpl @{ accion = "APROBAR"; comentario = "Aprobada por la validacion automatica"; version = $enviada.version }
if ($aprobada.estadoValidacion -ne "APROBADO") { Fail "La intervencion no quedo APROBADA" }
Ok "Intervencion aprobada por EXPLOTACION"

# 9. Recalculo de la proxima fecha (regla por defecto: fecha de ejecucion + periodicidad)
$despues = @(Api Get "/api/locales/$($local.id)/obligaciones" $tExpl $null) | Where-Object { $_.id -eq $obligacion.id } | Select-Object -First 1
$esperada = ([datetime]::Parse($hoy)).AddMonths([int]$obligacion.periodicidadMeses).ToString("yyyy-MM-dd")
if ($despues.proximaFecha -ne $esperada) {
    Fail "La proxima fecha no se recalculo como se esperaba. Esperada $esperada, recibida $($despues.proximaFecha)"
}
Ok "Regla de periodicidad validada: nueva proxima fecha $($despues.proximaFecha)"

# 10. Historial del local
$historial = @(Api Get "/api/locales/$($local.id)/historial" $tExpl $null)
$entrada = $historial | Where-Object { $_.mantenimientoId -eq $nuevo.id -and $_.tipoRegistro -eq "APROBADO" }
if (-not $entrada) { Fail "El cumplimiento aprobado no aparece en el historial del local" }
Ok "Cumplimiento visible en el historial del local"

Write-Host ""
Write-Host "FACILITYHUB - VALIDACION API SUPERADA" -ForegroundColor Green
Write-Host "Flujo validado: salud -> login -> consulta -> mantenimiento -> asignacion -> intervencion -> evidencia -> envio -> aprobacion -> recalculo -> historial."
