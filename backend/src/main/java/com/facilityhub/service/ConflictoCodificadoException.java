package com.facilityhub.service;

import java.util.Map;

/**
 * FH-012: conflicto (409) con un codigo estable y datos no sensibles para que el cliente distinga el caso (impacto sin
 * confirmar, datos desactualizados, duplicado...). El manejador los anade como propiedades del ProblemDetail.
 */
public class ConflictoCodificadoException extends ConflictoException {
    private final String codigo;
    private final Map<String, Object> datos;

    public ConflictoCodificadoException(String codigo, String message, Map<String, Object> datos) {
        super(message);
        this.codigo = codigo;
        this.datos = datos == null ? Map.of() : datos;
    }

    public String getCodigo() { return codigo; }
    public Map<String, Object> getDatos() { return datos; }
}
