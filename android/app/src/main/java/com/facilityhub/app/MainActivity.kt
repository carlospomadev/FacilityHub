package com.facilityhub.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
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
            MaterialTheme {
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
    flujo: FlujoViewModel = viewModel()
) {

    val nav = rememberNavController()

    val salir: () -> Unit = {
        vm.logout()
        flujo.limpiarSesion()
        nav.navigate("login") {
            popUpTo(0)
        }
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

                onLogout = salir
            )
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
                onSolicitudes = { nav.navigate("inquilino-solicitudes") }
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

                onMenu = { ruta -> nav.navigate(ruta) }
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