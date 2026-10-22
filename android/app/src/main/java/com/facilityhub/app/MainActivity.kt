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
    vm: FacilityViewModel = viewModel()
) {

    val nav = rememberNavController()

    val start =
        if (vm.loggedIn) {
            "centros"
        } else {
            "login"
        }

    NavHost(
        navController = nav,
        startDestination = start
    ) {

        composable("login") {

            LoginScreen(vm) {

                nav.navigate("centros") {

                    popUpTo("login") {
                        inclusive = true
                    }
                }
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

                onLogout = {

                    vm.logout()

                    nav.navigate("login") {
                        popUpTo(0)
                    }
                }
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
                obligacion = obligacion
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