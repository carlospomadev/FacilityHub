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

    // FH-006: el local tiene alguna ocupacion vigente (de cualquier inquilino). Requisito para que una
    // obligacion pase a responsabilidad INQUILINO.
    @Query("""
        select count(o) > 0 from OcupacionLocal o
          join o.local l
        where l.id = :localId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
        """)
    boolean existeOcupacionVigenteDelLocal(@Param("localId") Long localId, @Param("hoy") LocalDate hoy);

    // FH-006 / P6-C2: la ocupacion vigente concreta de un inquilino en un local (misma regla que existeOcupacionVigente).
    @Query("""
        select o from OcupacionLocal o
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
    Optional<OcupacionLocal> ocupacionVigente(@Param("localId") Long localId,
                                              @Param("inquilinoId") Long inquilinoId,
                                              @Param("hoy") LocalDate hoy);

    // FH-006 / P6-C2: ocupaciones vigentes del inquilino (con su local), para listar sus obligaciones.
    @Query("""
        select o from OcupacionLocal o
          join fetch o.local l
          join l.centro c
        where o.inquilino.id = :inquilinoId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
          and l.activo = true
          and c.activo = true
        order by l.codigo asc
        """)
    java.util.List<OcupacionLocal> ocupacionesVigentesDeInquilino(@Param("inquilinoId") Long inquilinoId,
                                                                  @Param("hoy") LocalDate hoy);

    // FH-006 / P6-C2: ocupacion vigente del local (cualquier inquilino), para validar asignaciones de gestion en
    // obligaciones INQUILINO.
    @Query("""
        select o from OcupacionLocal o
        where o.local.id = :localId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
        """)
    java.util.List<OcupacionLocal> ocupacionesVigentesDelLocal(@Param("localId") Long localId, @Param("hoy") LocalDate hoy);

    // FH-013 / RF-03 (D4): ocupaciones vigentes en cualquier local del centro (activa, iniciada y sin terminar),
    // independientemente de que el local este activo. Bloquean la desactivacion del centro.
    @Query("""
        select count(o) from OcupacionLocal o
          join o.local l
        where l.centro.id = :centroId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
        """)
    long contarVigentesDelCentro(@Param("centroId") Long centroId, @Param("hoy") java.time.LocalDate hoy);

    // FH-013 / RF-03 (D4, ajuste): ocupaciones futuras del centro (activa, empieza despues de hoy y sin terminar).
    // Tambien bloquean la desactivacion: el inquilino perderia el acceso al empezar. Independiente de las vigentes.
    @Query("""
        select count(o) from OcupacionLocal o
          join o.local l
        where l.centro.id = :centroId
          and o.activo = true
          and o.fechaInicio > :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
        """)
    long contarFuturasDelCentro(@Param("centroId") Long centroId, @Param("hoy") java.time.LocalDate hoy);
}
