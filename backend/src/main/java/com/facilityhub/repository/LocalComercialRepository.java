package com.facilityhub.repository;

import com.facilityhub.domain.LocalComercial;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalComercialRepository extends JpaRepository<LocalComercial, Long> {
    java.util.List<LocalComercial> findByCentroIdAndActivoTrueOrderByCodigoAsc(Long centroId);
}
