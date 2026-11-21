package com.facilityhub.repository;

import com.facilityhub.domain.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {
    // Insercion idempotente: un duplicado (mismo destinatario, tipo y evento) no hace nada y no aborta la transaccion
    // del evento que la origina. Devuelve 1 si se ha creado y 0 si ya existia.
    @Modifying
    @Query(value = """
        INSERT INTO facilityhub.alertas (destinatario_usuario_id, tipo, evento_ref, mantenimiento_id, obligacion_id,
          intervencion_id, mensaje, canal, created_at)
        VALUES (:destinatario, :tipo, :eventoRef, :mantenimiento, :obligacion, :intervencion, :mensaje, 'APP', :ahora)
        ON CONFLICT (destinatario_usuario_id, tipo, evento_ref) DO NOTHING
        """, nativeQuery = true)
    int insertarSiNoExiste(@Param("destinatario") Long destinatario, @Param("tipo") String tipo,
                           @Param("eventoRef") String eventoRef, @Param("mantenimiento") Long mantenimiento,
                           @Param("obligacion") Long obligacion, @Param("intervencion") Long intervencion,
                           @Param("mensaje") String mensaje, @Param("ahora") LocalDateTime ahora);

    // FH-006 / P6-D2: impide dos ejecuciones de recordatorios a la vez (se libera al terminar la transaccion).
    @Query(value = "SELECT pg_try_advisory_xact_lock(:clave)", nativeQuery = true)
    boolean bloquearEjecucion(@Param("clave") long clave);

    boolean existsByDestinatarioIdAndTipoAndEventoRef(Long destinatarioId, com.facilityhub.domain.TipoAlerta tipo, String eventoRef);

    List<Alerta> findTop100ByDestinatarioIdOrderByCreatedAtDescIdDesc(Long destinatarioId);

    List<Alerta> findTop100ByDestinatarioIdAndLeidaAtIsNullOrderByCreatedAtDescIdDesc(Long destinatarioId);

    long countByDestinatarioIdAndLeidaAtIsNull(Long destinatarioId);

    Optional<Alerta> findByIdAndDestinatarioId(Long id, Long destinatarioId);
}
