package com.facilityhub.p4;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FH-005A1 / P4 - Subida y descarga con almacenamiento temporal sintetico: P4-11 y P4-26.
 * La fila se revierte con la transaccion; el binario queda en el directorio temporal de la ejecucion,
 * que se borra al terminar la JVM. Nunca se usa backend/storage.
 */
class DocumentosArchivosP4IT extends P4IntegracionBase {

    @Test
    void p4_11_explotacionSubeYElDocumentoNaceInternoYPendienteEnAlmacenamientoTemporal() throws Exception {
        String nombre = nombreSintetico("subida.pdf");
        byte[] contenido = "%PDF-1.4 P4 sintetico".getBytes(StandardCharsets.US_ASCII);
        long archivosAntes = archivosEnAlmacenamiento();

        MvcResult resultado = mvc.perform(multipart("/api/mantenimientos/{id}/documentos", idMantenimientoDeLocal(LOCAL_PROPIO))
                .file(new MockMultipartFile("file", nombre, "application/pdf", contenido))
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_EXPLOTACION)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombreOriginal").value(nombre))
            .andExpect(jsonPath("$.estadoRevision").value("PENDIENTE"))
            .andExpect(jsonPath("$.visibilidad").value("INTERNO"))
            .andReturn();
        sincronizar();

        Number id = com.jayway.jsonpath.JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
        Path ruta = Paths.get(valorEnBd("ruta_archivo", id.longValue())).toAbsolutePath().normalize();
        assertThat(ruta.startsWith(P4Entorno.directorioAlmacenamiento())).isTrue();
        assertThat(archivosEnAlmacenamiento()).isEqualTo(archivosAntes + 1);
    }

    @Test
    void p4_26_inquilinoDescargaUnCompartidoValidoDeSuLocal() throws Exception {
        String nombre = nombreSintetico("descarga.pdf");
        byte[] contenido = "%PDF-1.4 P4 descarga sintetica".getBytes(StandardCharsets.US_ASCII);
        Path archivo = crearArchivoSintetico(nombre, contenido);
        Long documento = insertarDocumento(idMantenimientoDeLocal(LOCAL_PROPIO), nombre, "VALIDO", "COMPARTIDO", archivo.toString());

        MvcResult resultado = mvc.perform(get("/api/documentos/{id}/archivo", documento)
                .header(HttpHeaders.AUTHORIZATION, bearer(EMAIL_INQUILINO)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\""))
            .andReturn();

        assertThat(resultado.getResponse().getContentAsByteArray()).isEqualTo(contenido);
    }
}
