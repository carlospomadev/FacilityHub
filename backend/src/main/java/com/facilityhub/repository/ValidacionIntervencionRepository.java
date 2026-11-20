package com.facilityhub.repository;

import com.facilityhub.domain.ValidacionIntervencion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidacionIntervencionRepository extends JpaRepository<ValidacionIntervencion, Long> {
    List<ValidacionIntervencion> findByIntervencionIdOrderByFechaAscIdAsc(Long intervencionId);
}
