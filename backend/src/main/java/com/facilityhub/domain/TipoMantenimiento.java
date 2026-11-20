package com.facilityhub.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "tipos_mantenimiento", schema = "facilityhub")
public class TipoMantenimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String codigo;
    @Column(nullable = false, length = 150) private String nombre;
    @Column(columnDefinition = "text") private String descripcion;
    @Column(name = "periodicidad_meses", nullable = false) private Integer periodicidadMeses;
    @Column(name = "requiere_documento", nullable = false) private boolean requiereDocumento = true;
    @Column(nullable = false) private boolean activo = true;
    // FH-006 / P6-C1 (D-7): regla de recalculo de la proxima fecha.
    @Enumerated(EnumType.STRING) @Column(name = "regla_recalculo", nullable = false, length = 30)
    private ReglaRecalculo reglaRecalculo = ReglaRecalculo.DESDE_EJECUCION;

    protected TipoMantenimiento() {}
    public ReglaRecalculo getReglaRecalculo() { return reglaRecalculo == null ? ReglaRecalculo.DESDE_EJECUCION : reglaRecalculo; }
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public Integer getPeriodicidadMeses() { return periodicidadMeses; }
    public boolean isRequiereDocumento() { return requiereDocumento; }
    public boolean isActivo() { return activo; }
}
