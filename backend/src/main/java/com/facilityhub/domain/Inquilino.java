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

    // FH-015 / RF-05: alta desde la gestion (ADMIN). El CIF llega ya normalizado y comprobado por el servicio.
    public Inquilino(String cif, String razonSocial, String nombreComercial, String emailContacto, String telefono,
                     LocalDateTime createdAt) {
        this.cif = cif;
        this.razonSocial = razonSocial;
        this.nombreComercial = nombreComercial;
        this.emailContacto = emailContacto;
        this.telefono = telefono;
        this.activo = true;
        this.createdAt = createdAt;
    }

    // FH-015 (D4): datos editables. El CIF no se cambia aqui.
    public void actualizar(String razonSocial, String nombreComercial, String emailContacto, String telefono) {
        this.razonSocial = razonSocial;
        this.nombreComercial = nombreComercial;
        this.emailContacto = emailContacto;
        this.telefono = telefono;
    }

    // FH-015 (D4): un registro antiguo sin CIF puede recibirlo una sola vez; despues es inmutable.
    public void asignarCif(String cif) {
        if (this.cif != null) {
            throw new IllegalStateException("El CIF de un inquilino no se puede modificar");
        }
        this.cif = cif;
    }

    // FH-015 (D5): desactivar o reactivar, sin cascada.
    public void cambiarActivo(boolean activo) { this.activo = activo; }

    public Long getId() { return id; }
    public String getCif() { return cif; }
    public String getRazonSocial() { return razonSocial; }
    public String getNombreComercial() { return nombreComercial; }
    public String getEmailContacto() { return emailContacto; }
    public String getTelefono() { return telefono; }
    public boolean isActivo() { return activo; }
}
