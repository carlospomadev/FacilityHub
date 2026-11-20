package com.facilityhub.repository;

import com.facilityhub.domain.AuditoriaEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, Long> {
    List<AuditoriaEvento> findByEntidadAndEntidadIdOrderByFechaAscIdAsc(String entidad, Long entidadId);
}
