# FacilityHub - Demostracion reproducible (FH-008B). Comando principal.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\demo\demo.ps1 -Accion <accion>
#
# Presentar ante el tribunal:
#   PrepararPresentacion  Reconstruye la demo en su ESTADO INICIAL (como 'Construir'), arranca el backend 18080 y el
#                         backend 8080 del emulador, comprueba conectividad, aislamiento, cuentas y estados de los datos,
#                         arranca el emulador Pixel_6, instala la app y la deja ABIERTA EN LA PANTALLA DE LOGIN.
#                         No ejecuta recorridos automaticos ni modifica el escenario.
# Pruebas automaticas (modifican datos; despues, 'Reiniciar'):
#   Validar     Comprobaciones de la API (solo lectura). -ConCicloCompleto anade scripts\validar-api.ps1 (escribe).
#   Android     Recorridos por rol con ADB/UI Automator (45-90 min). -Roles limita los roles a probar.
# Mantenimiento del escenario:
#   Construir   Cluster demo + base facilityhub_demo recreada + Flyway V1-V9 + backend 18080 + cuentas y datos.
#   Reiniciar   Vuelve al estado inicial (equivale a 'Construir'; los backends previos de la demo se detienen).
#   Estado      Recursos activos de la demo.
#   Detener     Detiene SOLO lo creado por la demo (backends, emulador si lo arranco la demo, cluster) y restaura las
#               animaciones del emulador si quedaron desactivadas.
#   Eliminar    Detener + borra %LOCALAPPDATA%\FacilityHubDemo (solo si tiene el marcador de la demo).
#
# Todo el estado vive en %LOCALAPPDATA%\FacilityHubDemo. Credenciales en credenciales-demo.json (solo el usuario actual);
# nunca se imprimen. -RegenerarCredenciales crea otras nuevas.

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("PrepararPresentacion", "Construir", "Validar", "Android", "Reiniciar", "Estado", "Detener", "Eliminar")]
    [string]$Accion,
    [switch]$ConCicloCompleto,
    [switch]$RegenerarCredenciales,
    [string[]]$Roles = @("ADMIN", "EXPLOTACION", "PROVEEDOR", "INQUILINO_A", "INQUILINO_B")
)

. (Join-Path $PSScriptRoot "lib-demo.ps1")
. (Join-Path $PSScriptRoot "lib-android.ps1")
. (Join-Path $PSScriptRoot "demo-datos.ps1")

function Invoke-Construir {
    Assert-Herramientas
    Initialize-Raiz
    Start-ClusterDemo
    Stop-ProcesoDemo "backend-$($script:Demo.PuertoApi)"
    Stop-ProcesoDemo "backend-$($script:Demo.PuertoAndroid)"
    Reset-BaseDemo
    if (Test-Path $script:Demo.Storage) { Get-ChildItem $script:Demo.Storage -Force | Remove-Item -Recurse -Force }
    Get-ChildItem $script:Demo.Documentos -Force -ErrorAction SilentlyContinue | Remove-Item -Force
    Initialize-Backend
    Start-BackendDemo $script:Demo.PuertoApi
    $migraciones = Invoke-Psql "SELECT string_agg(version, ',' ORDER BY installed_rank) || ' / ok=' || bool_and(success) FROM facilityhub.flyway_schema_history;"
    if ($migraciones -notmatch '^1,2,3,4,5,6,7,8,9 / ok=t') { Stop-Demo "Migraciones inesperadas: $migraciones" }
    Write-Ok "Flyway: versiones $migraciones"
    $cred = Get-CredencialesDemo -Regenerar:$RegenerarCredenciales
    Invoke-SemillaDemo $cred
    Write-Host ""
    Write-Host "DEMO CONSTRUIDA. API: http://127.0.0.1:$($script:Demo.PuertoApi)  Credenciales: $($script:Demo.Credenciales)" -ForegroundColor Green
}

function Invoke-PrepararPresentacion {
    $resumen = New-Object System.Collections.ArrayList
    $anota = { param([string]$c, [bool]$ok, [string]$d = "") [void]$resumen.Add([pscustomobject]@{ c = $c; ok = $ok; d = $d }) }

    Invoke-Construir
    Start-BackendDemo $script:Demo.PuertoAndroid
    $cred = Get-CredencialesDemo
    $man = Get-Content -Raw $script:Demo.Manifiesto | ConvertFrom-Json

    # Conectividad y aislamiento de los dos backends.
    foreach ($puerto in @($script:Demo.PuertoApi, $script:Demo.PuertoAndroid)) {
        $h = $null; try { $h = Invoke-RestMethod -Uri "http://127.0.0.1:$puerto/actuator/health" -TimeoutSec 5 } catch { }
        $p = Get-ProcesoDemoVivo "backend-$puerto"
        & $anota "Backend $puerto UP y conectado solo a la base demo" ($h -and $h.status -eq "UP" -and $p) "127.0.0.1:$puerto -> 127.0.0.1:$($script:Demo.PuertoBD)/$($script:Demo.BaseDatos)"
    }
    # Cuentas: login real de las seis (por el backend del emulador, el que usara la app).
    foreach ($c in $cred.cuentas) {
        $r = Invoke-ApiCodigo POST "/api/auth/login" $null @{ email = $c.email; password = $c.password } $script:Demo.PuertoAndroid
        & $anota "Cuenta $($c.rol) $($c.email) autentica" ($r.codigo -eq 200) "HTTP $($r.codigo)"
    }
    # Estados iniciales del escenario.
    $tExpl = Get-TokenDemo $cred "explotacion" $script:Demo.PuertoAndroid
    $esperados = [ordered]@{ A1 = "APROBADO"; B1 = "APROBADO"; A2 = "PENDIENTE_VALIDACION"; C1 = "REQUIERE_CORRECCION" }
    foreach ($k in $esperados.Keys) {
        $estado = (Invoke-Api GET "/api/intervenciones/$($man.intervenciones.$k)" $tExpl $null $script:Demo.PuertoAndroid).estadoValidacion
        & $anota "Intervencion $k en estado inicial $($esperados[$k])" ($estado -eq $esperados[$k]) "estado: $estado"
    }
    $res = Invoke-Api GET "/api/gestion/resumen" $tExpl $null $script:Demo.PuertoAndroid
    & $anota "Panel inicial: vencidos 2, proximos 2, pend. validacion 1, correccion 1, sin asignar 2" `
        ($res.vencidos -eq 2 -and $res.proximos -eq 2 -and $res.pendientesValidacion -eq 1 -and $res.requierenCorreccion -eq 1 -and $res.sinAsignar -eq 2) `
        "$($res.vencidos)/$($res.proximos)/$($res.pendientesValidacion)/$($res.requierenCorreccion)/$($res.sinAsignar)"

    # Emulador y app abierta en el login (sin recorridos automaticos ni cambios de datos).
    Start-EmuladorDemo
    Install-AppDemo
    $enLogin = Start-AppEnLogin
    $campos = @(Get-Nodos | Where-Object { $_.clase -like "*EditText" })
    $pwVacio = (@($campos | Where-Object { $_.password -and $_.texto -and $_.texto -ne "Contrasena" -and $_.texto -notlike "Contrase*" }).Count -eq 0)
    & $anota "App instalada y abierta en la pantalla de login (sin sesion iniciada)" ($enLogin -and $pwVacio) ""

    Write-Host ""
    Write-Host "RESUMEN DE PREPARACION (sin contrasenas)" -ForegroundColor Cyan
    foreach ($x in $resumen) {
        $txt = if ($x.d) { "$($x.c) ($($x.d))" } else { $x.c }
        if ($x.ok) { Write-Host "  [OK]    $txt" -ForegroundColor Green } else { Write-Host "  [FALLO] $txt" -ForegroundColor Red }
    }
    $fallos = @($resumen | Where-Object { -not $_.ok }).Count
    $sello = Get-Date -Format "yyyyMMdd-HHmmss"
    $resumen | ConvertTo-Json -Depth 3 | Set-Content -Path (Join-Path $script:Demo.Resultados "presentacion-$sello.json") -Encoding UTF8
    Write-Host ""
    if ($fallos -eq 0) {
        Write-Host "LISTO PARA PRESENTAR: app en el login; credenciales en $($script:Demo.Credenciales)" -ForegroundColor Green
        Write-Host "Al terminar: demo.ps1 -Accion Detener   (o -Accion Reiniciar para volver al estado inicial)" -ForegroundColor Green
        $script:CodigoSalida = 0
        return
    }
    Write-Host "PREPARACION CON $fallos COMPROBACIONES FALLIDAS" -ForegroundColor Red
    $script:CodigoSalida = 1
}

function Show-Estado {
    Write-Host "Raiz: $($script:Demo.Raiz) (marcador: $(Test-RaizPropia))"
    Write-Host "Cluster demo activo: $(Test-ClusterActivo) (127.0.0.1:$($script:Demo.PuertoBD), base $($script:Demo.BaseDatos))"
    foreach ($p in @((Read-Estado).procesos)) {
        $vivo = [bool](Get-ProcesoDemoVivo $p.nombre)
        Write-Host ("Proceso {0}: PID {1} activo={2}" -f $p.nombre, $p.pid, $vivo)
    }
    Write-Host "Credenciales: $(Test-Path $script:Demo.Credenciales)  Manifiesto: $(Test-Path $script:Demo.Manifiesto)"
}

function Invoke-Detener {
    if (-not (Test-RaizPropia)) { Write-Aviso "No hay demo creada."; return }
    Restore-AnimacionesDemo
    Stop-EmuladorDemo
    Stop-ProcesoDemo "backend-$($script:Demo.PuertoAndroid)"
    Stop-ProcesoDemo "backend-$($script:Demo.PuertoApi)"
    Stop-ClusterDemo
}

$script:CodigoSalida = 0
switch ($Accion) {
    "PrepararPresentacion" { Invoke-PrepararPresentacion | Out-Null }
    "Construir" { Invoke-Construir }
    "Reiniciar" { Invoke-Construir }
    "Validar" {
        & (Join-Path $PSScriptRoot "demo-validar-api.ps1") -ConCicloCompleto:$ConCicloCompleto
        $script:CodigoSalida = $LASTEXITCODE
    }
    "Android" {
        & (Join-Path $PSScriptRoot "demo-android.ps1") -Roles $Roles
        $script:CodigoSalida = $LASTEXITCODE
    }
    "Estado" { Show-Estado }
    "Detener" { Invoke-Detener }
    "Eliminar" {
        Invoke-Detener
        if (-not (Test-RaizPropia)) { Write-Aviso "No hay demo que eliminar."; break }
        if (Test-ClusterActivo) { Stop-Demo "El cluster demo sigue activo; no se elimina nada." }
        # Ruta fija bajo %LOCALAPPDATA% y con el marcador de la demo: nunca otra carpeta.
        $raiz = [System.IO.Path]::GetFullPath($script:Demo.Raiz)
        if ($raiz -ne [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA "FacilityHubDemo"))) { Stop-Demo "Ruta de la demo inesperada." }
        Remove-Item -Recurse -Force $raiz
        Write-Ok "Eliminado $raiz"
    }
}
exit $script:CodigoSalida
