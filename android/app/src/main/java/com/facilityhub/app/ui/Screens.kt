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
fun LoginScreen(
    vm: FacilityViewModel,
    onLoggedIn: () -> Unit
) {
    var email by remember {
        mutableStateOf("explotacion@facilityhub.local")
    }

    var password by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FacilityHub",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Mantenimiento preventivo y cumplimiento documental"
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = {
                Text("Correo")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = {
                Text("Contraseña")
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = {
                vm.login(
                    email = email,
                    password = password,
                    onSuccess = onLoggedIn
                )
            },
            enabled = !vm.loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (vm.loading) {
                    "Accediendo..."
                } else {
                    "Iniciar sesión"
                }
            )
        }

        vm.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}


@Composable
fun CentrosScreen(
    vm: FacilityViewModel,
    onCentro: (CentroDto) -> Unit,
    onLogout: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (vm.centros.isEmpty()) {
            vm.cargarCentros()
        }
    }

    ScreenScaffold(
        title = "Centros",
        subtitle = "${vm.userName} · ${vm.role}",
        onAction = onLogout
    ) {
        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.centros) { centro ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCentro(centro)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = centro.nombre,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = listOfNotNull(
                                centro.codigo,
                                centro.ciudad
                            ).joinToString(" · ")
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun LocalesScreen(
    vm: FacilityViewModel,
    centroId: Long,
    centroNombre: String,
    onLocal: (LocalDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(centroId) {
        vm.cargarLocales(centroId)
    }

    ScreenScaffold(
        title = "Locales",
        subtitle = centroNombre,
        onAction = onBack
    ) {
        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.locales) { local ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLocal(local)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = local.nombre ?: local.codigo,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = listOfNotNull(
                                local.codigo,
                                local.planta,
                                local.ubicacion
                            ).joinToString(" · ")
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun LocalDetalleScreen(
    vm: FacilityViewModel,
    localId: Long,
    localNombre: String,
    onObligacion: (ObligacionDto) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(localId) {
        vm.cargarDetalleLocal(localId)
    }

    ScreenScaffold(
        title = localNombre,
        subtitle = "Detalle del local",
        onAction = onBack
    ) {
        if (vm.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        vm.inquilino?.let { inquilino ->
            SectionTitle("Inquilino actual")

            Text(
                text = inquilino.nombreComercial ?: inquilino.razonSocial,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = inquilino.emailContacto.orEmpty()
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        SectionTitle("Instalaciones")

        vm.instalaciones.forEach { instalacion ->
            Text(
                text = "• ${instalacion.nombre} (${instalacion.estado})"
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        SectionTitle("Obligaciones de mantenimiento")

        vm.obligaciones.forEach { obligacion ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable {
                        onObligacion(obligacion)
                    }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = obligacion.tipoMantenimiento,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Próxima fecha: ${obligacion.proximaFecha}"
                    )

                    Text(
                        text = "Periodicidad: ${obligacion.periodicidadMeses} meses · ${obligacion.estado}"
                    )
                }
            }
        }
    }
}


@Composable
fun MantenimientosScreen(
    vm: FacilityViewModel,
    obligacion: ObligacionDto,
    onBack: () -> Unit
) {
    var fecha by remember {
        mutableStateOf(obligacion.proximaFecha)
    }

    var descripcion by remember {
        mutableStateOf("")
    }

    LaunchedEffect(obligacion.id) {
        vm.cargarMantenimientos(obligacion.id)
    }

    ScreenScaffold(
        title = "Mantenimientos",
        subtitle = obligacion.tipoMantenimiento,
        onAction = onBack
    ) {
        SectionTitle("Nueva ejecución")

        OutlinedTextField(
            value = fecha,
            onValueChange = {
                fecha = it
            },
            label = {
                Text("Fecha (AAAA-MM-DD)")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedTextField(
            value = descripcion,
            onValueChange = {
                descripcion = it
            },
            label = {
                Text("Descripción")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Button(
            onClick = {
                vm.crearMantenimiento(
                    obligacionId = obligacion.id,
                    fecha = fecha,
                    descripcion = descripcion.ifBlank { null }
                ) {}
            },
            enabled = !vm.loading
        ) {
            Text("Crear mantenimiento")
        }

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        SectionTitle("Histórico / programados")

        vm.mantenimientos.forEach { mantenimiento ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "${mantenimiento.fechaProgramada} · ${mantenimiento.estado}",
                        fontWeight = FontWeight.Bold
                    )

                    mantenimiento.descripcion?.let { descripcion ->
                        Text(
                            text = descripcion
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ScreenScaffold(
    title: String,
    subtitle: String,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    TextButton(
                        onClick = onAction
                    ) {
                        Text(
                            if (title == "Centros") {
                                "Salir"
                            } else {
                                "Volver"
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            content = content
        )
    }
}


@Composable
private fun SectionTitle(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}