package com.facilityhub.repository;

import com.facilityhub.domain.OcupacionLocal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface OcupacionLocalRepository extends JpaRepository<OcupacionLocal, Long> {
    Optional<OcupacionLocal> findByLocalIdAndActivoTrue(Long localId);

    // Vigente: activa, ya iniciada y con fecha de fin inclusiva o sin fin.
    // FH-005A2-B1: ademas el local y su centro deben estar activos (misma regla que los listados de A2-B).
    // Unico uso: DocumentoAutorizacionService.puedeAccederLocal (acceso del INQUILINO).
    @Query("""
        select count(o) > 0 from OcupacionLocal o
          join o.local l
          join l.centro c
        where l.id = :localId
          and o.inquilino.id = :inquilinoId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
          and l.activo = true
          and c.activo = true
        """)
    boolean existeOcupacionVigente(@Param("localId") Long localId,
                                   @Param("inquilinoId") Long inquilinoId,
                                   @Param("hoy") LocalDate hoy);
}
