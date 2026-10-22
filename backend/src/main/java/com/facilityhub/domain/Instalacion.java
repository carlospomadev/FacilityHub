package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "instalaciones", schema = "facilityhub")
public class Instalacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "local_id") private LocalComercial local;
    @Column(nullable = false, length = 120) private String nombre;
    @Column(nullable = false, length = 80) private String tipo;
    @Column(columnDefinition = "text") private String descripcion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoInstalacion estado = EstadoInstalacion.ACTIVA;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected Instalacion() {}
    public Long getId() { return id; }
    public LocalComercial getLocal() { return local; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public EstadoInstalacion getEstado() { return estado; }
}
