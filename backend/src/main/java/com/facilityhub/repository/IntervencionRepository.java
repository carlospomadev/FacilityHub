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

    // FH-012 / RF-08 (D2): intervenciones abiertas de un tipo que quedarian bloqueadas al exigir documento. Abiertas =
    // aun por enviar (BORRADOR o REQUIERE_CORRECCION); el envio exige evidencia solo si el resultado es ejecutado, asi
    // que cuentan las de resultado nulo (aun sin decidir) o distinto de NO_REALIZADO, y sin ningun documento adjunto.
    @Query("""
        select count(i) from Intervencion i
        where i.mantenimiento.obligacion.tipoMantenimiento.id = :tipoId
          and i.estadoValidacion in (com.facilityhub.domain.EstadoValidacion.BORRADOR,
                                     com.facilityhub.domain.EstadoValidacion.REQUIERE_CORRECCION)
          and (i.resultado is null or i.resultado <> com.facilityhub.domain.ResultadoIntervencion.NO_REALIZADO)
          and not exists (select d.id from DocumentoMantenimiento d where d.intervencion = i)
        """)
    long contarAbiertasSinDocumento(@Param("tipoId") Long tipoId);

    List<Intervencion> findByMantenimientoIdOrderByIdDesc(Long mantenimientoId);

    Optional<Intervencion> findFirstByMantenimientoIdAndEstadoValidacionIn(Long mantenimientoId, Collection<EstadoValidacion> estados);

    boolean existsByMantenimientoIdAndEstadoValidacionIn(Long mantenimientoId, Collection<EstadoValidacion> estados);

    // FH-006 / P6-D3: intervenciones en los estados indicados de un lote de mantenimientos (una consulta, sin N+1).
    List<Intervencion> findByMantenimientoIdInAndEstadoValidacionIn(Collection<Long> mantenimientoIds,
                                                                    Collection<EstadoValidacion> estados);

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

    // FH-006 / P6-D3-2: intervenciones cerradas (historial) de un lote de mantenimientos, con su proveedor.
    @Query("""
        select i from Intervencion i
          join fetch i.proveedor
        where i.mantenimiento.id in :mantenimientoIds and i.estadoValidacion in :estados
        order by i.fechaEjecucion desc, i.id desc
        """)
    List<Intervencion> historialDeMantenimientos(@Param("mantenimientoIds") Collection<Long> mantenimientoIds,
                                                 @Param("estados") Collection<EstadoValidacion> estados);

    // Limite inferior de una nueva ejecucion: la ultima ejecucion aprobada de la obligacion (evita reutilizar una
    // ejecucion ya acreditada).
    @Query("""
        select max(i.fechaEjecucion) from Intervencion i
        where i.mantenimiento.obligacion.id = :obligacionId and i.estadoValidacion = com.facilityhub.domain.EstadoValidacion.APROBADO
        """)
    LocalDate ultimaEjecucionAprobada(@Param("obligacionId") Long obligacionId);
}
