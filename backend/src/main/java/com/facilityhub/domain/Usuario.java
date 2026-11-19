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
    // FH-006: empresa proveedora del tecnico (solo rol PROVEEDOR; garantizado tambien por trigger en BD).
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proveedor_id") private Proveedor proveedor;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected Usuario() {}

    // FH-006: alta de un tecnico de proveedor. El hash lo calcula el servicio con el PasswordEncoder del backend.
    public static Usuario nuevoTecnico(String nombre, String apellidos, String email, String passwordHash,
                                       Rol rolProveedor, Proveedor proveedor, LocalDateTime ahora) {
        Usuario u = new Usuario();
        u.nombre = nombre;
        u.apellidos = apellidos;
        u.email = email;
        u.passwordHash = passwordHash;
        u.activo = true;
        u.rol = rolProveedor;
        u.proveedor = proveedor;
        u.createdAt = ahora;
        u.updatedAt = ahora;
        return u;
    }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getApellidos() { return apellidos; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActivo() { return activo; }
    public Rol getRol() { return rol; }
    public Inquilino getInquilino() { return inquilino; }
    public Proveedor getProveedor() { return proveedor; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void cambiarActivo(boolean activo, LocalDateTime ahora) {
        this.activo = activo;
        this.updatedAt = ahora;
    }
}
