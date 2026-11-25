package com.davidsant7e.luna.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidsant7e.luna.nucleo.Agenda
import com.davidsant7e.luna.nucleo.Formato
import com.davidsant7e.luna.nucleo.Plantilla
import com.davidsant7e.luna.nucleo.Textos
import com.davidsant7e.luna.nucleo.TipoDia

// ======================= Lista de tipos =======================

@Composable
fun PantallaTipos(e: Estado, vm: LunaViewModel) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (e.datos.tipos.isEmpty()) item {
                Text(
                    "Un tipo de día es una plantilla: «Viaje», «Trabajo», «Gimnasio»… Le pones sus actividades (también las de " +
                        "días antes o después) y lo asignas a días concretos o con repetición: «Trabajo: L a V», «Viaje: cada 15 días». " +
                        "Esos días se llenan solos con sus checks.",
                    Modifier.padding(8.dp),
                )
            }
            items(e.datos.tipos, key = { it.id }) { t ->
                val proximos = remember(t, e.hoy) { Agenda.proximosDias(t, e.hoy, 4) }
                Card(Modifier.fillMaxWidth().clickable { vm.editarTipo(t.id) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Punto(colorDe(t.color), 14.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(t.nombre, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text("${t.plantillas.size} ${if (t.plantillas.size == 1) "actividad" else "actividades"}", style = MaterialTheme.typography.bodySmall)
                        }
                        t.repeticiones.forEach { Text(Textos.repeticion(it), style = MaterialTheme.typography.bodySmall) }
                        if (t.fechas.isNotEmpty()) Text("${t.fechas.size} ${if (t.fechas.size == 1) "día suelto" else "días sueltos"}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (proximos.isEmpty()) "Sin días próximos" else "Próximos: " + proximos.joinToString(", ") { Textos.fechaCorta(it) },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = vm::nuevoTipo, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Durazno, contentColor = AzulNoche,
        ) { Text("+ Tipo de día") }
    }
}

// ======================= Editor de un tipo =======================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorTipo(t: TipoDia, e: Estado, vm: LunaViewModel) {
    val ctx = LocalContext.current
    val nuevo = e.datos.tipo(t.id) == null
    var plantillaAbierta by remember { mutableStateOf<Pair<Plantilla, Boolean>?>(null) }
    var repeticionNueva by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    var salir by remember { mutableStateOf(false) }
    val cambiado = t != e.datos.tipo(t.id)
    BackHandler { if (cambiado) salir = true else vm.cerrarEditor() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (nuevo) "Nuevo tipo de día" else t.nombre) },
                navigationIcon = { TextButton(onClick = { if (cambiado) salir = true else vm.cerrarEditor() }) { Text("‹", color = Durazno) } },
                actions = { TextButton(onClick = vm::guardarEditando) { Text("Guardar", color = Durazno, fontWeight = FontWeight.Bold) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzulNoche, titleContentColor = Durazno),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(t.nombre, { vm.cambiarEditando(t.copy(nombre = it)) }, Modifier.fillMaxWidth(),
                    label = { Text("Nombre (Viaje, Trabajo…)") }, singleLine = true)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipoDia.COLORES.forEach { c ->
                        Box(
                            Modifier.size(32.dp).background(colorDe(c), CircleShape)
                                .border(if (c == t.color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { vm.cambiarEditando(t.copy(color = c)) },
                        )
                    }
                }
            }

            // ---- Actividades ----
            item { Seccion("Actividades") }
            if (t.plantillas.isEmpty()) item {
                Text("Agrega lo que hay que hacer: «Empacar» 1 día antes, «Revisar documentos» el mismo día a las 6:00, «Deshacer maleta» 1 día después…",
                    style = MaterialTheme.typography.bodySmall)
            }
            val ordenadas = t.plantillas.sortedWith(compareBy({ it.desplazamiento }, { it.franja.inicio ?: -1 }, { it.titulo.lowercase() }))
            items(ordenadas, key = { "p-" + it.id }) { p ->
                Card(Modifier.fillMaxWidth().clickable { plantillaAbierta = p to false }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.titulo, fontWeight = FontWeight.Medium)
                        Text(
                            Textos.desplazamiento(p.desplazamiento).replaceFirstChar { it.uppercase() } + " · " + Textos.franja(p.franja),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (p.desplazamiento != 0) MaterialTheme.colorScheme.secondary else Color.Unspecified,
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { plantillaAbierta = Plantilla(Formato.nuevoId(), "") to true }) { Text("+ Actividad") }
            }

            // ---- Qué días ----
            item { Seccion("¿Qué días?") }
            item {
                Text("Se repite", style = MaterialTheme.typography.labelLarge)
            }
            items(t.repeticiones.indices.toList(), key = { "r-$it" }) { i ->
                val r = t.repeticiones[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Textos.repeticion(r), Modifier.weight(1f))
                    TextButton(onClick = { vm.cambiarEditando(t.copy(repeticiones = t.repeticiones.filterIndexed { j, _ -> j != i })) }) { Text("Quitar") }
                }
            }
            item { OutlinedButton(onClick = { repeticionNueva = true }) { Text("+ Repetición") } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Días sueltos", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        t.fechas.sorted().forEach { f ->
                            InputChip(selected = false, onClick = { vm.cambiarEditando(t.copy(fechas = t.fechas - f)) },
                                label = { Text(Textos.fechaCorta(f) + (if (f.year != e.hoy.year) " ${f.year}" else "") + "  ×") })
                        }
                        OutlinedButton(onClick = { elegirFecha(ctx, e.dia) { f -> vm.cambiarEditando(t.copy(fechas = t.fechas + f, excepciones = t.excepciones - f)) } }) {
                            Text("+ Día")
                        }
                    }
                    Text("También puedes marcar un día desde la pestaña «Día» (+ Marcar día como…).", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (t.excepciones.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Excepciones (días que no)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        t.excepciones.sorted().forEach { f ->
                            InputChip(selected = false, onClick = { vm.cambiarEditando(t.copy(excepciones = t.excepciones - f)) },
                                label = { Text(Textos.fechaCorta(f) + (if (f.year != e.hoy.year) " ${f.year}" else "") + "  ×") })
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Días seguidos = un solo tramo")
                        Text(
                            "Si marcas varios días seguidos (un viaje de 3 días), lo de «antes» va antes del primero y lo de «después» después del último.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(t.agruparSeguidos, { vm.cambiarEditando(t.copy(agruparSeguidos = it)) })
                }
            }
            item {
                val prox = remember(t, e.hoy) { Agenda.proximosDias(t, e.hoy, 6) }
                Text(
                    if (prox.isEmpty()) "Todavía no tiene días próximos." else "Próximos días: " + prox.joinToString(", ") { Textos.fechaCorta(it) },
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (!nuevo) item {
                HorizontalDivider()
                TextButton(onClick = { borrar = true }) { Text("Borrar este tipo de día", color = Choque) }
            }
        }
    }

    plantillaAbierta?.let { (p, esNueva) ->
        DialogoPlantilla(
            p, t.nombre, esNueva,
            guardar = { np -> vm.cambiarEditando(t.copy(plantillas = if (esNueva) t.plantillas + np else t.plantillas.map { if (it.id == np.id) np else it })) },
            borrar = { vm.cambiarEditando(t.copy(plantillas = t.plantillas.filter { it.id != p.id })) },
        ) { plantillaAbierta = null }
    }
    if (repeticionNueva) DialogoRepeticion(e.dia, { r -> vm.cambiarEditando(t.copy(repeticiones = t.repeticiones + r)) }) { repeticionNueva = false }
    if (borrar) Confirmar(
        "¿Borrar «${t.nombre}»?", "Se quitan sus actividades de todos los días (también las ya marcadas como hechas).", "Borrar",
        { vm.borrarTipo(t.id) },
    ) { borrar = false }
    if (salir) Confirmar("¿Salir sin guardar?", "Los cambios de este tipo de día se pierden.", "Salir", { vm.cerrarEditor() }) { salir = false }
}

@Composable
private fun Seccion(t: String) {
    Text(t, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
}
