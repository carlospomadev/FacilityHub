package com.facilityhub.repository;

import com.facilityhub.domain.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {
    java.util.List<Mantenimiento> findByObligacionIdOrderByFechaProgramadaDesc(Long obligacionId);

    // FH-006 / P6-D2: candidatos a recordatorio. Obligacion ACTIVA y vigente, mantenimiento no cancelado, sin intervencion
    // APROBADO y con fecha programada dentro de [desde, hasta]. FINALIZADO significa "ejecutado y enviado", no cumplido:
    // solo es candidato si su intervencion espera validacion (los FINALIZADO sin intervencion abierta quedan fuera).
    @Query("""
        select m from Mantenimiento m
          join fetch m.obligacion o
          join fetch o.tipoMantenimiento
          left join fetch o.local
        where o.estado = com.facilityhub.domain.EstadoObligacion.ACTIVA and o.activo = true
          and m.estado <> com.facilityhub.domain.EstadoMantenimiento.CANCELADO
          and (m.estado <> com.facilityhub.domain.EstadoMantenimiento.FINALIZADO
               or exists (select 1 from Intervencion p where p.mantenimiento = m
                          and p.estadoValidacion = com.facilityhub.domain.EstadoValidacion.PENDIENTE_VALIDACION))
          and m.fechaProgramada between :desde and :hasta
          and not exists (select 1 from Intervencion i where i.mantenimiento = m
                          and i.estadoValidacion = com.facilityhub.domain.EstadoValidacion.APROBADO)
        order by m.fechaProgramada, m.id
        """)
    List<Mantenimiento> candidatosRecordatorio(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    // FH-006 / P6-D3: trabajos abiertos del panel de gestion. Mismo criterio de "abierto" que candidatosRecordatorio
    // (FINALIZADO = enviado, no cumplido), sin limite inferior de antiguedad y con centro y local para el listado.
    @Query("""
        select m from Mantenimiento m
          join fetch m.obligacion o
          join fetch o.tipoMantenimiento
          join fetch o.centro
          left join fetch o.local
        where o.estado = com.facilityhub.domain.EstadoObligacion.ACTIVA and o.activo = true
          and m.estado <> com.facilityhub.domain.EstadoMantenimiento.CANCELADO
          and (m.estado <> com.facilityhub.domain.EstadoMantenimiento.FINALIZADO
               or exists (select 1 from Intervencion p where p.mantenimiento = m
                          and p.estadoValidacion = com.facilityhub.domain.EstadoValidacion.PENDIENTE_VALIDACION))
          and m.fechaProgramada <= :hasta
          and not exists (select 1 from Intervencion i where i.mantenimiento = m
                          and i.estadoValidacion = com.facilityhub.domain.EstadoValidacion.APROBADO)
        order by m.fechaProgramada, m.id
        """)
    List<Mantenimiento> abiertosHasta(@Param("hasta") LocalDate hasta);

    // FH-006 / P6-D3-2: mantenimientos de las obligaciones de un local (historial), con tipo e instalacion.
    @Query("""
        select m from Mantenimiento m
          join fetch m.obligacion o
          join fetch o.tipoMantenimiento
          left join fetch o.instalacion
        where o.local.id = :localId
        order by m.fechaProgramada desc, m.id desc
        """)
    List<Mantenimiento> delLocal(@Param("localId") Long localId);

    // FH-013 / RF-03 (D4): mantenimientos ABIERTOS de cualquier obligacion del centro. Mismo criterio de dominio que el
    // panel: no CANCELADO, sin intervencion APROBADO y, ademas, en PROGRAMADO/PENDIENTE/EN_PROCESO o con una intervencion
    // sin resolver (BORRADOR, PENDIENTE_VALIDACION o REQUIERE_CORRECCION; incluye un FINALIZADO enviado sin validar).
    @Query("""
        select count(m) from Mantenimiento m
        where m.obligacion.centro.id = :centroId
          and m.estado <> com.facilityhub.domain.EstadoMantenimiento.CANCELADO
          and not exists (select 1 from Intervencion a where a.mantenimiento = m
                          and a.estadoValidacion = com.facilityhub.domain.EstadoValidacion.APROBADO)
          and (m.estado in (com.facilityhub.domain.EstadoMantenimiento.PROGRAMADO,
                            com.facilityhub.domain.EstadoMantenimiento.PENDIENTE,
                            com.facilityhub.domain.EstadoMantenimiento.EN_PROCESO)
               or exists (select 1 from Intervencion i where i.mantenimiento = m
                          and i.estadoValidacion in (com.facilityhub.domain.EstadoValidacion.BORRADOR,
                                                     com.facilityhub.domain.EstadoValidacion.PENDIENTE_VALIDACION,
                                                     com.facilityhub.domain.EstadoValidacion.REQUIERE_CORRECCION)))
        """)
    long contarAbiertosDelCentro(@Param("centroId") Long centroId);

    // FH-014 / RF-04 (D4): la misma definicion de mantenimiento abierto, para las obligaciones de un local.
    @Query("""
        select count(m) from Mantenimiento m
        where m.obligacion.local.id = :localId
          and m.estado <> com.facilityhub.domain.EstadoMantenimiento.CANCELADO
          and not exists (select 1 from Intervencion a where a.mantenimiento = m
                          and a.estadoValidacion = com.facilityhub.domain.EstadoValidacion.APROBADO)
          and (m.estado in (com.facilityhub.domain.EstadoMantenimiento.PROGRAMADO,
                            com.facilityhub.domain.EstadoMantenimiento.PENDIENTE,
                            com.facilityhub.domain.EstadoMantenimiento.EN_PROCESO)
               or exists (select 1 from Intervencion i where i.mantenimiento = m
                          and i.estadoValidacion in (com.facilityhub.domain.EstadoValidacion.BORRADOR,
                                                     com.facilityhub.domain.EstadoValidacion.PENDIENTE_VALIDACION,
                                                     com.facilityhub.domain.EstadoValidacion.REQUIERE_CORRECCION)))
        """)
    long contarAbiertosDelLocal(@Param("localId") Long localId);

    // FH-014 / RF-04: todos los mantenimientos del local (historial), solo informativo.
    @Query("select count(m) from Mantenimiento m where m.obligacion.local.id = :localId")
    long contarDelLocal(@Param("localId") Long localId);
}
