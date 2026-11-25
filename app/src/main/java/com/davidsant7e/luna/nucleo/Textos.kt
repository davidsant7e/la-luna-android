package com.davidsant7e.luna.nucleo

import java.time.DayOfWeek
import java.time.LocalDate

/** Textos en español (sin depender del idioma del celular). */
object Textos {
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val DIAS_CORTOS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    private val DIAS_LARGOS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    private val INICIALES = listOf("L", "M", "X", "J", "V", "S", "D")

    fun hora(min: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", min / 60, min % 60)

    fun franja(f: Franja): String = when {
        f.inicio == null -> "Todo el día"
        f.fin == null -> hora(f.inicio)
        else -> hora(f.inicio) + "–" + hora(f.fin)
    }

    /** «vie 28 nov» */
    fun fechaCorta(d: LocalDate): String = "${diaCorto(d.dayOfWeek)} ${d.dayOfMonth} ${MESES[d.monthValue - 1]}"

    /** «viernes 28 nov 2026» */
    fun fechaLarga(d: LocalDate): String = "${diaLargo(d.dayOfWeek)} ${d.dayOfMonth} ${MESES[d.monthValue - 1]} ${d.year}"

    /** «28 nov» (con año si no es [anioActual]). */
    fun diaMes(d: LocalDate, anioActual: Int? = null): String =
        "${d.dayOfMonth} ${MESES[d.monthValue - 1]}" + if (anioActual != null && d.year != anioActual) " ${d.year}" else ""

    fun diaCorto(d: DayOfWeek) = DIAS_CORTOS[d.value - 1]
    fun diaLargo(d: DayOfWeek) = DIAS_LARGOS[d.value - 1]
    fun inicial(d: DayOfWeek) = INICIALES[d.value - 1]

    /** «2 días antes», «el mismo día», «1 día después». */
    fun desplazamiento(k: Int): String = when {
        k == 0 -> "el mismo día"
        k < 0 -> "${-k} ${if (k == -1) "día" else "días"} antes"
        else -> "$k ${if (k == 1) "día" else "días"} después"
    }

    /** «L a V», «L X V», «todos los días». */
    fun diasSemana(dias: Set<DayOfWeek>): String {
        if (dias.size == 7) return "todos los días"
        val v = dias.map { it.value }.sorted()
        if (v.size >= 3 && v.last() - v.first() == v.size - 1) return "${INICIALES[v.first() - 1]} a ${INICIALES[v.last() - 1]}"
        return v.joinToString(" ") { INICIALES[it - 1] }
    }

    fun repeticion(r: Repeticion): String {
        val base = when (r) {
            is Repeticion.CadaNDias -> (if (r.cada == 1) "Todos los días" else "Cada ${r.cada} días") + " desde ${diaMes(r.inicio)} ${r.inicio.year}"
            is Repeticion.Semanal -> "Cada semana: ${diasSemana(r.dias)}" + (r.desde?.let { " desde ${diaMes(it)} ${it.year}" } ?: "")
        }
        return base + (r.hasta?.let { " hasta ${diaMes(it)} ${it.year}" } ?: "")
    }

    /** De dónde sale una instancia: «Viaje · 1 día antes (vie 28 nov)», «Trabajo», o null si es suelta. */
    fun origen(i: Instancia, datos: Datos): String? {
        val t = i.tipoId?.let { datos.tipo(it) } ?: return null
        if (i.desplazamiento == 0 || i.ancla == null) return t.nombre
        return "${t.nombre} · ${desplazamiento(i.desplazamiento)} (${fechaCorta(i.ancla)})"
    }
}
