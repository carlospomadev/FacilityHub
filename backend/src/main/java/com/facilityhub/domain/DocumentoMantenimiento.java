package com.facilityhub.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "documentos_mantenimiento", schema = "facilityhub")
public class DocumentoMantenimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mantenimiento_id") private Mantenimiento mantenimiento;
    @Column(name = "nombre_original", nullable = false, length = 255) private String nombreOriginal;
    @Column(name = "ruta_archivo", nullable = false, length = 500) private String rutaArchivo;
    @Column(name = "tipo_documento", length = 80) private String tipoDocumento;
    @Column(name = "fecha_documento") private LocalDate fechaDocumento;
    @Column(name = "fecha_caducidad") private LocalDate fechaCaducidad;
    @Enumerated(EnumType.STRING) @Column(name = "estado_revision", nullable = false, length = 20) private EstadoRevisionDocumento estadoRevision = EstadoRevisionDocumento.PENDIENTE;
    @Column(columnDefinition = "text") private String observaciones;
    @Column(name = "fecha_subida", nullable = false) private LocalDateTime fechaSubida;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private VisibilidadDocumento visibilidad = VisibilidadDocumento.INTERNO;
    // FH-006 / P6-C1: evidencia o certificado de una intervencion, con su autor. Nulos en los documentos P5.
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "intervencion_id") private Intervencion intervencion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "subido_por_usuario_id") private Usuario subidoPor;
    @Enumerated(EnumType.STRING) @Column(length = 20) private CategoriaDocumento categoria;

    protected DocumentoMantenimiento() {}

    // FH-006 / P6-C1: documento de una intervencion. Nace INTERNO y PENDIENTE; compartir es siempre explicito (D-11).
    public static DocumentoMantenimiento deIntervencion(Intervencion intervencion, String nombreOriginal, String rutaArchivo,
                                                       CategoriaDocumento categoria, Usuario subidoPor) {
        DocumentoMantenimiento d = new DocumentoMantenimiento(intervencion.getMantenimiento(), nombreOriginal, rutaArchivo,
            categoria.name(), null, null);
        d.intervencion = intervencion;
        d.subidoPor = subidoPor;
        d.categoria = categoria;
        return d;
    }

    public Intervencion getIntervencion() { return intervencion; }
    public Usuario getSubidoPor() { return subidoPor; }
    public CategoriaDocumento getCategoria() { return categoria; }

    public DocumentoMantenimiento(Mantenimiento mantenimiento, String nombreOriginal, String rutaArchivo,
                                  String tipoDocumento, LocalDate fechaDocumento, LocalDate fechaCaducidad) {
        if (fechaCaducidad != null && fechaDocumento != null && fechaCaducidad.isBefore(fechaDocumento)) {
            throw new IllegalArgumentException("La fecha de caducidad no puede ser anterior a la fecha del documento");
        }
        this.mantenimiento = mantenimiento;
        this.nombreOriginal = nombreOriginal;
        this.rutaArchivo = rutaArchivo;
        this.tipoDocumento = tipoDocumento;
        this.fechaDocumento = fechaDocumento;
        this.fechaCaducidad = fechaCaducidad;
        this.estadoRevision = EstadoRevisionDocumento.PENDIENTE;
        this.fechaSubida = LocalDateTime.now();
        this.visibilidad = VisibilidadDocumento.INTERNO;
    }

    public Long getId() { return id; }
    public Mantenimiento getMantenimiento() { return mantenimiento; }
    public String getNombreOriginal() { return nombreOriginal; }
    public String getRutaArchivo() { return rutaArchivo; }
    public String getTipoDocumento() { return tipoDocumento; }
    public LocalDate getFechaDocumento() { return fechaDocumento; }
    public LocalDate getFechaCaducidad() { return fechaCaducidad; }
    public EstadoRevisionDocumento getEstadoRevision() { return estadoRevision; }
    public String getObservaciones() { return observaciones; }
    public LocalDateTime getFechaSubida() { return fechaSubida; }
    public VisibilidadDocumento getVisibilidad() { return visibilidad; }

    public void revisar(EstadoRevisionDocumento estadoRevision, String observaciones) {
        this.estadoRevision = estadoRevision;
        this.observaciones = observaciones;
        // Un documento que deja de ser VALIDO pierde la publicacion; volver a VALIDO no lo republica.
        if (estadoRevision != EstadoRevisionDocumento.VALIDO) {
            this.visibilidad = VisibilidadDocumento.INTERNO;
        }
    }

    public void cambiarVisibilidad(VisibilidadDocumento visibilidad) {
        if (visibilidad == VisibilidadDocumento.COMPARTIDO && estadoRevision != EstadoRevisionDocumento.VALIDO) {
            throw new IllegalArgumentException("Solo se pueden compartir documentos en estado VALIDO");
        }
        this.visibilidad = visibilidad;
    }
}
