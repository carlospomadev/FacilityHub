# FacilityHub - Demo (FH-008B): cuentas y datos sinteticos. Se carga desde demo.ps1 (requiere lib-demo.ps1).
# Datos maestros sin API (usuarios de inquilino, zona comun, contrasenas): SQL solo en la base demo desechable.
# Todo lo funcional (proveedor, tecnicos, mantenimientos, asignaciones, intervenciones, documentos, validaciones,
# recordatorios): endpoints reales del backend. Fechas relativas a hoy en Europe/Madrid.

function Invoke-SemillaDemo($cred) {
    Assert-BaseDemo $script:Demo.BaseDatos
    $ya = Invoke-Psql "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'demo_meta' AND table_name = 'semilla';"
    if ([int]$ya -gt 0) { Stop-Demo "La base demo ya tiene datos de la demo. Usa '-Accion Reiniciar' para reconstruirla (evita duplicados)." 3 }

    $hoy = Get-HoyMadrid
    $f = { param([int]$dias) Format-Fecha ($hoy.AddDays($dias)) }
    Write-Paso "Sembrando la demo (hoy en Madrid: $(Format-Fecha $hoy))"

    # ---- 1. Datos maestros y cuentas sin API (SQL por entrada estandar, una transaccion) ----
    $a = Get-Cuenta $cred "admin"; $e = Get-Cuenta $cred "explotacion"; $ia = Get-Cuenta $cred "inquilinoA"; $ib = Get-Cuenta $cred "inquilinoB"
    $inicio = & $f -365
    $sqlMaestros = @"
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SET search_path TO facilityhub, public;
-- Contrasenas BCrypt de las cuentas demo de V3 y alta del inquilino B (sin API de alta de usuarios inquilino).
UPDATE usuarios SET password_hash = crypt('$($a.password)', gen_salt('bf', 10)) WHERE email = '$($a.email)';
UPDATE usuarios SET password_hash = crypt('$($e.password)', gen_salt('bf', 10)) WHERE email = '$($e.email)';
UPDATE usuarios SET password_hash = crypt('$($ia.password)', gen_salt('bf', 10)) WHERE email = '$($ia.email)';
INSERT INTO usuarios (nombre, apellidos, email, password_hash, rol_id, inquilino_id)
VALUES ('Usuario', 'Inquilino B Demo', '$($ib.email)', crypt('$($ib.password)', gen_salt('bf', 10)),
        (SELECT id FROM roles WHERE codigo = 'INQUILINO'), (SELECT id FROM inquilinos WHERE nombre_comercial = 'Tienda Demo'));
-- Fechas de inicio holgadas para que la demo funcione en cualquier fecha futura.
UPDATE ocupaciones_local SET fecha_inicio = LEAST(fecha_inicio, DATE '$inicio') WHERE activo;
UPDATE obligaciones_mantenimiento SET fecha_inicio = LEAST(fecha_inicio, DATE '$inicio');
-- Zona comun: instalacion, tipo y obligacion NHOOD sin local.
INSERT INTO tipos_mantenimiento (codigo, nombre, descripcion, periodicidad_meses, requiere_documento)
VALUES ('ZC-PRES-TRIM', 'Revision grupo de presion (zona comun)', 'Revision trimestral del grupo de presion de agua', 3, TRUE);
INSERT INTO instalaciones (centro_id, local_id, nombre, tipo, descripcion)
VALUES ((SELECT id FROM centros WHERE codigo = 'CC-MAD-01'), NULL, 'Grupo de presion zona comun', 'FONTANERIA', 'Instalacion de zona comun (demo)');
INSERT INTO obligaciones_mantenimiento (centro_id, local_id, instalacion_id, tipo_mantenimiento_id, fecha_inicio, proxima_fecha, responsable, observaciones)
VALUES ((SELECT id FROM centros WHERE codigo = 'CC-MAD-01'), NULL,
        (SELECT id FROM instalaciones WHERE nombre = 'Grupo de presion zona comun'),
        (SELECT id FROM tipos_mantenimiento WHERE codigo = 'ZC-PRES-TRIM'), DATE '$inicio', DATE '$(& $f 10)', 'NHOOD', 'Zona comun (demo)');
-- Registro P5 cerrado: historico sin validacion (anterior al circuito de validacion).
INSERT INTO mantenimientos (obligacion_id, fecha_programada, fecha_realizacion, estado, descripcion, observaciones, validacion_requerida)
VALUES ((SELECT o.id FROM obligaciones_mantenimiento o JOIN locales l ON l.id = o.local_id JOIN tipos_mantenimiento t ON t.id = o.tipo_mantenimiento_id
         WHERE l.codigo = 'L-101' AND t.codigo = 'PCI-TRIM'), DATE '$(& $f -150)', DATE '$(& $f -150)', 'FINALIZADO',
        'Revision PCI registrada antes de la validacion (historico P5)', 'Acta en papel archivada', FALSE);
COMMIT;
"@
    Invoke-Psql $sqlMaestros | Out-Null
    $sqlMaestros = $null
    Write-Ok "Datos maestros y cuentas sin API preparados (inquilino B, zona comun, registro P5)"

    $id = { param([string]$sql) [long](Invoke-Psql $sql) }
    $O1 = & $id "SELECT o.id FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id=o.local_id JOIN facilityhub.tipos_mantenimiento t ON t.id=o.tipo_mantenimiento_id WHERE l.codigo='L-101' AND t.codigo='PCI-TRIM';"
    $O2 = & $id "SELECT o.id FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id=o.local_id JOIN facilityhub.tipos_mantenimiento t ON t.id=o.tipo_mantenimiento_id WHERE l.codigo='L-101' AND t.codigo='CLIMA-SEM';"
    $O3 = & $id "SELECT o.id FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.locales l ON l.id=o.local_id JOIN facilityhub.tipos_mantenimiento t ON t.id=o.tipo_mantenimiento_id WHERE l.codigo='L-205' AND t.codigo='BT-ANUAL';"
    $OZ = & $id "SELECT o.id FROM facilityhub.obligaciones_mantenimiento o JOIN facilityhub.tipos_mantenimiento t ON t.id=o.tipo_mantenimiento_id WHERE o.local_id IS NULL AND t.codigo='ZC-PRES-TRIM';"
    $P5 = & $id "SELECT id FROM facilityhub.mantenimientos WHERE NOT validacion_requerida AND estado='FINALIZADO';"
    $L101 = & $id "SELECT id FROM facilityhub.locales WHERE codigo='L-101';"
    $L205 = & $id "SELECT id FROM facilityhub.locales WHERE codigo='L-205';"

    # ---- 2. Cuentas por API ----
    $tAdmin = Get-TokenDemo $cred "admin"
    $tExpl = Get-TokenDemo $cred "explotacion"
    Get-TokenDemo $cred "inquilinoA" | Out-Null
    Get-TokenDemo $cred "inquilinoB" | Out-Null
    $prov = Invoke-Api POST "/api/proveedores" $tAdmin @{ cif = "B00000001"; razonSocial = "Mantenimientos Demo S.L."; emailContacto = "contacto@demo.facilityhub.test"; telefono = "000000000" }
    $cp = Get-Cuenta $cred "proveedor"; $ct = Get-Cuenta $cred "tecnico"
    Invoke-Api POST "/api/proveedores/$($prov.id)/usuarios" $tAdmin @{ nombre = "Responsable"; apellidos = "Proveedor Demo"; email = $cp.email; password = $cp.password } | Out-Null
    $tec = Invoke-Api POST "/api/proveedores/$($prov.id)/usuarios" $tAdmin @{ nombre = "Tecnico"; apellidos = "Demo"; email = $ct.email; password = $ct.password }
    Get-TokenDemo $cred "proveedor" | Out-Null
    $tTec = Get-TokenDemo $cred "tecnico"
    Write-Ok "Cuentas creadas y autenticadas: ADMIN, EXPLOTACION, INQUILINO A, INQUILINO B, PROVEEDOR y TECNICO"

    # ---- 3. Mantenimientos (EXPLOTACION) ----
    $nuevo = { param($obl, [int]$dias, [string]$desc) (Invoke-Api POST "/api/obligaciones/$obl/mantenimientos" $tExpl @{ fechaProgramada = (& $f $dias); descripcion = $desc }).id }
    $M = [ordered]@{
        A1 = & $nuevo $O1 -20 "Revision PCI trimestral - aprobada con certificado compartido"
        C1 = & $nuevo $O2 -5  "Mantenimiento de climatizacion - devuelto para correccion"
        P1 = & $nuevo $O2 7   "Mantenimiento de climatizacion - asignado, proximo a vencer"
        B1 = & $nuevo $O3 -15 "Revision electrica L-205 - aprobada (inquilino B)"
        V1 = & $nuevo $O3 -4  "Revision electrica L-205 - vencida sin proveedor"
        Z1 = & $nuevo $OZ 10  "Revision grupo de presion - zona comun sin proveedor"
    }
    foreach ($k in @("A1", "C1", "P1", "B1")) {
        Invoke-Api PUT "/api/mantenimientos/$($M[$k])/asignacion" $tExpl @{ proveedorId = $prov.id; tecnicoUsuarioId = $tec.id } | Out-Null
    }
    Write-Ok "Mantenimientos creados y proveedor asignado (A1, C1, P1, B1); V1 y Z1 sin proveedor"

    # ---- 4. Documentos PDF sinteticos ----
    $docs = [ordered]@{}
    $pdf = { param([string]$clave, [string]$nombre, [string[]]$lineas)
        $ruta = Join-Path $script:Demo.Documentos $nombre
        $sha = New-PdfSintetico $ruta $lineas
        $docs[$clave] = [ordered]@{ nombre = $nombre; ruta = $ruta; sha256 = $sha }
    }
    & $pdf "certA1" "Certificado-PCI-L101-$(& $f -20).pdf" @("FacilityHub - DOCUMENTO SINTETICO DE DEMOSTRACION", "Certificado de revision PCI trimestral", "Local L-101 - Restaurante Demo", "Fecha de ejecucion: $(& $f -20)", "Empresa: Mantenimientos Demo S.L.")
    & $pdf "evidA1" "Parte-trabajo-PCI-L101-$(& $f -20).pdf" @("FacilityHub - DOCUMENTO SINTETICO DE DEMOSTRACION", "Parte de trabajo interno (evidencia)", "Local L-101", "No se comparte con el inquilino")
    & $pdf "certA2" "Certificado-PCI-L101-$(& $f -3).pdf" @("FacilityHub - DOCUMENTO SINTETICO DE DEMOSTRACION", "Certificado pendiente de validacion", "Local L-101", "Fecha de ejecucion: $(& $f -3)")
    & $pdf "evidA2" "Parte-trabajo-PCI-L101-$(& $f -3).pdf" @("FacilityHub - DOCUMENTO SINTETICO DE DEMOSTRACION", "Parte de trabajo pendiente de validacion")
    & $pdf "evidC1" "Parte-climatizacion-L101-$(& $f -5).pdf" @("FacilityHub - DOCUMENTO SINTETICO DE DEMOSTRACION", "Parte de climatizacion sin firma (pendiente de correccion)")
    & $pdf "certB1" "Certificado-BT-L205-$(& $f -15).pdf" @("FacilityHub - DOCUMENTO SINTETICO DE DEMOSTRACION", "Certificado de revision electrica", "Local L-205 - Tienda Demo", "Fecha de ejecucion: $(& $f -15)")
    & $pdf "evidB1" "Parte-trabajo-BT-L205-$(& $f -15).pdf" @("FacilityHub - DOCUMENTO SINTETICO DE DEMOSTRACION", "Parte de trabajo interno L-205")
    & $pdf "docP5" "Acta-revision-PCI-historica.pdf" @("FacilityHub - DOCUMENTO SINTETICO DE DEMOSTRACION", "Acta historica (registro P5, sin validacion)")

    # ---- 5. Intervenciones (TECNICO) y validaciones (EXPLOTACION) por la API real ----
    $I = [ordered]@{}
    $registrar = { param([string]$k, [int]$dias, [string]$resultado, [string]$incidencias)
        $cuerpo = @{ fechaEjecucion = (& $f $dias); actuaciones = "Revision realizada segun protocolo (demo)"; resultado = $resultado }
        if ($incidencias) { $cuerpo.incidencias = $incidencias }
        $I[$k] = (Invoke-Api POST "/api/mantenimientos/$($M[$k])/intervenciones" $tTec $cuerpo).id
    }
    $adjuntar = { param([string]$k, [string]$doc, [string]$categoria)
        $r = Send-Multipart "/api/intervenciones/$($I[$k])/documentos?categoria=$categoria" $tTec $docs[$doc].ruta
        $docs[$doc].id = (@($r.documentos) | Where-Object { $_.nombreOriginal -eq $docs[$doc].nombre } | Select-Object -First 1).id
        return $r.version
    }
    $enviar = { param([string]$k, $version) (Invoke-Api POST "/api/intervenciones/$($I[$k])/envio" $tTec @{ version = $version }).version }
    $validar = { param([string]$k, [string]$accion, [string]$comentario, $version, $compartir)
        Invoke-Api POST "/api/intervenciones/$($I[$k])/validacion" $tExpl @{ accion = $accion; comentario = $comentario; version = $version; documentosCompartidos = @($compartir) } | Out-Null
    }

    # A1: aprobada con certificado compartido y evidencia interna (L-101, inquilino A).
    & $registrar "A1" -20 "CONFORME" $null
    & $adjuntar "A1" "evidA1" "EVIDENCIA" | Out-Null
    $v = & $adjuntar "A1" "certA1" "CERTIFICADO"
    $v = & $enviar "A1" $v
    & $validar "A1" "APROBAR" "Documentacion correcta" $v @($docs.certA1.id)
    # B1: aprobada con certificado compartido (L-205, inquilino B).
    & $registrar "B1" -15 "CONFORME" $null
    & $adjuntar "B1" "evidB1" "EVIDENCIA" | Out-Null
    $v = & $adjuntar "B1" "certB1" "CERTIFICADO"
    $v = & $enviar "B1" $v
    & $validar "B1" "APROBAR" "Documentacion correcta" $v @($docs.certB1.id)
    # A2: segunda revision PCI enviada y pendiente de validar (mantenimiento nuevo de la obligacion O1).
    $M.A2 = & $nuevo $O1 -3 "Revision PCI - enviada, pendiente de validacion"
    Invoke-Api PUT "/api/mantenimientos/$($M.A2)/asignacion" $tExpl @{ proveedorId = $prov.id; tecnicoUsuarioId = $tec.id } | Out-Null
    & $registrar "A2" -3 "CONFORME" $null
    & $adjuntar "A2" "evidA2" "EVIDENCIA" | Out-Null
    $v = & $adjuntar "A2" "certA2" "CERTIFICADO"
    & $enviar "A2" $v | Out-Null
    # C1: enviada y devuelta para correccion.
    & $registrar "C1" -5 "CON_INCIDENCIAS" "Filtro deteriorado; se recomienda sustitucion"
    $v = & $adjuntar "C1" "evidC1" "EVIDENCIA"
    $v = & $enviar "C1" $v
    & $validar "C1" "SOLICITAR_CORRECCION" "Falta el parte firmado por el tecnico; adjuntalo y reenvia" $v @()
    Write-Ok "Intervenciones: A1 y B1 aprobadas, A2 pendiente de validacion, C1 requiere correccion"

    # ---- 6. Documento del registro P5 (gestion) ----
    $r = Send-Multipart "/api/mantenimientos/$P5/documentos" $tExpl $docs.docP5.ruta
    $docs.docP5.id = $r.id

    # ---- 7. Recordatorios: ejecucion MANUAL unica por ADMIN (la tarea programada sigue desactivada) ----
    $rec = Invoke-Api POST "/api/admin/recordatorios/ejecutar?simulacion=false" $tAdmin $null
    Write-Ok "Recordatorios manuales: $($rec.avisos) avisos (tarea automatica desactivada)"

    # ---- 8. Manifiesto (sin secretos) y marca de semilla ----
    $M.P5 = $P5
    $manifiesto = [ordered]@{
        generado = (Get-Date -Format s); hoyMadrid = (Format-Fecha $hoy)
        locales = [ordered]@{ L101 = $L101; L205 = $L205 }
        obligaciones = [ordered]@{ O1_PCI_L101 = $O1; O2_CLIMA_L101 = $O2; O3_BT_L205 = $O3; OZ_ZONA_COMUN = $OZ }
        mantenimientos = $M; intervenciones = $I
        proveedor = [ordered]@{ id = $prov.id; razonSocial = "Mantenimientos Demo S.L."; tecnicoUsuarioId = $tec.id }
        documentos = $docs
        fechas = [ordered]@{ A1 = (& $f -20); A2 = (& $f -3); C1 = (& $f -5); P1 = (& $f 7); B1 = (& $f -15); V1 = (& $f -4); Z1 = (& $f 10); P5 = (& $f -150) }
    }
    $manifiesto | ConvertTo-Json -Depth 6 | Set-Content -Path $script:Demo.Manifiesto -Encoding UTF8
    Invoke-Psql "CREATE SCHEMA IF NOT EXISTS demo_meta; CREATE TABLE demo_meta.semilla (creada timestamptz NOT NULL DEFAULT now(), hoy date NOT NULL); INSERT INTO demo_meta.semilla (hoy) VALUES (DATE '$(Format-Fecha $hoy)');" | Out-Null
    Write-Ok "Semilla completada. Manifiesto: $($script:Demo.Manifiesto)"
}
