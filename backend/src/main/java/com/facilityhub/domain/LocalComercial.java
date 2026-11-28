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

    // FH-014 / RF-04: alta desde la API. El centro y el codigo se fijan aqui y no tienen metodo para cambiarlos (D2, D6).
    public LocalComercial(Centro centro, String codigo, String nombre, String planta, String ubicacion, LocalDateTime createdAt) {
        this.centro = centro;
        this.codigo = codigo;
        this.nombre = nombre;
        this.planta = planta;
        this.ubicacion = ubicacion;
        this.activo = true;
        this.createdAt = createdAt;
    }

    // FH-014 / RF-04: edicion de los datos descriptivos (no el centro ni el codigo). Las reglas las valida LocalGestionService.
    public void actualizar(String nombre, String planta, String ubicacion) {
        this.nombre = nombre;
        this.planta = planta;
        this.ubicacion = ubicacion;
    }

    public void cambiarActivo(boolean activo) { this.activo = activo; }
    public Long getId() { return id; }
    public Centro getCentro() { return centro; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getPlanta() { return planta; }
    public String getUbicacion() { return ubicacion; }
    public boolean isActivo() { return activo; }
}
