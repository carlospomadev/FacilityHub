package com.facilityhub.api;

import com.facilityhub.api.dto.intervencion.*;
import com.facilityhub.domain.CategoriaDocumento;
import com.facilityhub.service.DocumentoService;
import com.facilityhub.service.IntervencionService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

// FH-006 / P6-C1: intervenciones (registro, evidencias, presentacion y validacion). Permisos finos en el servicio.
@RestController
public class IntervencionController {
    private final IntervencionService intervencionService;

    public IntervencionController(IntervencionService intervencionService) {
        this.intervencionService = intervencionService;
    }

    @GetMapping("/api/mantenimientos/{mantenimientoId}/intervenciones")
    public List<IntervencionDto> listar(@PathVariable Long mantenimientoId, Principal principal) {
        return intervencionService.listarPorMantenimiento(mantenimientoId, email(principal));
    }

    @PostMapping("/api/mantenimientos/{mantenimientoId}/intervenciones")
    @ResponseStatus(HttpStatus.CREATED)
    public IntervencionDto registrar(@PathVariable Long mantenimientoId,
                                     @Valid @RequestBody(required = false) DatosIntervencionRequest datos,
                                     Principal principal) {
        return intervencionService.registrar(mantenimientoId, datos, email(principal));
    }

    @GetMapping("/api/intervenciones/pendientes")
    public List<IntervencionPendienteDto> pendientes(Principal principal) {
        return intervencionService.bandejaValidacion(email(principal));
    }

    @GetMapping("/api/intervenciones/{id}")
    public IntervencionDto obtener(@PathVariable Long id, Principal principal) {
        return intervencionService.obtener(id, email(principal));
    }

    @PatchMapping("/api/intervenciones/{id}")
    public IntervencionDto actualizar(@PathVariable Long id, @Valid @RequestBody DatosIntervencionRequest datos,
                                      Principal principal) {
        return intervencionService.actualizar(id, datos, email(principal));
    }

    @PostMapping(value = "/api/intervenciones/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IntervencionDto adjuntar(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                    @RequestParam CategoriaDocumento categoria, Principal principal) {
        return intervencionService.adjuntar(id, file, categoria, email(principal));
    }

    @PostMapping("/api/intervenciones/{id}/envio")
    public IntervencionDto enviar(@PathVariable Long id, @Valid @RequestBody EnvioRequest request, Principal principal) {
        return intervencionService.enviar(id, request.version(), email(principal));
    }

    @PostMapping("/api/intervenciones/{id}/validacion")
    public IntervencionDto validar(@PathVariable Long id, @Valid @RequestBody ValidacionRequest request, Principal principal) {
        return intervencionService.validar(id, request, email(principal));
    }

    // FH-006 / P6-C3: descarga de documentos de una intervencion (gestion: todos; PROVEEDOR: los que subio su empresa).
    @GetMapping("/api/intervenciones/{id}/documentos/{documentoId}/archivo")
    public ResponseEntity<Resource> descargar(@PathVariable Long id, @PathVariable Long documentoId, Principal principal) {
        DocumentoService.DescargaDocumento descarga = intervencionService.descargar(id, documentoId, email(principal));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + descarga.nombre().replace("\"", "") + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(descarga.resource());
    }

    private static String email(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
