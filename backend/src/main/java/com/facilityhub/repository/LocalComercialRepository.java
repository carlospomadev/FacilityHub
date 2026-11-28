package com.facilityhub.repository;

import com.facilityhub.domain.LocalComercial;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LocalComercialRepository extends JpaRepository<LocalComercial, Long> {
    List<LocalComercial> findByCentroIdAndActivoTrueOrderByCodigoAsc(Long centroId);

    // FH-005A2-B: solo los locales activos del centro ocupados hoy por el inquilino; nunca los de otros inquilinos.
    @Query("""
        select distinct l from OcupacionLocal o
          join o.local l
        where l.centro.id = :centroId
          and o.inquilino.id = :inquilinoId
          and o.activo = true
          and o.fechaInicio <= :hoy
          and (o.fechaFin is null or o.fechaFin >= :hoy)
          and l.activo = true
        order by l.codigo asc
        """)
    List<LocalComercial> findVisiblesEnCentroParaInquilino(@Param("centroId") Long centroId,
                                                           @Param("inquilinoId") Long inquilinoId,
                                                           @Param("hoy") LocalDate hoy);

    // FH-013 / RF-03: uso de un centro (resumen antes de desactivar).
    long countByCentroIdAndActivoTrue(Long centroId);

    long countByCentroId(Long centroId);

    // FH-014 / RF-04: gestion de locales (activos e inactivos) de un centro.
    List<LocalComercial> findByCentroIdOrderByCodigoAsc(Long centroId);

    // FH-014 / RF-04 (D2): el indice uq_local_centro_codigo distingue mayusculas; la comprobacion previa no.
    boolean existsByCentroIdAndCodigoIgnoreCase(Long centroId, String codigo);

    // FH-014 / RF-04 (D7): bloquea la fila del local hasta el commit. Orden fijo en toda la aplicacion: primero la fila
    // del centro (CentroRepository.findParaAlta) y despues la del local. Lo usan la edicion y la (des)activacion del
    // local y las altas de instalaciones, obligaciones y mantenimientos de un local, asi que no se cruzan.
    @Query("select l.centro.id from LocalComercial l where l.id = :id")
    Optional<Long> centroIdDe(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LocalComercial l where l.id = :id")
    Optional<LocalComercial> findParaAlta(@Param("id") Long id);

    @Query("select l.activo from LocalComercial l where l.id = :id")
    Optional<Boolean> activoDe(@Param("id") Long id);

    // FH-014 / RF-04 (D7, D10): bloquea la fila del local y lee su estado despues del bloqueo (consulta escalar: no usa
    // una copia que ya estuviera cargada). Llamar con la fila del centro ya bloqueada.
    default boolean bloquearYEstaActivo(Long id) {
        findParaAlta(id);
        return activoDe(id).orElse(false);
    }
}
