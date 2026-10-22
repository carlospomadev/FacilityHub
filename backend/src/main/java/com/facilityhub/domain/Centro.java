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
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getCiudad() { return ciudad; }
    public String getDescripcion() { return descripcion; }
    public boolean isActivo() { return activo; }
}
