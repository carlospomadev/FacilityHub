package com.facilityhub.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// FH-006 / P6-C1: evento de auditoria inmutable (trigger en BD). El detalle no incluye datos personales ni contenido.
@Entity
@Table(name = "auditoria_eventos", schema = "facilityhub")
public class AuditoriaEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40, updatable = false) private String entidad;
    @Column(name = "entidad_id", nullable = false, updatable = false) private Long entidadId;
    @Column(nullable = false, length = 40, updatable = false) private String accion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id", updatable = false) private Usuario usuario;
    @Column(nullable = false, updatable = false) private LocalDateTime fecha;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", updatable = false) private String detalle;

    protected AuditoriaEvento() {}

    public AuditoriaEvento(String entidad, Long entidadId, String accion, Usuario usuario, LocalDateTime fecha, String detalle) {
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.accion = accion;
        this.usuario = usuario;
        this.fecha = fecha;
        this.detalle = detalle;
    }

    public Long getId() { return id; }
    public String getEntidad() { return entidad; }
    public Long getEntidadId() { return entidadId; }
    public String getAccion() { return accion; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFecha() { return fecha; }
    public String getDetalle() { return detalle; }
}
