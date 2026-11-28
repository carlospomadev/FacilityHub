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

    // FH-013 / RF-03 (D3 y D7): clave propia para el catalogo de centros (independiente de la de tipos).
    static final long CLAVE_CATALOGO_CENTROS = 8_013_001L;

    /** Debe llamarse dentro de una transaccion de escritura; espera si otra transaccion tiene el bloqueo. */
    public void bloquear() {
        bloquear(CLAVE_CATALOGO_TIPOS);
    }

    /** FH-013: escrituras del catalogo de centros. Mismo orden fijo: 1) este bloqueo, 2) la fila del centro. */
    public void bloquearCentros() {
        bloquear(CLAVE_CATALOGO_CENTROS);
    }

    // FH-015 / RF-05 (D2 y D7): clave propia para el catalogo de inquilinos (unicidad del CIF sin indice).
    static final long CLAVE_CATALOGO_INQUILINOS = 8_015_001L;

    /** FH-015: escrituras del catalogo de inquilinos. Orden fijo: 1) este bloqueo, 2) la fila del inquilino. */
    public void bloquearInquilinos() {
        bloquear(CLAVE_CATALOGO_INQUILINOS);
    }

    private void bloquear(long clave) {
        entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:clave)) b")
            .setParameter("clave", clave)
            .getSingleResult();
    }
}
