package com.facilityhub.service;

import com.facilityhub.api.dto.tipo.CambiarActivoTipoRequest;
import com.facilityhub.api.dto.tipo.CrearTipoRequest;
import com.facilityhub.api.dto.tipo.EditarTipoRequest;
import com.facilityhub.api.dto.tipo.TipoGestionDto;
import com.facilityhub.domain.ReglaRecalculo;
import com.facilityhub.domain.TipoMantenimiento;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.IntervencionRepository;
import com.facilityhub.repository.ObligacionMantenimientoRepository;
import com.facilityhub.repository.TipoMantenimientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * FH-012 / RF-08: gestion del catalogo de tipos de mantenimiento. Decisiones aprobadas:
 *  - D1: consulta para ADMIN y EXPLOTACION; alta, edicion y activacion solo ADMIN (403 para el resto).
 *  - D2: periodicidad, regla y documento se aplican a partir de ahora (sin recalcular fechas guardadas). Si el tipo tiene
 *        obligaciones activas, hace falta confirmarImpacto; exigir documento se rechaza si hay intervenciones abiertas
 *        sin evidencias (no se salta con la confirmacion).
 *  - D3: nombre unico sin distinguir mayusculas ni espacios exteriores; codigo unico.
 *  - D4: codigo inmutable, [A-Z0-9-]{2,40}, normalizado a mayusculas.
 *  - D5: periodicidad 1..120 meses; solo las reglas existentes.
 *  - D6: escrituras serializadas (bloqueo asesor del catalogo + bloqueo de la fila) y deteccion de formularios
 *        desactualizados con la huella de los valores, comparada dentro de la transaccion bloqueada.
 *  - D7: desactivar con obligaciones activas exige confirmacion; nunca toca obligaciones ni mantenimientos.
 * Ninguna operacion modifica obligaciones, mantenimientos, intervenciones, documentos ni alertas.
 */
@Service
public class TipoMantenimientoService {
    static final String ENTIDAD = "TIPO_MANTENIMIENTO";
    private static final Pattern FORMATO_CODIGO = Pattern.compile("[A-Z0-9-]{2,40}");
    static final String MENSAJE_DESACTUALIZADO = "Este tipo ha sido modificado por otro usuario. Actualiza los datos antes de guardar";

    private final TipoMantenimientoRepository tipoRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final IntervencionRepository intervencionRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final BloqueoCatalogo bloqueo;

    public TipoMantenimientoService(TipoMantenimientoRepository tipoRepository,
                                    ObligacionMantenimientoRepository obligacionRepository,
                                    IntervencionRepository intervencionRepository, DocumentoAutorizacionService autorizacion,
                                    AuditoriaService auditoria, BloqueoCatalogo bloqueo) {
        this.tipoRepository = tipoRepository;
        this.obligacionRepository = obligacionRepository;
        this.intervencionRepository = intervencionRepository;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.bloqueo = bloqueo;
    }

    // ---------- Consulta (ADMIN y EXPLOTACION) ----------

    @Transactional(readOnly = true)
    public List<TipoGestionDto> listarCatalogo(String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        Map<Long, Long> uso = new HashMap<>();
        for (Object[] fila : obligacionRepository.contarActivasPorTipo()) {
            uso.put((Long) fila[0], (Long) fila[1]);
        }
        return tipoRepository.findAllByOrderByNombreAsc().stream()
            .map(t -> toDto(t, uso.getOrDefault(t.getId(), 0L)))
            .toList();
    }

    @Transactional(readOnly = true)
    public TipoGestionDto obtener(Long id, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        TipoMantenimiento tipo = tipoRepository.findById(id).orElseThrow(() -> noEncontrado(id));
        return toDto(tipo, obligacionRepository.countByTipoMantenimientoIdAndActivoTrue(id));
    }

    // ---------- Escritura (solo ADMIN) ----------

    @Transactional
    public TipoGestionDto crear(CrearTipoRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        String codigo = normalizarCodigo(request.codigo());
        String nombre = request.nombre().strip();
        bloqueo.bloquear();
        if (tipoRepository.existsByCodigo(codigo)) {
            throw new ConflictoCodificadoException("CODIGO_DUPLICADO", "Ya existe un tipo de mantenimiento con ese codigo", null);
        }
        if (nombreEnUso(nombre, null)) {
            throw new ConflictoCodificadoException("NOMBRE_DUPLICADO", "Ya existe un tipo de mantenimiento con ese nombre", null);
        }
        TipoMantenimiento tipo = new TipoMantenimiento(codigo, nombre, vacioANulo(request.descripcion()),
            request.periodicidadMeses(), request.requiereDocumento(), ReglaRecalculo.valueOf(request.reglaRecalculo()));
        tipo = tipoRepository.saveAndFlush(tipo);
        auditoria.registrar(ENTIDAD, tipo.getId(), "CREAR", usuario, AuditoriaService.detalle(
            "codigo", codigo, "nombre", nombre, "periodicidadMeses", tipo.getPeriodicidadMeses(),
            "reglaRecalculo", tipo.getReglaRecalculo().name(), "requiereDocumento", tipo.isRequiereDocumento()));
        return toDto(tipo, 0L);
    }

    @Transactional
    public TipoGestionDto editar(Long id, EditarTipoRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        // Orden fijo: catalogo y despues la fila. La huella se compara ya con todo bloqueado.
        bloqueo.bloquear();
        TipoMantenimiento tipo = tipoRepository.findParaEditar(id).orElseThrow(() -> noEncontrado(id));
        if (request.codigo() != null && !normalizarCodigoSinValidar(request.codigo()).equals(tipo.getCodigo())) {
            throw new IllegalArgumentException("El codigo de un tipo de mantenimiento no se puede modificar");
        }
        exigirHuella(request.huella(), tipo);

        String nombre = request.nombre().strip();
        String descripcion = vacioANulo(request.descripcion());
        ReglaRecalculo regla = ReglaRecalculo.valueOf(request.reglaRecalculo());
        int periodicidad = request.periodicidadMeses();
        boolean requiereDocumento = request.requiereDocumento();

        Map<String, Object[]> cambios = new LinkedHashMap<>();
        anotar(cambios, "nombre", tipo.getNombre(), nombre);
        anotar(cambios, "descripcion", tipo.getDescripcion(), descripcion);
        anotar(cambios, "periodicidadMeses", tipo.getPeriodicidadMeses(), periodicidad);
        anotar(cambios, "reglaRecalculo", tipo.getReglaRecalculo().name(), regla.name());
        anotar(cambios, "requiereDocumento", tipo.isRequiereDocumento(), requiereDocumento);
        if (cambios.isEmpty()) {
            return toDto(tipo, obligacionRepository.countByTipoMantenimientoIdAndActivoTrue(id));
        }
        if (cambios.containsKey("nombre") && nombreEnUso(nombre, id)) {
            throw new ConflictoCodificadoException("NOMBRE_DUPLICADO", "Ya existe un tipo de mantenimiento con ese nombre", null);
        }
        // Proteccion documental (D2): no se salta con confirmarImpacto.
        if (!tipo.isRequiereDocumento() && requiereDocumento) {
            long abiertas = intervencionRepository.contarAbiertasSinDocumento(id);
            if (abiertas > 0) {
                throw new ConflictoCodificadoException("INTERVENCIONES_SIN_DOCUMENTO",
                    "No se puede exigir documento: hay " + abiertas + " intervenciones abiertas de este tipo sin evidencias. "
                        + "Deben adjuntar una evidencia o resolverse antes del cambio",
                    Map.of("intervencionesAbiertas", abiertas));
            }
        }
        long activas = obligacionRepository.countByTipoMantenimientoIdAndActivoTrue(id);
        List<String> sensibles = cambios.keySet().stream()
            .filter(c -> c.equals("periodicidadMeses") || c.equals("reglaRecalculo") || c.equals("requiereDocumento"))
            .toList();
        if (!sensibles.isEmpty() && activas > 0 && !Boolean.TRUE.equals(request.confirmarImpacto())) {
            throw new ConflictoCodificadoException("IMPACTO_NO_CONFIRMADO",
                "El tipo se usa en " + activas + " obligaciones activas. Confirma el cambio: se aplicara a partir de ahora "
                    + "y no modificara las proximas fechas ya guardadas",
                Map.of("obligacionesActivas", activas, "campos", String.join(",", sensibles)));
        }

        tipo.actualizar(nombre, descripcion, periodicidad, requiereDocumento, regla);
        tipoRepository.flush();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", new ArrayList<>(cambios.keySet()));
        cambios.forEach((campo, valores) -> {
            detalle.put(campo + "Anterior", valores[0]);
            detalle.put(campo + "Nuevo", valores[1]);
        });
        detalle.put("obligacionesActivas", activas);
        detalle.put("impactoConfirmado", !sensibles.isEmpty() && activas > 0);
        auditoria.registrar(ENTIDAD, id, "EDITAR", usuario, detalle);
        return toDto(tipo, activas);
    }

    @Transactional
    public TipoGestionDto cambiarActivo(Long id, CambiarActivoTipoRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        bloqueo.bloquear();
        TipoMantenimiento tipo = tipoRepository.findParaEditar(id).orElseThrow(() -> noEncontrado(id));
        exigirHuella(request.huella(), tipo);
        long activas = obligacionRepository.countByTipoMantenimientoIdAndActivoTrue(id);
        boolean activo = request.activo();
        if (tipo.isActivo() == activo) {
            return toDto(tipo, activas);
        }
        if (!activo && activas > 0 && !Boolean.TRUE.equals(request.confirmarImpacto())) {
            throw new ConflictoCodificadoException("IMPACTO_NO_CONFIRMADO",
                "El tipo se usa en " + activas + " obligaciones activas. Confirma la desactivacion: dejara de ofrecerse para "
                    + "obligaciones nuevas y las existentes seguiran funcionando",
                Map.of("obligacionesActivas", activas, "campos", "activo"));
        }
        tipo.cambiarActivo(activo);
        tipoRepository.flush();
        auditoria.registrar(ENTIDAD, id, activo ? "REACTIVAR" : "DESACTIVAR", usuario,
            AuditoriaService.detalle("activoAnterior", !activo, "activoNuevo", activo, "obligacionesActivas", activas));
        return toDto(tipo, activas);
    }

    // ---------- Auxiliares ----------

    private void exigirHuella(String huellaCliente, TipoMantenimiento tipo) {
        if (!huella(tipo).equals(huellaCliente)) {
            throw new ConflictoCodificadoException("DATOS_DESACTUALIZADOS", MENSAJE_DESACTUALIZADO, null);
        }
    }

    private static void anotar(Map<String, Object[]> cambios, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.put(campo, new Object[] {anterior, nuevo});
        }
    }

    /** Huella SHA-256 de los valores editables y del estado (codigo incluido). Sin columna de version (D6). */
    public static String huella(TipoMantenimiento t) {
        String texto = String.join("\u001f", t.getCodigo(), t.getNombre(),
            t.getDescripcion() == null ? "\u0000" : t.getDescripcion(), String.valueOf(t.getPeriodicidadMeses()),
            t.getReglaRecalculo().name(), String.valueOf(t.isRequiereDocumento()), String.valueOf(t.isActivo()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalizarCodigo(String codigo) {
        String normalizado = normalizarCodigoSinValidar(codigo);
        if (!FORMATO_CODIGO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("El codigo debe tener entre 2 y 40 caracteres: letras, numeros y guiones, sin espacios");
        }
        return normalizado;
    }

    private static String normalizarCodigoSinValidar(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
    }

    // D3: comparacion en Java (no con lower() de SQL, que depende de la intercalacion de la base y no trata igual las
    // tildes). El catalogo es pequeno y la comprobacion se hace con el bloqueo del catalogo tomado.
    private boolean nombreEnUso(String nombre, Long excluirId) {
        String buscado = clave(nombre);
        return tipoRepository.findAll().stream()
            .anyMatch(t -> !t.getId().equals(excluirId) && clave(t.getNombre()).equals(buscado));
    }

    static String clave(String nombre) {
        return nombre.strip().toLowerCase(Locale.ROOT);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("Tipo de mantenimiento no encontrado: " + id);
    }

    private static TipoGestionDto toDto(TipoMantenimiento t, long obligacionesActivas) {
        return new TipoGestionDto(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion(), t.getPeriodicidadMeses(),
            t.isRequiereDocumento(), t.getReglaRecalculo().name(), t.isActivo(), obligacionesActivas, huella(t));
    }
}
