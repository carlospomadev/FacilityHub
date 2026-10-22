package com.facilityhub.repository;

import com.facilityhub.domain.DocumentoMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoMantenimientoRepository extends JpaRepository<DocumentoMantenimiento, Long> {
    List<DocumentoMantenimiento> findByMantenimientoIdOrderByFechaSubidaDesc(Long mantenimientoId);
}
