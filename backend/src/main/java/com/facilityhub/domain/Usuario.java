package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios", schema = "facilityhub")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80) private String nombre;
    @Column(length = 120) private String apellidos;
    @Column(nullable = false, unique = true, length = 150) private String email;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(nullable = false) private boolean activo = true;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "rol_id") private Rol rol;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inquilino_id") private Inquilino inquilino;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected Usuario() {}
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getApellidos() { return apellidos; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActivo() { return activo; }
    public Rol getRol() { return rol; }
    public Inquilino getInquilino() { return inquilino; }
}
