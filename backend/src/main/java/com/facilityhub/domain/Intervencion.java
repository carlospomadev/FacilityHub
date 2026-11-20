package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// FH-006 / P6-C1: ejecucion registrada de un mantenimiento y su ciclo de validacion documental.
// Reglas de estado en la entidad (y CHECK en BD); los permisos los decide PoliticaAccesoService.
@Entity
@Table(name = "intervenciones", schema = "facilityhub")
public class Intervencion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mantenimiento_id") private Mantenimiento mantenimiento;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "proveedor_id") private Proveedor proveedor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tecnico_usuario_id") private Usuario tecnico;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OrigenIntervencion origen;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "registrada_por_usuario_id") private Usuario registradaPor;
    @Column(name = "numero_envio", nullable = false) private int numeroEnvio;
    @Column(name = "fecha_ejecucion") private LocalDate fechaEjecucion;
    @Column(columnDefinition = "text") private String actuaciones;
    @Enumerated(EnumType.STRING) @Column(length = 20) private ResultadoIntervencion resultado;
    @Column(columnDefinition = "text") private String incidencias;
    @Enumerated(EnumType.STRING) @Column(name = "estado_validacion", nullable = false, length = 25)
    private EstadoValidacion estadoValidacion = EstadoValidacion.BORRADOR;
    @Column(name = "ejecucion_anticipada", nullable = false) private boolean ejecucionAnticipada;
    @Column(name = "enviada_at") private LocalDateTime enviadaAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "enviada_por_usuario_id") private Usuario enviadaPor;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected Intervencion() {}

    public Intervencion(Mantenimiento mantenimiento, Proveedor proveedor, Usuario tecnico, OrigenIntervencion origen,
                        Usuario registradaPor, LocalDateTime ahora) {
        this.mantenimiento = mantenimiento;
        this.proveedor = proveedor;
        this.tecnico = tecnico;
        this.origen = origen;
        this.registradaPor = registradaPor;
        this.estadoValidacion = EstadoValidacion.BORRADOR;
        this.createdAt = ahora;
        this.updatedAt = ahora;
    }

    public Long getId() { return id; }
    public Mantenimiento getMantenimiento() { return mantenimiento; }
    public Proveedor getProveedor() { return proveedor; }
    public Usuario getTecnico() { return tecnico; }
    public OrigenIntervencion getOrigen() { return origen; }
    public Usuario getRegistradaPor() { return registradaPor; }
    public int getNumeroEnvio() { return numeroEnvio; }
    public LocalDate getFechaEjecucion() { return fechaEjecucion; }
    public String getActuaciones() { return actuaciones; }
    public ResultadoIntervencion getResultado() { return resultado; }
    public String getIncidencias() { return incidencias; }
    public EstadoValidacion getEstadoValidacion() { return estadoValidacion; }
    public boolean isEjecucionAnticipada() { return ejecucionAnticipada; }
    public LocalDateTime getEnviadaAt() { return enviadaAt; }
    public Usuario getEnviadaPor() { return enviadaPor; }
    public long getVersion() { return version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    // Fecha en que se introdujo en FacilityHub (distinta de fechaEjecucion, la fecha real de la visita).
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Solo en BORRADOR o REQUIERE_CORRECCION. Los null no cambian el valor actual.
    public void actualizar(LocalDate fechaEjecucion, String actuaciones, ResultadoIntervencion resultado,
                           String incidencias, LocalDateTime ahora) {
        exigirEditable();
        if (fechaEjecucion != null) this.fechaEjecucion = fechaEjecucion;
        if (actuaciones != null) this.actuaciones = actuaciones;
        if (resultado != null) this.resultado = resultado;
        if (incidencias != null) this.incidencias = incidencias;
        this.updatedAt = ahora;
    }

    // Presentacion para validacion. Las comprobaciones de fechas y documentos las hace el servicio antes de llamar.
    public void enviar(Usuario usuario, LocalDateTime ahora, LocalDate fechaProgramada) {
        exigirEditable();
        if (fechaEjecucion == null) throw new IllegalArgumentException("La fecha de ejecucion es obligatoria");
        if (resultado == null) throw new IllegalArgumentException("El resultado es obligatorio");
        if (resultado != ResultadoIntervencion.CONFORME && (incidencias == null || incidencias.isBlank())) {
            throw new IllegalArgumentException("Las incidencias son obligatorias si el resultado no es CONFORME");
        }
        this.numeroEnvio++;
        this.estadoValidacion = EstadoValidacion.PENDIENTE_VALIDACION;
        this.ejecucionAnticipada = fechaProgramada != null && fechaEjecucion.isBefore(fechaProgramada);
        this.enviadaAt = ahora;
        this.enviadaPor = usuario;
        this.updatedAt = ahora;
    }

    public void aprobar(LocalDateTime ahora) {
        exigirPendiente();
        if (resultado == null || !resultado.ejecutado()) {
            throw new IllegalStateException("Una intervencion NO_REALIZADO no puede aprobarse como cumplimiento");
        }
        this.estadoValidacion = EstadoValidacion.APROBADO;
        this.updatedAt = ahora;
    }

    // Cierre administrativo de un intento fallido: no declara cumplida la obligacion.
    public void cerrarSinCumplimiento(LocalDateTime ahora) {
        exigirPendiente();
        if (resultado != ResultadoIntervencion.NO_REALIZADO) {
            throw new IllegalStateException("Solo un intento NO_REALIZADO puede cerrarse sin cumplimiento");
        }
        this.estadoValidacion = EstadoValidacion.CERRADO_SIN_CUMPLIMIENTO;
        this.updatedAt = ahora;
    }

    public void solicitarCorreccion(LocalDateTime ahora) {
        exigirPendiente();
        this.estadoValidacion = EstadoValidacion.REQUIERE_CORRECCION;
        this.updatedAt = ahora;
    }

    private void exigirEditable() {
        if (!estadoValidacion.editable()) {
            throw new IllegalStateException("La intervencion no admite cambios en estado " + estadoValidacion);
        }
    }

    private void exigirPendiente() {
        if (estadoValidacion != EstadoValidacion.PENDIENTE_VALIDACION) {
            throw new IllegalStateException("La intervencion no esta pendiente de validacion");
        }
    }
}
