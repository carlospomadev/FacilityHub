package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mantenimientos", schema = "facilityhub")
public class Mantenimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "obligacion_id") private ObligacionMantenimiento obligacion;
    @Column(name = "fecha_programada", nullable = false) private LocalDate fechaProgramada;
    @Column(name = "fecha_realizacion") private LocalDate fechaRealizacion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoMantenimiento estado = EstadoMantenimiento.PROGRAMADO;
    @Column(columnDefinition = "text") private String descripcion;
    @Column(columnDefinition = "text") private String observaciones;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected Mantenimiento() {}

    public Mantenimiento(ObligacionMantenimiento obligacion, LocalDate fechaProgramada, String descripcion) {
        this.obligacion = obligacion;
        this.fechaProgramada = fechaProgramada;
        this.descripcion = descripcion;
        this.estado = EstadoMantenimiento.PROGRAMADO;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public ObligacionMantenimiento getObligacion() { return obligacion; }
    public LocalDate getFechaProgramada() { return fechaProgramada; }
    public LocalDate getFechaRealizacion() { return fechaRealizacion; }
    public EstadoMantenimiento getEstado() { return estado; }
    public String getDescripcion() { return descripcion; }
    public String getObservaciones() { return observaciones; }

    public void cambiarEstado(EstadoMantenimiento nuevoEstado, LocalDate fechaRealizacion, String observaciones) {
        if (nuevoEstado == EstadoMantenimiento.FINALIZADO && fechaRealizacion == null) {
            throw new IllegalArgumentException("Un mantenimiento finalizado requiere fecha de realizacion");
        }
        this.estado = nuevoEstado;
        this.fechaRealizacion = fechaRealizacion;
        this.observaciones = observaciones;
        this.updatedAt = LocalDateTime.now();
    }
}
