package com.facilityhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { FacilityHubApp() } }
    }
}

@Composable
fun FacilityHubApp(vm: FacilityViewModel = viewModel()) {
    val nav = rememberNavController()
    val start = if (vm.loggedIn) "centros" else "login"

    NavHost(navController = nav, startDestination = start) {
        composable("login") {
            LoginScreen(vm) { nav.navigate("centros") { popUpTo("login") { inclusive = true } } }
        }
        composable("centros") {
            CentrosScreen(vm, onCentro = { c ->
                nav.navigate("locales/${c.id}/${enc(c.nombre)}")
            }, onLogout = {
                vm.logout(); nav.navigate("login") { popUpTo(0) }
            })
        }
        composable("locales/{centroId}/{nombre}", arguments = listOf(
            navArgument("centroId") { type = NavType.LongType }, navArgument("nombre") { type = NavType.StringType }
        )) { e ->
            val id = e.arguments!!.getLong("centroId")
            val nombre = dec(e.arguments!!.getString("nombre")!!)
            LocalesScreen(vm, id, nombre, onLocal = { l -> nav.navigate("local/${l.id}/${enc(l.nombre ?: l.codigo)}") }, onBack = { nav.popBackStack() })
        }
        composable("local/{localId}/{nombre}", arguments = listOf(
            navArgument("localId") { type = NavType.LongType }, navArgument("nombre") { type = NavType.StringType }
        )) { e ->
            val id = e.arguments!!.getLong("localId")
            val nombre = dec(e.arguments!!.getString("nombre")!!)
            LocalDetalleScreen(vm, id, nombre, onObligacion = { o ->
                nav.navigate("mantenimientos/${o.id}/${enc(o.tipoMantenimiento)}/${o.localId}/${o.periodicidadMeses}/${o.proximaFecha}/${o.estado}")
            }, onBack = { nav.popBackStack() })
        }
        composable("mantenimientos/{id}/{tipo}/{localId}/{periodicidad}/{fecha}/{estado}", arguments = listOf(
            navArgument("id") { type = NavType.LongType }, navArgument("tipo") { type = NavType.StringType },
            navArgument("localId") { type = NavType.LongType }, navArgument("periodicidad") { type = NavType.IntType },
            navArgument("fecha") { type = NavType.StringType }, navArgument("estado") { type = NavType.StringType }
        )) { e ->
            val o = ObligacionDto(
                id = e.arguments!!.getLong("id"), localId = e.arguments!!.getLong("localId"), instalacion = null,
                tipoMantenimiento = dec(e.arguments!!.getString("tipo")!!), periodicidadMeses = e.arguments!!.getInt("periodicidad"),
                proximaFecha = e.arguments!!.getString("fecha")!!, estado = e.arguments!!.getString("estado")!!
            )
            MantenimientosScreen(vm, o) { nav.popBackStack() }
        }
    }
}

private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
private fun dec(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
