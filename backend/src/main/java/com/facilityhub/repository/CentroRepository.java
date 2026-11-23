package com.facilityhub.repository;

import com.facilityhub.domain.Centro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CentroRepository extends JpaRepository<Centro, Long> {
    List<Centro> findByActivoTrueOrderByNombreAsc();

    // FH-010A / RF-07: bloqueo de la fila del centro durante un alta de instalacion. Serializa las altas
    // concurrentes del mismo centro para que la comprobacion de duplicados no tenga carreras (sin migracion).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Centro c where c.id = :id")
    Optional<Centro> findParaAlta(@Param("id") Long id);

    // FH-005A2-B: centros activos con al menos un local activo ocupado hoy por el inquilino
    // (ocupacion activa, fecha_inicio <= hoy, fecha_fin nula o >= hoy). DISTINCT evita duplicados por varios locales.
    @Query("""
        select distinct c from OcupacionLocal o
          join o.local l
          join l.centro c
        where o.inquilino.id = :inquilinoId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
          and l.activo = true
          and c.activo = true
        order by c.nombre asc
        """)
    List<Centro> findVisiblesParaInquilino(@Param("inquilinoId") Long inquilinoId, @Param("hoy") LocalDate hoy);

    // FH-005A2-B: mismas condiciones que findVisiblesParaInquilino, para un centro concreto.
    @Query("""
        select count(o) > 0 from OcupacionLocal o
          join o.local l
          join l.centro c
        where c.id = :centroId
          and o.inquilino.id = :inquilinoId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
          and l.activo = true
          and c.activo = true
        """)
    boolean esVisibleParaInquilino(@Param("centroId") Long centroId,
                                   @Param("inquilinoId") Long inquilinoId,
                                   @Param("hoy") LocalDate hoy);
}
