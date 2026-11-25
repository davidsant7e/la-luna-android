package com.davidsant7e.luna.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davidsant7e.luna.nucleo.Franja
import com.davidsant7e.luna.nucleo.Textos
import java.time.LocalDate

fun colorDe(argb: Long) = Color(argb.toInt())

@Composable
fun Punto(color: Color, tam: Dp = 10.dp) {
    Box(Modifier.size(tam).background(color, CircleShape))
}

/** Selector de fecha del sistema (no necesita nada experimental). */
fun elegirFecha(ctx: Context, inicial: LocalDate, listo: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, a, m, d -> listo(LocalDate.of(a, m + 1, d)) }, inicial.year, inicial.monthValue - 1, inicial.dayOfMonth).show()
}

/** Selector de hora del sistema, 24 h. Devuelve minutos desde la medianoche. */
fun elegirHora(ctx: Context, inicial: Int, listo: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> listo(h * 60 + m) }, inicial / 60, inicial % 60, true).show()
}

/** Botón que abre un menú. */
@Composable
fun Menu(texto: String, opciones: List<Pair<String, () -> Unit>>, color: Color? = null) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { abierto = true }) {
            if (color != null) Text(texto, color = color) else Text(texto)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (t, f) -> DropdownMenuItem(text = { Text(t) }, onClick = { abierto = false; f() }) }
        }
    }
}

/** Todo el día / a una hora / de … a …, con los relojes del sistema. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorHorario(franja: Franja, cambiar: (Franja) -> Unit) {
    val ctx = LocalContext.current
    val modo = when {
        franja.inicio == null -> 0
        franja.fin == null -> 1
        else -> 2
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = modo == 0, onClick = { cambiar(Franja.TODO_EL_DIA) }, label = { Text("Todo el día") })
            FilterChip(selected = modo == 1, onClick = { cambiar(Franja(franja.inicio ?: (8 * 60))) }, label = { Text("A una hora") })
            FilterChip(selected = modo == 2, onClick = {
                val ini = (franja.inicio ?: (8 * 60)).coerceAtMost(23 * 60)
                cambiar(Franja(ini, franja.fin ?: (ini + 60)))
            }, label = { Text("De … a …") })
        }
        if (modo > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ini = franja.inicio!!
                OutlinedButton(onClick = {
                    elegirHora(ctx, ini) { m ->
                        val fin = franja.fin
                        cambiar(if (fin == null) Franja(m) else Franja(m, if (fin > m) fin else (m + (fin - ini)).coerceAtMost(Franja.MINUTOS_DIA).coerceAtLeast(m + 1)))
                    }
                }) { Text(Textos.hora(ini)) }
                if (modo == 2) {
                    Text("a")
                    val fin = franja.fin!!
                    OutlinedButton(onClick = {
                        elegirHora(ctx, fin % Franja.MINUTOS_DIA) { m ->
                            val v = if (m == 0) Franja.MINUTOS_DIA else m
                            if (v > ini) cambiar(Franja(ini, v))
                        }
                    }) { Text(Textos.hora(fin)) }
                }
            }
            if (modo == 2) Text("La hora final debe ser después de la inicial (00:00 = medianoche).", style = MaterialTheme.typography.bodySmall)
        }
    }
}
