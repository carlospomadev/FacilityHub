package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inquilinos", schema = "facilityhub")
public class Inquilino {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 20) private String cif;
    @Column(name = "razon_social", nullable = false, length = 150) private String razonSocial;
    @Column(name = "nombre_comercial", length = 120) private String nombreComercial;
    @Column(name = "email_contacto", length = 150) private String emailContacto;
    @Column(length = 30) private String telefono;
    @Column(nullable = false) private boolean activo = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected Inquilino() {}
    public Long getId() { return id; }
    public String getCif() { return cif; }
    public String getRazonSocial() { return razonSocial; }
    public String getNombreComercial() { return nombreComercial; }
    public String getEmailContacto() { return emailContacto; }
    public String getTelefono() { return telefono; }
    public boolean isActivo() { return activo; }
}
