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

    protected DocumentoMantenimiento() {}

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

    public void revisar(EstadoRevisionDocumento estadoRevision, String observaciones) {
        this.estadoRevision = estadoRevision;
        this.observaciones = observaciones;
    }
}
