package com.facilityhub.repository;

import com.facilityhub.domain.TipoMantenimiento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// FH-010B / RF-09: catalogo de tipos (lectura). FH-012 / RF-08: gestion del catalogo.
public interface TipoMantenimientoRepository extends JpaRepository<TipoMantenimiento, Long> {
    List<TipoMantenimiento> findByActivoTrueOrderByNombreAsc();

    // FH-012: catalogo completo (activos e inactivos).
    List<TipoMantenimiento> findAllByOrderByNombreAsc();

    boolean existsByCodigo(String codigo);

    // FH-012 (D6): bloqueo de la fila durante la edicion (despues del bloqueo asesor del catalogo).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TipoMantenimiento t where t.id = :id")
    Optional<TipoMantenimiento> findParaEditar(@Param("id") Long id);
}
