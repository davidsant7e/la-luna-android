package com.davidsant7e.luna.nucleo

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Qué notificar y cuándo (sin Android). La capa Android programa una sola alarma para el [siguiente] aviso;
 * al sonar muestra los avisos [entre] la última vez y ahora, y programa el siguiente.
 * Así una actividad marcada como hecha (u omitida) antes de su hora ya no avisa.
 */
object Recordatorios {

    data class Ajustes(
        /** Avisar antes de cada actividad con hora. */
        val actividades: Boolean = false,
        val minutosAntes: Int = 10,
        /** Resumen del día por la mañana. */
        val resumen: Boolean = false,
        val horaResumen: Int = 7 * 60,
    ) {
        val alguno get() = actividades || resumen
    }

    data class Aviso(val momento: LocalDateTime, val titulo: String, val texto: String, val clave: String, val esResumen: Boolean = false)

    fun delDia(datos: Datos, d: LocalDate, a: Ajustes): List<Aviso> {
        val r = ArrayList<Aviso>()
        val inst = Agenda.instancias(datos, d)
        if (a.resumen && inst.isNotEmpty()) {
            r += Aviso(d.atStartOfDay().plusMinutes(a.horaResumen.toLong()), "Hoy: ${resumenCorto(datos, d)}", textoResumen(datos, d), "resumen:$d", esResumen = true)
        }
        if (a.actividades) for (i in inst) {
            val ini = i.franja.inicio ?: continue
            if (i.clave in datos.hechas) continue
            r += Aviso(
                d.atStartOfDay().plusMinutes((ini - a.minutosAntes).toLong()),
                i.titulo,
                Textos.franja(i.franja) + (Textos.origen(i, datos)?.let { " · $it" } ?: ""),
                "act:$d:${i.clave}",
            )
        }
        return r.sortedBy { it.momento }
    }

    /** Avisos con momento en (desde, hasta]. */
    fun entre(datos: Datos, a: Ajustes, desde: LocalDateTime, hasta: LocalDateTime): List<Aviso> {
        if (!a.alguno || !hasta.isAfter(desde)) return emptyList()
        val r = ArrayList<Aviso>()
        // Un aviso puede ser del día siguiente (minutos antes de las 00:xx) → se revisa un día más.
        var d = desde.toLocalDate()
        while (!d.isAfter(hasta.toLocalDate().plusDays(1))) {
            r += delDia(datos, d, a).filter { it.momento.isAfter(desde) && !it.momento.isAfter(hasta) }
            d = d.plusDays(1)
        }
        return r.sortedBy { it.momento }
    }

    /** Primer aviso después de [despuesDe], buscando hasta [dias] días adelante. */
    fun siguiente(datos: Datos, a: Ajustes, despuesDe: LocalDateTime, dias: Int = 60): Aviso? {
        if (!a.alguno) return null
        var d = despuesDe.toLocalDate()
        repeat(dias + 2) {
            // Los del día siguiente pueden caer antes de medianoche (minutos antes de las 00:xx).
            (delDia(datos, d, a) + delDia(datos, d.plusDays(1), a))
                .filter { it.momento.isAfter(despuesDe) }
                .minByOrNull { it.momento }
                ?.let { return it }
            d = d.plusDays(1)
        }
        return null
    }

    /** «5 actividades · Viaje, Trabajo» */
    fun resumenCorto(datos: Datos, d: LocalDate): String {
        val n = Agenda.instancias(datos, d).size
        val tipos = Agenda.tiposDelDia(datos, d).joinToString(", ") { it.nombre }
        return "$n ${if (n == 1) "actividad" else "actividades"}" + if (tipos.isNotEmpty()) " · $tipos" else ""
    }

    fun textoResumen(datos: Datos, d: LocalDate): String {
        val inst = Agenda.instancias(datos, d)
        val conflictos = Conflictos.delDia(datos, d)
        val sb = StringBuilder()
        inst.take(8).forEach { sb.append(Textos.franja(it.franja)).append("  ").append(it.titulo).append('\n') }
        if (inst.size > 8) sb.append("… y ${inst.size - 8} más\n")
        val choques = conflictos.count { it.nivel == Conflictos.Nivel.CHOQUE }
        val avisos = conflictos.size - choques
        if (choques > 0) sb.append("⚠ $choques ${if (choques == 1) "choque" else "choques"} de horario\n")
        if (avisos > 0) sb.append("• $avisos ${if (avisos == 1) "aviso" else "avisos"} para revisar\n")
        return sb.toString().trimEnd()
    }
}
