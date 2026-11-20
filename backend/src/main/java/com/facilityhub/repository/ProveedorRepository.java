package com.facilityhub.repository;

import com.facilityhub.domain.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    List<Proveedor> findAllByOrderByRazonSocialAsc();

    // FH-006 / P6-C2: busqueda exacta por CIF para que el inquilino declare un proveedor sin listar el catalogo.
    java.util.Optional<Proveedor> findFirstByCifIgnoreCaseAndActivoTrue(String cif);
}
