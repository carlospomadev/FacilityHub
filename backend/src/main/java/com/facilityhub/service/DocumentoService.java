package com.facilityhub.service;

import com.facilityhub.api.dto.documento.CambiarVisibilidadRequest;
import com.facilityhub.api.dto.documento.DocumentoDto;
import com.facilityhub.api.dto.documento.DocumentoInquilinoDto;
import com.facilityhub.api.dto.documento.DocumentoRespuesta;
import com.facilityhub.api.dto.documento.RevisarDocumentoRequest;
import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoRevisionDocumento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.domain.VisibilidadDocumento;
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
    private final DocumentoAutorizacionService autorizacion;

    public DocumentoService(DocumentoMantenimientoRepository documentoRepository,
                            MantenimientoRepository mantenimientoRepository,
                            DocumentStorageService storageService,
                            DocumentoAutorizacionService autorizacion) {
        this.documentoRepository = documentoRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.storageService = storageService;
        this.autorizacion = autorizacion;
    }

    // El filtrado se aplica en la consulta, antes de serializar.
    @Transactional(readOnly = true)
    public List<DocumentoRespuesta> listar(Long mantenimientoId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Mantenimiento mantenimiento = mantenimientoRepository.findById(mantenimientoId)
            .orElseThrow(RecursoNoDisponibleException::new);
        if (!autorizacion.puedeLeerMantenimiento(usuario, mantenimiento)) {
            throw new RecursoNoDisponibleException();
        }

        if (autorizacion.esGestion(usuario)) {
            return documentoRepository.findByMantenimientoIdOrderByFechaSubidaDesc(mantenimientoId).stream()
                .<DocumentoRespuesta>map(this::toDto)
                .toList();
        }
        return documentoRepository.findByMantenimientoIdAndVisibilidadAndEstadoRevisionOrderByFechaSubidaDesc(
                mantenimientoId, VisibilidadDocumento.COMPARTIDO, EstadoRevisionDocumento.VALIDO).stream()
            .<DocumentoRespuesta>map(this::toInquilinoDto)
            .toList();
    }

    @Transactional
    public DocumentoDto subir(Long mantenimientoId, MultipartFile file, String tipoDocumento,
                              LocalDate fechaDocumento, LocalDate fechaCaducidad, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        Mantenimiento mantenimiento = mantenimientoRepository.findById(mantenimientoId)
            .orElseThrow(RecursoNoDisponibleException::new);

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

    // Si el documento deja de ser VALIDO, la entidad lo vuelve INTERNO en la misma transaccion.
    @Transactional
    public DocumentoDto revisar(Long documentoId, RevisarDocumentoRequest request, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        DocumentoMantenimiento documento = documentoRepository.findById(documentoId)
            .orElseThrow(RecursoNoDisponibleException::new);
        documento.revisar(request.estado(), request.observaciones());
        return toDto(documento);
    }

    @Transactional
    public DocumentoDto cambiarVisibilidad(Long documentoId, CambiarVisibilidadRequest request, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        DocumentoMantenimiento documento = documentoRepository.findById(documentoId)
            .orElseThrow(RecursoNoDisponibleException::new);
        documento.cambiarVisibilidad(request.visibilidad());
        return toDto(documento);
    }

    @Transactional(readOnly = true)
    public DescargaDocumento obtenerDescarga(Long documentoId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        DocumentoMantenimiento documento = documentoRepository.findById(documentoId)
            .orElseThrow(RecursoNoDisponibleException::new);
        if (!autorizacion.puedeLeerDocumento(usuario, documento)) {
            throw new RecursoNoDisponibleException();
        }
        Resource resource = storageService.cargar(documento.getRutaArchivo());
        return new DescargaDocumento(documento.getNombreOriginal(), resource);
    }

    private DocumentoDto toDto(DocumentoMantenimiento d) {
        return new DocumentoDto(
            d.getId(), d.getMantenimiento().getId(), d.getNombreOriginal(), d.getTipoDocumento(),
            d.getFechaDocumento(), d.getFechaCaducidad(), d.getEstadoRevision().name(),
            d.getObservaciones(), d.getFechaSubida(), d.getVisibilidad().name()
        );
    }

    private DocumentoInquilinoDto toInquilinoDto(DocumentoMantenimiento d) {
        return new DocumentoInquilinoDto(
            d.getId(), d.getMantenimiento().getId(), d.getNombreOriginal(), d.getTipoDocumento(),
            d.getFechaDocumento(), d.getFechaCaducidad(), d.getEstadoRevision().name(), d.getFechaSubida()
        );
    }

    public record DescargaDocumento(String nombre, Resource resource) {}
}
