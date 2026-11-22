# FacilityHub - Demo (FH-008B): utilidades Android (ADB, UI Automator, emulador, app). Requiere lib-demo.ps1.

$script:Pkg = "com.facilityhub.app"
$script:ClavesAnimacion = @("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

function Invoke-Adb([string[]]$argumentos) {
    $anterior = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    try { $o = & $script:Demo.Adb @argumentos 2>&1; return (($o | ForEach-Object { "$_" }) -join "`n") } finally { $ErrorActionPreference = $anterior }
}

function Test-EmuladorConectado { return ((Invoke-Adb @("devices")) -match "emulator-\d+\s+device") }

# ---------------- Emulador ----------------
# El emulador se registra como proceso de la demo (PID + instante de inicio) solo si lo arranca la demo.
function Start-EmuladorDemo {
    if (-not (Test-Path $script:Demo.Adb)) { Stop-Demo "No se encuentra adb en $($script:Demo.Adb)" }
    if (Test-EmuladorConectado) {
        $propio = Get-ProcesoDemoVivo "emulador"
        Write-Ok ("Emulador ya en marcha" + $(if ($propio) { " (arrancado por la demo)" } else { " (ajeno a la demo: no se detendra)" }))
    } else {
        if (-not (Test-Path $script:Demo.Emulador)) { Stop-Demo "No se encuentra el emulador en $($script:Demo.Emulador)" }
        Write-Paso "Arrancando el emulador $($script:Demo.Avd)"
        $p = Start-Process -FilePath $script:Demo.Emulador -ArgumentList @("-avd", $script:Demo.Avd, "-no-snapshot-save", "-no-boot-anim") -WindowStyle Minimized -PassThru
        Add-ProcesoEstado "emulador" $p
        Invoke-Adb @("wait-for-device") | Out-Null
    }
    for ($i = 0; $i -lt 120; $i++) {
        if ((Invoke-Adb @("shell", "getprop", "sys.boot_completed")).Trim() -eq "1") { Write-Ok "Emulador listo"; return }
        Start-Sleep -Seconds 2
    }
    Stop-Demo "El emulador no termino de arrancar"
}

function Stop-EmuladorDemo {
    $p = Get-ProcesoDemoVivo "emulador"
    if (-not $p) { Remove-ProcesoEstado "emulador"; return }
    Restore-AnimacionesDemo
    Invoke-Adb @("emu", "kill") | Out-Null
    if (-not $p.WaitForExit(30000)) { Stop-Process -Id $p.Id -Force }
    # Espera a que el emulador desaparezca de adb (sus procesos auxiliares tardan unos segundos en cerrarse).
    for ($i = 0; $i -lt 30 -and (Test-EmuladorConectado); $i++) { Start-Sleep -Seconds 1 }
    Remove-ProcesoEstado "emulador"
    Write-Ok "Emulador detenido (lo habia arrancado la demo)"
}

# ---------------- Animaciones (solo durante las pruebas automaticas) ----------------
# Los valores originales se guardan en estado.json: si el proceso muere sin pasar por 'finally', 'Detener' los restaura.
function Disable-AnimacionesDemo {
    $e = Read-Estado
    if (-not ($e.PSObject.Properties.Name -contains "animacionesOriginales") -or -not $e.animacionesOriginales) {
        $orig = [ordered]@{}
        foreach ($k in $script:ClavesAnimacion) { $orig[$k] = (Invoke-Adb @("shell", "settings", "get", "global", $k)).Trim() }
        $e | Add-Member -NotePropertyName animacionesOriginales -NotePropertyValue ([pscustomobject]$orig) -Force
        Save-Estado $e
    }
    foreach ($k in $script:ClavesAnimacion) { Invoke-Adb @("shell", "settings", "put", "global", $k, "0") | Out-Null }
    Write-Ok "Animaciones del emulador desactivadas durante las pruebas (originales guardadas)"
}

function Restore-AnimacionesDemo {
    $e = Read-Estado
    if (-not ($e.PSObject.Properties.Name -contains "animacionesOriginales") -or -not $e.animacionesOriginales) { return }
    if (-not (Test-EmuladorConectado)) { return }   # sin emulador no se puede restaurar; se conserva para la proxima vez
    foreach ($k in $script:ClavesAnimacion) {
        $v = [string]$e.animacionesOriginales.$k
        if ($v -and $v -ne "null") { Invoke-Adb @("shell", "settings", "put", "global", $k, $v) | Out-Null }
        else { Invoke-Adb @("shell", "settings", "delete", "global", $k) | Out-Null }
    }
    $e.animacionesOriginales = $null
    Save-Estado $e
    Write-Ok "Animaciones del emulador restauradas"
}

# ---------------- App ----------------
function Install-AppDemo {
    Write-Paso "Compilando e instalando la app"
    Push-Location (Join-Path $script:Demo.Repo "android")
    try { $rc = Invoke-Nativo (Join-Path $script:Demo.Repo "android\gradlew.bat") @("--offline", "-q", ":app:assembleDebug") (Join-Path $script:Demo.Logs "gradle.log") } finally { Pop-Location }
    if ($rc -ne 0) { Stop-Demo "La compilacion Android fallo (ver logs\gradle.log)" }
    $apk = Join-Path $script:Demo.Repo "android\app\build\outputs\apk\debug\app-debug.apk"
    if ((Invoke-Adb @("install", "-r", $apk)) -notmatch "Success") { Stop-Demo "No se pudo instalar el APK" }
    Write-Ok "App instalada"
}

# Arranca la app sin sesion (datos borrados) y la deja en la pantalla de login.
function Start-AppEnLogin {
    Invoke-Adb @("shell", "pm", "clear", $script:Pkg) | Out-Null
    Invoke-Adb @("shell", "monkey", "-p", $script:Pkg, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null
    Start-Sleep -Seconds 4
    if (Wait-Texto "Allow" 6) { Invoke-Tap "Allow" | Out-Null }
    return [bool](Wait-Texto "Iniciar sesi*" 30)
}

# ---------------- UI Automator ----------------
function Get-Nodos {
    $doc = $null
    for ($intento = 0; $intento -lt 3; $intento++) {
        Invoke-Adb @("shell", "uiautomator", "dump", "/sdcard/fh-ui.xml") | Out-Null
        $raw = Invoke-Adb @("shell", "cat", "/sdcard/fh-ui.xml")
        $inicio = $raw.IndexOf("<?xml")
        if ($inicio -ge 0) {
            try { $doc = [xml]$raw.Substring($inicio); break } catch { Start-Sleep -Milliseconds 700 }
        } else { Start-Sleep -Milliseconds 700 }
    }
    if (-not $doc) { return @() }
    $lista = foreach ($n in $doc.SelectNodes("//node")) {
        if ($n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            [pscustomobject]@{
                texto = if ($n.text) { $n.text } else { $n.'content-desc' }
                clase = $n.class; password = ($n.password -eq "true"); focused = ($n.focused -eq "true")
                x = [int](([int]$matches[1] + [int]$matches[3]) / 2); y = [int](([int]$matches[2] + [int]$matches[4]) / 2)
            }
        }
    }
    return @($lista)
}

function Find-Nodo([string]$patron, [int]$indice = 0, $nodos = $null) {
    if (-not $nodos) { $nodos = Get-Nodos }
    $hits = @($nodos | Where-Object { $_.texto -and $_.texto -like $patron })
    if ($hits.Count -gt $indice) { return $hits[$indice] }
    return $null
}

function Wait-Texto([string]$patron, [int]$segundos = 25) {
    $fin = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $fin) {
        $n = Find-Nodo $patron
        if ($n) { return $n }
        Start-Sleep -Milliseconds 800
    }
    return $null
}

function Invoke-Tap([string]$patron, [int]$indice = 0, [int]$espera = 1500) {
    $n = Find-Nodo $patron $indice
    if (-not $n) { return $false }
    Invoke-Adb @("shell", "input", "tap", "$($n.x)", "$($n.y)") | Out-Null
    Start-Sleep -Milliseconds $espera
    return $true
}

# Busca un texto desplazando la pantalla hacia abajo si no esta visible.
function Find-ConScroll([string]$patron, [int]$desplazamientos = 5) {
    for ($k = 0; $k -le $desplazamientos; $k++) {
        $n = Find-Nodo $patron
        if ($n) { return $n }
        Invoke-Adb @("shell", "input", "swipe", "540", "1900", "540", "900", "400") | Out-Null
        Start-Sleep -Milliseconds 900
    }
    return $null
}

function Invoke-TapScroll([string]$patron, [int]$espera = 1500) {
    $n = Find-ConScroll $patron
    if (-not $n) { return $false }
    Invoke-Adb @("shell", "input", "tap", "$($n.x)", "$($n.y)") | Out-Null
    Start-Sleep -Milliseconds $espera
    return $true
}

function Invoke-Volver { Invoke-Tap "Volver" | Out-Null; Start-Sleep -Milliseconds 800 }
