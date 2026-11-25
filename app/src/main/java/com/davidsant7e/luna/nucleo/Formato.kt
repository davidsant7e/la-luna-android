package com.davidsant7e.luna.nucleo

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/**
 * JSON de La Luna: es el archivo de la app (`luna.json`) y el respaldo que se exporta. Ejemplo:
 *
 * ```json
 * {"app":"la-luna","version":1,
 *  "tipos":[{"id":"a1","nombre":"Viaje","color":"#F4A582","agrupar":true,
 *            "plantillas":[{"id":"p1","titulo":"Empacar","dias":-1,"inicio":"20:00","fin":"21:00"}],
 *            "fechas":["2026-12-24"],
 *            "repeticiones":[{"clase":"cada","inicio":"2026-11-28","cada":15}],
 *            "excepciones":["2026-12-13"]}],
 *  "sueltas":[{"id":"s1","titulo":"Cita médica","fecha":"2026-11-27","inicio":"09:00","fin":"10:00","nota":""}],
 *  "hechas":["s:s1"],"omitidas":[],"vistos":[]}
 * ```
 * Las horas van como "HH:MM" (fin puede ser "24:00"); sin "inicio" = todo el día. Días de la semana: 1 = lunes … 7 = domingo.
 */
object Formato {
    const val VERSION = 1

    fun nuevoId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    fun aJson(d: Datos): String = Json.escribir(
        linkedMapOf(
            "app" to "la-luna",
            "version" to VERSION,
            "tipos" to d.tipos.map(::tipoAMapa),
            "sueltas" to d.sueltas.map(::sueltaAMapa),
            "hechas" to d.hechas.sorted(),
            "omitidas" to d.omitidas.sorted(),
            "vistos" to d.vistos.sorted(),
        ),
    )

    /** Lee un respaldo. Lanza [IllegalArgumentException] si no es un JSON de La Luna. Ignora elementos dañados. */
    fun deJson(texto: String): Datos {
        val raiz = Json.parse(texto.trim().removePrefix("﻿")) as? Map<*, *> ?: throw IllegalArgumentException("No es un objeto JSON")
        if (raiz["app"] != null && raiz["app"] != "la-luna") throw IllegalArgumentException("Este JSON es de otra app (${raiz["app"]})")
        if (raiz["tipos"] == null && raiz["sueltas"] == null) throw IllegalArgumentException("No tiene tipos ni actividades de La Luna")
        return Datos(
            sueltas = lista(raiz["sueltas"]).mapNotNull { runCatching { sueltaDeMapa(it as Map<*, *>) }.getOrNull() },
            tipos = lista(raiz["tipos"]).mapNotNull { runCatching { tipoDeMapa(it as Map<*, *>) }.getOrNull() },
            hechas = textos(raiz["hechas"]),
            omitidas = textos(raiz["omitidas"]),
            vistos = textos(raiz["vistos"]),
        )
    }

    // ---- Horas y colores ----

    fun horaATexto(m: Int) = Textos.hora(m)

    fun textoAHora(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val p = s.trim().split(':')
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        if (h !in 0..24 || m !in 0..59 || (h == 24 && m != 0)) return null
        return h * 60 + m
    }

    fun colorATexto(c: Long) = "#%06X".format(c and 0xFFFFFF)

    fun textoAColor(s: String?): Long? {
        val h = s?.trim()?.removePrefix("#") ?: return null
        if (h.length != 6) return null
        return h.toLongOrNull(16)?.let { it or 0xFF000000 }
    }

    // ---- Mapas ----

    private fun franjaEnMapa(f: Franja, m: MutableMap<String, Any?>) {
        f.inicio?.let { m["inicio"] = horaATexto(it) }
        f.fin?.let { m["fin"] = horaATexto(it) }
    }

    private fun franjaDeMapa(m: Map<*, *>): Franja {
        val ini = textoAHora(m["inicio"] as? String) ?: return Franja.TODO_EL_DIA
        val fin = textoAHora(m["fin"] as? String)
        return if (ini >= Franja.MINUTOS_DIA) Franja.TODO_EL_DIA
        else if (fin != null && fin > ini) Franja(ini, fin) else Franja(ini)
    }

    private fun sueltaAMapa(s: Suelta): Map<String, Any?> = linkedMapOf<String, Any?>(
        "id" to s.id, "titulo" to s.titulo, "fecha" to s.fecha.toString(),
    ).also { franjaEnMapa(s.franja, it); if (s.nota.isNotEmpty()) it["nota"] = s.nota }

    private fun sueltaDeMapa(m: Map<*, *>) = Suelta(
        id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: nuevoId(),
        titulo = (m["titulo"] as String),
        fecha = LocalDate.parse(m["fecha"] as String),
        franja = franjaDeMapa(m),
        nota = (m["nota"] as? String).orEmpty(),
    )

    private fun plantillaAMapa(p: Plantilla): Map<String, Any?> = linkedMapOf<String, Any?>(
        "id" to p.id, "titulo" to p.titulo, "dias" to p.desplazamiento,
    ).also { franjaEnMapa(p.franja, it); if (p.nota.isNotEmpty()) it["nota"] = p.nota }

    private fun plantillaDeMapa(m: Map<*, *>) = Plantilla(
        id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: nuevoId(),
        titulo = m["titulo"] as String,
        desplazamiento = (m["dias"] as? Number)?.toInt() ?: 0,
        franja = franjaDeMapa(m),
        nota = (m["nota"] as? String).orEmpty(),
    )

    private fun repeticionAMapa(r: Repeticion): Map<String, Any?> = when (r) {
        is Repeticion.CadaNDias -> linkedMapOf("clase" to "cada", "inicio" to r.inicio.toString(), "cada" to r.cada)
        is Repeticion.Semanal -> linkedMapOf<String, Any?>("clase" to "semanal", "dias" to r.dias.map { it.value }.sorted())
            .also { m -> r.desde?.let { m["desde"] = it.toString() } }
    }.let { m -> if (r.hasta != null) m + ("hasta" to r.hasta.toString()) else m }

    private fun repeticionDeMapa(m: Map<*, *>): Repeticion {
        val hasta = (m["hasta"] as? String)?.let(LocalDate::parse)
        return when (m["clase"]) {
            "cada" -> Repeticion.CadaNDias(LocalDate.parse(m["inicio"] as String), (m["cada"] as Number).toInt(), hasta)
            "semanal" -> Repeticion.Semanal(
                lista(m["dias"]).mapNotNull { (it as? Number)?.toInt()?.takeIf { v -> v in 1..7 }?.let(DayOfWeek::of) }.toSet()
                    .also { require(it.isNotEmpty()) },
                (m["desde"] as? String)?.let(LocalDate::parse),
                hasta,
            )
            else -> throw IllegalArgumentException("Repetición desconocida")
        }
    }

    private fun tipoAMapa(t: TipoDia): Map<String, Any?> = linkedMapOf(
        "id" to t.id,
        "nombre" to t.nombre,
        "color" to colorATexto(t.color),
        "agrupar" to t.agruparSeguidos,
        "plantillas" to t.plantillas.map(::plantillaAMapa),
        "fechas" to t.fechas.sorted().map { it.toString() },
        "repeticiones" to t.repeticiones.map(::repeticionAMapa),
        "excepciones" to t.excepciones.sorted().map { it.toString() },
    )

    private fun tipoDeMapa(m: Map<*, *>) = TipoDia(
        id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: nuevoId(),
        nombre = m["nombre"] as String,
        color = textoAColor(m["color"] as? String) ?: TipoDia.COLORES.first(),
        plantillas = lista(m["plantillas"]).mapNotNull { runCatching { plantillaDeMapa(it as Map<*, *>) }.getOrNull() },
        fechas = fechas(m["fechas"]),
        repeticiones = lista(m["repeticiones"]).mapNotNull { runCatching { repeticionDeMapa(it as Map<*, *>) }.getOrNull() },
        excepciones = fechas(m["excepciones"]),
        agruparSeguidos = (m["agrupar"] as? Boolean) ?: true,
    )

    private fun lista(v: Any?): List<Any?> = v as? List<Any?> ?: emptyList()
    private fun textos(v: Any?): Set<String> = lista(v).filterIsInstance<String>().toSet()
    private fun fechas(v: Any?): Set<LocalDate> = lista(v).mapNotNull { (it as? String)?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull() } }.toSet()
}
