package com.facilityhub.repository;

import com.facilityhub.domain.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findByEmailAndActivoTrue(String email);

    boolean existsByEmailIgnoreCase(String email);

    java.util.List<Usuario> findByProveedorIdOrderByNombreAsc(Long proveedorId);

    // FH-006 / P6-D1: destinatarios de alertas (solo usuarios activos).
    @org.springframework.data.jpa.repository.Query("""
        select u from Usuario u join u.rol r where r.codigo = :rol and u.activo = true order by u.id""")
    java.util.List<Usuario> activosConRol(@org.springframework.data.repository.query.Param("rol") String rol);

    @org.springframework.data.jpa.repository.Query("""
        select u from Usuario u join u.proveedor p where p.id = :proveedorId and p.activo = true and u.activo = true order by u.id""")
    java.util.List<Usuario> tecnicosActivos(@org.springframework.data.repository.query.Param("proveedorId") Long proveedorId);

    @org.springframework.data.jpa.repository.Query("""
        select u from Usuario u join u.rol r join u.inquilino i
        where i.id = :inquilinoId and i.activo = true and r.codigo = 'INQUILINO' and u.activo = true order by u.id""")
    java.util.List<Usuario> usuariosActivosDeInquilino(@org.springframework.data.repository.query.Param("inquilinoId") Long inquilinoId);

    // FH-015 / RF-05: usuarios vinculados al inquilino (activos o no), para el resumen de la desactivacion.
    long countByInquilinoId(Long inquilinoId);
}