package com.facilityhub.repository;

import com.facilityhub.domain.Instalacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InstalacionRepository extends JpaRepository<Instalacion, Long> {
    List<Instalacion> findByLocalIdOrderByNombreAsc(Long localId);

    // FH-010A / RF-07: todas las instalaciones del centro; primero la zona comun, despues por codigo de local.
    @Query("""
        select i from Instalacion i
          left join fetch i.local l
        where i.centro.id = :centroId
        order by case when l.id is null then 0 else 1 end, l.codigo, i.nombre
        """)
    List<Instalacion> findDelCentro(@Param("centroId") Long centroId);

    // FH-010A / RF-07: duplicado = mismo nombre (sin distinguir mayusculas) en la misma ubicacion.
    boolean existsByLocalIdAndNombreIgnoreCase(Long localId, String nombre);

    boolean existsByCentroIdAndLocalIsNullAndNombreIgnoreCase(Long centroId, String nombre);
}
