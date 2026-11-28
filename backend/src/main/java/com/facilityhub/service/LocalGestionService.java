package com.facilityhub.service;

import com.facilityhub.api.dto.local.CambiarActivoLocalRequest;
import com.facilityhub.api.dto.local.CrearLocalRequest;
import com.facilityhub.api.dto.local.EditarLocalRequest;
import com.facilityhub.api.dto.local.LocalGestionDto;
import com.facilityhub.domain.Centro;
import com.facilityhub.domain.LocalComercial;
import com.facilityhub.domain.Usuario;
import com.facilityhub.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * FH-014 / RF-04: gestion de locales comerciales. Decisiones aprobadas:
 *  - D1: consulta de gestion para ADMIN y EXPLOTACION (incluye inactivos); alta, edicion y activacion solo ADMIN.
 *  - D2: codigo obligatorio, unico dentro del centro (uq_local_centro_codigo), en mayusculas sin espacios exteriores
 *        e inmutable.
 *  - D3: obligatorios centro, codigo y nombre; planta y ubicacion opcionales (longitudes del esquema).
 *  - D4: desactivar se bloquea (409 LOCAL_EN_USO, tambien con confirmacion) si hay ocupaciones vigentes o futuras activas
 *        o mantenimientos abiertos; si no, exige confirmacion. Sin cascada ni borrados.
 *  - D5: centro inactivo: no se crean ni se reactivan locales; editar y desactivar si.
 *  - D6: el centro de un local no cambia (la peticion de edicion lo rechaza explicitamente).
 *  - D7: bloqueo de la fila del centro y despues la del local (orden fijo) + huella (409 DATOS_DESACTUALIZADOS).
 *  - D9: el nombre puede repetirse.
 */
@Service
public class LocalGestionService {
    static final String ENTIDAD = "LOCAL";
    static final int MAX_CODIGO = 40;
    static final String MENSAJE_DESACTUALIZADO = "Este local ha sido modificado por otro usuario. Actualiza los datos antes de guardar";

    private final CentroRepository centroRepository;
    private final LocalComercialRepository localRepository;
    private final InstalacionRepository instalacionRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public LocalGestionService(CentroRepository centroRepository, LocalComercialRepository localRepository,
                               InstalacionRepository instalacionRepository,
                               ObligacionMantenimientoRepository obligacionRepository,
                               OcupacionLocalRepository ocupacionRepository, MantenimientoRepository mantenimientoRepository,
                               DocumentoAutorizacionService autorizacion, AuditoriaService auditoria, Clock clock) {
        this.centroRepository = centroRepository;
        this.localRepository = localRepository;
        this.instalacionRepository = instalacionRepository;
        this.obligacionRepository = obligacionRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---------- Consulta (ADMIN y EXPLOTACION) ----------

    @Transactional(readOnly = true)
    public List<LocalGestionDto> listar(Long centroId, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        if (!centroRepository.existsById(centroId)) {
            throw centroNoEncontrado(centroId);
        }
        return localRepository.findByCentroIdOrderByCodigoAsc(centroId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LocalGestionDto obtener(Long id, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return toDto(localRepository.findById(id).orElseThrow(() -> noEncontrado(id)));
    }

    // ---------- Escritura (solo ADMIN) ----------

    @Transactional
    public LocalGestionDto crear(Long centroId, CrearLocalRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        String codigo = normalizarCodigo(request.codigo());
        String nombre = request.nombre().strip();
        // La fila del centro queda bloqueada: dos altas del mismo centro se serializan (duplicados sin carreras) y una
        // desactivacion simultanea del centro espera a que termine el alta (D5).
        Centro centro = centroRepository.findParaAlta(centroId).orElseThrow(() -> centroNoEncontrado(centroId));
        if (!centro.isActivo()) {
            throw new ConflictoCodificadoException("CENTRO_INACTIVO", "El centro esta inactivo: no admite locales nuevos", null);
        }
        if (localRepository.existsByCentroIdAndCodigoIgnoreCase(centroId, codigo)) {
            throw codigoDuplicado();
        }
        LocalComercial local;
        try {
            local = localRepository.saveAndFlush(new LocalComercial(centro, codigo, nombre, vacioANulo(request.planta()),
                vacioANulo(request.ubicacion()), LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Ultima defensa: el indice uq_local_centro_codigo.
            throw codigoDuplicado();
        }
        auditoria.registrar(ENTIDAD, local.getId(), "CREAR", usuario, AuditoriaService.detalle(
            "centroId", centroId, "codigo", codigo, "nombre", nombre));
        return toDto(local);
    }

    @Transactional
    public LocalGestionDto editar(Long id, EditarLocalRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        LocalComercial local = bloquear(id);
        if (request.centroId() != null && !request.centroId().equals(local.getCentro().getId())) {
            throw new IllegalArgumentException("El centro de un local no se puede modificar");
        }
        if (request.codigo() != null && !normalizarSinValidar(request.codigo()).equals(local.getCodigo())) {
            throw new IllegalArgumentException("El codigo de un local no se puede modificar");
        }
        exigirHuella(request.huella(), local);
        String nombre = request.nombre().strip();
        String planta = vacioANulo(request.planta());
        String ubicacion = vacioANulo(request.ubicacion());
        Map<String, Object[]> cambios = new LinkedHashMap<>();
        anotar(cambios, "nombre", local.getNombre(), nombre);
        anotar(cambios, "planta", local.getPlanta(), planta);
        anotar(cambios, "ubicacion", local.getUbicacion(), ubicacion);
        if (cambios.isEmpty()) {
            return toDto(local);
        }
        local.actualizar(nombre, planta, ubicacion);
        localRepository.flush();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", new ArrayList<>(cambios.keySet()));
        cambios.forEach((campo, v) -> { detalle.put(campo + "Anterior", v[0]); detalle.put(campo + "Nuevo", v[1]); });
        auditoria.registrar(ENTIDAD, id, "EDITAR", usuario, detalle);
        return toDto(local);
    }

    @Transactional
    public LocalGestionDto cambiarActivo(Long id, CambiarActivoLocalRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        // Filas del centro y del local bloqueadas: ninguna alta de instalacion, obligacion o mantenimiento del local
        // puede colarse entre el recuento y el cambio de estado.
        LocalComercial local = bloquear(id);
        exigirHuella(request.huella(), local);
        boolean activo = request.activo();
        if (local.isActivo() == activo) {
            return toDto(local);
        }
        LocalGestionDto uso = toDto(local);
        if (activo) {
            if (!local.getCentro().isActivo()) {
                throw new ConflictoCodificadoException("CENTRO_INACTIVO",
                    "No se puede reactivar: el centro del local esta inactivo", null);
            }
        } else {
            // Se comprueba antes que la confirmacion: confirmarImpacto no evita el bloqueo.
            if (uso.ocupacionesVigentes() > 0 || uso.ocupacionesFuturas() > 0 || uso.mantenimientosAbiertos() > 0) {
                throw new ConflictoCodificadoException("LOCAL_EN_USO",
                    "No se puede desactivar: el local tiene " + uso.ocupacionesVigentes() + " ocupaciones vigentes, "
                        + uso.ocupacionesFuturas() + " ocupaciones futuras y " + uso.mantenimientosAbiertos()
                        + " mantenimientos abiertos",
                    Map.of("ocupacionesVigentes", uso.ocupacionesVigentes(), "ocupacionesFuturas", uso.ocupacionesFuturas(),
                        "mantenimientosAbiertos", uso.mantenimientosAbiertos()));
            }
            if (!Boolean.TRUE.equals(request.confirmarImpacto())) {
                throw new ConflictoCodificadoException("IMPACTO_NO_CONFIRMADO",
                    "Confirma la desactivacion: el local dejara de mostrarse y no admitira altas nuevas",
                    Map.of("instalaciones", uso.instalaciones(), "obligacionesActivas", uso.obligacionesActivas(),
                        "mantenimientos", uso.mantenimientos(), "ocupaciones", uso.ocupaciones()));
            }
        }
        local.cambiarActivo(activo);
        localRepository.flush();
        auditoria.registrar(ENTIDAD, id, activo ? "REACTIVAR" : "DESACTIVAR", usuario, AuditoriaService.detalle(
            "activoAnterior", !activo, "activoNuevo", activo, "centroId", uso.centroId(),
            "instalaciones", uso.instalaciones(), "obligacionesActivas", uso.obligacionesActivas(),
            "mantenimientos", uso.mantenimientos(), "ocupaciones", uso.ocupaciones(),
            "ocupacionesVigentes", uso.ocupacionesVigentes(), "ocupacionesFuturas", uso.ocupacionesFuturas(),
            "mantenimientosAbiertos", uso.mantenimientosAbiertos()));
        return toDto(local);
    }

    // ---------- Auxiliares ----------

    // Orden fijo (D7): fila del centro y despues fila del local, el mismo que usan las altas relacionadas.
    private LocalComercial bloquear(Long id) {
        // Solo el id del centro (sin cargar el local): asi el local se lee ya bloqueado y con sus valores actuales.
        Long centroId = localRepository.centroIdDe(id).orElseThrow(() -> noEncontrado(id));
        centroRepository.findParaAlta(centroId).orElseThrow(() -> centroNoEncontrado(centroId));
        return localRepository.findParaAlta(id).orElseThrow(() -> noEncontrado(id));
    }

    private void exigirHuella(String huellaCliente, LocalComercial local) {
        if (!huella(local).equals(huellaCliente)) {
            throw new ConflictoCodificadoException("DATOS_DESACTUALIZADOS", MENSAJE_DESACTUALIZADO, null);
        }
    }

    private static void anotar(Map<String, Object[]> cambios, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) cambios.put(campo, new Object[] {anterior, nuevo});
    }

    /** Huella SHA-256 del centro, los valores editables y el estado (codigo incluido). Sin columna de version (D7). */
    public static String huella(LocalComercial l) {
        String texto = String.join("\u001f", String.valueOf(l.getCentro().getId()), l.getCodigo(), nulo(l.getNombre()),
            nulo(l.getPlanta()), nulo(l.getUbicacion()), String.valueOf(l.isActivo()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nulo(String v) { return v == null ? "\u0000" : v; }

    static String normalizarCodigo(String codigo) {
        String normalizado = normalizarSinValidar(codigo);
        if (normalizado.isEmpty() || normalizado.length() > MAX_CODIGO
            || normalizado.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("El codigo es obligatorio y debe tener como maximo " + MAX_CODIGO + " caracteres");
        }
        return normalizado;
    }

    private static String normalizarSinValidar(String codigo) {
        return codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static ConflictoCodificadoException codigoDuplicado() {
        return new ConflictoCodificadoException("CODIGO_DUPLICADO", "Ya existe un local con ese codigo en este centro", null);
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("Local no encontrado: " + id);
    }

    private static RecursoNoEncontradoException centroNoEncontrado(Long id) {
        return new RecursoNoEncontradoException("Centro no encontrado: " + id);
    }

    private LocalGestionDto toDto(LocalComercial l) {
        Long id = l.getId();
        Centro c = l.getCentro();
        LocalDate hoy = autorizacion.hoy(); // fecha de Europe/Madrid (reloj de la aplicacion)
        return new LocalGestionDto(id, c.getId(), c.getCodigo(), c.getNombre(), c.isActivo(), l.getCodigo(), l.getNombre(),
            l.getPlanta(), l.getUbicacion(), l.isActivo(), instalacionRepository.countByLocalId(id),
            obligacionRepository.countByLocalIdAndActivoTrue(id), mantenimientoRepository.contarDelLocal(id),
            ocupacionRepository.countByLocalId(id), ocupacionRepository.contarVigentesDelLocal(id, hoy),
            ocupacionRepository.contarFuturasDelLocal(id, hoy), mantenimientoRepository.contarAbiertosDelLocal(id), huella(l));
    }
}
