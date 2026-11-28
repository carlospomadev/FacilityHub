package com.facilityhub.service;

import com.facilityhub.api.dto.inquilino.gestion.CambiarActivoInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.CrearInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.EditarInquilinoRequest;
import com.facilityhub.api.dto.inquilino.gestion.InquilinoGestionDto;
import com.facilityhub.domain.EstadoSolicitudProveedor;
import com.facilityhub.domain.Inquilino;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * FH-015 / RF-05: gestion de inquilinos. Decisiones aprobadas:
 *  - D1: consulta de gestion para ADMIN y EXPLOTACION (incluye inactivos); alta, edicion y activacion solo ADMIN.
 *  - D2: CIF obligatorio en el alta, normalizado (sin espacios, guiones ni puntos, en mayusculas) y unico entre todos los
 *        inquilinos, tambien los inactivos. Sin indice en la BD: bloqueo asesor del catalogo + comprobacion.
 *  - D3: obligatorios CIF y razon social; nombre comercial, email y telefono opcionales (longitudes del esquema).
 *  - D4: el CIF es inmutable; un registro antiguo sin CIF puede recibirlo una sola vez.
 *  - D5: desactivar se bloquea (409 INQUILINO_EN_USO, tambien con confirmacion) si hay ocupaciones vigentes o futuras
 *        activas, o mantenimientos abiertos vinculados a sus ocupaciones; si no, exige confirmacion. Sin cascada.
 *  - D6: sin borrado; ocupaciones, usuarios, solicitudes y declaraciones no se tocan.
 *  - D7: bloqueo asesor del catalogo y despues fila del inquilino (orden fijo) + huella (409 DATOS_DESACTUALIZADOS).
 */
@Service
public class InquilinoGestionService {
    static final String ENTIDAD = "INQUILINO";
    static final int MIN_CIF = 5;
    static final int MAX_CIF = 20;
    static final String MENSAJE_DESACTUALIZADO = "Este inquilino ha sido modificado por otro usuario. Actualiza los datos antes de guardar";
    private static final Pattern FORMATO_CIF = Pattern.compile("^[A-Z0-9]+$");

    private final InquilinoRepository inquilinoRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SolicitudProveedorRepository solicitudRepository;
    private final ObligacionProveedorRepository declaracionRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final BloqueoCatalogo bloqueo;
    private final Clock clock;

    public InquilinoGestionService(InquilinoRepository inquilinoRepository, OcupacionLocalRepository ocupacionRepository,
                                   MantenimientoRepository mantenimientoRepository, UsuarioRepository usuarioRepository,
                                   SolicitudProveedorRepository solicitudRepository,
                                   ObligacionProveedorRepository declaracionRepository,
                                   DocumentoAutorizacionService autorizacion, AuditoriaService auditoria,
                                   BloqueoCatalogo bloqueo, Clock clock) {
        this.inquilinoRepository = inquilinoRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.solicitudRepository = solicitudRepository;
        this.declaracionRepository = declaracionRepository;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.bloqueo = bloqueo;
        this.clock = clock;
    }

    // ---------- Consulta (ADMIN y EXPLOTACION) ----------

    @Transactional(readOnly = true)
    public List<InquilinoGestionDto> listar(String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return inquilinoRepository.findAllByOrderByRazonSocialAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public InquilinoGestionDto obtener(Long id, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return toDto(inquilinoRepository.findById(id).orElseThrow(() -> noEncontrado(id)));
    }

    // ---------- Escritura (solo ADMIN) ----------

    @Transactional
    public InquilinoGestionDto crear(CrearInquilinoRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        String cif = normalizarCif(request.cif());
        String razonSocial = request.razonSocial().strip();
        // Dos altas con el mismo CIF se serializan: la segunda ve la fila de la primera (D2, sin indice unico).
        bloqueo.bloquearInquilinos();
        if (inquilinoRepository.existeCif(cif, -1L)) {
            throw cifDuplicado();
        }
        Inquilino inquilino = inquilinoRepository.saveAndFlush(new Inquilino(cif, razonSocial,
            vacioANulo(request.nombreComercial()), vacioANulo(request.emailContacto()), vacioANulo(request.telefono()),
            LocalDateTime.now(clock)));
        auditoria.registrar(ENTIDAD, inquilino.getId(), "CREAR", usuario, AuditoriaService.detalle(
            "cif", cif, "razonSocial", razonSocial));
        return toDto(inquilino);
    }

    @Transactional
    public InquilinoGestionDto editar(Long id, EditarInquilinoRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        Inquilino inquilino = bloquear(id);
        String cifNuevo = null;
        if (request.cif() != null && !request.cif().isBlank()) {
            String cif = normalizarCif(request.cif());
            if (inquilino.getCif() == null) {
                cifNuevo = cif; // registro antiguo sin CIF: se asigna una sola vez (D4)
            } else if (!cif.equals(normalizarSinValidar(inquilino.getCif()))) {
                throw new IllegalArgumentException("El CIF de un inquilino no se puede modificar");
            }
        }
        exigirHuella(request.huella(), inquilino);
        if (cifNuevo != null && inquilinoRepository.existeCif(cifNuevo, id)) {
            throw cifDuplicado();
        }
        String razonSocial = request.razonSocial().strip();
        String nombreComercial = vacioANulo(request.nombreComercial());
        String emailContacto = vacioANulo(request.emailContacto());
        String telefono = vacioANulo(request.telefono());
        Map<String, Object[]> cambios = new LinkedHashMap<>();
        if (cifNuevo != null) cambios.put("cif", new Object[] {null, cifNuevo});
        anotar(cambios, "razonSocial", inquilino.getRazonSocial(), razonSocial);
        anotar(cambios, "nombreComercial", inquilino.getNombreComercial(), nombreComercial);
        anotar(cambios, "emailContacto", inquilino.getEmailContacto(), emailContacto);
        anotar(cambios, "telefono", inquilino.getTelefono(), telefono);
        if (cambios.isEmpty()) {
            return toDto(inquilino);
        }
        if (cifNuevo != null) inquilino.asignarCif(cifNuevo);
        inquilino.actualizar(razonSocial, nombreComercial, emailContacto, telefono);
        inquilinoRepository.flush();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", new ArrayList<>(cambios.keySet()));
        cambios.forEach((campo, v) -> { detalle.put(campo + "Anterior", v[0]); detalle.put(campo + "Nuevo", v[1]); });
        auditoria.registrar(ENTIDAD, id, "EDITAR", usuario, detalle);
        return toDto(inquilino);
    }

    @Transactional
    public InquilinoGestionDto cambiarActivo(Long id, CambiarActivoInquilinoRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        Inquilino inquilino = bloquear(id);
        exigirHuella(request.huella(), inquilino);
        boolean activo = request.activo();
        if (inquilino.isActivo() == activo) {
            return toDto(inquilino);
        }
        InquilinoGestionDto uso = toDto(inquilino);
        if (!activo) {
            // Se comprueba antes que la confirmacion: confirmarImpacto no evita el bloqueo. Desactivar corta el acceso
            // de sus usuarios al area del inquilino, por eso no se permite mientras ocupe (o vaya a ocupar) un local.
            if (uso.ocupacionesVigentes() > 0 || uso.ocupacionesFuturas() > 0 || uso.mantenimientosAbiertos() > 0) {
                throw new ConflictoCodificadoException("INQUILINO_EN_USO",
                    "No se puede desactivar: el inquilino tiene " + uso.ocupacionesVigentes() + " ocupaciones vigentes, "
                        + uso.ocupacionesFuturas() + " ocupaciones futuras y " + uso.mantenimientosAbiertos()
                        + " mantenimientos abiertos",
                    Map.of("ocupacionesVigentes", uso.ocupacionesVigentes(), "ocupacionesFuturas", uso.ocupacionesFuturas(),
                        "mantenimientosAbiertos", uso.mantenimientosAbiertos()));
            }
            if (!Boolean.TRUE.equals(request.confirmarImpacto())) {
                throw new ConflictoCodificadoException("IMPACTO_NO_CONFIRMADO",
                    "Confirma la desactivacion: sus usuarios dejaran de acceder al area del inquilino",
                    Map.of("ocupaciones", uso.ocupaciones(), "usuarios", uso.usuarios(),
                        "solicitudesPendientes", uso.solicitudesPendientes(),
                        "declaracionesActivas", uso.declaracionesActivas()));
            }
        }
        inquilino.cambiarActivo(activo);
        inquilinoRepository.flush();
        auditoria.registrar(ENTIDAD, id, activo ? "REACTIVAR" : "DESACTIVAR", usuario, AuditoriaService.detalle(
            "activoAnterior", !activo, "activoNuevo", activo, "ocupaciones", uso.ocupaciones(),
            "ocupacionesVigentes", uso.ocupacionesVigentes(), "ocupacionesFuturas", uso.ocupacionesFuturas(),
            "mantenimientosAbiertos", uso.mantenimientosAbiertos(), "usuarios", uso.usuarios(),
            "solicitudesPendientes", uso.solicitudesPendientes(), "declaracionesActivas", uso.declaracionesActivas()));
        return toDto(inquilino);
    }

    // ---------- Auxiliares ----------

    // Orden fijo (D7): bloqueo asesor del catalogo y despues la fila del inquilino, leida ya bloqueada.
    private Inquilino bloquear(Long id) {
        bloqueo.bloquearInquilinos();
        return inquilinoRepository.findParaGestion(id).orElseThrow(() -> noEncontrado(id));
    }

    private void exigirHuella(String huellaCliente, Inquilino inquilino) {
        if (!huella(inquilino).equals(huellaCliente)) {
            throw new ConflictoCodificadoException("DATOS_DESACTUALIZADOS", MENSAJE_DESACTUALIZADO, null);
        }
    }

    private static void anotar(Map<String, Object[]> cambios, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) cambios.put(campo, new Object[] {anterior, nuevo});
    }

    /** Huella SHA-256 de los valores y el estado. Sin columna de version (D7). */
    public static String huella(Inquilino i) {
        String texto = String.join("\u001f", nulo(i.getCif()), nulo(i.getRazonSocial()), nulo(i.getNombreComercial()),
            nulo(i.getEmailContacto()), nulo(i.getTelefono()), String.valueOf(i.isActivo()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nulo(String v) { return v == null ? "\u0000" : v; }

    /** D2: sin espacios, guiones ni puntos, en mayusculas; solo letras y digitos, de 5 a 20 caracteres. */
    static String normalizarCif(String cif) {
        String normalizado = normalizarSinValidar(cif);
        if (normalizado.length() < MIN_CIF || normalizado.length() > MAX_CIF || !FORMATO_CIF.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("El CIF/NIF es obligatorio: de " + MIN_CIF + " a " + MAX_CIF
                + " letras o digitos (se ignoran espacios, guiones y puntos)");
        }
        return normalizado;
    }

    private static String normalizarSinValidar(String cif) {
        return cif == null ? "" : cif.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static ConflictoCodificadoException cifDuplicado() {
        return new ConflictoCodificadoException("CIF_DUPLICADO", "Ya existe un inquilino con ese CIF/NIF", null);
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("Inquilino no encontrado: " + id);
    }

    private InquilinoGestionDto toDto(Inquilino i) {
        Long id = i.getId();
        LocalDate hoy = autorizacion.hoy(); // fecha de Europe/Madrid (reloj de la aplicacion)
        return new InquilinoGestionDto(id, i.getCif(), i.getRazonSocial(), i.getNombreComercial(), i.getEmailContacto(),
            i.getTelefono(), i.isActivo(), ocupacionRepository.countByInquilinoId(id),
            ocupacionRepository.contarVigentesDelInquilino(id, hoy), ocupacionRepository.contarFuturasDelInquilino(id, hoy),
            mantenimientoRepository.contarAbiertosDelInquilino(id), usuarioRepository.countByInquilinoId(id),
            solicitudRepository.countByInquilinoIdAndEstado(id, EstadoSolicitudProveedor.PENDIENTE),
            declaracionRepository.contarActivasDelInquilino(id), huella(i));
    }
}
