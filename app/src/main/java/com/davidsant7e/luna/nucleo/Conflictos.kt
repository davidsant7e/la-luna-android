package com.davidsant7e.luna.nucleo

import java.time.LocalDate

/**
 * Avisos de conflicto de un día:
 *
 * - **Choque** ([Nivel.CHOQUE]): dos actividades del mismo día (sueltas o de tipos) con horas que se solapan
 *   (menos dos plantillas del mismo tipo y del mismo día marcado, que se pensaron juntas).
 * - **Cruce** ([Nivel.AVISO]): una actividad de «antes» o «después» de un tipo cae en un día marcado con otro tipo
 *   (o con otro tramo del mismo tipo). Ej.: «Deshacer maleta» (1 día después del viaje) cae en un día de Trabajo.
 * - **Cerca** ([Nivel.AVISO]): una actividad suelta cae en los días de antes/después que cubre un tipo
 *   (entre su plantilla más temprana y la más tardía), sin que ese día sea del tipo. Ej.: «Cita médica» el día antes del viaje.
 *
 * Cada conflicto tiene una [Conflicto.clave] estable para marcarlo como revisado.
 */
object Conflictos {

    enum class Nivel { CHOQUE, AVISO }
    enum class Clase { SOLAPE, CRUCE, CERCA }

    data class Conflicto(
        val clave: String,
        val fecha: LocalDate,
        val nivel: Nivel,
        val clase: Clase,
        val texto: String,
        /** Claves de las instancias involucradas (para resaltarlas en el día). */
        val instancias: List<String>,
    )

    fun delDia(datos: Datos, d: LocalDate): List<Conflicto> {
        val inst = Agenda.instancias(datos, d)
        return solapes(d, inst) + cruces(datos, d, inst) + cercanas(datos, d, inst)
    }

    /** Conflictos de [desde] a [hasta] (incluidos); sin los ya revisados si [ocultarVistos]. */
    fun entre(datos: Datos, desde: LocalDate, hasta: LocalDate, ocultarVistos: Boolean = true): List<Conflicto> {
        val r = ArrayList<Conflicto>()
        var d = desde
        while (!d.isAfter(hasta)) {
            r += delDia(datos, d)
            d = d.plusDays(1)
        }
        return if (ocultarVistos) r.filter { it.clave !in datos.vistos } else r
    }

    fun solapes(d: LocalDate, inst: List<Instancia>): List<Conflicto> {
        val conHora = inst.filter { !it.franja.todoElDia }
        val r = ArrayList<Conflicto>()
        for (i in conHora.indices) for (j in i + 1 until conHora.size) {
            val a = conHora[i]
            val b = conHora[j]
            if (!a.franja.solapaCon(b.franja)) continue
            // Las plantillas de un mismo tipo y un mismo día marcado se diseñaron juntas (p. ej. «Jornada 8–17» y
            // «Revisar correo 8:00»): no se avisan entre ellas.
            if (a.tipoId != null && a.tipoId == b.tipoId && a.ancla == b.ancla) continue
            val claves = listOf(a.clave, b.clave).sorted()
            r += Conflicto(
                "solape:$d:${claves[0]}|${claves[1]}", d, Nivel.CHOQUE, Clase.SOLAPE,
                "«${a.titulo}» (${Textos.franja(a.franja)}) se cruza con «${b.titulo}» (${Textos.franja(b.franja)})",
                claves,
            )
        }
        return r
    }

    fun cruces(datos: Datos, d: LocalDate, inst: List<Instancia>): List<Conflicto> {
        val tiposHoy = Agenda.tiposDelDia(datos, d)
        if (tiposHoy.isEmpty()) return emptyList()
        val r = ArrayList<Conflicto>()
        for (i in inst) {
            if (i.desplazamiento == 0 || i.tipoId == null || i.ancla == null) continue
            val origen = datos.tipo(i.tipoId) ?: continue
            for (u in tiposHoy) {
                // Sin tramos, los días seguidos del mismo tipo se pisan a propósito (p. ej. «preparar ropa» cada día de trabajo).
                if (u.id == origen.id && !origen.agruparSeguidos) continue
                val otro = if (u.id == origen.id) "otro día de ${u.nombre}" else "un día de ${u.nombre}"
                r += Conflicto(
                    "cruce:$d:${i.clave}:${u.id}", d, Nivel.AVISO, Clase.CRUCE,
                    "«${i.titulo}» (${Textos.desplazamiento(i.desplazamiento)} de ${origen.nombre} del ${Textos.fechaCorta(i.ancla)}) cae en $otro",
                    listOf(i.clave),
                )
            }
        }
        return r
    }

    fun cercanas(datos: Datos, d: LocalDate, inst: List<Instancia>): List<Conflicto> {
        val sueltas = inst.filter { it.esSuelta }
        if (sueltas.isEmpty()) return emptyList()
        val r = ArrayList<Conflicto>()
        for (t in datos.tipos) {
            if (t.aplica(d)) continue
            for ((ancla, k) in anclasCerca(t, d)) {
                for (s in sueltas) {
                    r += Conflicto(
                        "cerca:$d:${s.clave}:${t.id}:$ancla", d, Nivel.AVISO, Clase.CERCA,
                        "«${s.titulo}» es ${Textos.desplazamiento(k)} de ${t.nombre} (${Textos.fechaCorta(ancla)})",
                        listOf(s.clave),
                    )
                }
            }
        }
        return r
    }

    /**
     * Días marcados con [t] cuyo alcance de antes/después cubre [d]: pares (ancla, desplazamiento de d respecto al ancla).
     * Con tramos, «antes» cuenta desde el primer día del tramo y «después» desde el último.
     */
    fun anclasCerca(t: TipoDia, d: LocalDate): List<Pair<LocalDate, Int>> {
        val r = ArrayList<Pair<LocalDate, Int>>()
        for (k in t.alcanceAntes..t.alcanceDespues) {
            if (k == 0) continue
            val ancla = d.minusDays(k.toLong())
            if (Agenda.anclaValida(t, ancla, k)) r += ancla to k
        }
        return r
    }
}
