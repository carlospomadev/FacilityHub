package com.facilityhub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.facilityhub.app.data.*

@Composable
fun LoginScreen(vm: FacilityViewModel, onLoggedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }{ mutableStateOf("explotacion@facilityhub.local") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("FacilityHub", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Mantenimiento preventivo y cumplimiento documental")
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Correo") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.login(email, password, onLoggedIn) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.loading) "Accediendo..." else "Iniciar sesión")
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
fun CentrosScreen(vm: FacilityViewModel, onCentro: (CentroDto) -> Unit, onLogout: () -> Unit) {
    LaunchedEffect(Unit) { if (vm.centros.isEmpty()) vm.cargarCentros() }
    ScreenScaffold("Centros", "${vm.userName} · ${vm.role}", onLogout) {
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.centros) { c ->
                Card(Modifier.fillMaxWidth().clickable { onCentro(c) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.nombre, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(c.codigo, c.ciudad).joinToString(" · "))
                    }
                }
            }
        }
    }
}

@Composable
fun LocalesScreen(vm: FacilityViewModel, centroId: Long, centroNombre: String, onLocal: (LocalDto) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(centroId) { vm.cargarLocales(centroId) }
    ScreenScaffold("Locales", centroNombre, onBack) {
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.locales) { l ->
                Card(Modifier.fillMaxWidth().clickable { onLocal(l) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(l.nombre ?: l.codigo, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(l.codigo, l.planta, l.ubicacion).joinToString(" · "))
                    }
                }
            }
        }
    }
}

@Composable
fun LocalDetalleScreen(vm: FacilityViewModel, localId: Long, localNombre: String, onObligacion: (ObligacionDto) -> Unit, onBack: () -> Unit) {
    LaunchedEffect(localId) { vm.cargarDetalleLocal(localId) }
    ScreenScaffold(localNombre, "Detalle del local", onBack) {
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.inquilino?.let {
            SectionTitle("Inquilino actual")
            Text(it.nombreComercial ?: it.razonSocial, fontWeight = FontWeight.Bold)
            Text(it.emailContacto.orEmpty())
            Spacer(Modifier.height(16.dp))
        }
        SectionTitle("Instalaciones")
        vm.instalaciones.forEach { Text("• ${it.nombre} (${it.estado})") }
        Spacer(Modifier.height(16.dp))
        SectionTitle("Obligaciones de mantenimiento")
        vm.obligaciones.forEach { o ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onObligacion(o) }) {
                Column(Modifier.padding(14.dp)) {
                    Text(o.tipoMantenimiento, fontWeight = FontWeight.Bold)
                    Text("Próxima fecha: ${o.proximaFecha}")
                    Text("Periodicidad: ${o.periodicidadMeses} meses · ${o.estado}")
                }
            }
        }
    }
}

@Composable
fun MantenimientosScreen(vm: FacilityViewModel, obligacion: ObligacionDto, onBack: () -> Unit) {
    var fecha by remember { mutableStateOf(obligacion.proximaFecha) }
    var descripcion by remember { mutableStateOf("") }
    LaunchedEffect(obligacion.id) { vm.cargarMantenimientos(obligacion.id) }
    ScreenScaffold("Mantenimientos", obligacion.tipoMantenimiento, onBack) {
        SectionTitle("Nueva ejecución")
        OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Button(onClick = { vm.crearMantenimiento(obligacion.id, fecha, descripcion.ifBlank { null }) {} }, enabled = !vm.loading) { Text("Crear mantenimiento") }
        Spacer(Modifier.height(18.dp))
        SectionTitle("Histórico / programados")
        vm.mantenimientos.forEach { m ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("${m.fechaProgramada} · ${m.estado}", fontWeight = FontWeight.Bold)
                    m.descripcion?.let { Text(it) }
                }
            }
        }
    }
}

@Composable
private fun ScreenScaffold(title: String, subtitle: String, onAction: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        Surface(shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = onAction) { Text(if (title == "Centros") "Salir" else "Volver") }
            }
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), content = content)
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp)) }
