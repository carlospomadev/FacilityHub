package com.facilityhub.service;

import com.facilityhub.api.dto.centro.CambiarActivoCentroRequest;
import com.facilityhub.api.dto.centro.CentroGestionDto;
import com.facilityhub.api.dto.centro.CrearCentroRequest;
import com.facilityhub.api.dto.centro.EditarCentroRequest;
import com.facilityhub.domain.Centro;
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
 * FH-013 / RF-03: gestion de centros comerciales. Decisiones aprobadas:
 *  - D1: consulta de gestion para ADMIN y EXPLOTACION (incluye inactivos); alta, edicion y activacion solo ADMIN.
 *  - D2: codigo obligatorio, unico e inmutable, [A-Z0-9-]{2,30} en mayusculas.
 *  - D3: no se repite nombre + ciudad (sin distinguir mayusculas ni espacios exteriores), con escrituras serializadas.
 *  - D4: desactivar se bloquea (409 CENTRO_EN_USO) si hay ocupaciones vigentes o futuras o mantenimientos abiertos; si no, exige
 *        confirmacion y no toca nada mas (sin cascada ni borrados). Reactivar esta permitido.
 *  - D5: obligatorios codigo, nombre y ciudad.
 *  - D7: bloqueo asesor del catalogo de centros + bloqueo de la fila + huella de los valores (409 DATOS_DESACTUALIZADOS).
 */
@Service
public class CentroGestionService {
    static final String ENTIDAD = "CENTRO";
    private static final Pattern FORMATO_CODIGO = Pattern.compile("[A-Z0-9-]{2,30}");
    static final String MENSAJE_DESACTUALIZADO = "Este centro ha sido modificado por otro usuario. Actualiza los datos antes de guardar";

    private final CentroRepository centroRepository;
    private final LocalComercialRepository localRepository;
    private final InstalacionRepository instalacionRepository;
    private final ObligacionMantenimientoRepository obligacionRepository;
    private final OcupacionLocalRepository ocupacionRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final DocumentoAutorizacionService autorizacion;
    private final AuditoriaService auditoria;
    private final BloqueoCatalogo bloqueo;
    private final Clock clock;

    public CentroGestionService(CentroRepository centroRepository, LocalComercialRepository localRepository,
                                InstalacionRepository instalacionRepository,
                                ObligacionMantenimientoRepository obligacionRepository,
                                OcupacionLocalRepository ocupacionRepository, MantenimientoRepository mantenimientoRepository,
                                DocumentoAutorizacionService autorizacion, AuditoriaService auditoria, BloqueoCatalogo bloqueo,
                                Clock clock) {
        this.centroRepository = centroRepository;
        this.localRepository = localRepository;
        this.instalacionRepository = instalacionRepository;
        this.obligacionRepository = obligacionRepository;
        this.ocupacionRepository = ocupacionRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.bloqueo = bloqueo;
        this.clock = clock;
    }

    // ---------- Consulta (ADMIN y EXPLOTACION) ----------

    @Transactional(readOnly = true)
    public List<CentroGestionDto> listar(String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return centroRepository.findAllByOrderByNombreAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CentroGestionDto obtener(Long id, String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return toDto(centroRepository.findById(id).orElseThrow(() -> noEncontrado(id)));
    }

    // ---------- Escritura (solo ADMIN) ----------

    @Transactional
    public CentroGestionDto crear(CrearCentroRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        String codigo = normalizarCodigo(request.codigo());
        String nombre = request.nombre().strip();
        String ciudad = request.ciudad().strip();
        bloqueo.bloquearCentros();
        if (centroRepository.existsByCodigo(codigo)) {
            throw new ConflictoCodificadoException("CODIGO_DUPLICADO", "Ya existe un centro con ese codigo", null);
        }
        if (nombreCiudadEnUso(nombre, ciudad, null)) {
            throw new ConflictoCodificadoException("NOMBRE_DUPLICADO", "Ya existe un centro con ese nombre en esa ciudad", null);
        }
        Centro centro = centroRepository.saveAndFlush(new Centro(codigo, nombre, vacioANulo(request.direccion()), ciudad,
            vacioANulo(request.descripcion()), LocalDateTime.now(clock)));
        auditoria.registrar(ENTIDAD, centro.getId(), "CREAR", usuario, AuditoriaService.detalle(
            "codigo", codigo, "nombre", nombre, "ciudad", ciudad));
        return toDto(centro);
    }

    @Transactional
    public CentroGestionDto editar(Long id, EditarCentroRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        // Orden fijo: catalogo de centros y despues la fila (el mismo bloqueo de fila que usan las altas del centro).
        bloqueo.bloquearCentros();
        Centro centro = centroRepository.findParaAlta(id).orElseThrow(() -> noEncontrado(id));
        if (request.codigo() != null && !normalizarSinValidar(request.codigo()).equals(centro.getCodigo())) {
            throw new IllegalArgumentException("El codigo de un centro no se puede modificar");
        }
        exigirHuella(request.huella(), centro);
        String nombre = request.nombre().strip();
        String ciudad = request.ciudad().strip();
        String direccion = vacioANulo(request.direccion());
        String descripcion = vacioANulo(request.descripcion());
        Map<String, Object[]> cambios = new LinkedHashMap<>();
        anotar(cambios, "nombre", centro.getNombre(), nombre);
        anotar(cambios, "ciudad", centro.getCiudad(), ciudad);
        anotar(cambios, "direccion", centro.getDireccion(), direccion);
        anotar(cambios, "descripcion", centro.getDescripcion(), descripcion);
        if (cambios.isEmpty()) {
            return toDto(centro);
        }
        if ((cambios.containsKey("nombre") || cambios.containsKey("ciudad")) && nombreCiudadEnUso(nombre, ciudad, id)) {
            throw new ConflictoCodificadoException("NOMBRE_DUPLICADO", "Ya existe un centro con ese nombre en esa ciudad", null);
        }
        centro.actualizar(nombre, direccion, ciudad, descripcion);
        centroRepository.flush();
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", new ArrayList<>(cambios.keySet()));
        cambios.forEach((campo, v) -> { detalle.put(campo + "Anterior", v[0]); detalle.put(campo + "Nuevo", v[1]); });
        auditoria.registrar(ENTIDAD, id, "EDITAR", usuario, detalle);
        return toDto(centro);
    }

    @Transactional
    public CentroGestionDto cambiarActivo(Long id, CambiarActivoCentroRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirAdmin(usuario);
        bloqueo.bloquearCentros();
        // La fila del centro queda bloqueada: ninguna alta de instalacion, obligacion o mantenimiento del centro puede
        // colarse entre el recuento de trabajo abierto y la desactivacion.
        Centro centro = centroRepository.findParaAlta(id).orElseThrow(() -> noEncontrado(id));
        exigirHuella(request.huella(), centro);
        boolean activo = request.activo();
        if (centro.isActivo() == activo) {
            return toDto(centro);
        }
        CentroGestionDto uso = toDto(centro);
        if (!activo) {
            // Se comprueba antes que la confirmacion: confirmarImpacto no evita el bloqueo.
            if (uso.ocupacionesVigentes() > 0 || uso.ocupacionesFuturas() > 0 || uso.mantenimientosAbiertos() > 0) {
                throw new ConflictoCodificadoException("CENTRO_EN_USO",
                    "No se puede desactivar: el centro tiene " + uso.ocupacionesVigentes() + " ocupaciones vigentes, "
                        + uso.ocupacionesFuturas() + " ocupaciones futuras y " + uso.mantenimientosAbiertos()
                        + " mantenimientos abiertos",
                    Map.of("ocupacionesVigentes", uso.ocupacionesVigentes(), "ocupacionesFuturas", uso.ocupacionesFuturas(),
                        "mantenimientosAbiertos", uso.mantenimientosAbiertos()));
            }
            if (!Boolean.TRUE.equals(request.confirmarImpacto())) {
                throw new ConflictoCodificadoException("IMPACTO_NO_CONFIRMADO",
                    "Confirma la desactivacion: el centro dejara de mostrarse y no admitira altas nuevas",
                    Map.of("localesActivos", uso.localesActivos(), "instalaciones", uso.instalaciones(),
                        "obligacionesActivas", uso.obligacionesActivas()));
            }
        }
        centro.cambiarActivo(activo);
        centroRepository.flush();
        auditoria.registrar(ENTIDAD, id, activo ? "REACTIVAR" : "DESACTIVAR", usuario, AuditoriaService.detalle(
            "activoAnterior", !activo, "activoNuevo", activo, "localesActivos", uso.localesActivos(),
            "instalaciones", uso.instalaciones(), "obligacionesActivas", uso.obligacionesActivas(),
            "ocupacionesVigentes", uso.ocupacionesVigentes(), "ocupacionesFuturas", uso.ocupacionesFuturas(),
            "mantenimientosAbiertos", uso.mantenimientosAbiertos()));
        return toDto(centro);
    }

    // ---------- Auxiliares ----------

    private void exigirHuella(String huellaCliente, Centro centro) {
        if (!huella(centro).equals(huellaCliente)) {
            throw new ConflictoCodificadoException("DATOS_DESACTUALIZADOS", MENSAJE_DESACTUALIZADO, null);
        }
    }

    // D3: comparacion en Java (independiente de la intercalacion de la base), con el bloqueo del catalogo tomado.
    private boolean nombreCiudadEnUso(String nombre, String ciudad, Long excluirId) {
        String buscado = clave(nombre, ciudad);
        return centroRepository.findAll().stream()
            .anyMatch(c -> !c.getId().equals(excluirId) && clave(c.getNombre(), c.getCiudad()).equals(buscado));
    }

    static String clave(String nombre, String ciudad) {
        return (nombre == null ? "" : nombre.strip().toLowerCase(Locale.ROOT)) + "\u001f"
            + (ciudad == null ? "" : ciudad.strip().toLowerCase(Locale.ROOT));
    }

    private static void anotar(Map<String, Object[]> cambios, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) cambios.put(campo, new Object[] {anterior, nuevo});
    }

    /** Huella SHA-256 de los valores editables y del estado (codigo incluido). Sin columna de version (D7). */
    public static String huella(Centro c) {
        String texto = String.join("\u001f", c.getCodigo(), c.getNombre(), nulo(c.getDireccion()), nulo(c.getCiudad()),
            nulo(c.getDescripcion()), String.valueOf(c.isActivo()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nulo(String v) { return v == null ? "\u0000" : v; }

    static String normalizarCodigo(String codigo) {
        String normalizado = normalizarSinValidar(codigo);
        if (!FORMATO_CODIGO.matcher(normalizado).matches()) {
            throw new IllegalArgumentException("El codigo debe tener entre 2 y 30 caracteres: letras, numeros y guiones, sin espacios");
        }
        return normalizado;
    }

    private static String normalizarSinValidar(String codigo) {
        return codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException("Centro no encontrado: " + id);
    }

    private CentroGestionDto toDto(Centro c) {
        Long id = c.getId();
        LocalDate hoy = autorizacion.hoy(); // fecha de Europe/Madrid (reloj de la aplicacion)
        return new CentroGestionDto(id, c.getCodigo(), c.getNombre(), c.getDireccion(), c.getCiudad(), c.getDescripcion(),
            c.isActivo(), localRepository.countByCentroIdAndActivoTrue(id), localRepository.countByCentroId(id),
            instalacionRepository.countByCentroId(id), obligacionRepository.countByCentroIdAndActivoTrue(id),
            ocupacionRepository.contarVigentesDelCentro(id, hoy), ocupacionRepository.contarFuturasDelCentro(id, hoy),
            mantenimientoRepository.contarAbiertosDelCentro(id), huella(c));
    }
}
