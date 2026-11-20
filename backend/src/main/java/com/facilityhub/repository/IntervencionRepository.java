package com.facilityhub.repository;

import com.facilityhub.domain.EstadoValidacion;
import com.facilityhub.domain.Intervencion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IntervencionRepository extends JpaRepository<Intervencion, Long> {

    // Envio y validacion bloquean la fila: dos peticiones simultaneas se serializan y la segunda ve el estado nuevo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Intervencion i where i.id = :id")
    Optional<Intervencion> bloquear(@Param("id") Long id);

    List<Intervencion> findByMantenimientoIdOrderByIdDesc(Long mantenimientoId);

    Optional<Intervencion> findFirstByMantenimientoIdAndEstadoValidacionIn(Long mantenimientoId, Collection<EstadoValidacion> estados);

    boolean existsByMantenimientoIdAndEstadoValidacionIn(Long mantenimientoId, Collection<EstadoValidacion> estados);

    @Query("""
        select i from Intervencion i
          join fetch i.mantenimiento m
          join fetch m.obligacion o
          join fetch o.tipoMantenimiento
          join fetch o.centro
          left join fetch o.local
          join fetch i.proveedor
        where i.estadoValidacion = :estado
        order by i.enviadaAt asc, i.id asc
        """)
    List<Intervencion> bandeja(@Param("estado") EstadoValidacion estado);

    // Limite inferior de una nueva ejecucion: la ultima ejecucion aprobada de la obligacion (evita reutilizar una
    // ejecucion ya acreditada).
    @Query("""
        select max(i.fechaEjecucion) from Intervencion i
        where i.mantenimiento.obligacion.id = :obligacionId and i.estadoValidacion = com.facilityhub.domain.EstadoValidacion.APROBADO
        """)
    LocalDate ultimaEjecucionAprobada(@Param("obligacionId") Long obligacionId);
}
