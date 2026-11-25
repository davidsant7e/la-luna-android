package com.davidsant7e.luna.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.davidsant7e.luna.nucleo.Agenda
import com.davidsant7e.luna.nucleo.Conflictos
import com.davidsant7e.luna.nucleo.Formato
import com.davidsant7e.luna.nucleo.Instancia
import com.davidsant7e.luna.nucleo.Suelta
import com.davidsant7e.luna.nucleo.Textos
import java.time.LocalDate

/** Rojo de los choques de horario. */
val Choque = Color(0xFFC62828)

private enum class Ventana { AVISOS, ACERCA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(e: Estado, vm: LunaViewModel, a: Acciones) {
    val snack = remember { SnackbarHostState() }
    var ventana by remember { mutableStateOf<Ventana?>(null) }
    LaunchedEffect(e.mensaje) {
        e.mensaje?.let { snack.showSnackbar(it); vm.cerrarMensaje() }
    }
    val pendientes = remember(e.datos, e.hoy) { Conflictos.entre(e.datos, e.hoy, e.hoy.plusDays(DIAS_REVISAR)).size }

    val editando = e.editando
    if (editando != null) {
        EditorTipo(editando, e, vm)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("XVIII · La Luna") },
                    actions = {
                        Menu(
                            "⋮",
                            listOf(
                                "Avisos" to { ventana = Ventana.AVISOS },
                                "Exportar respaldo (JSON)" to a.compartirRespaldo,
                                "Importar respaldo" to a.abrirArchivo,
                                "Acerca de" to { ventana = Ventana.ACERCA },
                            ),
                            color = Durazno,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AzulNoche, titleContentColor = Durazno),
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = e.pestana == Pestana.DIA, onClick = { vm.pestana(Pestana.DIA) },
                        icon = { Text("☾") }, label = { Text("Día") })
                    NavigationBarItem(selected = e.pestana == Pestana.TIPOS, onClick = { vm.pestana(Pestana.TIPOS) },
                        icon = { Text("◐") }, label = { Text("Tipos de día") })
                    NavigationBarItem(selected = e.pestana == Pestana.REVISAR, onClick = { vm.pestana(Pestana.REVISAR) },
                        icon = {
                            BadgedBox(badge = { if (pendientes > 0) Badge { Text(if (pendientes > 99) "99+" else "$pendientes") } }) { Text("⚠") }
                        },
                        label = { Text("Revisar") })
                }
            },
            snackbarHost = { SnackbarHost(snack) },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (e.pestana) {
                    Pestana.DIA -> PantallaDia(e, vm)
                    Pestana.TIPOS -> PantallaTipos(e, vm)
                    Pestana.REVISAR -> PantallaRevisar(e, vm)
                }
            }
        }
    }

    e.porImportar?.let { DialogoImportar(it, vm::confirmarImportar) }
    when (ventana) {
        Ventana.AVISOS -> DialogoAvisos(e.avisos, a, vm::cambiarAvisos) { ventana = null }
        Ventana.ACERCA -> AlertDialog(
            onDismissRequest = { ventana = null },
            title = { Text("XVIII · La Luna 0.1") },
            text = {
                Text(
                    "Checks de las actividades de cada día. Los tipos de día (Viaje, Trabajo…) cargan solos sus actividades, " +
                        "también las de antes y después, y La Luna avisa si algo se cruza.\n\n" +
                        "Todo queda en este celular. Mantén presionado el ícono → «Enviar registro» para revisar errores.",
                )
            },
            confirmButton = { TextButton(onClick = { ventana = null }) { Text("Cerrar") } },
        )
        null -> {}
    }
}

private const val DIAS_REVISAR = 60L

// ======================= Día =======================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PantallaDia(e: Estado, vm: LunaViewModel) {
    val ctx = LocalContext.current
    val d = e.datos
    val dia = e.dia
    val todas = remember(d, dia) { Agenda.instanciasConOmitidas(d, dia) }
    val visibles = todas.filter { it.clave !in d.omitidas }
    val omitidas = todas.filter { it.clave in d.omitidas }
    val conflictos = remember(d, dia) { Conflictos.delDia(d, dia) }
    val nivelDe = HashMap<String, Conflictos.Nivel>().apply {
        conflictos.forEach { c -> c.instancias.forEach { k -> if (this[k] != Conflictos.Nivel.CHOQUE) this[k] = c.nivel } }
    }
    val tiposHoy = Agenda.tiposDelDia(d, dia)
    val hechas = visibles.count { it.clave in d.hechas }

    var sueltaAbierta by remember { mutableStateOf<Pair<Suelta, Boolean>?>(null) }
    var instanciaAbierta by remember { mutableStateOf<Instancia?>(null) }
    var quitarTipo by remember { mutableStateOf<String?>(null) }
    var verOmitidas by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Semana(e, vm) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.moverDias(-1) }) { Text("‹") }
                        Text(
                            (if (dia == e.hoy) "Hoy · " else "") + Textos.fechaLarga(dia),
                            Modifier.weight(1f).clickable { elegirFecha(ctx, dia) { vm.irA(it) } },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = { vm.moverDias(1) }) { Text("›") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        tiposHoy.forEach { t ->
                            FilterChip(
                                selected = true, onClick = { quitarTipo = t.id },
                                label = { Text(t.nombre + "  ×") },
                                leadingIcon = { Punto(colorDe(t.color)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorDe(t.color).copy(alpha = 0.25f)),
                            )
                        }
                        val otros = d.tipos.filter { t -> tiposHoy.none { it.id == t.id } }
                        if (otros.isNotEmpty()) Menu("+ Marcar día como…", otros.map { t -> t.nombre to { vm.marcarDia(t.id, dia) } })
                        else if (d.tipos.isEmpty()) TextButton(onClick = vm::nuevoTipo) { Text("+ Crear un tipo de día") }
                    }
                    if (visibles.isNotEmpty()) {
                        LinearProgressIndicator(progress = { hechas.toFloat() / visibles.size }, modifier = Modifier.fillMaxWidth())
                        Text("$hechas de ${visibles.size} hechas" + if (hechas == visibles.size) " ✓" else "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (conflictos.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Para revisar", fontWeight = FontWeight.Bold)
                            conflictos.forEach { c ->
                                val visto = c.clave in d.vistos
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        (if (c.nivel == Conflictos.Nivel.CHOQUE) "⚠ " else "• ") + c.texto,
                                        Modifier.weight(1f),
                                        color = if (c.nivel == Conflictos.Nivel.CHOQUE && !visto) Choque else Color.Unspecified,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    TextButton(onClick = { vm.visto(c.clave, !visto) }) { Text(if (visto) "Visto ✓" else "Visto") }
                                }
                            }
                        }
                    }
                }
            }
            if (visibles.isEmpty()) {
                item {
                    Text(
                        if (d.tipos.isEmpty() && d.sueltas.isEmpty())
                            "Agrega actividades con «+ Actividad», o crea tipos de día (Viaje, Trabajo…) en la pestaña «Tipos de día» para que sus actividades se carguen solas."
                        else "Nada para este día.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(visibles, key = { it.clave }) { i ->
                FilaInstancia(i, e, nivelDe[i.clave], onCheck = { vm.alternar(i.clave) }) {
                    if (i.sueltaId != null) d.sueltas.firstOrNull { it.id == i.sueltaId }?.let { sueltaAbierta = it to false }
                    else instanciaAbierta = i
                }
            }
            if (omitidas.isNotEmpty()) {
                item {
                    TextButton(onClick = { verOmitidas = !verOmitidas }, Modifier.padding(horizontal = 8.dp)) {
                        Text((if (verOmitidas) "▾ " else "▸ ") + "Quitadas solo este día (${omitidas.size})")
                    }
                }
                if (verOmitidas) items(omitidas, key = { "o-" + it.clave }) { i ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${Textos.franja(i.franja)} · ${i.titulo}", Modifier.weight(1f), textDecoration = TextDecoration.LineThrough)
                        TextButton(onClick = { vm.restaurar(i.clave) }) { Text("Devolver") }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { sueltaAbierta = Suelta(Formato.nuevoId(), "", dia) to true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Durazno, contentColor = AzulNoche,
        ) { Text("+ Actividad") }
    }

    sueltaAbierta?.let { (s, nueva) ->
        DialogoSuelta(s, nueva, vm::guardarSuelta, { vm.borrarSuelta(s.id) }) { sueltaAbierta = null }
    }
    instanciaAbierta?.let { i ->
        val t = i.tipoId?.let { d.tipo(it) }
        AlertDialog(
            onDismissRequest = { instanciaAbierta = null },
            title = { Text(i.titulo) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Textos.franja(i.franja) + " · " + (Textos.origen(i, d) ?: ""))
                    if (i.nota.isNotBlank()) Text(i.nota, style = MaterialTheme.typography.bodySmall)
                    Text("Viene del tipo de día «${t?.nombre}». Puedes quitarla solo de este día o cambiarla en el tipo (para todos los días).",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.omitir(i.clave); instanciaAbierta = null }) { Text("Quitar solo este día") }
            },
            dismissButton = {
                Row {
                    if (t != null) TextButton(onClick = { instanciaAbierta = null; vm.editarTipo(t.id) }) { Text("Editar tipo") }
                    TextButton(onClick = { instanciaAbierta = null }) { Text("Cerrar") }
                }
            },
        )
    }
    quitarTipo?.let { id ->
        val t = d.tipo(id)
        if (t != null) Confirmar(
            "¿Quitar ${t.nombre} de este día?",
            "Se quitan sus actividades de este día y las de antes y después. " +
                (if (t.porRepeticion(dia)) "El día queda como excepción de la repetición." else ""),
            "Quitar", { vm.quitarDia(id, dia) },
        ) { quitarTipo = null }
    }
}

/** Tira de la semana: días con los colores de sus tipos y una marca si hay choques. */
@Composable
private fun Semana(e: Estado, vm: LunaViewModel) {
    val lunes = e.dia.minusDays((e.dia.dayOfWeek.value - 1).toLong())
    Row(Modifier.fillMaxWidth().background(AzulNoche).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { vm.moverDias(-7) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) { Text("«", color = Durazno) }
        (0L..6L).forEach { n ->
            val d = lunes.plusDays(n)
            DiaSemana(d, e, Modifier.weight(1f)) { vm.irA(d) }
        }
        TextButton(onClick = { vm.moverDias(7) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) { Text("»", color = Durazno) }
    }
    if (e.dia != e.hoy) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = vm::irAHoy) { Text("Volver a hoy") }
        }
    }
}

@Composable
private fun DiaSemana(d: LocalDate, e: Estado, mod: Modifier, onClick: () -> Unit) {
    val tipos = remember(e.datos, d) { Agenda.tiposDelDia(e.datos, d) }
    val conf = remember(e.datos, d) { Conflictos.delDia(e.datos, d).filter { it.clave !in e.datos.vistos } }
    val prog = remember(e.datos, d) { Agenda.progreso(e.datos, d) }
    val sel = d == e.dia
    Column(
        mod.padding(2.dp)
            .background(if (sel) Durazno else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (sel) AzulNoche else Durazno
        Text(Textos.inicial(d.dayOfWeek), color = c, style = MaterialTheme.typography.labelSmall)
        Text(
            "${d.dayOfMonth}", color = c, fontWeight = if (d == e.hoy) FontWeight.ExtraBold else FontWeight.Normal,
            textDecoration = if (d == e.hoy) TextDecoration.Underline else null,
        )
        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            tipos.take(3).forEach { Punto(colorDe(it.color), 6.dp) }
        }
        Text(
            when {
                conf.any { it.nivel == Conflictos.Nivel.CHOQUE } -> "⚠"
                conf.isNotEmpty() -> "•"
                prog.completo -> "✓"
                else -> " "
            },
            color = if (conf.any { it.nivel == Conflictos.Nivel.CHOQUE }) (if (sel) Choque else Color(0xFFFF8A80)) else c,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FilaInstancia(i: Instancia, e: Estado, nivel: Conflictos.Nivel?, onCheck: () -> Unit, onClick: () -> Unit) {
    val d = e.datos
    val hecha = i.clave in d.hechas
    val tipo = i.tipoId?.let { d.tipo(it) }
    val barra = tipo?.let { colorDe(it.color) } ?: MaterialTheme.colorScheme.primary
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
        border = if (nivel == Conflictos.Nivel.CHOQUE) BorderStroke(1.5.dp, Choque) else null,
    ) {
        Row(Modifier.height(IntrinsicSize.Min).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(barra))
            Checkbox(checked = hecha, onCheckedChange = { onCheck() })
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    i.titulo, fontWeight = FontWeight.Medium,
                    textDecoration = if (hecha) TextDecoration.LineThrough else null,
                    color = if (hecha) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else Color.Unspecified,
                )
                val origen = Textos.origen(i, d)
                Text(
                    Textos.franja(i.franja) + (origen?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (i.nota.isNotBlank()) Text(i.nota, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            when (nivel) {
                Conflictos.Nivel.CHOQUE -> Text("⚠", color = Choque, modifier = Modifier.padding(end = 12.dp))
                Conflictos.Nivel.AVISO -> Text("•", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 12.dp))
                null -> Spacer(Modifier.width(12.dp))
            }
        }
    }
}

// ======================= Revisar =======================

@Composable
private fun PantallaRevisar(e: Estado, vm: LunaViewModel) {
    var todos by remember { mutableStateOf(false) }
    val desde = e.hoy.minusDays(7)
    val hasta = e.hoy.plusDays(DIAS_REVISAR)
    val lista = remember(e.datos, e.hoy, todos) { Conflictos.entre(e.datos, desde, hasta, ocultarVistos = !todos) }
    val porDia = lista.groupBy { it.fecha }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choques y avisos de la semana pasada a los próximos $DIAS_REVISAR días.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("Ver revisados", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Switch(todos, { todos = it })
            }
        }
        if (lista.isEmpty()) item {
            Text("Nada que revisar: no hay horas que se crucen ni actividades de antes o después que caigan en otros días marcados. ☾",
                Modifier.padding(16.dp))
        }
        porDia.forEach { (f, cs) ->
            item(key = "f-$f") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (f == e.hoy) "Hoy · " else "") + Textos.fechaLarga(f) + (if (f.isBefore(e.hoy)) " (pasado)" else ""),
                        Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary,
                    )
                    OutlinedButton(onClick = { vm.irA(f) }) { Text("Ver día") }
                }
            }
            items(cs, key = { it.clave }) { c ->
                val visto = c.clave in e.datos.vistos
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (c.clase) {
                                    Conflictos.Clase.SOLAPE -> "⚠ Se cruzan las horas"
                                    Conflictos.Clase.CRUCE -> "• Cae en otro día marcado"
                                    Conflictos.Clase.CERCA -> "• Actividad cerca de un día marcado"
                                },
                                fontWeight = FontWeight.Bold,
                                color = if (c.nivel == Conflictos.Nivel.CHOQUE) Choque else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(c.texto, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { vm.visto(c.clave, !visto) }) { Text(if (visto) "Volver a avisar" else "Visto") }
                    }
                }
            }
        }
    }
}
