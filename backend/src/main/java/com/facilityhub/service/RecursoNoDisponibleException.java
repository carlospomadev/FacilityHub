package com.facilityhub.service;

// 404 uniforme: no distingue entre recurso inexistente, ajeno o no visible.
public class RecursoNoDisponibleException extends RuntimeException {
    public RecursoNoDisponibleException() { super("Recurso no encontrado"); }
}
