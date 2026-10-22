package com.facilityhub.repository;

import com.facilityhub.domain.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {
    java.util.List<Mantenimiento> findByObligacionIdOrderByFechaProgramadaDesc(Long obligacionId);
}
