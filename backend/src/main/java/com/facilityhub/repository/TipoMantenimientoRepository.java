package com.facilityhub.repository;

import com.facilityhub.domain.TipoMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// FH-010B / RF-09: catalogo de tipos de mantenimiento (solo lectura; RF-08 queda fuera de alcance).
public interface TipoMantenimientoRepository extends JpaRepository<TipoMantenimiento, Long> {
    List<TipoMantenimiento> findByActivoTrueOrderByNombreAsc();
}
