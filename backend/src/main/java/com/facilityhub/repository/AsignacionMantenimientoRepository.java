package com.facilityhub.repository;

import com.facilityhub.domain.AsignacionMantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AsignacionMantenimientoRepository extends JpaRepository<AsignacionMantenimiento, Long> {

    Optional<AsignacionMantenimiento> findByMantenimientoIdAndActivaTrue(Long mantenimientoId);

    List<AsignacionMantenimiento> findByProveedorIdAndActivaTrue(Long proveedorId);

    // FH-006 / P6-D3: asignaciones activas de un lote de mantenimientos (una consulta; lo que no aparece esta sin asignar).
    @Query("""
        select a from AsignacionMantenimiento a
          join fetch a.proveedor
        where a.activa = true and a.mantenimiento.id in :mantenimientoIds
        """)
    List<AsignacionMantenimiento> activasDeMantenimientos(@Param("mantenimientoIds") java.util.Collection<Long> mantenimientoIds);

    // Trabajos visibles para un proveedor: solo asignaciones activas de su empresa, con los datos que muestra el DTO.
    @Query("""
        select a from AsignacionMantenimiento a
          join fetch a.mantenimiento m
          join fetch m.obligacion o
          join fetch o.tipoMantenimiento
          join fetch o.centro
          left join fetch o.local
          left join fetch o.instalacion
          left join fetch a.tecnico
        where a.proveedor.id = :proveedorId and a.activa = true
        order by m.fechaProgramada asc, m.id asc
        """)
    List<AsignacionMantenimiento> trabajosActivosDeProveedor(@Param("proveedorId") Long proveedorId);

    @Query("""
        select a from AsignacionMantenimiento a
          join fetch a.mantenimiento m
          join fetch m.obligacion o
          join fetch o.tipoMantenimiento
          join fetch o.centro
          left join fetch o.local
          left join fetch o.instalacion
          left join fetch a.tecnico
        where a.proveedor.id = :proveedorId and a.activa = true and m.id = :mantenimientoId
        """)
    Optional<AsignacionMantenimiento> trabajoActivoDeProveedor(@Param("proveedorId") Long proveedorId,
                                                              @Param("mantenimientoId") Long mantenimientoId);
}
