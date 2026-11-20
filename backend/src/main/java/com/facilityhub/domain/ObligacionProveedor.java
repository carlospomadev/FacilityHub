package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// FH-006 / P6-C2: proveedor declarado por el inquilino para una obligacion INQUILINO, ligado a la ocupacion que lo
// declara (un nuevo ocupante no hereda la declaracion). Gestion lo acepta o lo rechaza; una sola activa por obligacion.
@Entity
@Table(name = "obligacion_proveedor", schema = "facilityhub")
public class ObligacionProveedor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "obligacion_id") private ObligacionMantenimiento obligacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "proveedor_id") private Proveedor proveedor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ocupacion_id") private OcupacionLocal ocupacion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoDeclaracionProveedor estado;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "declarado_por_usuario_id") private Usuario declaradoPor;
    @Column(name = "declarado_at", nullable = false) private LocalDateTime declaradoAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "revisado_por_usuario_id") private Usuario revisadoPor;
    @Column(name = "revisado_at") private LocalDateTime revisadoAt;
    @Column(name = "motivo_revision", columnDefinition = "text") private String motivoRevision;
    @Column(nullable = false) private boolean activo;
    @Column(name = "finalizado_at") private LocalDateTime finalizadoAt;
    @Version @Column(nullable = false) private long version;

    protected ObligacionProveedor() {}

    public ObligacionProveedor(ObligacionMantenimiento obligacion, Proveedor proveedor, OcupacionLocal ocupacion,
                               Usuario declaradoPor, LocalDateTime ahora) {
        this.obligacion = obligacion;
        this.proveedor = proveedor;
        this.ocupacion = ocupacion;
        this.declaradoPor = declaradoPor;
        this.declaradoAt = ahora;
        this.estado = EstadoDeclaracionProveedor.PROPUESTO;
        this.activo = true;
    }

    public Long getId() { return id; }
    public ObligacionMantenimiento getObligacion() { return obligacion; }
    public Proveedor getProveedor() { return proveedor; }
    public OcupacionLocal getOcupacion() { return ocupacion; }
    public EstadoDeclaracionProveedor getEstado() { return estado; }
    public Usuario getDeclaradoPor() { return declaradoPor; }
    public LocalDateTime getDeclaradoAt() { return declaradoAt; }
    public Usuario getRevisadoPor() { return revisadoPor; }
    public LocalDateTime getRevisadoAt() { return revisadoAt; }
    public String getMotivoRevision() { return motivoRevision; }
    public boolean isActivo() { return activo; }
    public long getVersion() { return version; }

    public boolean aceptadaPara(OcupacionLocal ocupacionVigente) {
        return activo && estado == EstadoDeclaracionProveedor.ACEPTADO && ocupacion.getId().equals(ocupacionVigente.getId());
    }

    public void aceptar(Usuario usuario, String motivo, LocalDateTime ahora) {
        exigirPropuesta();
        this.estado = EstadoDeclaracionProveedor.ACEPTADO;
        this.revisadoPor = usuario;
        this.revisadoAt = ahora;
        this.motivoRevision = motivo;
    }

    // El rechazo cierra la declaracion (el inquilino puede declarar otra).
    public void rechazar(Usuario usuario, String motivo, LocalDateTime ahora) {
        exigirPropuesta();
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El rechazo requiere un motivo");
        }
        this.estado = EstadoDeclaracionProveedor.RECHAZADO;
        this.revisadoPor = usuario;
        this.revisadoAt = ahora;
        this.motivoRevision = motivo.trim();
        this.activo = false;
        this.finalizadoAt = ahora;
    }

    // Sustituida por una nueva declaracion: queda en el historial sin cambiar su estado.
    public void finalizar(LocalDateTime ahora) {
        this.activo = false;
        this.finalizadoAt = ahora;
    }

    private void exigirPropuesta() {
        if (!activo || estado != EstadoDeclaracionProveedor.PROPUESTO) {
            throw new IllegalStateException("La declaracion no esta pendiente de revision");
        }
    }
}
