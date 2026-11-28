package com.facilityhub.repository;

import com.facilityhub.domain.ObligacionMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ObligacionMantenimientoRepository extends JpaRepository<ObligacionMantenimiento, Long> {
    List<ObligacionMantenimiento> findByLocalIdAndActivoTrueOrderByProximaFechaAsc(Long localId);

    // FH-010B / RF-09: misma clave que el indice uq_obligacion_activa (local, instalacion o 0, tipo; solo activas).
    @Query("""
        select count(o) > 0 from ObligacionMantenimiento o
          left join o.instalacion i
        where o.activo = true
          and o.local.id = :localId
          and o.tipoMantenimiento.id = :tipoId
          and coalesce(i.id, 0) = :instalacionId
        """)
    boolean existeActivaEnLocal(@Param("localId") Long localId, @Param("instalacionId") Long instalacionId,
                                @Param("tipoId") Long tipoId);

    // FH-010B / RF-09: misma clave que el indice V10 uq_obligacion_activa_zona_comun (centro, instalacion o 0, tipo).
    @Query("""
        select count(o) > 0 from ObligacionMantenimiento o
          left join o.instalacion i
        where o.activo = true
          and o.local is null
          and o.centro.id = :centroId
          and o.tipoMantenimiento.id = :tipoId
          and coalesce(i.id, 0) = :instalacionId
        """)
    boolean existeActivaEnZonaComun(@Param("centroId") Long centroId, @Param("instalacionId") Long instalacionId,
                                    @Param("tipoId") Long tipoId);

    // FH-010B / RF-09: obligaciones activas del centro; primero la zona comun, despues por codigo de local.
    @Query("""
        select o from ObligacionMantenimiento o
          left join fetch o.local l
          left join fetch o.instalacion
          join fetch o.tipoMantenimiento t
        where o.centro.id = :centroId
          and o.activo = true
        order by case when l.id is null then 0 else 1 end, l.codigo, o.proximaFecha, t.nombre
        """)
    List<ObligacionMantenimiento> findActivasDelCentro(@Param("centroId") Long centroId);

    // FH-012 / RF-08: obligaciones activas que usan un tipo (aviso de impacto) y recuento por tipo (catalogo).
    long countByTipoMantenimientoIdAndActivoTrue(Long tipoId);

    @Query("select o.tipoMantenimiento.id, count(o) from ObligacionMantenimiento o where o.activo = true group by o.tipoMantenimiento.id")
    List<Object[]> contarActivasPorTipo();

    // FH-013 / RF-03: uso de un centro (resumen antes de desactivar).
    long countByCentroIdAndActivoTrue(Long centroId);
}
