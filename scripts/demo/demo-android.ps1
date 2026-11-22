# FacilityHub - Demo (FH-008B): recorridos Android automatizados (ADB + UI Automator) contra la demo.
# - Arranca un backend demo en 8080 (el emulador usa 10.0.2.2:8080) solo si el puerto esta libre y verifica su aislamiento.
# - Usa el emulador Pixel_6 (lo arranca si no hay ninguno y lo registra para poder detenerlo despues).
# - No captura pantallas de login ni registra contrasenas o tokens. Capturas en %LOCALAPPDATA%\FacilityHubDemo\capturas.
# - Desactiva las animaciones del emulador durante las pruebas y las restaura SIEMPRE al terminar (try/finally; si el
#   proceso muere, 'Detener' las restaura a partir de estado.json).
# - MODIFICA datos de la demo (EXPLOTACION aprueba A2; PROVEEDOR reenvia C1): despues conviene '-Accion Reiniciar'.
# -Roles permite pruebas dirigidas: ADMIN, EXPLOTACION, PROVEEDOR, INQUILINO_A, INQUILINO_B (por defecto, todos).

param([string[]]$Roles = @("ADMIN", "EXPLOTACION", "PROVEEDOR", "INQUILINO_A", "INQUILINO_B"))

. (Join-Path $PSScriptRoot "lib-demo.ps1")
. (Join-Path $PSScriptRoot "lib-android.ps1")

$script:Pasos = New-Object System.Collections.ArrayList
function Test-Rol([string]$r) { return ($Roles -contains $r) }

# ---------------- Resultados ----------------
function Add-Paso([string]$rol, [string]$id, [string]$descripcion, [bool]$ok, [string]$detalle = "") {
    [void]$script:Pasos.Add([pscustomobject]@{ rol = $rol; id = $id; descripcion = $descripcion; ok = $ok; detalle = $detalle })
    if ($ok) { Write-Host "[OK]    $rol $id $descripcion $detalle" -ForegroundColor Green } else { Write-Host "[FALLO] $rol $id $descripcion $detalle" -ForegroundColor Red }
}


# Escribe en un campo concreto tras comprobar que tiene el foco y que es (o no) un campo de contrasena.
function Set-Campo([int]$indice, [string]$valor, [bool]$esPassword) {
    $campos = @(Get-Nodos | Where-Object { $_.clase -like "*EditText" })
    if ($campos.Count -le $indice) { return $false }
    $c = $campos[$indice]
    Invoke-Adb @("shell", "input", "tap", "$($c.x)", "$($c.y)") | Out-Null
    Start-Sleep -Milliseconds 600
    # El teclado desplaza la pantalla: se comprueba que el campo con foco es el mismo (por posicion en la lista).
    $tras = @(Get-Nodos | Where-Object { $_.clase -like "*EditText" })
    $idxFoco = -1
    for ($k = 0; $k -lt $tras.Count; $k++) { if ($tras[$k].focused) { $idxFoco = $k } }
    if ($idxFoco -ne $indice -or $tras[$idxFoco].password -ne $esPassword) { return $false }
    Invoke-Adb @("shell", "input", "keycombination", "113", "29") | Out-Null   # Ctrl+A
    Invoke-Adb @("shell", "input", "keyevent", "67") | Out-Null                 # Supr
    Invoke-Adb @("shell", "input", "text", $valor.Replace(" ", "%s")) | Out-Null
    Invoke-Adb @("shell", "input", "keyevent", "111") | Out-Null                # cierra el teclado
    Start-Sleep -Milliseconds 500
    return $true
}

# Captura solo si la pantalla no es la de login (nunca se fotografian credenciales).
function Save-Captura([string]$nombre) {
    if (Find-Nodo "Iniciar sesi*") { Write-Aviso "Captura '$nombre' omitida: pantalla de login"; return }
    Invoke-Adb @("shell", "screencap", "-p", "/sdcard/fh-captura.png") | Out-Null
    Invoke-Adb @("pull", "/sdcard/fh-captura.png", (Join-Path $script:Demo.Capturas "$nombre.png")) | Out-Null
    Invoke-Adb @("shell", "rm", "/sdcard/fh-captura.png") | Out-Null
}

# FH-009 / R3: cierre de sesion desde el menu de cuenta de las pantallas principales (con confirmacion).
function Invoke-CerrarSesion {
    if (-not (Invoke-Tap "Men? de cuenta" 0 1000)) { return $false }
    if (-not (Invoke-Tap "Cerrar sesi*" 0 1000)) { return $false }
    if (Wait-Texto "?Cerrar sesi*" 5) { Invoke-Tap "Cerrar sesi*" 0 2000 | Out-Null }
    return [bool](Wait-Texto "Iniciar sesi*" 10)
}

function Invoke-Login($cred, [string]$clave) {
    $c = Get-Cuenta $cred $clave
    # Cerrar una sesion previa si la hay (desde un detalle se vuelve primero a la pantalla principal).
    for ($i = 0; $i -lt 4 -and -not (Find-Nodo "Iniciar sesi*"); $i++) {
        if (-not (Invoke-CerrarSesion)) { Invoke-Tap "Volver" | Out-Null }
    }
    if (-not (Wait-Texto "Iniciar sesi*" 10)) { return $false }
    if (-not (Set-Campo 0 $c.email $false)) { return $false }
    $email = @(Get-Nodos | Where-Object { $_.clase -like "*EditText" })[0]
    if ($email.texto -ne $c.email) { return $false }
    if (-not (Set-Campo 1 $c.password $true)) { return $false }
    Invoke-Tap "Iniciar sesi*" 0 2500 | Out-Null
    # FH-009 / R3: pantalla inicial por rol (gestion: Panel de Explotacion; INQUILINO: Mis obligaciones).
    $destino = switch ($c.rol) { "PROVEEDOR" { "Mis trabajos" } "INQUILINO" { "Mis obligaciones" } default { "Panel de Explotaci*" } }
    return [bool](Wait-Texto $destino 20)
}

# ---------------- Preparacion ----------------
if (-not (Test-Path $script:Demo.Manifiesto)) { Stop-Demo "No hay demo construida. Ejecuta '-Accion Construir'." 2 }
$cred = Get-CredencialesDemo
$man = Get-Content -Raw $script:Demo.Manifiesto | ConvertFrom-Json

Start-BackendDemo $script:Demo.PuertoAndroid
Start-EmuladorDemo
Install-AppDemo
Invoke-Adb @("logcat", "-c", "-b", "crash") | Out-Null
Get-ChildItem $script:Demo.Capturas -Filter *.png -ErrorAction SilentlyContinue | Remove-Item -Force
Disable-AnimacionesDemo
try {
if (-not (Start-AppEnLogin)) { Stop-Demo "La app no llego a la pantalla de login" }

# ---------------- ADMIN ----------------
if (Test-Rol "ADMIN") {
$ok = Invoke-Login $cred "admin"
Add-Paso "ADMIN" "AND-ADM-01" "login" $ok
if ($ok) {
    # FH-009 / R3: ADMIN entra en el Panel; los accesos de gestion se reparten entre la barra inferior (Panel, Centros,
    # Proveedores, Avisos) y los accesos de la pestana Centros (Validaciones, Declarados, Solicitudes).
    Invoke-Tap "Centros" 0 2500 | Out-Null
    $nodos = Get-Nodos
    $menu = @("Panel", "Centros", "Proveedores", "Avisos", "Validaciones", "Declarados", "Solicitudes") | Where-Object { -not (Find-Nodo $_ 0 $nodos) }
    Add-Paso "ADMIN" "AND-ADM-02" "menu de gestion completo (Panel, Centros, Proveedores, Avisos, Validaciones, Declarados, Solicitudes)" ($menu.Count -eq 0) "faltan: $($menu -join ',')"
    Save-Captura "admin-01-centros"
    Invoke-Tap "Proveedores" | Out-Null
    Add-Paso "ADMIN" "AND-ADM-03" "lista de proveedores con Mantenimientos Demo S.L." ([bool](Wait-Texto "Mantenimientos Demo S.L.*")) ""
    Save-Captura "admin-02-proveedores"
    Invoke-Tap "Centros" 0 2500 | Out-Null
    Invoke-Tap "Centro Comercial Demo Madrid" | Out-Null
    Add-Paso "ADMIN" "AND-ADM-04" "datos del centro: locales L-101 y L-205" ([bool](Wait-Texto "L-101*") -and [bool](Find-Nodo "L-205*")) ""
    Save-Captura "admin-03-locales"
    Invoke-Volver
}

}

# ---------------- EXPLOTACION ----------------
if (Test-Rol "EXPLOTACION") {
$ok = Invoke-Login $cred "explotacion"
Add-Paso "EXPLOTACION" "AND-EXP-01" "login" $ok
if ($ok) {
    Invoke-Tap "Panel de Explotaci*" 0 3000 | Out-Null
    $okPanel = [bool](Wait-Texto "Datos a $($man.hoyMadrid)*")
    Add-Paso "EXPLOTACION" "AND-EXP-02" "panel abierto con fecha de hoy" $okPanel ""
    Add-Paso "EXPLOTACION" "AND-EXP-03" "vencidos: revision electrica L-205 y climatizacion L-101" ([bool](Wait-Texto "*retraso*") -and [bool](Find-Nodo "*L-205*")) ""
    Save-Captura "explotacion-01-panel-vencidos"
    # Acceso a un mantenimiento (vencido sin proveedor).
    Invoke-Tap "*L-205*" 0 2500 | Out-Null
    Add-Paso "EXPLOTACION" "AND-EXP-04" "gestion del mantenimiento vencido sin proveedor" ([bool](Wait-Texto "Asignaci*")) ""
    Save-Captura "explotacion-02-mantenimiento"
    Invoke-Volver
    Invoke-Tap "Pr?ximos" | Out-Null
    Add-Paso "EXPLOTACION" "AND-EXP-05" "proximos: incluye zona comun" ([bool](Wait-Texto "*Zona com*")) ""
    Save-Captura "explotacion-03-proximos"
    Invoke-Tap "Pend. validaci*" | Out-Null
    Add-Paso "EXPLOTACION" "AND-EXP-06" "pendientes de validacion: revision PCI enviada" ([bool](Wait-Texto "Finalizado (enviado)*")) ""
    Save-Captura "explotacion-04-pendientes"
    Invoke-Tap "Finalizado (enviado)*" 0 3000 | Out-Null
    $okDoc = [bool](Wait-Texto "Intervenci*") -and [bool](Find-ConScroll "Certificado-PCI-L101*")
    Save-Captura "explotacion-05-intervencion"
    $check = $null
    if (Find-ConScroll "Compartir con el inquilino*") { $check = @(Get-Nodos | Where-Object { $_.clase -like "*CheckBox" }) | Select-Object -First 1 }
    if ($check) { Invoke-Adb @("shell", "input", "tap", "$($check.x)", "$($check.y)") | Out-Null; Start-Sleep -Milliseconds 700 }
    $okInt = [bool](Find-ConScroll "Aprobar")
    Add-Paso "EXPLOTACION" "AND-EXP-07" "consulta de la intervencion pendiente con sus documentos" ($okDoc -and $okInt) "certificado marcado para compartir: $([bool]$check)"
    if ($okInt) {
        Invoke-Tap "Aprobar" | Out-Null
        if (Wait-Texto "Confirmar" 8) { Invoke-Tap "Confirmar" 0 3000 | Out-Null }
        $tExpl = Get-TokenDemo $cred "explotacion"
        $estado = (Invoke-Api GET "/api/intervenciones/$($man.intervenciones.A2)" $tExpl).estadoValidacion
        Add-Paso "EXPLOTACION" "AND-EXP-08" "aprobacion desde la app (certificado compartido)" ($estado -eq "APROBADO") "estado en el backend: $estado"
        Save-Captura "explotacion-06-aprobada"
    }
    Invoke-Volver; Invoke-Volver
    # FH-009 / R3: el Panel es la pantalla inicial; los centros estan en su pestana.
    if (-not (Find-Nodo "Centro Comercial Demo Madrid")) { Invoke-Tap "Centros" 0 2500 | Out-Null }
    Invoke-Tap "Centro Comercial Demo Madrid" | Out-Null
    Invoke-Tap "Restaurante Demo" 0 2500 | Out-Null
    Invoke-TapScroll "Historial de mantenimientos" 3000 | Out-Null
    $okHist = [bool](Wait-Texto "Cumplimiento aprobado")
    Save-Captura "explotacion-07-historial"
    $okP5 = [bool](Find-ConScroll "Hist?rico P5*")
    Save-Captura "explotacion-08-historial-p5"
    Add-Paso "EXPLOTACION" "AND-EXP-09" "historial del local con aprobados y registro P5 sin validacion" ($okHist -and $okP5) ""
    Invoke-Volver; Invoke-Volver; Invoke-Volver
}

}

# ---------------- PROVEEDOR (tecnico) ----------------
if (Test-Rol "PROVEEDOR") {
$ok = Invoke-Login $cred "proveedor"
Add-Paso "PROVEEDOR" "AND-PRO-01" "login del responsable de la empresa" $ok
$ok = Invoke-Login $cred "tecnico"
Add-Paso "PROVEEDOR" "AND-PRO-02" "login del tecnico" $ok
if ($ok) {
    Add-Paso "PROVEEDOR" "AND-PRO-03" "trabajos asignados (incluye el devuelto para correccion)" ([bool](Wait-Texto "*Requiere correcci*")) ""
    Save-Captura "proveedor-01-mis-trabajos"
    Invoke-Tap "*Requiere correcci*" 0 3000 | Out-Null
    Invoke-TapScroll "*Requiere correcci*" 3000 | Out-Null
    $okDocP = [bool](Find-ConScroll "Parte-climatizacion*")
    Save-Captura "proveedor-02-intervencion"
    $okEnv = [bool](Find-ConScroll "Enviar a validaci*")
    Add-Paso "PROVEEDOR" "AND-PRO-04" "intervencion con evidencia y comentario de correccion" ($okEnv -and $okDocP) ""
    if ($okEnv) {
        Invoke-Tap "Enviar a validaci*" | Out-Null
        if (Wait-Texto "Enviar" 8) { Invoke-Tap "Enviar" 0 3000 | Out-Null }
        $tTec = Get-TokenDemo $cred "tecnico"
        $estado = (Invoke-Api GET "/api/intervenciones/$($man.intervenciones.C1)" $tTec).estadoValidacion
        Add-Paso "PROVEEDOR" "AND-PRO-05" "reenvio a validacion desde la app" ($estado -eq "PENDIENTE_VALIDACION") "estado en el backend: $estado"
        Save-Captura "proveedor-03-reenviada"
    }
    Invoke-Volver; Invoke-Volver
}

}

# ---------------- INQUILINO A ----------------
if (Test-Rol "INQUILINO_A") {
$ok = Invoke-Login $cred "inquilinoA"
Add-Paso "INQUILINO A" "AND-INQ-01" "login" $ok
if ($ok) {
    Invoke-Tap "Mis obligaciones*" 0 3000 | Out-Null
    $okObl = [bool](Wait-Texto "Historial del local L-101*")
    Add-Paso "INQUILINO A" "AND-INQ-02" "Mis obligaciones solo con su local (sin L-205)" ($okObl -and -not (Find-Nodo "*L-205*")) ""
    Save-Captura "inquilinoA-01-obligaciones"
    Invoke-Tap "Historial del local L-101*" 0 3000 | Out-Null
    $okH = [bool](Wait-Texto "Cumplimiento aprobado*")
    $nodos = Get-Nodos
    $sinInternos = -not (Find-Nodo "Parte-*" 0 $nodos) -and -not (Find-Nodo "Acta-*" 0 $nodos) -and -not (Find-Nodo "*L205*" 0 $nodos) -and -not (Find-Nodo "Hist?rico P5*" 0 $nodos)
    Add-Paso "INQUILINO A" "AND-INQ-03" "historial con su certificado compartido" ($okH -and [bool](Find-Nodo $man.documentos.certA1.nombre 0 $nodos)) ""
    Add-Paso "INQUILINO A" "AND-INQ-04" "sin documentos internos, P5 ni datos de otro inquilino" $sinInternos ""
    Save-Captura "inquilinoA-02-historial"
    # Descarga real del certificado (selector de Android) y verificacion SHA-256.
    $nombre = $man.documentos.certA1.nombre
    Invoke-Adb @("shell", "rm", "-f", "/sdcard/Download/$nombre") | Out-Null
    $fila = Find-Nodo $nombre
    $boton = @(Get-Nodos | Where-Object { $_.texto -eq "Descargar" } | Sort-Object { [math]::Abs($_.y - $fila.y) }) | Select-Object -First 1
    if ($boton) { Invoke-Adb @("shell", "input", "tap", "$($boton.x)", "$($boton.y)") | Out-Null; Start-Sleep -Seconds 3 }
    if (Wait-Texto "SAVE" 10) { Invoke-Tap "SAVE" 0 3000 | Out-Null } elseif (Wait-Texto "Guardar" 3) { Invoke-Tap "Guardar" 0 3000 | Out-Null }
    $okDesc = [bool](Wait-Texto "Documento descargado*" 15)
    $local = Join-Path $script:Demo.Resultados "descarga-android-$nombre"
    Invoke-Adb @("pull", "/sdcard/Download/$nombre", $local) | Out-Null
    $sha = if (Test-Path $local) { (Get-FileHash -Algorithm SHA256 $local).Hash.ToLowerInvariant() } else { "" }
    Add-Paso "INQUILINO A" "AND-INQ-05" "descarga del certificado compartido desde la app" ($okDesc -and $sha -eq $man.documentos.certA1.sha256) "SHA-256 coincide: $($sha -eq $man.documentos.certA1.sha256)"
    Save-Captura "inquilinoA-03-descarga"
    Invoke-Adb @("shell", "rm", "-f", "/sdcard/Download/$nombre") | Out-Null
    Invoke-Volver; Invoke-Volver
}

}

# ---------------- INQUILINO B ----------------
if (Test-Rol "INQUILINO_B") {
$ok = Invoke-Login $cred "inquilinoB"
Add-Paso "INQUILINO B" "AND-INQ-06" "login" $ok
if ($ok) {
    Invoke-Tap "Mis obligaciones*" 0 3000 | Out-Null
    Invoke-Tap "Historial del local L-205*" 0 3000 | Out-Null
    $nodos = Get-Nodos
    Add-Paso "INQUILINO B" "AND-INQ-07" "solo ve su certificado (L-205) y nada de L-101" ([bool](Find-Nodo $man.documentos.certB1.nombre 0 $nodos) -and -not (Find-Nodo "*L101*" 0 $nodos)) ""
    Save-Captura "inquilinoB-01-historial"
    Invoke-Volver; Invoke-Volver
}

# ---------------- Cierre ----------------
}

# ---------------- Cierre ----------------
# Solo cuentan los cierres del proceso de la app (el propio UI Automator puede registrar los suyos si otra herramienta
# lo usa a la vez).
$crashes = @((Invoke-Adb @("logcat", "-d", "-b", "crash")) -split "`n" | Where-Object { $_ -match "Process: $([regex]::Escape($script:Pkg))" }).Count
Add-Paso "APP" "AND-APP-01" "sin cierres inesperados de la app" ($crashes -eq 0) "cierres de $($script:Pkg): $crashes"
Invoke-CerrarSesion | Out-Null
Invoke-Adb @("shell", "pm", "clear", $script:Pkg) | Out-Null
} finally {
    Restore-AnimacionesDemo
}


$ok = @($script:Pasos | Where-Object { $_.ok }).Count
$fallos = @($script:Pasos | Where-Object { -not $_.ok }).Count
$sello = Get-Date -Format "yyyyMMdd-HHmmss"
$script:Pasos | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $script:Demo.Resultados "android-$sello.json") -Encoding UTF8
$md = @("# Validacion Android de la demo ($sello)", "", "Resultado: $ok correctas, $fallos fallidas", "", "| Rol | ID | Paso | Resultado | Detalle |", "|---|---|---|---|---|")
$md += $script:Pasos | ForEach-Object { "| $($_.rol) | $($_.id) | $($_.descripcion) | $(if ($_.ok) { 'OK' } else { 'FALLO' }) | $($_.detalle) |" }
$md | Set-Content -Path (Join-Path $script:Demo.Resultados "android-$sello.md") -Encoding UTF8
Write-Host ""
Write-Host "RESULTADO ANDROID: $ok correctas, $fallos fallidas. Capturas: $($script:Demo.Capturas)" -ForegroundColor $(if ($fallos -eq 0) { "Green" } else { "Red" })
if ((Test-Rol "EXPLOTACION") -or (Test-Rol "PROVEEDOR")) {
    Write-Aviso "Los recorridos han modificado datos (A2 aprobada, C1 reenviada). Para volver al estado inicial: -Accion Reiniciar"
}
if ($fallos -gt 0) { exit 1 } else { exit 0 }
