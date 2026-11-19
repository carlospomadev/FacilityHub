package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// FH-006 / P6-B: asignacion de un mantenimiento a una empresa proveedora (y opcionalmente a un tecnico).
// Una sola activa por mantenimiento (indice unico parcial en BD). Cerrar una asignacion conserva su historico.
@Entity
@Table(name = "asignaciones_mantenimiento", schema = "facilityhub")
public class AsignacionMantenimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mantenimiento_id") private Mantenimiento mantenimiento;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "proveedor_id") private Proveedor proveedor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tecnico_usuario_id") private Usuario tecnico;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "asignado_por_usuario_id") private Usuario asignadoPor;
    @Column(name = "asignado_at", nullable = false) private LocalDateTime asignadoAt;
    @Column(nullable = false) private boolean activa = true;
    @Column(name = "finalizada_at") private LocalDateTime finalizadaAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "finalizada_por_usuario_id") private Usuario finalizadaPor;
    @Enumerated(EnumType.STRING) @Column(name = "motivo_fin", length = 20) private MotivoFinAsignacion motivoFin;

    protected AsignacionMantenimiento() {}

    public AsignacionMantenimiento(Mantenimiento mantenimiento, Proveedor proveedor, Usuario tecnico,
                                   Usuario asignadoPor, LocalDateTime asignadoAt) {
        this.mantenimiento = mantenimiento;
        this.proveedor = proveedor;
        this.tecnico = tecnico;
        this.asignadoPor = asignadoPor;
        this.asignadoAt = asignadoAt;
        this.activa = true;
    }

    public Long getId() { return id; }
    public Mantenimiento getMantenimiento() { return mantenimiento; }
    public Proveedor getProveedor() { return proveedor; }
    public Usuario getTecnico() { return tecnico; }
    public Usuario getAsignadoPor() { return asignadoPor; }
    public LocalDateTime getAsignadoAt() { return asignadoAt; }
    public boolean isActiva() { return activa; }
    public LocalDateTime getFinalizadaAt() { return finalizadaAt; }
    public Usuario getFinalizadaPor() { return finalizadaPor; }
    public MotivoFinAsignacion getMotivoFin() { return motivoFin; }

    public void finalizar(MotivoFinAsignacion motivo, Usuario usuario, LocalDateTime cuando) {
        if (!activa) {
            throw new IllegalStateException("La asignacion ya esta finalizada");
        }
        this.activa = false;
        this.motivoFin = motivo;
        this.finalizadaPor = usuario;
        this.finalizadaAt = cuando;
    }
}
