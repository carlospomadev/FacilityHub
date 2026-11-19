package com.facilityhub.repository;

import com.facilityhub.domain.LocalComercial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LocalComercialRepository extends JpaRepository<LocalComercial, Long> {
    List<LocalComercial> findByCentroIdAndActivoTrueOrderByCodigoAsc(Long centroId);

    // FH-005A2-B: solo los locales activos del centro ocupados hoy por el inquilino; nunca los de otros inquilinos.
    @Query("""
        select distinct l from OcupacionLocal o
          join o.local l
        where l.centro.id = :centroId
          and o.inquilino.id = :inquilinoId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
          and l.activo = true
        order by l.codigo asc
        """)
    List<LocalComercial> findVisiblesEnCentroParaInquilino(@Param("centroId") Long centroId,
                                                           @Param("inquilinoId") Long inquilinoId,
                                                           @Param("hoy") LocalDate hoy);
}
