package com.facilityhub.repository;

import com.facilityhub.domain.ObligacionMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObligacionMantenimientoRepository extends JpaRepository<ObligacionMantenimiento, Long> {
    java.util.List<ObligacionMantenimiento> findByLocalIdAndActivoTrueOrderByProximaFechaAsc(Long localId);
}
