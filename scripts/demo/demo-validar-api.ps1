# FacilityHub - Demo (FH-008B): validacion automatica de la API contra la demo (solo lectura).
# Resultados en %LOCALAPPDATA%\FacilityHubDemo\resultados\api-*.json/.md, sin tokens ni contrasenas.
# -ConCicloCompleto: ejecuta ademas scripts\validar-api.ps1 (circuito completo con escritura) contra el backend demo 18080.

param([switch]$ConCicloCompleto)

. (Join-Path $PSScriptRoot "lib-demo.ps1")

if (-not (Test-Path $script:Demo.Manifiesto)) { Stop-Demo "No hay demo construida (falta el manifiesto). Ejecuta '-Accion Construir'." 2 }
$reg = @((Read-Estado).procesos | Where-Object { $_.nombre -eq "backend-$($script:Demo.PuertoApi)" }) | Select-Object -First 1
if (-not $reg -or -not (Get-Process -Id $reg.pid -ErrorAction SilentlyContinue)) { Stop-Demo "El backend demo ($($script:Demo.PuertoApi)) no esta activo. Ejecuta '-Accion Construir'." 2 }
Assert-AislamientoBackend $script:Demo.PuertoApi $reg.pid

$cred = Get-CredencialesDemo
$man = Get-Content -Raw $script:Demo.Manifiesto | ConvertFrom-Json
# Nota: en PowerShell las variables no distinguen mayusculas; por eso el manifiesto es $man y no $m.
$M = $man.mantenimientos; $I = $man.intervenciones; $D = $man.documentos
$resultados = New-Object System.Collections.ArrayList

function Add-Resultado([string]$id, [string]$descripcion, [bool]$ok, [string]$detalle) {
    [void]$resultados.Add([pscustomobject]@{ id = $id; descripcion = $descripcion; ok = $ok; detalle = $detalle })
    if ($ok) { Write-Host "[OK]    $id $descripcion ($detalle)" -ForegroundColor Green } else { Write-Host "[FALLO] $id $descripcion ($detalle)" -ForegroundColor Red }
}
function Test-Codigo([string]$id, [string]$descripcion, [string]$metodo, [string]$ruta, [string]$token, [int]$esperado, $cuerpo = $null) {
    $r = Invoke-ApiCodigo $metodo $ruta $token $cuerpo
    Add-Resultado $id $descripcion ($r.codigo -eq $esperado) "$metodo $ruta -> HTTP $($r.codigo), esperado $esperado"
    return $r
}
function ConvertFrom-Bytes($r) { return ([System.Text.Encoding]::UTF8.GetString($r.bytes) | ConvertFrom-Json) }
function Get-Sha256Bytes([byte[]]$b) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    return (-join ($sha.ComputeHash($b) | ForEach-Object { $_.ToString("x2") }))
}

Write-Paso "Validando la API de la demo en http://127.0.0.1:$($script:Demo.PuertoApi)"

# ---- 1. Autenticacion de las 6 cuentas y rechazo de credenciales erroneas ----
$T = @{}
foreach ($c in $cred.cuentas) {
    $r = Invoke-ApiCodigo POST "/api/auth/login" $null @{ email = $c.email; password = $c.password }
    $ok = ($r.codigo -eq 200)
    if ($ok) { $j = ConvertFrom-Bytes $r; $T[$c.clave] = $j.token; $ok = ($j.rol -eq $c.rol) }
    Add-Resultado "AUT-$($c.clave)" "login $($c.rol) ($($c.descripcion))" $ok "HTTP $($r.codigo)"
}
# Contrasena deliberadamente incorrecta (debe devolver 401).
$r = Invoke-ApiCodigo POST "/api/auth/login" $null @{ email = (Get-Cuenta $cred "explotacion").email; password = "incorrecta-demo" }
Add-Resultado "AUT-ERR" "contrasena incorrecta rechazada" ($r.codigo -eq 401) "HTTP $($r.codigo)"
Test-Codigo "AUT-SIN" "sin token -> 401" GET "/api/centros" $null 401 | Out-Null

# ---- 2. Permisos por rol ----
Test-Codigo "ROL-01" "INQUILINO no accede al panel" GET "/api/gestion/resumen" $T.inquilinoA 403 | Out-Null
Test-Codigo "ROL-02" "PROVEEDOR no accede al panel" GET "/api/gestion/resumen" $T.tecnico 403 | Out-Null
Test-Codigo "ROL-03" "PROVEEDOR no accede a centros" GET "/api/centros" $T.tecnico 403 | Out-Null
Test-Codigo "ROL-04" "INQUILINO no crea proveedores" POST "/api/proveedores" $T.inquilinoA 403 @{ razonSocial = "x" } | Out-Null
Test-Codigo "ROL-05" "EXPLOTACION no da de alta proveedores (solo ADMIN)" POST "/api/proveedores" $T.explotacion 403 @{ razonSocial = "x" } | Out-Null
Test-Codigo "ROL-06" "ADMIN accede al panel" GET "/api/gestion/resumen" $T.admin 200 | Out-Null
Test-Codigo "ROL-07" "INQUILINO no ve la bandeja de validacion" GET "/api/intervenciones/pendientes" $T.inquilinoA 403 | Out-Null

# ---- 3. Centros, locales y obligaciones ----
$r = Test-Codigo "CON-01" "EXPLOTACION consulta centros" GET "/api/centros" $T.explotacion 200
$centros = @(ConvertFrom-Bytes $r)
$r = Test-Codigo "CON-02" "EXPLOTACION consulta locales" GET "/api/centros/$($centros[0].id)/locales" $T.explotacion 200
$locales = @(ConvertFrom-Bytes $r)
Add-Resultado "CON-03" "el centro tiene los locales L-101 y L-205" ((@($locales | ForEach-Object { $_.codigo }) -contains "L-101") -and (@($locales | ForEach-Object { $_.codigo }) -contains "L-205")) "$($locales.Count) locales"
$r = Test-Codigo "CON-04" "obligaciones de L-101" GET "/api/locales/$($man.locales.L101)/obligaciones" $T.explotacion 200
$obl = @(ConvertFrom-Bytes $r)
Add-Resultado "CON-05" "L-101 tiene obligaciones PCI y climatizacion" ($obl.Count -ge 2) "$($obl.Count) obligaciones"
Test-Codigo "CON-06" "INQUILINO A consulta sus obligaciones" GET "/api/inquilino/obligaciones" $T.inquilinoA 200 | Out-Null
Test-Codigo "CON-07" "INQUILINO A no consulta locales ajenos (L-205)" GET "/api/locales/$($man.locales.L205)/obligaciones" $T.inquilinoA 404 | Out-Null

# ---- 4. Asignacion de proveedor ----
$r = Test-Codigo "ASI-01" "asignacion de P1 (proximo, asignado)" GET "/api/mantenimientos/$($M.P1)/asignacion" $T.explotacion 200
$asi = ConvertFrom-Bytes $r
Add-Resultado "ASI-02" "P1 asignado a Mantenimientos Demo S.L." ($asi.proveedor -eq "Mantenimientos Demo S.L." -or $asi.razonSocial -eq "Mantenimientos Demo S.L." -or ([string]($asi | ConvertTo-Json)) -match "Mantenimientos Demo") "proveedorId $($asi.proveedorId)"
$r = Test-Codigo "ASI-03" "trabajos del tecnico" GET "/api/proveedor/trabajos" $T.tecnico 200
$trab = @(ConvertFrom-Bytes $r) | ForEach-Object { $_.mantenimientoId }
Add-Resultado "ASI-04" "el tecnico ve P1 y C1 asignados" (($trab -contains [long]$M.P1) -and ($trab -contains [long]$M.C1)) "mantenimientos $($trab -join ',')"
Add-Resultado "ASI-05" "el tecnico no ve trabajos sin asignar (V1, Z1)" (-not ($trab -contains [long]$M.V1) -and -not ($trab -contains [long]$M.Z1)) ""

# ---- 5. Intervenciones: registro, envio, validacion, correccion y recalculo ----
$iA1 = ConvertFrom-Bytes (Test-Codigo "INT-01" "detalle intervencion A1" GET "/api/intervenciones/$($I.A1)" $T.explotacion 200)
Add-Resultado "INT-02" "A1 aprobada con 2 documentos" ($iA1.estadoValidacion -eq "APROBADO" -and @($iA1.documentos).Count -eq 2) "$($iA1.estadoValidacion)"
$compA1 = @($iA1.documentos | Where-Object { $_.visibilidad -eq "COMPARTIDO" }) | ForEach-Object { $_.id }
Add-Resultado "INT-03" "solo el certificado de A1 queda COMPARTIDO; la evidencia queda INTERNA" (@($compA1).Count -eq 1 -and [long]$compA1[0] -eq [long]$D.certA1.id) "compartidos: $($compA1 -join ',')"
$pend = @(ConvertFrom-Bytes (Test-Codigo "INT-04" "bandeja de validacion" GET "/api/intervenciones/pendientes" $T.explotacion 200)) | ForEach-Object { $_.id }
Add-Resultado "INT-05" "A2 enviada y pendiente de validacion" ($pend -contains [long]$I.A2) "pendientes: $($pend -join ',')"
$iC1 = ConvertFrom-Bytes (Test-Codigo "INT-06" "detalle intervencion C1" GET "/api/intervenciones/$($I.C1)" $T.tecnico 200)
Add-Resultado "INT-07" "C1 requiere correccion con comentario de Explotacion" ($iC1.estadoValidacion -eq "REQUIERE_CORRECCION" -and @($iC1.validaciones | Where-Object { $_.accion -eq "SOLICITAR_CORRECCION" }).Count -eq 1) "$($iC1.estadoValidacion)"
Add-Resultado "INT-08" "el tecnico puede corregir y reenviar C1" ((@($iC1.accionesPermitidas) -contains "ENVIAR_INTERVENCION")) "acciones: $(@($iC1.accionesPermitidas) -join ',')"
$o1 = @($obl | Where-Object { $_.id -eq [long]$man.obligaciones.O1_PCI_L101 })[0]
$esperada = ([datetime]::ParseExact($man.fechas.A1, "yyyy-MM-dd", $null)).AddMonths(3).ToString("yyyy-MM-dd")
Add-Resultado "INT-09" "recalculo: proxima fecha PCI = ejecucion A1 + 3 meses" ($o1.proximaFecha -eq $esperada) "proxima $($o1.proximaFecha), esperada $esperada"

# ---- 6. Panel de Explotacion ----
$res = ConvertFrom-Bytes (Test-Codigo "PAN-01" "resumen del panel" GET "/api/gestion/resumen" $T.explotacion 200)
$filtros = [ordered]@{ VENCIDOS = @($M.V1, $M.C1); PROXIMOS = @($M.P1, $M.Z1); PENDIENTE_VALIDACION = @($M.A2); REQUIERE_CORRECCION = @($M.C1); SIN_ASIGNAR = @($M.V1, $M.Z1) }
$campo = @{ VENCIDOS = "vencidos"; PROXIMOS = "proximos"; PENDIENTE_VALIDACION = "pendientesValidacion"; REQUIERE_CORRECCION = "requierenCorreccion"; SIN_ASIGNAR = "sinAsignar" }
foreach ($f in $filtros.Keys) {
    $lista = @(ConvertFrom-Bytes (Invoke-ApiCodigo GET "/api/gestion/trabajos?filtro=$f" $T.explotacion))
    $ids = @($lista | ForEach-Object { [long]$_.mantenimientoId })
    $faltan = @($filtros[$f] | Where-Object { $ids -notcontains [long]$_ })
    Add-Resultado "PAN-$f" "filtro $f contiene los casos de la demo y coincide con el resumen" (($faltan.Count -eq 0) -and ($res.($campo[$f]) -eq $lista.Count)) "lista $($lista.Count), resumen $($res.($campo[$f])), faltan $($faltan -join ',')"
}
$sin = @(ConvertFrom-Bytes (Invoke-ApiCodigo GET "/api/gestion/trabajos?filtro=SIN_ASIGNAR" $T.explotacion)) | ForEach-Object { [long]$_.mantenimientoId }
Add-Resultado "PAN-ASIG" "P1 (asignado) no figura como sin asignar" (-not ($sin -contains [long]$M.P1)) ""
$zc = @(ConvertFrom-Bytes (Invoke-ApiCodigo GET "/api/gestion/trabajos?filtro=PROXIMOS" $T.explotacion)) | Where-Object { [long]$_.mantenimientoId -eq [long]$M.Z1 }
Add-Resultado "PAN-ZC" "Z1 aparece como zona comun" ($zc -and $zc.zonaComun -eq $true) ""

# ---- 7. Alertas ----
foreach ($k in @("explotacion", "tecnico", "inquilinoA")) {
    $al = @(ConvertFrom-Bytes (Test-Codigo "ALE-$k" "alertas propias de $k" GET "/api/alertas" $T[$k] 200))
    Add-Resultado "ALE-$k-N" "$k tiene alertas generadas por la demo" ($al.Count -gt 0) "$($al.Count) alertas; tipos $((@($al | ForEach-Object { $_.tipo }) | Sort-Object -Unique) -join ',')"
}

# ---- 8. Historial del local ----
$hg = @(ConvertFrom-Bytes (Test-Codigo "HIS-01" "historial de gestion L-101" GET "/api/locales/$($man.locales.L101)/historial" $T.explotacion 200))
$p5 = @($hg | Where-Object { [long]$_.mantenimientoId -eq [long]$M.P5 })[0]
Add-Resultado "HIS-02" "gestion ve A1 aprobada y el registro P5 sin validacion" ((@($hg | Where-Object { [long]$_.mantenimientoId -eq [long]$M.A1 -and $_.tipoRegistro -eq "APROBADO" }).Count -eq 1) -and $p5 -and $p5.tipoRegistro -eq "HISTORICO_P5" -and $null -eq $p5.estadoValidacion) "etiqueta P5: $($p5.etiqueta)"
$hi = @(ConvertFrom-Bytes (Test-Codigo "HIS-03" "historial del inquilino A en L-101" GET "/api/locales/$($man.locales.L101)/historial" $T.inquilinoA 200))
$certsA = @($hi | ForEach-Object { @($_.cumplimiento.certificados) } | ForEach-Object { [long]$_.id })
Add-Resultado "HIS-04" "el inquilino A solo ve A1 y su certificado compartido" ((@($hi | ForEach-Object { [long]$_.mantenimientoId }) -join ',') -eq "$($M.A1)" -and ($certsA -join ',') -eq "$($D.certA1.id)") "mantenimientos $((@($hi | ForEach-Object { $_.mantenimientoId })) -join ','); certificados $($certsA -join ',')"
$crudo = [System.Text.Encoding]::UTF8.GetString((Invoke-ApiCodigo GET "/api/locales/$($man.locales.L101)/historial" $T.inquilinoA).bytes)
Add-Resultado "HIS-05" "el historial del inquilino no expone documentos internos, observaciones ni P5" (-not ($crudo -match "Parte-trabajo|Acta-revision|observaciones|HISTORICO_P5")) ""
Test-Codigo "HIS-06" "inquilino B no ve el historial de L-101" GET "/api/locales/$($man.locales.L101)/historial" $T.inquilinoB 404 | Out-Null
Test-Codigo "HIS-07" "PROVEEDOR no ve historiales de locales" GET "/api/locales/$($man.locales.L101)/historial" $T.tecnico 403 | Out-Null

# ---- 9. Descarga real de documentos con SHA-256 ----
$casos = @(
    @{ id = "DOC-01"; d = "inquilino A descarga su certificado compartido"; tok = "inquilinoA"; doc = "certA1"; cod = 200 },
    @{ id = "DOC-02"; d = "inquilino B descarga su certificado compartido"; tok = "inquilinoB"; doc = "certB1"; cod = 200 },
    @{ id = "DOC-03"; d = "inquilino A NO descarga la evidencia interna de A1"; tok = "inquilinoA"; doc = "evidA1"; cod = 404 },
    @{ id = "DOC-04"; d = "inquilino A NO descarga el certificado aun no validado (A2)"; tok = "inquilinoA"; doc = "certA2"; cod = 404 },
    @{ id = "DOC-05"; d = "inquilino A NO descarga el certificado de otra ocupacion (L-205)"; tok = "inquilinoA"; doc = "certB1"; cod = 404 },
    @{ id = "DOC-06"; d = "inquilino B NO descarga el certificado de L-101"; tok = "inquilinoB"; doc = "certA1"; cod = 404 },
    @{ id = "DOC-07"; d = "inquilino A NO descarga el acta historica P5"; tok = "inquilinoA"; doc = "docP5"; cod = 404 },
    @{ id = "DOC-08"; d = "EXPLOTACION descarga la evidencia interna (permisos de gestion)"; tok = "explotacion"; doc = "evidA1"; cod = 200 }
)
foreach ($c in $casos) {
    $doc = $D.($c.doc)
    $r = Invoke-ApiCodigo GET "/api/documentos/$($doc.id)/archivo" $T[$c.tok]
    $ok = ($r.codigo -eq $c.cod)
    $det = "HTTP $($r.codigo), esperado $($c.cod)"
    if ($ok -and $c.cod -eq 200) {
        $sha = Get-Sha256Bytes $r.bytes
        $ok = ($sha -eq $doc.sha256)
        $det += "; SHA-256 descargado $($sha.Substring(0,16))... = subido $($doc.sha256.Substring(0,16))... : $ok"
    }
    Add-Resultado $c.id $c.d $ok $det
}

# ---- 10. Ciclo completo opcional (escribe datos en la demo) ----
if ($ConCicloCompleto) {
    Write-Paso "Ejecutando scripts\validar-api.ps1 contra el backend demo (escribe datos)"
    $env:FACILITYHUB_TEST_PASSWORD = (Get-Cuenta $cred "explotacion").password
    $env:FACILITYHUB_ADMIN_PASSWORD = (Get-Cuenta $cred "admin").password
    try {
        $salida = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $script:Demo.Repo "scripts\validar-api.ps1") -ConfirmarEntornoDesechable -Base "http://127.0.0.1:$($script:Demo.PuertoApi)" 2>&1
        $codigo = $LASTEXITCODE
    } finally { Remove-Item Env:FACILITYHUB_TEST_PASSWORD, Env:FACILITYHUB_ADMIN_PASSWORD -ErrorAction SilentlyContinue }
    $salida | ForEach-Object { "  $_" } | Write-Host
    Add-Resultado "CIC-01" "validar-api.ps1: circuito mantenimiento -> intervencion -> envio -> aprobacion -> recalculo -> historial" ($codigo -eq 0) "exit $codigo"
}

# ---- Resumen ----
$ok = @($resultados | Where-Object { $_.ok }).Count
$fallos = @($resultados | Where-Object { -not $_.ok }).Count
$sello = Get-Date -Format "yyyyMMdd-HHmmss"
$json = Join-Path $script:Demo.Resultados "api-$sello.json"
$resultados | ConvertTo-Json -Depth 4 | Set-Content -Path $json -Encoding UTF8
$md = @("# Validacion API de la demo ($sello)", "", "Resultado: $ok correctas, $fallos fallidas", "", "| ID | Comprobacion | Resultado | Detalle |", "|---|---|---|---|")
$md += $resultados | ForEach-Object { "| $($_.id) | $($_.descripcion) | $(if ($_.ok) { 'OK' } else { 'FALLO' }) | $($_.detalle) |" }
$md | Set-Content -Path (Join-Path $script:Demo.Resultados "api-$sello.md") -Encoding UTF8
Write-Host ""
Write-Host "RESULTADO API: $ok correctas, $fallos fallidas. Detalle: $json" -ForegroundColor $(if ($fallos -eq 0) { "Green" } else { "Red" })
if ($fallos -gt 0) { exit 1 } else { exit 0 }
