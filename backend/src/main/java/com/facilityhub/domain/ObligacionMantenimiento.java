package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "obligaciones_mantenimiento", schema = "facilityhub")
public class ObligacionMantenimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    // FH-006: centro obligatorio; local NULL = zona comun. Responsable contractual explicito (NHOOD / INQUILINO).
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "centro_id") private Centro centro;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "local_id") private LocalComercial local;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ResponsableObligacion responsable;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "instalacion_id") private Instalacion instalacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tipo_mantenimiento_id") private TipoMantenimiento tipoMantenimiento;
    @Column(name = "fecha_inicio", nullable = false) private LocalDate fechaInicio;
    @Column(name = "proxima_fecha", nullable = false) private LocalDate proximaFecha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoObligacion estado = EstadoObligacion.ACTIVA;
    @Column(nullable = false) private boolean activo = true;
    @Column(columnDefinition = "text") private String observaciones;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected ObligacionMantenimiento() {}

    // FH-010B / RF-09: alta desde la API (estado ACTIVA, activo = true). Las reglas las valida el servicio; la BD las
    // garantiza (FK compuestas, ck_obligacion_responsable_local, ck_proxima_fecha, trigger de coherencia e indices unicos).
    public ObligacionMantenimiento(Centro centro, LocalComercial local, Instalacion instalacion, TipoMantenimiento tipo,
                                   ResponsableObligacion responsable, LocalDate fechaInicio, LocalDate proximaFecha,
                                   String observaciones, LocalDateTime createdAt) {
        this.centro = centro;
        this.local = local;
        this.instalacion = instalacion;
        this.tipoMantenimiento = tipo;
        this.responsable = responsable;
        this.fechaInicio = fechaInicio;
        this.proximaFecha = proximaFecha;
        this.observaciones = observaciones;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Centro getCentro() { return centro; }
    public LocalComercial getLocal() { return local; }
    public ResponsableObligacion getResponsable() { return responsable; }
    public boolean esZonaComun() { return local == null; }

    // La validacion de negocio (ocupacion vigente para INQUILINO) la hace el servicio; la BD impide INQUILINO sin local.
    public void cambiarResponsable(ResponsableObligacion responsable) {
        if (responsable == null) {
            throw new IllegalArgumentException("El responsable es obligatorio");
        }
        if (responsable == ResponsableObligacion.INQUILINO && local == null) {
            throw new IllegalArgumentException("Una obligacion de zona comun no puede ser responsabilidad del inquilino");
        }
        this.responsable = responsable;
    }
    public Instalacion getInstalacion() { return instalacion; }
    public TipoMantenimiento getTipoMantenimiento() { return tipoMantenimiento; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getProximaFecha() { return proximaFecha; }
    public EstadoObligacion getEstado() { return estado; }
    public boolean isActivo() { return activo; }
    public String getObservaciones() { return observaciones; }
    public void actualizarProximaFecha(LocalDate proximaFecha) { this.proximaFecha = proximaFecha; }
}
