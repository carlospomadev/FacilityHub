package com.facilityhub.api;

import com.facilityhub.api.dto.documento.DocumentoDto;
import com.facilityhub.api.dto.documento.RevisarDocumentoRequest;
import com.facilityhub.service.DocumentoService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
public class DocumentoController {
    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @GetMapping("/api/mantenimientos/{mantenimientoId}/documentos")
    public List<DocumentoDto> listar(@PathVariable Long mantenimientoId) {
        return documentoService.listar(mantenimientoId);
    }

    @PostMapping(value = "/api/mantenimientos/{mantenimientoId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoDto subir(
        @PathVariable Long mantenimientoId,
        @RequestPart("file") MultipartFile file,
        @RequestParam(required = false) String tipoDocumento,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDocumento,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCaducidad
    ) {
        return documentoService.subir(mantenimientoId, file, tipoDocumento, fechaDocumento, fechaCaducidad);
    }

    @PatchMapping("/api/documentos/{documentoId}/revision")
    public DocumentoDto revisar(@PathVariable Long documentoId,
                                 @Valid @RequestBody RevisarDocumentoRequest request) {
        return documentoService.revisar(documentoId, request);
    }

    @GetMapping("/api/documentos/{documentoId}/archivo")
    public ResponseEntity<Resource> descargar(@PathVariable Long documentoId) {
        DocumentoService.DescargaDocumento descarga = documentoService.obtenerDescarga(documentoId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + descarga.nombre().replace("\"", "") + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(descarga.resource());
    }
}
