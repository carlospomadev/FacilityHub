package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// FH-006: empresa proveedora. Sus tecnicos son usuarios con rol PROVEEDOR vinculados por usuarios.proveedor_id.
@Entity
@Table(name = "proveedores", schema = "facilityhub")
public class Proveedor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 20) private String cif;
    @Column(name = "razon_social", nullable = false, length = 150) private String razonSocial;
    @Column(name = "email_contacto", length = 150) private String emailContacto;
    @Column(length = 30) private String telefono;
    @Column(nullable = false) private boolean activo = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected Proveedor() {}

    public Proveedor(String cif, String razonSocial, String emailContacto, String telefono) {
        this.cif = cif;
        this.razonSocial = razonSocial;
        this.emailContacto = emailContacto;
        this.telefono = telefono;
        this.activo = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getCif() { return cif; }
    public String getRazonSocial() { return razonSocial; }
    public String getEmailContacto() { return emailContacto; }
    public String getTelefono() { return telefono; }
    public boolean isActivo() { return activo; }

    public void actualizarDatos(String razonSocial, String emailContacto, String telefono) {
        if (razonSocial != null) this.razonSocial = razonSocial;
        if (emailContacto != null) this.emailContacto = emailContacto;
        if (telefono != null) this.telefono = telefono;
    }

    public void cambiarActivo(boolean activo) { this.activo = activo; }
}
