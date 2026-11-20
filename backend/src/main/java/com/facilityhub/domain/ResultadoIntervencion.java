package com.facilityhub.domain;

public enum ResultadoIntervencion {
    CONFORME,
    CON_INCIDENCIAS,
    // Comunica que la ejecucion no se pudo realizar: nunca es cumplimiento valido ni recalcula la proxima fecha.
    NO_REALIZADO;

    public boolean ejecutado() {
        return this != NO_REALIZADO;
    }
}
