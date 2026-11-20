package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// FH-006 / P6-C1: historial inmutable de validaciones (trigger en BD impide UPDATE y DELETE).
@Entity
@Table(name = "validaciones_intervencion", schema = "facilityhub")
public class ValidacionIntervencion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "intervencion_id", updatable = false) private Intervencion intervencion;
    @Column(name = "numero_envio", nullable = false, updatable = false) private int numeroEnvio;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 25, updatable = false) private AccionValidacion accion;
    @Column(columnDefinition = "text", updatable = false) private String comentario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "usuario_id", updatable = false) private Usuario usuario;
    @Column(nullable = false, updatable = false) private LocalDateTime fecha;

    protected ValidacionIntervencion() {}

    public ValidacionIntervencion(Intervencion intervencion, AccionValidacion accion, String comentario, Usuario usuario,
                                  LocalDateTime fecha) {
        this.intervencion = intervencion;
        this.numeroEnvio = intervencion.getNumeroEnvio();
        this.accion = accion;
        this.comentario = comentario;
        this.usuario = usuario;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public Intervencion getIntervencion() { return intervencion; }
    public int getNumeroEnvio() { return numeroEnvio; }
    public AccionValidacion getAccion() { return accion; }
    public String getComentario() { return comentario; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFecha() { return fecha; }
}
