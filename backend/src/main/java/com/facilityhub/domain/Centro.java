package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "centros", schema = "facilityhub")
public class Centro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30) private String codigo;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(length = 200) private String direccion;
    @Column(length = 100) private String ciudad;
    @Column(columnDefinition = "text") private String descripcion;
    @Column(nullable = false) private boolean activo = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected Centro() {}

    // FH-013 / RF-03: alta desde la API. El codigo se fija aqui y no tiene metodo para cambiarlo (D2).
    public Centro(String codigo, String nombre, String direccion, String ciudad, String descripcion, LocalDateTime createdAt) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.descripcion = descripcion;
        this.activo = true;
        this.createdAt = createdAt;
    }

    // FH-013 / RF-03: edicion de los datos descriptivos (no el codigo). Las reglas las valida CentroGestionService.
    public void actualizar(String nombre, String direccion, String ciudad, String descripcion) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.descripcion = descripcion;
    }

    public void cambiarActivo(boolean activo) { this.activo = activo; }
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getCiudad() { return ciudad; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
}
