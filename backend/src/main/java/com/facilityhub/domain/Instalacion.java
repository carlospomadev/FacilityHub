package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "instalaciones", schema = "facilityhub")
public class Instalacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    // FH-006: la instalacion pertenece a un centro; local NULL = zona comun (coherencia garantizada en BD).
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "centro_id") private Centro centro;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "local_id") private LocalComercial local;
    @Column(nullable = false, length = 120) private String nombre;
    @Column(nullable = false, length = 80) private String tipo;
    @Column(columnDefinition = "text") private String descripcion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoInstalacion estado = EstadoInstalacion.ACTIVA;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected Instalacion() {}

    // FH-010A / RF-07: alta desde la API. La coherencia centro/local la comprueba el servicio y la garantiza la BD
    // (fk_instalacion_local_centro).
    public Instalacion(Centro centro, LocalComercial local, String nombre, String tipo, String descripcion,
                       EstadoInstalacion estado, LocalDateTime createdAt) {
        this.centro = centro;
        this.local = local;
        this.nombre = nombre;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.estado = estado;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Centro getCentro() { return centro; }
    public LocalComercial getLocal() { return local; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public EstadoInstalacion getEstado() { return estado; }
}
