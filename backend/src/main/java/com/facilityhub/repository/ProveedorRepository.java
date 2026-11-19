package com.facilityhub.repository;

import com.facilityhub.domain.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    List<Proveedor> findAllByOrderByRazonSocialAsc();
}
