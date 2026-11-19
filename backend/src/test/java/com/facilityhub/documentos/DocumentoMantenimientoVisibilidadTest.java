package com.facilityhub.documentos;

import com.facilityhub.domain.DocumentoMantenimiento;
import com.facilityhub.domain.EstadoRevisionDocumento;
import com.facilityhub.domain.Mantenimiento;
import com.facilityhub.domain.VisibilidadDocumento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DocumentoMantenimientoVisibilidadTest {
    private final Mantenimiento mantenimiento = mock(Mantenimiento.class);

    private DocumentoMantenimiento nuevo() {
        return new DocumentoMantenimiento(mantenimiento, "SINTETICO.pdf", "/almacen/sintetico.pdf", null, null, null);
    }

    @Test
    void documentoNuevoNaceInterno() {
        assertThat(nuevo().getVisibilidad()).isEqualTo(VisibilidadDocumento.INTERNO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoRevisionDocumento.class, names = {"PENDIENTE", "RECHAZADO", "CADUCADO"})
    void noSePuedeCompartirSiNoEsValido(EstadoRevisionDocumento estado) {
        DocumentoMantenimiento documento = nuevo();
        documento.revisar(estado, null);

        assertThatThrownBy(() -> documento.cambiarVisibilidad(VisibilidadDocumento.COMPARTIDO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(documento.getVisibilidad()).isEqualTo(VisibilidadDocumento.INTERNO);
    }

    @Test
    void documentoValidoSePuedeCompartirYVolverAInterno() {
        DocumentoMantenimiento documento = nuevo();
        documento.revisar(EstadoRevisionDocumento.VALIDO, null);

        documento.cambiarVisibilidad(VisibilidadDocumento.COMPARTIDO);
        assertThat(documento.getVisibilidad()).isEqualTo(VisibilidadDocumento.COMPARTIDO);

        documento.cambiarVisibilidad(VisibilidadDocumento.INTERNO);
        assertThat(documento.getVisibilidad()).isEqualTo(VisibilidadDocumento.INTERNO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoRevisionDocumento.class, names = {"PENDIENTE", "RECHAZADO", "CADUCADO"})
    void compartidoQuePierdeValidoSeDespublica(EstadoRevisionDocumento estado) {
        DocumentoMantenimiento documento = nuevo();
        documento.revisar(EstadoRevisionDocumento.VALIDO, null);
        documento.cambiarVisibilidad(VisibilidadDocumento.COMPARTIDO);

        documento.revisar(estado, "motivo sintetico");

        assertThat(documento.getVisibilidad()).isEqualTo(VisibilidadDocumento.INTERNO);
    }

    @Test
    void volverAValidoNoRepublica() {
        DocumentoMantenimiento documento = nuevo();
        documento.revisar(EstadoRevisionDocumento.VALIDO, null);
        documento.cambiarVisibilidad(VisibilidadDocumento.COMPARTIDO);
        documento.revisar(EstadoRevisionDocumento.CADUCADO, null);

        documento.revisar(EstadoRevisionDocumento.VALIDO, null);

        assertThat(documento.getVisibilidad()).isEqualTo(VisibilidadDocumento.INTERNO);
    }

    @Test
    void revisarComoValidoMantieneUnCompartido() {
        DocumentoMantenimiento documento = nuevo();
        documento.revisar(EstadoRevisionDocumento.VALIDO, null);
        documento.cambiarVisibilidad(VisibilidadDocumento.COMPARTIDO);

        documento.revisar(EstadoRevisionDocumento.VALIDO, "sin cambios");

        assertThat(documento.getVisibilidad()).isEqualTo(VisibilidadDocumento.COMPARTIDO);
    }
}
