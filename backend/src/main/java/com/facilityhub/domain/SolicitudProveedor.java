package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// FH-006 / P6-C2: el inquilino pide incorporar un proveedor nuevo; ADMIN la atiende vinculando la empresa que da de
// alta por su circuito habitual (D-1) o la rechaza con respuesta. El inquilino nunca crea empresas ni usuarios.
@Entity
@Table(name = "solicitudes_proveedor", schema = "facilityhub")
public class SolicitudProveedor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inquilino_id") private Inquilino inquilino;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "obligacion_id") private ObligacionMantenimiento obligacion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "solicitado_por_usuario_id") private Usuario solicitadoPor;
    @Column(name = "razon_social", nullable = false, length = 150) private String razonSocial;
    @Column(length = 20) private String cif;
    @Column(length = 300) private String contacto;
    @Column(columnDefinition = "text") private String observaciones;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoSolicitudProveedor estado;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proveedor_id") private Proveedor proveedor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "atendida_por_usuario_id") private Usuario atendidaPor;
    @Column(name = "atendida_at") private LocalDateTime atendidaAt;
    @Column(columnDefinition = "text") private String respuesta;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Version @Column(nullable = false) private long version;

    protected SolicitudProveedor() {}

    public SolicitudProveedor(Inquilino inquilino, ObligacionMantenimiento obligacion, Usuario solicitadoPor,
                              String razonSocial, String cif, String contacto, String observaciones, LocalDateTime ahora) {
        if (razonSocial == null || razonSocial.isBlank()) {
            throw new IllegalArgumentException("La razon social es obligatoria");
        }
        this.inquilino = inquilino;
        this.obligacion = obligacion;
        this.solicitadoPor = solicitadoPor;
        this.razonSocial = razonSocial.trim();
        this.cif = cif == null || cif.isBlank() ? null : cif.trim().toUpperCase();
        this.contacto = contacto;
        this.observaciones = observaciones;
        this.estado = EstadoSolicitudProveedor.PENDIENTE;
        this.createdAt = ahora;
    }

    public Long getId() { return id; }
    public Inquilino getInquilino() { return inquilino; }
    public ObligacionMantenimiento getObligacion() { return obligacion; }
    public Usuario getSolicitadoPor() { return solicitadoPor; }
    public String getRazonSocial() { return razonSocial; }
    public String getCif() { return cif; }
    public String getContacto() { return contacto; }
    public String getObservaciones() { return observaciones; }
    public EstadoSolicitudProveedor getEstado() { return estado; }
    public Proveedor getProveedor() { return proveedor; }
    public Usuario getAtendidaPor() { return atendidaPor; }
    public LocalDateTime getAtendidaAt() { return atendidaAt; }
    public String getRespuesta() { return respuesta; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }

    public void atender(Proveedor proveedor, Usuario usuario, String respuesta, LocalDateTime ahora) {
        exigirPendiente();
        this.estado = EstadoSolicitudProveedor.ATENDIDA;
        this.proveedor = proveedor;
        this.atendidaPor = usuario;
        this.atendidaAt = ahora;
        this.respuesta = respuesta;
    }

    public void rechazar(Usuario usuario, String respuesta, LocalDateTime ahora) {
        exigirPendiente();
        if (respuesta == null || respuesta.isBlank()) {
            throw new IllegalArgumentException("El rechazo requiere una respuesta");
        }
        this.estado = EstadoSolicitudProveedor.RECHAZADA;
        this.atendidaPor = usuario;
        this.atendidaAt = ahora;
        this.respuesta = respuesta.trim();
    }

    private void exigirPendiente() {
        if (estado != EstadoSolicitudProveedor.PENDIENTE) {
            throw new IllegalStateException("La solicitud ya esta resuelta");
        }
    }
}
