package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "obligaciones_mantenimiento", schema = "facilityhub")
public class ObligacionMantenimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "local_id") private LocalComercial local;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "instalacion_id") private Instalacion instalacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tipo_mantenimiento_id") private TipoMantenimiento tipoMantenimiento;
    @Column(name = "fecha_inicio", nullable = false) private LocalDate fechaInicio;
    @Column(name = "proxima_fecha", nullable = false) private LocalDate proximaFecha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoObligacion estado = EstadoObligacion.ACTIVA;
    @Column(nullable = false) private boolean activo = true;
    @Column(columnDefinition = "text") private String observaciones;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected ObligacionMantenimiento() {}
    public Long getId() { return id; }
    public LocalComercial getLocal() { return local; }
    public Instalacion getInstalacion() { return instalacion; }
    public TipoMantenimiento getTipoMantenimiento() { return tipoMantenimiento; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getProximaFecha() { return proximaFecha; }
    public EstadoObligacion getEstado() { return estado; }
    public boolean isActivo() { return activo; }
    public String getObservaciones() { return observaciones; }
    public void actualizarProximaFecha(LocalDate proximaFecha) { this.proximaFecha = proximaFecha; }
}
