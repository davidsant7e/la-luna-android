package com.davidsant7e.luna.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.davidsant7e.luna.nucleo.Datos
import com.davidsant7e.luna.nucleo.Plantilla
import com.davidsant7e.luna.nucleo.Recordatorios
import com.davidsant7e.luna.nucleo.Repeticion
import com.davidsant7e.luna.nucleo.Suelta
import com.davidsant7e.luna.nucleo.Textos
import java.time.DayOfWeek
import java.time.LocalDate

/** Crear o editar una actividad suelta. */
@Composable
fun DialogoSuelta(inicial: Suelta, nueva: Boolean, guardar: (Suelta) -> Unit, borrar: () -> Unit, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var titulo by remember { mutableStateOf(inicial.titulo) }
    var fecha by remember { mutableStateOf(inicial.fecha) }
    var franja by remember { mutableStateOf(inicial.franja) }
    var nota by remember { mutableStateOf(inicial.nota) }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(if (nueva) "Nueva actividad" else "Actividad") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(titulo, { titulo = it }, Modifier.fillMaxWidth(), label = { Text("¿Qué hay que hacer?") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Día: ")
                    OutlinedButton(onClick = { elegirFecha(ctx, fecha) { fecha = it } }) { Text(Textos.fechaLarga(fecha)) }
                }
                SelectorHorario(franja) { franja = it }
                OutlinedTextField(nota, { nota = it }, Modifier.fillMaxWidth(), label = { Text("Nota (opcional)") }, maxLines = 4)
            }
        },
        confirmButton = {
            TextButton(onClick = { guardar(inicial.copy(titulo = titulo.trim(), fecha = fecha, franja = franja, nota = nota.trim())); cerrar() }, enabled = titulo.isNotBlank()) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row {
                if (!nueva) TextButton(onClick = { borrar(); cerrar() }) { Text("Borrar") }
                TextButton(onClick = cerrar) { Text("Cancelar") }
            }
        },
    )
}

/** Crear o editar una actividad plantilla de un tipo (con antes / mismo día / después). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoPlantilla(inicial: Plantilla, nombreTipo: String, nueva: Boolean, guardar: (Plantilla) -> Unit, borrar: () -> Unit, cerrar: () -> Unit) {
    var titulo by remember { mutableStateOf(inicial.titulo) }
    var cuando by remember { mutableStateOf(Integer.signum(inicial.desplazamiento)) }
    var dias by remember { mutableStateOf(maxOf(1, kotlin.math.abs(inicial.desplazamiento))) }
    var franja by remember { mutableStateOf(inicial.franja) }
    var nota by remember { mutableStateOf(inicial.nota) }
    val nombre = nombreTipo.ifBlank { "este tipo" }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(if (nueva) "Actividad de $nombre" else titulo.ifBlank { "Actividad" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(titulo, { titulo = it }, Modifier.fillMaxWidth(), label = { Text("¿Qué hay que hacer?") }, singleLine = true)
                Text("¿Cuándo, respecto al día de $nombre?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = cuando < 0, onClick = { cuando = -1 }, label = { Text("Antes") })
                    FilterChip(selected = cuando == 0, onClick = { cuando = 0 }, label = { Text("El mismo día") })
                    FilterChip(selected = cuando > 0, onClick = { cuando = 1 }, label = { Text("Después") })
                }
                if (cuando != 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { if (dias > 1) dias-- }) { Text("−") }
                        Text(" ${Textos.desplazamiento(cuando * dias)} ", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { if (dias < 60) dias++ }) { Text("+") }
                    }
                }
                SelectorHorario(franja) { franja = it }
                OutlinedTextField(nota, { nota = it }, Modifier.fillMaxWidth(), label = { Text("Nota (opcional)") }, maxLines = 4)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                guardar(inicial.copy(titulo = titulo.trim(), desplazamiento = cuando * dias, franja = franja, nota = nota.trim())); cerrar()
            }, enabled = titulo.isNotBlank()) { Text("Listo") }
        },
        dismissButton = {
            Row {
                if (!nueva) TextButton(onClick = { borrar(); cerrar() }) { Text("Quitar") }
                TextButton(onClick = cerrar) { Text("Cancelar") }
            }
        },
    )
}

/** Nueva repetición: cada N días o ciertos días de la semana. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoRepeticion(hoy: LocalDate, guardar: (Repeticion) -> Unit, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var semanal by remember { mutableStateOf(false) }
    var cada by remember { mutableStateOf("15") }
    var inicio by remember { mutableStateOf(hoy) }
    var dias by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var conDesde by remember { mutableStateOf(false) }
    var hasta by remember { mutableStateOf<LocalDate?>(null) }
    val n = cada.toIntOrNull()?.takeIf { it in 1..3650 }
    val valida = if (semanal) dias.isNotEmpty() else n != null
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Repetir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !semanal, onClick = { semanal = false }, label = { Text("Cada N días") })
                    FilterChip(selected = semanal, onClick = { semanal = true }, label = { Text("Días de la semana") })
                }
                if (!semanal) {
                    OutlinedTextField(
                        cada, { cada = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(),
                        label = { Text("Cada cuántos días") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = n == null,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Primer día: ")
                        OutlinedButton(onClick = { elegirFecha(ctx, inicio) { inicio = it } }) { Text(Textos.fechaLarga(inicio)) }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            FilterChip(selected = d in dias, onClick = { dias = if (d in dias) dias - d else dias + d }, label = { Text(Textos.inicial(d)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(conDesde, { conDesde = it })
                        Spacer(Modifier.width(8.dp))
                        if (conDesde) OutlinedButton(onClick = { elegirFecha(ctx, inicio) { inicio = it } }) { Text("Desde ${Textos.fechaCorta(inicio)}") }
                        else Text("Desde siempre")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(hasta != null, { hasta = if (it) inicio.plusMonths(3) else null })
                    Spacer(Modifier.width(8.dp))
                    val h = hasta
                    if (h != null) OutlinedButton(onClick = { elegirFecha(ctx, h) { hasta = it } }) { Text("Hasta ${Textos.fechaCorta(h)}") }
                    else Text("Sin fecha final")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valida, onClick = {
                val h = hasta?.takeIf { !it.isBefore(inicio) || semanal && !conDesde }
                guardar(if (semanal) Repeticion.Semanal(dias, if (conDesde) inicio else null, h) else Repeticion.CadaNDias(inicio, n!!, h))
                cerrar()
            }) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

/** Avisos: antes de cada actividad con hora y resumen de la mañana. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoAvisos(actual: Recordatorios.Ajustes, a: Acciones, guardar: (Recordatorios.Ajustes) -> Unit, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var aj by remember { mutableStateOf(actual) }
    var sinPermiso by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("Avisos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Avisar antes de cada actividad con hora", Modifier.weight(1f))
                    Switch(aj.actividades, { aj = aj.copy(actividades = it) })
                }
                if (aj.actividades) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 5, 10, 15, 30, 60).forEach { m ->
                            FilterChip(selected = aj.minutosAntes == m, onClick = { aj = aj.copy(minutosAntes = m) },
                                label = { Text(if (m == 0) "A la hora" else "$m min antes") })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Resumen del día por la mañana", Modifier.weight(1f))
                    Switch(aj.resumen, { aj = aj.copy(resumen = it) })
                }
                if (aj.resumen) {
                    OutlinedButton(onClick = { elegirHora(ctx, aj.horaResumen) { aj = aj.copy(horaResumen = it) } }) { Text("A las ${Textos.hora(aj.horaResumen)}") }
                }
                Text(
                    "Las actividades ya marcadas como hechas no avisan. Android puede retrasar un aviso unos minutos si el celular está en reposo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (sinPermiso) Text("Sin permiso de notificaciones no se pueden mostrar los avisos. Actívalo en Ajustes de Android → Apps → La Luna.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (aj.alguno) a.pedirPermisoAvisos { ok -> if (ok) { guardar(aj); cerrar() } else sinPermiso = true }
                else { guardar(aj); cerrar() }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoImportar(d: Datos, confirmar: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { confirmar(false) },
        title = { Text("¿Cargar el respaldo?") },
        text = {
            Text("Trae ${d.tipos.size} tipos de día y ${d.sueltas.size} actividades sueltas. Reemplaza todo lo que hay ahora en La Luna (exporta antes un respaldo si quieres guardarlo).")
        },
        confirmButton = { TextButton(onClick = { confirmar(true) }) { Text("Reemplazar") } },
        dismissButton = { TextButton(onClick = { confirmar(false) }) { Text("Cancelar") } },
    )
}

@Composable
fun Confirmar(titulo: String, texto: String, si: String, hacer: () -> Unit, cerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = { hacer(); cerrar() }) { Text(si) } },
        dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
    )
}
