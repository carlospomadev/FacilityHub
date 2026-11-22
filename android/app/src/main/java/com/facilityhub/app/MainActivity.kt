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
import androidx.compose.runtime.Composable
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

    val nav = rememberNavController()

    val salir: () -> Unit = {
        vm.logout()
        flujo.limpiarSesion()
        alertas.limpiar()
        panel.limpiar()
        historial.limpiar()
        nav.navigate("login") {
            popUpTo(0)
        }
    }

    // FH-006 / P6-D1: acceso a la bandeja desde las pantallas raiz (solo con sesion iniciada).
    val botonAlertas: @Composable () -> Unit = {
        BotonAlertas(alertas) { nav.navigate("alertas") }
    }

    // FH-006 / P6-B: pantalla inicial por rol (PROVEEDOR -> "trabajos"; el resto -> "centros").
    val start =
        if (vm.loggedIn) {
            vm.rutaInicio
        } else {
            "login"
        }

    NavHost(
        navController = nav,
        startDestination = start
    ) {

        composable("login") {

            LoginScreen(vm) {

                nav.navigate(vm.rutaInicio) {

                    popUpTo("login") {
                        inclusive = true
                    }
                }
            }
        }


        composable("trabajos") {

            TrabajosScreen(
                vm = vm,

                onTrabajo = { trabajo ->
                    nav.navigate("trabajo/${trabajo.mantenimientoId}")
                },

                onLogout = salir,

                accionAlertas = botonAlertas
            )
        }


        // FH-006 / P6-D1: «Mis notificaciones». Abrir una alerta lleva a la pantalla compatible, que vuelve a validar
        // el acceso contra el backend.
        composable("alertas") {
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
                onSesionCaducada = salir
            ) {
                nav.popBackStack()
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
            if (vm.puedeGestionar) {
                PanelGestionScreen(
                    panel = panel,
                    onMantenimiento = { t -> nav.navigate("mant-gestion/${t.mantenimientoId}/${enc(t.tipoMantenimiento)}") },
                    onIntervencion = { id -> nav.navigate("intervencion/$id") },
                    onDeclaraciones = { nav.navigate("declaraciones") },
                    onSolicitudes = { nav.navigate("solicitudes-gestion") },
                    onSesionCaducada = salir
                ) {
                    nav.popBackStack()
                }
            } else {
                SinPermisosScreen(onLogout = salir)
            }
        }

        // FH-006 / P6-D3-4: historial del local (gestion e INQUILINO; el PROVEEDOR no tiene acceso y el backend responde 403).
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
                    onSesionCaducada = salir
                ) {
                    nav.popBackStack()
                }
            } else {
                SinPermisosScreen(onLogout = salir)
            }
        }

        composable("validaciones") {
            BandejaValidacionScreen(flujo, onIntervencion = { id -> nav.navigate("intervencion/$id") }) { nav.popBackStack() }
        }

        composable("proveedores") {
            ProveedoresScreen(flujo) { nav.popBackStack() }
        }

        composable("declaraciones") {
            DeclaracionesScreen(flujo) { nav.popBackStack() }
        }

        composable("solicitudes-gestion") {
            SolicitudesGestionScreen(flujo, esAdmin = vm.esAdmin) { nav.popBackStack() }
        }

        composable("inquilino-obligaciones") {
            ObligacionesInquilinoScreen(
                flujo = flujo,
                onObligacion = { o -> nav.navigate("inquilino-mant/${o.id}/${enc(o.tipoMantenimiento)}") },
                onSolicitudes = { nav.navigate("inquilino-solicitudes") },
                onHistorial = { localId, local -> nav.navigate("historial/$localId/${enc(local)}") }
            ) {
                nav.popBackStack()
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
            SolicitudesInquilinoScreen(flujo) { nav.popBackStack() }
        }


        composable(
            route = "trabajo/{mantenimientoId}",

            arguments = listOf(
                navArgument("mantenimientoId") {
                    type = NavType.LongType
                }
            )
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


        composable("centros") {

            CentrosScreen(
                vm = vm,

                onCentro = { centro ->

                    nav.navigate(
                        "locales/${centro.id}/${enc(centro.nombre)}"
                    )
                },

                onLogout = salir,

                onMenu = { ruta -> nav.navigate(ruta) },

                accionAlertas = botonAlertas
            )
        }


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
                        "local/${local.id}/${enc(local.nombre ?: local.codigo)}"
                    )
                },

                onBack = {
                    nav.popBackStack()
                }
            )
        }


        composable(
            route = "local/{localId}/{nombre}",

            arguments = listOf(

                navArgument("localId") {
                    type = NavType.LongType
                },

                navArgument("nombre") {
                    type = NavType.StringType
                }
            )
        ) { entry ->

            val localId =
                entry.arguments!!.getLong("localId")

            val nombre =
                dec(
                    entry.arguments!!
                        .getString("nombre")!!
                )

            LocalDetalleScreen(
                vm = vm,
                localId = localId,
                localNombre = nombre,

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