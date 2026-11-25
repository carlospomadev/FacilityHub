package com.facilityhub.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.facilityhub.app.ui.theme.FacilityHubTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.facilityhub.app.data.ObligacionDto
import com.facilityhub.app.ui.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val localNetworkPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            // Si el usuario acepta, FacilityHub podrá acceder
            // al backend local mediante 10.0.2.2.
            // Si lo rechaza, la app seguirá abierta,
            // pero las llamadas al backend local fallarán.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        solicitarPermisoRedLocal()

        enableEdgeToEdge()

        setContent {
            // FH-009 / R1: tema propio (claro y oscuro segun el sistema).
            FacilityHubTheme {
                FacilityHubApp()
            }
        }
    }

    private fun solicitarPermisoRedLocal() {

        // ACCESS_LOCAL_NETWORK existe desde Android 17 / API 37.
        if (Build.VERSION.SDK_INT >= 37) {

            if (
                checkSelfPermission(
                    Manifest.permission.ACCESS_LOCAL_NETWORK
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                localNetworkPermissionLauncher.launch(
                    Manifest.permission.ACCESS_LOCAL_NETWORK
                )
            }
        }
    }
}


@Composable
fun FacilityHubApp(
    vm: FacilityViewModel = viewModel(),
    flujo: FlujoViewModel = viewModel(),
    alertas: AlertasViewModel = viewModel(),
    panel: PanelViewModel = viewModel(),
    historial: HistorialViewModel = viewModel()
) {
    // Cierre de sesion local: borra token y datos de todas las pantallas. Con [caducada], el login muestra el motivo.
    val cerrarSesion: (Boolean) -> Unit = { caducada ->
        flujo.limpiarSesion()
        alertas.limpiar()
        panel.limpiar()
        historial.limpiar()
        vm.logout(caducada)
    }
    val salir: () -> Unit = { cerrarSesion(false) }
    val caducar: () -> Unit = { cerrarSesion(true) }

    // FH-009 / R3: cualquier 401 fuera del login (token caducado o no valido) devuelve al login.
    val caducada = vm.sesionCaducada || flujo.sesionCaducada || alertas.sesionCaducada || panel.sesionCaducada ||
        historial.sesionCaducada
    LaunchedEffect(caducada) { if (caducada && vm.loggedIn) caducar() }

    // FH-009 / R3: un NavController por sesion. Al entrar o salir se crea uno nuevo, asi que no quedan pantallas, pilas
    // de pestanas guardadas ni datos de otro usuario accesibles con Atras.
    key(vm.loggedIn) {
        val nav = rememberNavController()
        val pestanas = if (vm.loggedIn) pestanasDeRol(vm.role) else emptyList()
        val rutasPestana = pestanas.map { it.ruta }.toSet()
        val entrada by nav.currentBackStackEntryAsState()
        val rutaActual = entrada?.destination?.route
        val enPestana = rutaActual in rutasPestana

        // Contador de avisos al mostrarse una pantalla con barra inferior (y al volver a ella).
        LaunchedEffect(rutaActual) { if (enPestana) alertas.cargarContador() }

        // Cambio de pestana sin duplicados: se vuelve a la pestana inicial guardando el estado de la actual y se
        // restaura el de la elegida. Atras desde otra pestana lleva a la inicial; desde la inicial, sale de la app.
        val irPestana: (String) -> Unit = { ruta ->
            nav.navigate(ruta) {
                popUpTo(vm.rutaInicio) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        val abrir: (String) -> Unit = { ruta -> if (ruta in rutasPestana) irPestana(ruta) else nav.navigate(ruta) }
        val menuCuenta: @Composable () -> Unit = { MenuCuenta(vm.userName, vm.role, salir) }

        // Las pantallas raiz de una pestana muestran el menu de cuenta y no tienen flecha atras.
        @Composable
        fun Raiz(ruta: String, contenido: @Composable () -> Unit) {
            CompositionLocalProvider(LocalMenuCuenta provides (if (ruta in rutasPestana) menuCuenta else null)) { contenido() }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (enPestana) BarraNavegacion(pestanas, rutaActual, alertas.noLeidas) { p -> irPestana(p.ruta) }
            }
        ) { relleno ->
            // Solo el hueco de la barra inferior: cada pantalla sigue gestionando la barra de estado y el teclado.
            val inferior = PaddingValues(bottom = relleno.calculateBottomPadding())
            NavHost(
                navController = nav,
                startDestination = if (vm.loggedIn) vm.rutaInicio else "login",
                modifier = Modifier.padding(inferior).consumeWindowInsets(inferior)
            ) {

                // Al iniciar sesion cambia vm.loggedIn y se crea la navegacion de la sesion (pantalla inicial del rol).
                composable("login") {
                    LoginScreen(vm) { }
                }

                composable("trabajos") {
                    Raiz("trabajos") {
                        TrabajosScreen(
                            vm = vm,
                            flujo = flujo,
                            onTrabajo = { trabajo -> nav.navigate("trabajo/${trabajo.mantenimientoId}") },
                            onIntervencion = { id -> nav.navigate("intervencion/$id") },
                            onBack = { nav.popBackStack() }
                        )
                    }
                }

                // FH-006 / P6-D1: «Mis notificaciones». Abrir una alerta lleva a la pantalla compatible, que vuelve a
                // validar el acceso contra el backend.
                composable("alertas") {
                    Raiz("alertas") {
                        MisNotificacionesScreen(
                            alertas = alertas,
                            rol = vm.role,
                            onAbrir = { destino ->
                                when (destino) {
                                    is DestinoAlerta.Trabajo -> nav.navigate("trabajo/${destino.mantenimientoId}")
                                    is DestinoAlerta.Intervencion -> nav.navigate("intervencion/${destino.intervencionId}")
                                    DestinoAlerta.Declaraciones -> nav.navigate("declaraciones")
                                    is DestinoAlerta.MantenimientosInquilino ->
                                        nav.navigate("inquilino-mant/${destino.obligacionId}/${enc("Mantenimientos")}")
                                    is DestinoAlerta.MantenimientoGestion ->
                                        nav.navigate("mant-gestion/${destino.mantenimientoId}/${enc("Mantenimiento")}")
                                }
                            },
                            onSesionCaducada = caducar
                        ) {
                            nav.popBackStack()
                        }
                    }
                }

                // FH-006 / P6-C3: rol ausente o desconocido -> sin acceso (denegacion por defecto).
                composable("sin-permisos") {
                    SinPermisosScreen(onLogout = salir)
                }

                composable(
                    route = "intervencion/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { entry ->
                    IntervencionScreen(flujo = flujo, intervencionId = entry.arguments!!.getLong("id")) {
                        nav.popBackStack()
                    }
                }

                composable(
                    route = "mant-gestion/{id}/{titulo}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("titulo") { type = NavType.StringType }
                    )
                ) { entry ->
                    MantenimientoGestionScreen(
                        flujo = flujo,
                        mantenimientoId = entry.arguments!!.getLong("id"),
                        titulo = dec(entry.arguments!!.getString("titulo")!!),
                        onIntervencion = { id -> nav.navigate("intervencion/$id") }
                    ) {
                        nav.popBackStack()
                    }
                }

                // FH-006 / P6-D3-3: panel de Explotacion, solo para gestion (el backend responde 403 al resto).
                composable("panel") {
                    Raiz("panel") {
                        if (vm.puedeGestionar) {
                            PanelGestionScreen(
                                panel = panel,
                                onMantenimiento = { t -> nav.navigate("mant-gestion/${t.mantenimientoId}/${enc(t.tipoMantenimiento)}") },
                                onIntervencion = { id -> nav.navigate("intervencion/$id") },
                                onDeclaraciones = { nav.navigate("declaraciones") },
                                onSolicitudes = { nav.navigate("solicitudes-gestion") },
                                onSesionCaducada = caducar
                            ) {
                                nav.popBackStack()
                            }
                        } else {
                            SinPermisosScreen(onLogout = salir)
                        }
                    }
                }

                // FH-006 / P6-D3-4: historial del local (gestion e INQUILINO; el PROVEEDOR no tiene acceso y el backend
                // responde 403).
                composable(
                    route = "historial/{localId}/{titulo}",
                    arguments = listOf(
                        navArgument("localId") { type = NavType.LongType },
                        navArgument("titulo") { type = NavType.StringType }
                    )
                ) { entry ->
                    if (vm.puedeGestionar || vm.esInquilino) {
                        HistorialLocalScreen(
                            historial = historial,
                            flujo = flujo,
                            localId = entry.arguments!!.getLong("localId"),
                            titulo = dec(entry.arguments!!.getString("titulo")!!),
                            onSesionCaducada = caducar
                        ) {
                            nav.popBackStack()
                        }
                    } else {
                        SinPermisosScreen(onLogout = salir)
                    }
                }

                composable("validaciones") {
                    Raiz("validaciones") {
                        BandejaValidacionScreen(flujo, onIntervencion = { id -> nav.navigate("intervencion/$id") }) { nav.popBackStack() }
                    }
                }

                composable("proveedores") {
                    Raiz("proveedores") {
                        ProveedoresScreen(flujo) { nav.popBackStack() }
                    }
                }

                // FH-012 / RF-08: catalogo de tipos de mantenimiento y su formulario (alta y edicion solo ADMIN).
                composable("tipos-mantenimiento") {
                    TiposMantenimientoScreen(
                        vm = vm,
                        onNuevo = { nav.navigate("tipo-nuevo") },
                        onTipo = { t -> nav.navigate("tipo-editar/${t.id}") },
                        onBack = { nav.popBackStack() }
                    )
                }

                composable("tipo-nuevo") {
                    TipoFormScreen(vm = vm, tipoId = null, onCancelar = { nav.popBackStack() }, onGuardado = { nav.popBackStack() })
                }

                composable("tipo-editar/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    TipoFormScreen(vm = vm, tipoId = entry.arguments!!.getLong("id"),
                        onCancelar = { nav.popBackStack() }, onGuardado = { nav.popBackStack() })
                }

                composable("declaraciones") {
                    DeclaracionesScreen(flujo) { nav.popBackStack() }
                }

                composable("solicitudes-gestion") {
                    SolicitudesGestionScreen(flujo, esAdmin = vm.esAdmin) { nav.popBackStack() }
                }

                composable("inquilino-obligaciones") {
                    Raiz("inquilino-obligaciones") {
                        ObligacionesInquilinoScreen(
                            flujo = flujo,
                            historial = historial,
                            onObligacion = { o -> nav.navigate("inquilino-mant/${o.id}/${enc(o.tipoMantenimiento)}") },
                            onBack = { nav.popBackStack() },
                            onSesionCaducada = caducar,
                            onCentro = { nav.navigate("centros") }
                        )
                    }
                }

                composable(
                    route = "inquilino-mant/{id}/{titulo}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("titulo") { type = NavType.StringType }
                    )
                ) { entry ->
                    MantenimientosInquilinoScreen(
                        flujo = flujo,
                        obligacionId = entry.arguments!!.getLong("id"),
                        titulo = dec(entry.arguments!!.getString("titulo")!!)
                    ) {
                        nav.popBackStack()
                    }
                }

                composable("inquilino-solicitudes") {
                    Raiz("inquilino-solicitudes") {
                        SolicitudesInquilinoScreen(flujo) { nav.popBackStack() }
                    }
                }

                composable(
                    route = "trabajo/{mantenimientoId}",
                    arguments = listOf(navArgument("mantenimientoId") { type = NavType.LongType })
                ) { entry ->
                    TrabajoDetalleScreen(
                        vm = vm,
                        mantenimientoId = entry.arguments!!.getLong("mantenimientoId"),
                        flujo = flujo,
                        onIntervencion = { id -> nav.navigate("intervencion/$id") }
                    ) {
                        nav.popBackStack()
                    }
                }

                // Centros: pestana de gestion (con los accesos que el rol no tiene en la barra); para el INQUILINO es
                // una pantalla secundaria abierta desde «Mi local».
                composable("centros") {
                    Raiz("centros") {
                        CentrosScreen(
                            vm = vm,
                            onCentro = { centro -> nav.navigate("locales/${centro.id}/${enc(centro.nombre)}") },
                            onBack = { nav.popBackStack() },
                            accesos = if (vm.puedeGestionar) ACCESOS_GESTION.filter { it.first !in rutasPestana } else emptyList(),
                            onMenu = abrir
                        )
                    }
                }

                rutasCentro(nav, vm)
            }
        }
    }
}

// Accesos de gestion (ruta, etiqueta); en «Centros» solo se muestran los que el rol no tiene en la barra inferior.
private val ACCESOS_GESTION = listOf(
    "validaciones" to "Validaciones",
    "proveedores" to "Proveedores",
    "declaraciones" to "Declarados",
    "solicitudes-gestion" to "Solicitudes",
    // FH-012 / RF-08: catalogo de tipos (ADMIN gestiona; EXPLOTACION consulta).
    "tipos-mantenimiento" to "Tipos de mantenimiento"
)

// Navegacion dentro de un centro: locales, ficha del local, mantenimientos y documentos (sin cambios en R3).
private fun NavGraphBuilder.rutasCentro(nav: NavHostController, vm: FacilityViewModel) {
    composable(
        route = "locales/{centroId}/{nombre}",

        arguments = listOf(

            navArgument("centroId") {
                type = NavType.LongType
            },

            navArgument("nombre") {
                type = NavType.StringType
            }
        )
    ) { entry ->

        val centroId =
            entry.arguments!!.getLong("centroId")

        val nombre =
            dec(
                entry.arguments!!
                    .getString("nombre")!!
            )

        LocalesScreen(
            vm = vm,
            centroId = centroId,
            centroNombre = nombre,

            onLocal = { local ->

                nav.navigate(
                    "local/${local.id}/${enc(local.nombre ?: local.codigo)}/$centroId/${enc(nombre)}"
                )
            },

            onInstalaciones = { nav.navigate("instalaciones-centro/$centroId/${enc(nombre)}") },

            onBack = {
                nav.popBackStack()
            }
        )
    }

    // FH-010A / RF-07: instalaciones del centro y alta (zona comun o local). localId = -1 -> zona comun.
    composable(
        route = "instalaciones-centro/{centroId}/{nombre}",
        arguments = listOf(navArgument("centroId") { type = NavType.LongType }, navArgument("nombre") { type = NavType.StringType })
    ) { entry ->
        val centroId = entry.arguments!!.getLong("centroId")
        val nombre = dec(entry.arguments!!.getString("nombre")!!)
        InstalacionesCentroScreen(
            vm = vm, centroId = centroId, centroNombre = nombre,
            onNueva = { nav.navigate("instalacion-nueva/$centroId/${enc(nombre)}/-1") },
            onNuevaObligacion = { nav.navigate("obligacion-nueva/$centroId/${enc(nombre)}/-1") },
            onBack = { nav.popBackStack() }
        )
    }

    // FH-010B / RF-09: alta de obligacion. localId = -1 -> zona comun.
    composable(
        route = "obligacion-nueva/{centroId}/{nombre}/{localId}",
        arguments = listOf(
            navArgument("centroId") { type = NavType.LongType },
            navArgument("nombre") { type = NavType.StringType },
            navArgument("localId") { type = NavType.LongType }
        )
    ) { entry ->
        val centroId = entry.arguments!!.getLong("centroId")
        val nombre = dec(entry.arguments!!.getString("nombre")!!)
        val localId = entry.arguments!!.getLong("localId").takeIf { it > 0 }
        ObligacionFormScreen(
            vm = vm, centroId = centroId, centroNombre = nombre, localInicial = localId,
            // Cancelar no llama a la API: solo vuelve a la pantalla anterior.
            onCancelar = { nav.popBackStack() },
            onCreada = { nav.popBackStack() }
        )
    }

    composable(
        route = "instalacion-nueva/{centroId}/{nombre}/{localId}",
        arguments = listOf(
            navArgument("centroId") { type = NavType.LongType },
            navArgument("nombre") { type = NavType.StringType },
            navArgument("localId") { type = NavType.LongType }
        )
    ) { entry ->
        val centroId = entry.arguments!!.getLong("centroId")
        val nombre = dec(entry.arguments!!.getString("nombre")!!)
        val localId = entry.arguments!!.getLong("localId").takeIf { it > 0 }
        InstalacionFormScreen(
            vm = vm, centroId = centroId, centroNombre = nombre, localInicial = localId,
            // Cancelar no llama a la API: solo vuelve a la pantalla anterior.
            onCancelar = { nav.popBackStack() },
            onCreada = { nav.popBackStack() }
        )
    }


    composable(
        route = "local/{localId}/{nombre}/{centroId}/{centroNombre}",

        arguments = listOf(

            navArgument("localId") {
                type = NavType.LongType
            },

            navArgument("nombre") {
                type = NavType.StringType
            },

            navArgument("centroId") { type = NavType.LongType },

            navArgument("centroNombre") { type = NavType.StringType }
        )
    ) { entry ->

        val localId =
            entry.arguments!!.getLong("localId")

        val nombre =
            dec(
                entry.arguments!!
                    .getString("nombre")!!
            )

        val centroId = entry.arguments!!.getLong("centroId")
        val centroNombre = dec(entry.arguments!!.getString("centroNombre")!!)

        LocalDetalleScreen(
            vm = vm,
            localId = localId,
            localNombre = nombre,

            onNuevaInstalacion = { nav.navigate("instalacion-nueva/$centroId/${enc(centroNombre)}/$localId") },
            onNuevaObligacion = { nav.navigate("obligacion-nueva/$centroId/${enc(centroNombre)}/$localId") },

            onObligacion = { obligacion ->

                nav.navigate(
                    "mantenimientos/" +
                            "${obligacion.id}/" +
                            "${enc(obligacion.tipoMantenimiento)}/" +
                            "${obligacion.localId}/" +
                            "${obligacion.periodicidadMeses}/" +
                            "${obligacion.proximaFecha}/" +
                            obligacion.estado
                )
            },

            onHistorial = { nav.navigate("historial/$localId/${enc(nombre)}") },

            onBack = {
                nav.popBackStack()
            }
        )
    }


    composable(
        route =
            "mantenimientos/{id}/{tipo}/{localId}/{periodicidad}/{fecha}/{estado}",

        arguments = listOf(

            navArgument("id") {
                type = NavType.LongType
            },

            navArgument("tipo") {
                type = NavType.StringType
            },

            navArgument("localId") {
                type = NavType.LongType
            },

            navArgument("periodicidad") {
                type = NavType.IntType
            },

            navArgument("fecha") {
                type = NavType.StringType
            },

            navArgument("estado") {
                type = NavType.StringType
            }
        )
    ) { entry ->

        val obligacion = ObligacionDto(

            id =
                entry.arguments!!.getLong("id"),

            localId =
                entry.arguments!!.getLong("localId"),

            instalacion = null,

            tipoMantenimiento =
                dec(
                    entry.arguments!!
                        .getString("tipo")!!
                ),

            periodicidadMeses =
                entry.arguments!!
                    .getInt("periodicidad"),

            proximaFecha =
                entry.arguments!!
                    .getString("fecha")!!,

            estado =
                entry.arguments!!
                    .getString("estado")!!
        )

        MantenimientosScreen(
            vm = vm,
            obligacion = obligacion,

            onDocumentos = { mantenimiento ->

                nav.navigate(
                    "documentos/${mantenimiento.id}/" +
                            enc(mantenimiento.descripcion ?: mantenimiento.fechaProgramada)
                )
            },

            onIntervenciones = { mantenimiento ->
                nav.navigate(
                    "mant-gestion/${mantenimiento.id}/" +
                            enc(mantenimiento.descripcion ?: mantenimiento.fechaProgramada)
                )
            }
        ) {
            nav.popBackStack()
        }
    }


    composable(
        route = "documentos/{mantenimientoId}/{titulo}",

        arguments = listOf(

            navArgument("mantenimientoId") {
                type = NavType.LongType
            },

            navArgument("titulo") {
                type = NavType.StringType
            }
        )
    ) { entry ->

        DocumentosScreen(
            vm = vm,

            mantenimientoId =
                entry.arguments!!.getLong("mantenimientoId"),

            titulo =
                dec(
                    entry.arguments!!
                        .getString("titulo")!!
                )
        ) {
            nav.popBackStack()
        }
    }
}


private fun enc(
    value: String
): String {

    return URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.toString()
    )
}


private fun dec(
    value: String
): String {

    return URLDecoder.decode(
        value,
        StandardCharsets.UTF_8.toString()
    )
}