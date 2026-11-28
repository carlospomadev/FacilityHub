package com.facilityhub.repository;

import com.facilityhub.domain.EstadoSolicitudProveedor;
import com.facilityhub.domain.SolicitudProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SolicitudProveedorRepository extends JpaRepository<SolicitudProveedor, Long> {
    List<SolicitudProveedor> findByInquilinoIdOrderByIdDesc(Long inquilinoId);

    List<SolicitudProveedor> findByEstadoOrderByIdAsc(EstadoSolicitudProveedor estado);

    List<SolicitudProveedor> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudProveedor s where s.id = :id")
    Optional<SolicitudProveedor> bloquear(@Param("id") Long id);

    // FH-015 / RF-05: solicitudes del inquilino en un estado (resumen de la desactivacion, solo informativo).
    long countByInquilinoIdAndEstado(Long inquilinoId, EstadoSolicitudProveedor estado);
}
