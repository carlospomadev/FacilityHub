package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "locales", schema = "facilityhub", uniqueConstraints =
    @UniqueConstraint(name = "uq_local_centro_codigo", columnNames = {"centro_id", "codigo"}))
public class LocalComercial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "centro_id") private Centro centro;
    @Column(nullable = false, length = 40) private String codigo;
    @Column(length = 120) private String nombre;
    @Column(length = 40) private String planta;
    @Column(length = 150) private String ubicacion;
    @Column(nullable = false) private boolean activo = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected LocalComercial() {}
    public Long getId() { return id; }
    public Centro getCentro() { return centro; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getPlanta() { return planta; }
    public String getUbicacion() { return ubicacion; }
    public boolean isActivo() { return activo; }
}
