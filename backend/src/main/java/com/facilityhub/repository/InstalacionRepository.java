package com.facilityhub.repository;

import com.facilityhub.domain.Instalacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstalacionRepository extends JpaRepository<Instalacion, Long> {
    java.util.List<Instalacion> findByLocalIdOrderByNombreAsc(Long localId);
}
