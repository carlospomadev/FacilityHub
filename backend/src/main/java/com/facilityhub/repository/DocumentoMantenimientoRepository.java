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
}
