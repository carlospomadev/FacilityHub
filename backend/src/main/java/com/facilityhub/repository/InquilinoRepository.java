package com.facilityhub.repository;

import com.facilityhub.domain.Inquilino;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InquilinoRepository extends JpaRepository<Inquilino, Long> {

    // FH-015 / RF-05 (D1): listado de gestion, activos e inactivos.
    List<Inquilino> findAllByOrderByRazonSocialAscIdAsc();

    // FH-015 (D2): CIF repetido entre TODOS los inquilinos (tambien inactivos). Se compara normalizado (sin espacios,
    // guiones ni puntos y en mayusculas) para detectar tambien los registros antiguos cargados con otro formato.
    // :excluirId permite ignorar el propio inquilino (-1 en el alta).
    @Query("""
        select count(i) > 0 from Inquilino i
        where i.cif is not null
          and upper(replace(replace(replace(i.cif, ' ', ''), '-', ''), '.', '')) = :cif
          and i.id <> :excluirId
        """)
    boolean existeCif(@Param("cif") String cifNormalizado, @Param("excluirId") Long excluirId);

    // FH-015 (D7): fila del inquilino bloqueada (despues del bloqueo asesor del catalogo de inquilinos).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inquilino i where i.id = :id")
    Optional<Inquilino> findParaGestion(@Param("id") Long id);
}
