package com.facilityhub.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "roles", schema = "facilityhub")
public class Rol {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;
    @Column(nullable = false, length = 80)
    private String nombre;
    @Column(length = 255)
    private String descripcion;

    protected Rol() {}
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
}
