package com.facilityhub.p4;

import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.MantenimientoRepository;
import com.facilityhub.repository.OcupacionLocalRepository;
import com.facilityhub.service.DocumentoAutorizacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FH-005A1 / P4 - JPQL real de ocupacion vigente en PostgreSQL: P4-16..22, y P4-23 (reloj Europe/Madrid en el servicio).
 * La ocupacion demo del local L-101 (Restaurante Demo) empieza el 2026-01-01 sin fecha de fin.
 * Las variaciones de fechas y estado se hacen por JDBC dentro de la transaccion de la prueba y se revierten.
 */
class OcupacionVigenteJpqlP4IT extends P4IntegracionBase {
    private static final LocalDate INICIO_DEMO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN_SINTETICO = LocalDate.of(2026, 11, 30);

    @Autowired private OcupacionLocalRepository ocupacionRepository;
    @Autowired private MantenimientoRepository mantenimientoRepository;
    @Autowired private DocumentoAutorizacionService autorizacion;

    private Long local;
    private Long inquilino;

    @BeforeEach
    void datosDeReferencia() {
        local = idLocal(LOCAL_PROPIO);
        inquilino = idInquilinoDelUsuario(EMAIL_INQUILINO);
        Integer ocupaciones = jdbc.queryForObject("""
            SELECT COUNT(*) FROM facilityhub.ocupaciones_local
            WHERE local_id = ? AND inquilino_id = ? AND activo AND fecha_inicio = ? AND fecha_fin IS NULL""",
            Integer.class, local, inquilino, INICIO_DEMO);
        assertThat(ocupaciones).as("ocupacion demo esperada en la base de pruebas").isEqualTo(1);
    }

    @Test
    void p4_16_diaAnteriorAlInicioNoEsVigente() {
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, INICIO_DEMO.minusDays(1))).isFalse();
    }

    @Test
    void p4_17_primerDiaEsVigente() {
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, INICIO_DEMO)).isTrue();
    }

    @Test
    void p4_18_sinFechaFinEsVigente() {
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, P4RelojConfig.HOY_MADRID)).isTrue();
    }

    @Test
    void p4_19_ultimoDiaInclusivoEsVigente() {
        fijarFechaFin(FIN_SINTETICO);
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, FIN_SINTETICO)).isTrue();
    }

    @Test
    void p4_20_diaSiguienteAlFinNoEsVigente() {
        fijarFechaFin(FIN_SINTETICO);
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, FIN_SINTETICO.plusDays(1))).isFalse();
    }

    @Test
    void p4_21_ocupacionInactivaNoEsVigente() {
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET activo = false WHERE local_id = ? AND inquilino_id = ?", local, inquilino);
        sincronizar();
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, P4RelojConfig.HOY_MADRID)).isFalse();
    }

    @Test
    void p4_22_otroInquilinoNoTieneOcupacionEnElLocal() {
        Long otroInquilino = idInquilinoOcupante(LOCAL_AJENO);
        assertThat(otroInquilino).isNotEqualTo(inquilino);
        assertThat(ocupacionRepository.existeOcupacionVigente(local, otroInquilino, P4RelojConfig.HOY_MADRID)).isFalse();
    }

    @Test
    void p4_23_elServicioUsaElDiaDeMadridNoElDeUtc() {
        // Inicio = 17/11 (dia de Madrid del reloj fijo). En UTC aun es 16/11: con UTC no seria vigente.
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET fecha_inicio = ? WHERE local_id = ? AND inquilino_id = ?",
            P4RelojConfig.HOY_MADRID, local, inquilino);
        sincronizar();
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(EMAIL_INQUILINO).orElseThrow();
        Mantenimiento mantenimiento = mantenimientoRepository.findById(idMantenimientoDeLocal(LOCAL_PROPIO)).orElseThrow();

        assertThat(autorizacion.puedeLeerMantenimiento(usuario, mantenimiento)).isTrue();
    }

    // ---- FH-005A2-B1: local y centro deben estar activos ----

    @Test
    void b1_01_localPropioInactivoNoEsVigente() {
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE id = ?", local);
        sincronizar();
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, P4RelojConfig.HOY_MADRID)).isFalse();
    }

    @Test
    void b1_02_centroInactivoConLocalActivoNoEsVigente() {
        jdbc.update("UPDATE facilityhub.centros SET activo = false WHERE id = (SELECT centro_id FROM facilityhub.locales WHERE id = ?)", local);
        sincronizar();
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, P4RelojConfig.HOY_MADRID)).isFalse();
    }

    @Test
    void b1_03_ultimoDiaYDiaSiguienteConLocalYCentroActivos() {
        fijarFechaFin(FIN_SINTETICO);
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, FIN_SINTETICO)).isTrue();
        assertThat(ocupacionRepository.existeOcupacionVigente(local, inquilino, FIN_SINTETICO.plusDays(1))).isFalse();
    }

    @Test
    void b1_04_gestionNoDependeDeLaActividadDelLocal() {
        jdbc.update("UPDATE facilityhub.locales SET activo = false WHERE id = ?", local);
        sincronizar();
        Usuario explotacion = usuarioRepository.findByEmailAndActivoTrue(EMAIL_EXPLOTACION).orElseThrow();
        assertThat(autorizacion.puedeAccederLocal(explotacion, local)).isTrue();
        Usuario inquilinoDemo = usuarioRepository.findByEmailAndActivoTrue(EMAIL_INQUILINO).orElseThrow();
        assertThat(autorizacion.puedeAccederLocal(inquilinoDemo, local)).isFalse();
    }

    private void fijarFechaFin(LocalDate fin) {
        jdbc.update("UPDATE facilityhub.ocupaciones_local SET fecha_fin = ? WHERE local_id = ? AND inquilino_id = ?", fin, local, inquilino);
        sincronizar();
    }
}
