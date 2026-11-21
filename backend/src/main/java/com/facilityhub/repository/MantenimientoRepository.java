package com.facilityhub.repository;

import com.facilityhub.domain.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {
    java.util.List<Mantenimiento> findByObligacionIdOrderByFechaProgramadaDesc(Long obligacionId);

    // FH-006 / P6-D2: candidatos a recordatorio. Obligacion ACTIVA y vigente, mantenimiento abierto (ni cancelado ni
    // finalizado), sin intervencion APROBADO y con fecha programada dentro de [desde, hasta].
    @Query("""
        select m from Mantenimiento m
          join fetch m.obligacion o
          join fetch o.tipoMantenimiento
          left join fetch o.local
        where o.estado = com.facilityhub.domain.EstadoObligacion.ACTIVA and o.activo = true
          and m.estado not in (com.facilityhub.domain.EstadoMantenimiento.CANCELADO,
                               com.facilityhub.domain.EstadoMantenimiento.FINALIZADO)
          and m.fechaProgramada between :desde and :hasta
          and not exists (select 1 from Intervencion i where i.mantenimiento = m
                          and i.estadoValidacion = com.facilityhub.domain.EstadoValidacion.APROBADO)
        order by m.fechaProgramada, m.id
        """)
    List<Mantenimiento> candidatosRecordatorio(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
