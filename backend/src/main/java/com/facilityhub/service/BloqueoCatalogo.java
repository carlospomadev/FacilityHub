package com.facilityhub.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * FH-012 / RF-08 (D3 y D6): serializa todas las escrituras del catalogo de tipos con un bloqueo asesor TRANSACCIONAL de
 * PostgreSQL ({@code pg_advisory_xact_lock}). El bloqueo es de la base de datos, asi que tambien serializa varias
 * instancias del backend; se libera solo al terminar la transaccion (commit o rollback). Sin migracion.
 * Orden fijo de adquisicion: 1) este bloqueo del catalogo, 2) la fila del tipo (PESSIMISTIC_WRITE). Nunca al reves.
 * Limitacion: no protege escrituras SQL hechas fuera del servicio (no hay indice unico de nombre).
 */
@Component
public class BloqueoCatalogo {
    // Clave fija de 64 bits para el catalogo de tipos de mantenimiento (no se usa en ningun otro sitio).
    static final long CLAVE_CATALOGO_TIPOS = 8_012_001L;

    @PersistenceContext
    private EntityManager entityManager;

    /** Debe llamarse dentro de una transaccion de escritura; espera si otra transaccion tiene el bloqueo. */
    public void bloquear() {
        entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:clave)) b")
            .setParameter("clave", CLAVE_CATALOGO_TIPOS)
            .getSingleResult();
    }
}
