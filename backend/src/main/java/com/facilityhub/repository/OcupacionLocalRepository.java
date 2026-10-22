package com.facilityhub.repository;

import com.facilityhub.domain.OcupacionLocal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OcupacionLocalRepository extends JpaRepository<OcupacionLocal, Long> {
    Optional<OcupacionLocal> findByLocalIdAndActivoTrue(Long localId);
}
