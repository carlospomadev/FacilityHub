package com.facilityhub.service;

import com.facilityhub.api.dto.documento.DocumentoDto;
import com.facilityhub.api.dto.documento.RevisarDocumentoRequest;
import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.repository.DocumentoMantenimientoRepository;
import com.facilityhub.repository.MantenimientoRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Service
public class DocumentoService {
    private final DocumentoMantenimientoRepository documentoRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final DocumentStorageService storageService;

    public DocumentoService(DocumentoMantenimientoRepository documentoRepository,
                            MantenimientoRepository mantenimientoRepository,
                            DocumentStorageService storageService) {
        this.documentoRepository = documentoRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<DocumentoDto> listar(Long mantenimientoId) {
        validarMantenimiento(mantenimientoId);
        return documentoRepository.findByMantenimientoIdOrderByFechaSubidaDesc(mantenimientoId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public DocumentoDto subir(Long mantenimientoId, MultipartFile file, String tipoDocumento,
                              LocalDate fechaDocumento, LocalDate fechaCaducidad) {
        Mantenimiento mantenimiento = mantenimientoRepository.findById(mantenimientoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Mantenimiento no encontrado: " + mantenimientoId));

        String ruta = storageService.guardar(mantenimientoId, file);
        DocumentoMantenimiento documento = new DocumentoMantenimiento(
            mantenimiento,
            file.getOriginalFilename() == null ? "documento" : file.getOriginalFilename(),
            ruta,
            tipoDocumento,
            fechaDocumento,
            fechaCaducidad
        );
        return toDto(documentoRepository.save(documento));
    }

    @Transactional
    public DocumentoDto revisar(Long documentoId, RevisarDocumentoRequest request) {
        DocumentoMantenimiento documento = documentoRepository.findById(documentoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado: " + documentoId));
        documento.revisar(request.estado(), request.observaciones());
        return toDto(documento);
    }

    @Transactional(readOnly = true)
    public DescargaDocumento obtenerDescarga(Long documentoId) {
        DocumentoMantenimiento documento = documentoRepository.findById(documentoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado: " + documentoId));
        Resource resource = storageService.cargar(documento.getRutaArchivo());
        return new DescargaDocumento(documento.getNombreOriginal(), resource);
    }

    private void validarMantenimiento(Long id) {
        if (!mantenimientoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Mantenimiento no encontrado: " + id);
        }
    }

    private DocumentoDto toDto(DocumentoMantenimiento d) {
        return new DocumentoDto(
            d.getId(), d.getMantenimiento().getId(), d.getNombreOriginal(), d.getTipoDocumento(),
            d.getFechaDocumento(), d.getFechaCaducidad(), d.getEstadoRevision().name(),
            d.getObservaciones(), d.getFechaSubida()
        );
    }

    public record DescargaDocumento(String nombre, Resource resource) {}
}
