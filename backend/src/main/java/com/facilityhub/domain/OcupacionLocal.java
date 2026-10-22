package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ocupaciones_local", schema = "facilityhub")
public class OcupacionLocal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "local_id") private LocalComercial local;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inquilino_id") private Inquilino inquilino;
    @Column(name = "fecha_inicio", nullable = false) private LocalDate fechaInicio;
    @Column(name = "fecha_fin") private LocalDate fechaFin;
    @Column(nullable = false) private boolean activo = true;

    protected OcupacionLocal() {}
    public Long getId() { return id; }
    public LocalComercial getLocal() { return local; }
    public Inquilino getInquilino() { return inquilino; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public boolean isActivo() { return activo; }
}
