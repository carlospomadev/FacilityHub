package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// FH-006 / P6-D1: alerta interna (canal APP) de un usuario. Se crea solo con AlertaRepository.insertarSiNoExiste
// (idempotente por destinatario, tipo y evento); la entidad se usa para leer y marcar como leida.
@Entity
@Table(name = "alertas", schema = "facilityhub")
public class Alerta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "destinatario_usuario_id", nullable = false) private Long destinatarioId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private TipoAlerta tipo;
    @Column(name = "evento_ref", nullable = false, length = 120) private String eventoRef;
    @Column(name = "mantenimiento_id") private Long mantenimientoId;
    @Column(name = "obligacion_id") private Long obligacionId;
    @Column(name = "intervencion_id") private Long intervencionId;
    @Column(nullable = false, length = 300) private String mensaje;
    @Column(nullable = false, length = 10) private String canal;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "leida_at") private LocalDateTime leidaAt;

    protected Alerta() {}

    public Long getId() { return id; }
    public Long getDestinatarioId() { return destinatarioId; }
    public TipoAlerta getTipo() { return tipo; }
    public String getEventoRef() { return eventoRef; }
    public Long getMantenimientoId() { return mantenimientoId; }
    public Long getObligacionId() { return obligacionId; }
    public Long getIntervencionId() { return intervencionId; }
    public String getMensaje() { return mensaje; }
    public String getCanal() { return canal; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLeidaAt() { return leidaAt; }

    // Idempotente: la primera lectura queda registrada.
    public void marcarLeida(LocalDateTime ahora) {
        if (leidaAt == null) {
            leidaAt = ahora;
        }
    }
}
