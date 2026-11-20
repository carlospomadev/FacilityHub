package com.facilityhub.repository;

import com.facilityhub.domain.EstadoDeclaracionProveedor;
import com.facilityhub.domain.ObligacionProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ObligacionProveedorRepository extends JpaRepository<ObligacionProveedor, Long> {
    Optional<ObligacionProveedor> findByObligacionIdAndActivoTrue(Long obligacionId);

    // Ultima declaracion de una ocupacion para la obligacion (activa o ya revisada), para mostrarla al inquilino.
    Optional<ObligacionProveedor> findFirstByObligacionIdAndOcupacionIdOrderByIdDesc(Long obligacionId, Long ocupacionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ObligacionProveedor d where d.id = :id")
    Optional<ObligacionProveedor> bloquear(@Param("id") Long id);

    @Query("""
        select d from ObligacionProveedor d
          join fetch d.obligacion o
          join fetch o.tipoMantenimiento
          join fetch o.local
          join fetch d.proveedor
          join fetch d.ocupacion oc
          join fetch oc.inquilino
        where d.activo = true and d.estado = :estado
        order by d.declaradoAt asc, d.id asc
        """)
    List<ObligacionProveedor> bandeja(@Param("estado") EstadoDeclaracionProveedor estado);
}
