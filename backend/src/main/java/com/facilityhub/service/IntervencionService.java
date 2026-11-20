package com.facilityhub.service;

import com.facilityhub.api.dto.intervencion.*;
import com.facilityhub.domain.*;
import com.facilityhub.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

// FH-006 / P6-C1: registro, evidencias, presentacion y validacion de intervenciones.
// - FINALIZADO (operativo) = ejecutado y presentado; el cumplimiento solo existe tras APROBADO (nunca implicito).
// - La proxima fecha solo cambia al aprobar una intervencion ejecutada (no NO_REALIZADO), segun la regla del tipo (D-7).
// - Concurrencia: version optimista en cada escritura y bloqueo de fila en envio y validacion (sin doble envio).
// - Auditoria en la misma transaccion; historial de validaciones inmutable.
@Service
public class IntervencionService {
    static final String ENTIDAD = "INTERVENCION";

    private final IntervencionRepository intervencionRepository;
    private final ValidacionIntervencionRepository validacionRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final AsignacionMantenimientoRepository asignacionRepository;
    private final DocumentoMantenimientoRepository documentoRepository;
    private final DocumentStorageService storageService;
    private final DocumentoAutorizacionService autorizacion;
    private final PoliticaAccesoService politica;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public IntervencionService(IntervencionRepository intervencionRepository,
                               ValidacionIntervencionRepository validacionRepository,
                               MantenimientoRepository mantenimientoRepository,
                               AsignacionMantenimientoRepository asignacionRepository,
                               DocumentoMantenimientoRepository documentoRepository,
                               DocumentStorageService storageService,
                               DocumentoAutorizacionService autorizacion,
                               PoliticaAccesoService politica,
                               AuditoriaService auditoria,
                               Clock clock) {
        this.intervencionRepository = intervencionRepository;
        this.validacionRepository = validacionRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.asignacionRepository = asignacionRepository;
        this.documentoRepository = documentoRepository;
        this.storageService = storageService;
        this.autorizacion = autorizacion;
        this.politica = politica;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    // ---------- Alta ----------

    // PROVEEDOR: con asignacion activa de su empresa (si no, 404 uniforme). Gestion: registro de un proveedor externo
    // sin acceso (D-2), sobre la asignacion activa. INQUILINO: nunca.
    @Transactional
    public IntervencionDto registrar(Long mantenimientoId, DatosIntervencionRequest datos, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Mantenimiento mantenimiento;
        Proveedor proveedor;
        Usuario tecnico;
        OrigenIntervencion origen;
        if (autorizacion.esProveedor(usuario)) {
            AsignacionMantenimiento asignacion = politica.asignacionActivaDelProveedor(usuario, mantenimientoId)
                .orElseThrow(RecursoNoDisponibleException::new);
            mantenimiento = asignacion.getMantenimiento();
            proveedor = asignacion.getProveedor();
            tecnico = usuario;
            origen = OrigenIntervencion.PROVEEDOR;
        } else if (autorizacion.esGestion(usuario)) {
            mantenimiento = mantenimientoRepository.findById(mantenimientoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mantenimiento no encontrado: " + mantenimientoId));
            AsignacionMantenimiento asignacion = asignacionRepository.findByMantenimientoIdAndActivaTrue(mantenimientoId)
                .orElseThrow(() -> new ConflictoException("El mantenimiento no tiene proveedor asignado"));
            proveedor = asignacion.getProveedor();
            tecnico = asignacion.getTecnico();
            origen = OrigenIntervencion.REGISTRO_GESTION;
        } else {
            throw new AccessDeniedException("Operacion no permitida");
        }
        if (!mantenimiento.isValidacionRequerida()) {
            throw new ConflictoException("Mantenimiento historico P5: sin validacion P6");
        }
        if (!politica.admiteNuevaIntervencion(mantenimiento)) {
            throw new ConflictoException("El mantenimiento no admite una nueva intervencion");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        Intervencion intervencion = new Intervencion(mantenimiento, proveedor, tecnico, origen, usuario, ahora);
        intervencion.actualizar(datos == null ? null : datos.fechaEjecucion(), datos == null ? null : datos.actuaciones(),
            datos == null ? null : datos.resultado(), datos == null ? null : datos.incidencias(), ahora);
        // El indice unico de intervencion abierta impide dos altas concurrentes (409).
        intervencionRepository.saveAndFlush(intervencion);
        mantenimiento.iniciarEjecucion(ahora);
        auditoria.registrar(ENTIDAD, intervencion.getId(), "REGISTRAR", usuario, AuditoriaService.detalle(
            "mantenimientoId", mantenimiento.getId(), "origen", origen.name(), "proveedorId", proveedor.getId()));
        return toDto(usuario, intervencion);
    }

    // ---------- Edicion y evidencias ----------

    @Transactional
    public IntervencionDto actualizar(Long intervencionId, DatosIntervencionRequest datos, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Intervencion intervencion = intervencionLegible(usuario, intervencionId);
        exigirEditable(usuario, intervencion);
        exigirVersion(datos.version(), intervencion);
        intervencion.actualizar(datos.fechaEjecucion(), datos.actuaciones(), datos.resultado(), datos.incidencias(),
            LocalDateTime.now(clock));
        intervencionRepository.saveAndFlush(intervencion);
        List<String> campos = new ArrayList<>();
        if (datos.fechaEjecucion() != null) campos.add("fechaEjecucion");
        if (datos.actuaciones() != null) campos.add("actuaciones");
        if (datos.resultado() != null) campos.add("resultado");
        if (datos.incidencias() != null) campos.add("incidencias");
        auditoria.registrar(ENTIDAD, intervencion.getId(), "EDITAR", usuario, AuditoriaService.detalle(
            "campos", String.join(",", campos), "estado", intervencion.getEstadoValidacion().name()));
        return toDto(usuario, intervencion);
    }

    @Transactional
    public IntervencionDto adjuntar(Long intervencionId, MultipartFile file, CategoriaDocumento categoria, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        Intervencion intervencion = intervencionLegible(usuario, intervencionId);
        exigirEditable(usuario, intervencion);
        if (categoria == null) {
            throw new IllegalArgumentException("La categoria es obligatoria");
        }
        String ruta = storageService.guardar(intervencion.getMantenimiento().getId(), file);
        String nombre = file.getOriginalFilename() == null ? "documento" : file.getOriginalFilename();
        DocumentoMantenimiento documento = documentoRepository.save(
            DocumentoMantenimiento.deIntervencion(intervencion, nombre, ruta, categoria, usuario));
        auditoria.registrar(ENTIDAD, intervencion.getId(), "ADJUNTAR", usuario, AuditoriaService.detalle(
            "documentoId", documento.getId(), "categoria", categoria.name()));
        return toDto(usuario, intervencion);
    }

    // ---------- Presentacion ----------

    @Transactional
    public IntervencionDto enviar(Long intervencionId, Long version, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        intervencionLegible(usuario, intervencionId);
        // Bloqueo de fila: un segundo envio simultaneo espera y despues encuentra PENDIENTE_VALIDACION (409).
        Intervencion intervencion = intervencionRepository.bloquear(intervencionId).orElseThrow(RecursoNoDisponibleException::new);
        exigirEditable(usuario, intervencion);
        exigirVersion(version, intervencion);
        Mantenimiento mantenimiento = intervencion.getMantenimiento();
        ObligacionMantenimiento obligacion = mantenimiento.getObligacion();
        LocalDate hoy = autorizacion.hoy();
        LocalDate fecha = intervencion.getFechaEjecucion();
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha de ejecucion es obligatoria");
        }
        if (fecha.isAfter(hoy)) {
            throw new IllegalArgumentException("La fecha de ejecucion no puede ser futura");
        }
        if (fecha.isBefore(obligacion.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de ejecucion es anterior al inicio de la obligacion");
        }
        ResultadoIntervencion resultado = intervencion.getResultado();
        if (resultado != null && resultado.ejecutado()) {
            // D-4: se admite la ejecucion anticipada, pero no reutilizar una ejecucion ya acreditada.
            LocalDate ultima = intervencionRepository.ultimaEjecucionAprobada(obligacion.getId());
            if (ultima != null && !fecha.isAfter(ultima)) {
                throw new IllegalArgumentException("La fecha de ejecucion debe ser posterior a la ultima ejecucion aprobada");
            }
        } else if (intervencionRepository.existsByMantenimientoIdAndEstadoValidacionIn(mantenimiento.getId(),
            List.of(EstadoValidacion.APROBADO))) {
            // Un intento (incluso historico) nunca altera un cumplimiento ya aprobado del mantenimiento.
            throw new ConflictoException("El mantenimiento ya tiene un cumplimiento aprobado");
        }
        // NO_REALIZADO: fecha real del intento (puede ser historica, no futura ni anterior al inicio de la obligacion).
        if (resultado != null && resultado.ejecutado() && obligacion.getTipoMantenimiento().isRequiereDocumento()
            && documentoRepository.countByIntervencionId(intervencion.getId()) == 0) {
            throw new IllegalArgumentException("Este mantenimiento requiere adjuntar al menos una evidencia o certificado");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        intervencion.enviar(usuario, ahora, mantenimiento.getFechaProgramada());
        if (resultado.ejecutado()) {
            mantenimiento.registrarEjecucion(fecha, ahora);
        } else {
            mantenimiento.marcarNoRealizado(ahora);
        }
        intervencionRepository.saveAndFlush(intervencion);
        auditoria.registrar(ENTIDAD, intervencion.getId(), "ENVIAR", usuario, AuditoriaService.detalle(
            "numeroEnvio", intervencion.getNumeroEnvio(), "resultado", resultado.name(),
            "ejecucionAnticipada", intervencion.isEjecucionAnticipada(), "origen", intervencion.getOrigen().name()));
        return toDto(usuario, intervencion);
    }

    // ---------- Validacion (solo gestion) ----------

    @Transactional
    public IntervencionDto validar(Long intervencionId, ValidacionRequest request, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        autorizacion.exigirGestion(usuario);
        Intervencion intervencion = intervencionRepository.bloquear(intervencionId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Intervencion no encontrada: " + intervencionId));
        exigirVersion(request.version(), intervencion);
        if (!politica.puedeValidar(usuario, intervencion)) {
            throw new ConflictoException("La intervencion no esta pendiente de validacion");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        Mantenimiento mantenimiento = intervencion.getMantenimiento();
        ObligacionMantenimiento obligacion = mantenimiento.getObligacion();
        if (request.accion() == AccionValidacion.APROBAR) {
            if (intervencion.getResultado() == null || !intervencion.getResultado().ejecutado()) {
                throw new ConflictoException("Una intervencion NO_REALIZADO no puede aprobarse como cumplimiento");
            }
            List<DocumentoMantenimiento> documentos = documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(intervencion.getId());
            Set<Long> compartir = new HashSet<>(request.documentosCompartidos() == null ? List.of() : request.documentosCompartidos());
            Set<Long> deEstaIntervencion = new HashSet<>();
            documentos.forEach(d -> deEstaIntervencion.add(d.getId()));
            if (!deEstaIntervencion.containsAll(compartir)) {
                throw new IllegalArgumentException("Solo se pueden compartir documentos de esta intervencion");
            }
            // D-11 y D-5: solo certificados, de forma explicita y solo si la obligacion afecta a un local.
            // Se valida antes de mutar ningun estado.
            for (DocumentoMantenimiento d : documentos) {
                if (compartir.contains(d.getId())
                    && (d.getCategoria() != CategoriaDocumento.CERTIFICADO || obligacion.getLocal() == null)) {
                    throw new IllegalArgumentException("Solo se comparten certificados de obligaciones de un local");
                }
            }
            intervencion.aprobar(ahora);
            for (DocumentoMantenimiento d : documentos) {
                d.revisar(EstadoRevisionDocumento.VALIDO, null);
                if (compartir.contains(d.getId())) {
                    d.cambiarVisibilidad(VisibilidadDocumento.COMPARTIDO);
                }
            }
            LocalDate anterior = obligacion.getProximaFecha();
            TipoMantenimiento tipo = obligacion.getTipoMantenimiento();
            LocalDate siguiente = tipo.getReglaRecalculo().siguiente(intervencion.getFechaEjecucion(),
                mantenimiento.getFechaProgramada(), tipo.getPeriodicidadMeses());
            obligacion.actualizarProximaFecha(siguiente);
            validacionRepository.save(new ValidacionIntervencion(intervencion, AccionValidacion.APROBAR, request.comentario(), usuario, ahora));
            auditoria.registrar(ENTIDAD, intervencion.getId(), "APROBAR", usuario, AuditoriaService.detalle(
                "numeroEnvio", intervencion.getNumeroEnvio(), "proximaFechaAnterior", anterior, "proximaFechaNueva", siguiente,
                "regla", tipo.getReglaRecalculo().name(), "documentosCompartidos", compartir.size()));
        } else if (request.accion() == AccionValidacion.CERRAR_SIN_CUMPLIMIENTO) {
            if (intervencion.getResultado() != ResultadoIntervencion.NO_REALIZADO) {
                throw new ConflictoException("Solo un intento NO_REALIZADO puede cerrarse sin cumplimiento");
            }
            if (request.comentario() == null || request.comentario().isBlank()) {
                throw new IllegalArgumentException("El cierre sin cumplimiento requiere un comentario");
            }
            if (request.documentosCompartidos() != null && !request.documentosCompartidos().isEmpty()) {
                throw new IllegalArgumentException("Un intento sin cumplimiento no comparte documentos");
            }
            intervencion.cerrarSinCumplimiento(ahora);
            // Evidencias del intento: revisadas, pero INTERNAS y sin valor de cumplimiento. Sin recalculo de fechas.
            documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(intervencion.getId())
                .forEach(d -> d.revisar(EstadoRevisionDocumento.VALIDO, null));
            // El mantenimiento sigue pendiente (PROGRAMADO, sin fecha de realizacion) y admite un nuevo intento.
            mantenimiento.marcarNoRealizado(ahora);
            validacionRepository.save(new ValidacionIntervencion(intervencion, AccionValidacion.CERRAR_SIN_CUMPLIMIENTO,
                request.comentario().trim(), usuario, ahora));
            auditoria.registrar(ENTIDAD, intervencion.getId(), "CERRAR_SIN_CUMPLIMIENTO", usuario, AuditoriaService.detalle(
                "numeroEnvio", intervencion.getNumeroEnvio(), "fechaIntento", intervencion.getFechaEjecucion(),
                "proximaFecha", obligacion.getProximaFecha()));
        } else {
            if (request.comentario() == null || request.comentario().isBlank()) {
                throw new IllegalArgumentException("La solicitud de correccion requiere un comentario");
            }
            intervencion.solicitarCorreccion(ahora);
            if (mantenimiento.getEstado() == EstadoMantenimiento.FINALIZADO) {
                mantenimiento.reabrirEjecucion(ahora);
            } else {
                // Intento NO_REALIZADO devuelto: vuelve a haber una intervencion abierta en curso.
                mantenimiento.iniciarEjecucion(ahora);
            }
            validacionRepository.save(new ValidacionIntervencion(intervencion, AccionValidacion.SOLICITAR_CORRECCION,
                request.comentario().trim(), usuario, ahora));
            auditoria.registrar(ENTIDAD, intervencion.getId(), "SOLICITAR_CORRECCION", usuario, AuditoriaService.detalle(
                "numeroEnvio", intervencion.getNumeroEnvio()));
        }
        intervencionRepository.saveAndFlush(intervencion);
        return toDto(usuario, intervencion);
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public IntervencionDto obtener(Long intervencionId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        return toDto(usuario, intervencionLegible(usuario, intervencionId));
    }

    // Gestion: todas las del mantenimiento. PROVEEDOR: solo las de su empresa y solo si tiene la asignacion activa o
    // alguna intervencion propia en ese mantenimiento (si no, 404 uniforme).
    @Transactional(readOnly = true)
    public List<IntervencionDto> listarPorMantenimiento(Long mantenimientoId, String email) {
        Usuario usuario = autorizacion.usuarioActual(email);
        if (autorizacion.esGestion(usuario)) {
            if (!mantenimientoRepository.existsById(mantenimientoId)) {
                throw new RecursoNoEncontradoException("Mantenimiento no encontrado: " + mantenimientoId);
            }
            return intervencionRepository.findByMantenimientoIdOrderByIdDesc(mantenimientoId).stream()
                .map(i -> toDto(usuario, i)).toList();
        }
        if (!autorizacion.esProveedor(usuario)) {
            throw new AccessDeniedException("Operacion no permitida");
        }
        List<IntervencionDto> propias = intervencionRepository.findByMantenimientoIdOrderByIdDesc(mantenimientoId).stream()
            .filter(i -> politica.puedeLeer(usuario, i))
            .map(i -> toDto(usuario, i)).toList();
        if (propias.isEmpty() && politica.asignacionActivaDelProveedor(usuario, mantenimientoId).isEmpty()) {
            throw new RecursoNoDisponibleException();
        }
        return propias;
    }

    @Transactional(readOnly = true)
    public List<IntervencionPendienteDto> bandejaValidacion(String email) {
        autorizacion.exigirGestion(autorizacion.usuarioActual(email));
        return intervencionRepository.bandeja(EstadoValidacion.PENDIENTE_VALIDACION).stream().map(i -> {
            ObligacionMantenimiento o = i.getMantenimiento().getObligacion();
            String local = o.getLocal() == null ? null : o.getLocal().getCodigo();
            return new IntervencionPendienteDto(i.getId(), i.getMantenimiento().getId(), o.getTipoMantenimiento().getNombre(),
                o.getCentro().getNombre(), local, o.getResponsable().name(), i.getProveedor().getRazonSocial(),
                i.getOrigen().name(), i.getNumeroEnvio(), i.getFechaEjecucion(),
                i.getResultado() == null ? null : i.getResultado().name(), i.isEjecucionAnticipada(), i.getEnviadaAt());
        }).toList();
    }

    // ---------- Utilidades ----------

    // Existencia y lectura: lo ajeno o inexistente responde 404 uniforme (gestion ve todo).
    private Intervencion intervencionLegible(Usuario usuario, Long intervencionId) {
        if (!autorizacion.esGestion(usuario) && !autorizacion.esProveedor(usuario)) {
            throw new AccessDeniedException("Operacion no permitida");
        }
        return intervencionRepository.findById(intervencionId)
            .filter(i -> politica.puedeLeer(usuario, i))
            .orElseThrow(RecursoNoDisponibleException::new);
    }

    private void exigirEditable(Usuario usuario, Intervencion intervencion) {
        if (!intervencion.getEstadoValidacion().editable()) {
            throw new ConflictoException("La intervencion no admite cambios en estado " + intervencion.getEstadoValidacion());
        }
        if (!politica.puedeEditar(usuario, intervencion)) {
            throw new AccessDeniedException("Operacion no permitida");
        }
    }

    private static void exigirVersion(Long version, Intervencion intervencion) {
        if (version == null) {
            throw new IllegalArgumentException("La version es obligatoria");
        }
        if (version != intervencion.getVersion()) {
            throw new ConflictoException("La intervencion fue modificada por otro usuario; recarga los datos");
        }
    }

    private IntervencionDto toDto(Usuario usuario, Intervencion i) {
        List<DocumentoIntervencionDto> documentos = i.getId() == null ? List.of()
            : documentoRepository.findByIntervencionIdOrderByFechaSubidaAscIdAsc(i.getId()).stream()
                .map(d -> new DocumentoIntervencionDto(d.getId(), d.getNombreOriginal(),
                    d.getCategoria() == null ? null : d.getCategoria().name(), d.getEstadoRevision().name(),
                    d.getVisibilidad().name(), d.getFechaSubida()))
                .toList();
        List<ValidacionDto> validaciones = i.getId() == null ? List.of()
            : validacionRepository.findByIntervencionIdOrderByFechaAscIdAsc(i.getId()).stream()
                .map(v -> new ValidacionDto(v.getNumeroEnvio(), v.getAccion().name(), v.getComentario(),
                    AsignacionService.nombreCompleto(v.getUsuario()), v.getFecha()))
                .toList();
        return new IntervencionDto(
            i.getId(), i.getMantenimiento().getId(), i.getProveedor().getId(), i.getProveedor().getRazonSocial(),
            AsignacionService.nombreCompleto(i.getTecnico()), i.getOrigen().name(),
            AsignacionService.nombreCompleto(i.getRegistradaPor()), i.getCreatedAt(), i.getNumeroEnvio(), i.getFechaEjecucion(),
            i.getActuaciones(), i.getResultado() == null ? null : i.getResultado().name(), i.getIncidencias(),
            i.getEstadoValidacion().name(), i.isEjecucionAnticipada(), i.getEnviadaAt(),
            AsignacionService.nombreCompleto(i.getEnviadaPor()), i.getVersion(), documentos, validaciones,
            politica.accionesIntervencion(usuario, i));
    }
}
