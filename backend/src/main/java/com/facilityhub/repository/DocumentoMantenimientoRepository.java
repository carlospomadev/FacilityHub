package com.facilityhub.repository;

import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoRevisionDocumento;
import com.facilityhub.domain.VisibilidadDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoMantenimientoRepository extends JpaRepository<DocumentoMantenimiento, Long> {
    List<DocumentoMantenimiento> findByMantenimientoIdOrderByFechaSubidaDesc(Long mantenimientoId);

    List<DocumentoMantenimiento> findByMantenimientoIdAndVisibilidadAndEstadoRevisionOrderByFechaSubidaDesc(
        Long mantenimientoId, VisibilidadDocumento visibilidad, EstadoRevisionDocumento estadoRevision);

    // FH-006 / P6-C1: evidencias y certificados de una intervencion.
    List<DocumentoMantenimiento> findByIntervencionIdOrderByFechaSubidaAscIdAsc(Long intervencionId);

    long countByIntervencionId(Long intervencionId);

    // FH-006 / P6-D3-2: documentos de un lote de mantenimientos o intervenciones (historial, sin N+1).
    List<DocumentoMantenimiento> findByMantenimientoIdInOrderByFechaSubidaAscIdAsc(java.util.Collection<Long> mantenimientoIds);

    List<DocumentoMantenimiento> findByIntervencionIdInOrderByFechaSubidaAscIdAsc(java.util.Collection<Long> intervencionIds);
}
