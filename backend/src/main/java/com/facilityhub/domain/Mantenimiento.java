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
    // FH-006: bloqueo optimista (409 ante modificaciones concurrentes).
    @Version @Column(nullable = false) private long version;
    // FH-006: FALSE en los registros P5 (historico sin validacion P6); TRUE en los nuevos.
    @Column(name = "validacion_requerida", nullable = false) private boolean validacionRequerida = true;
    // FH-006 / P6-C2: ocupacion responsable (obligaciones INQUILINO) y fecha prevista indicada por el inquilino.
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ocupacion_id") private OcupacionLocal ocupacion;
    @Column(name = "fecha_prevista_inquilino") private LocalDate fechaPrevistaInquilino;

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
    public long getVersion() { return version; }
    public boolean isValidacionRequerida() { return validacionRequerida; }
    public OcupacionLocal getOcupacion() { return ocupacion; }
    public LocalDate getFechaPrevistaInquilino() { return fechaPrevistaInquilino; }

    // Fecha del hecho para el control historico del inquilino: la realizacion si consta, si no la programada.
    public LocalDate fechaDelHecho() { return fechaRealizacion != null ? fechaRealizacion : fechaProgramada; }

    // P6-C2: el primer acto del inquilino sobre el mantenimiento lo vincula a su ocupacion; nunca se reasigna a otra.
    public void vincularOcupacion(OcupacionLocal ocupacion, LocalDateTime ahora) {
        if (this.ocupacion != null && !this.ocupacion.getId().equals(ocupacion.getId())) {
            throw new IllegalStateException("El mantenimiento pertenece a otra ocupacion");
        }
        if (this.ocupacion == null) {
            this.ocupacion = ocupacion;
            this.updatedAt = ahora;
        }
    }

    public void fijarFechaPrevista(LocalDate fecha, LocalDateTime ahora) {
        this.fechaPrevistaInquilino = fecha;
        this.updatedAt = ahora;
    }

    // FH-006 / P6-C1: transiciones operativas ligadas a las intervenciones (no tocan las observaciones internas).
    public void iniciarEjecucion(LocalDateTime ahora) {
        if (estado == EstadoMantenimiento.PROGRAMADO || estado == EstadoMantenimiento.PENDIENTE) {
            this.estado = EstadoMantenimiento.EN_PROCESO;
            this.updatedAt = ahora;
        }
    }

    // Envio de una ejecucion realizada: FINALIZADO significa "ejecutado y enviado", no cumplido (lo decide la validacion).
    public void registrarEjecucion(LocalDate fechaEjecucion, LocalDateTime ahora) {
        this.estado = EstadoMantenimiento.FINALIZADO;
        this.fechaRealizacion = fechaEjecucion;
        this.updatedAt = ahora;
    }

    // Correccion solicitada: la ejecucion deja de considerarse finalizada hasta un nuevo envio.
    public void reabrirEjecucion(LocalDateTime ahora) {
        this.estado = EstadoMantenimiento.EN_PROCESO;
        this.fechaRealizacion = null;
        this.updatedAt = ahora;
    }

    // NO_REALIZADO: queda pendiente de reprogramar; nunca FINALIZADO.
    public void marcarNoRealizado(LocalDateTime ahora) {
        this.estado = EstadoMantenimiento.PROGRAMADO;
        this.fechaRealizacion = null;
        this.updatedAt = ahora;
    }

    // FH-006: estados en los que un mantenimiento admite (re)asignacion de proveedor.
    public boolean admiteAsignacion() {
        return estado == EstadoMantenimiento.PROGRAMADO
            || estado == EstadoMantenimiento.PENDIENTE
            || estado == EstadoMantenimiento.EN_PROCESO;
    }

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
