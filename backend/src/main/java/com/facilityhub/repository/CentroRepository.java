package com.facilityhub.repository;

import com.facilityhub.domain.Centro;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CentroRepository extends JpaRepository<Centro, Long> {
    java.util.List<Centro> findByActivoTrueOrderByNombreAsc();
}
