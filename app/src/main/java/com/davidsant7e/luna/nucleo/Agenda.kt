package com.davidsant7e.luna.nucleo

import java.time.LocalDate

/**
 * Cálculo de las actividades de cada día y cambios sobre [Datos] (funciones puras: devuelven datos nuevos).
 *
 * Las actividades de los tipos no se guardan: se generan al abrir el día. Lo que se guarda por instancia
 * es si está hecha ([Datos.hechas]) u omitida solo ese día ([Datos.omitidas]), con su [Instancia.clave].
 *
 * Días seguidos del mismo tipo forman un **tramo** (si el tipo tiene [TipoDia.agruparSeguidos]):
 * las actividades de «antes» cuelgan del primer día del tramo y las de «después» del último.
 */
object Agenda {

    /** Tipos marcados en el día [d]. */
    fun tiposDelDia(datos: Datos, d: LocalDate): List<TipoDia> = datos.tipos.filter { it.aplica(d) }

    /**
     * ¿El día [ancla] genera la plantilla de desplazamiento [k]? Con tramos, «antes» solo desde el primer día
     * del tramo y «después» solo desde el último.
     */
    fun anclaValida(t: TipoDia, ancla: LocalDate, k: Int): Boolean {
        if (!t.aplica(ancla)) return false
        if (!t.agruparSeguidos || k == 0) return true
        return if (k < 0) !t.aplica(ancla.minusDays(1)) else !t.aplica(ancla.plusDays(1))
    }

    /** Todas las actividades del día (incluidas las omitidas), ordenadas: todo el día primero, luego por hora. */
    fun instanciasConOmitidas(datos: Datos, d: LocalDate): List<Instancia> {
        val r = ArrayList<Instancia>()
        for (s in datos.sueltas) if (s.fecha == d) {
            r += Instancia(Instancia.claveSuelta(s.id), s.titulo, d, s.franja, s.nota, sueltaId = s.id)
        }
        for (t in datos.tipos) for (p in t.plantillas) {
            val ancla = d.minusDays(p.desplazamiento.toLong())
            if (!anclaValida(t, ancla, p.desplazamiento)) continue
            r += Instancia(
                Instancia.claveTipo(t.id, p.id, ancla), p.titulo, d, p.franja, p.nota,
                tipoId = t.id, plantillaId = p.id, ancla = ancla, desplazamiento = p.desplazamiento,
            )
        }
        return ordenar(r)
    }

    /** Actividades del día sin las omitidas. */
    fun instancias(datos: Datos, d: LocalDate): List<Instancia> =
        instanciasConOmitidas(datos, d).filter { it.clave !in datos.omitidas }

    fun ordenar(l: List<Instancia>): List<Instancia> = l.sortedWith(
        compareBy<Instancia>({ it.franja.inicio ?: -1 }, { it.franja.fin ?: -1 }, { it.desplazamiento }, { it.titulo.lowercase() }),
    )

    data class Progreso(val hechas: Int, val total: Int) {
        val fraccion: Float get() = if (total == 0) 0f else hechas.toFloat() / total
        val completo get() = total > 0 && hechas == total
    }

    fun progreso(datos: Datos, d: LocalDate): Progreso {
        val l = instancias(datos, d)
        return Progreso(l.count { it.clave in datos.hechas }, l.size)
    }

    // ---------------- Cambios ----------------

    fun alternarHecha(datos: Datos, clave: String): Datos =
        datos.copy(hechas = if (clave in datos.hechas) datos.hechas - clave else datos.hechas + clave)

    fun omitir(datos: Datos, clave: String): Datos = datos.copy(omitidas = datos.omitidas + clave, hechas = datos.hechas - clave)
    fun restaurar(datos: Datos, clave: String): Datos = datos.copy(omitidas = datos.omitidas - clave)

    /** Agrega o reemplaza (mismo id) una actividad suelta. */
    fun guardarSuelta(datos: Datos, s: Suelta): Datos {
        val i = datos.sueltas.indexOfFirst { it.id == s.id }
        val l = datos.sueltas.toMutableList()
        if (i >= 0) l[i] = s else l += s
        return datos.copy(sueltas = l)
    }

    fun borrarSuelta(datos: Datos, id: String): Datos =
        datos.copy(sueltas = datos.sueltas.filter { it.id != id }, hechas = datos.hechas - Instancia.claveSuelta(id))

    /** Agrega o reemplaza (mismo id) un tipo de día. */
    fun guardarTipo(datos: Datos, t: TipoDia): Datos {
        val i = datos.tipos.indexOfFirst { it.id == t.id }
        val l = datos.tipos.toMutableList()
        if (i >= 0) l[i] = t else l += t
        return datos.copy(tipos = l)
    }

    /** Borra el tipo y lo que se guardó de sus instancias. */
    fun borrarTipo(datos: Datos, id: String): Datos {
        val prefijo = "t:$id:"
        return datos.copy(
            tipos = datos.tipos.filter { it.id != id },
            hechas = datos.hechas.filterNot { it.startsWith(prefijo) }.toSet(),
            omitidas = datos.omitidas.filterNot { it.startsWith(prefijo) }.toSet(),
        )
    }

    /** Marca el día con el tipo: quita la excepción si la había; si no lo marca una repetición, lo agrega a las fechas. */
    fun marcarDia(datos: Datos, tipoId: String, d: LocalDate): Datos {
        val t = datos.tipo(tipoId) ?: return datos
        var n = t.copy(excepciones = t.excepciones - d)
        if (!n.aplica(d)) n = n.copy(fechas = n.fechas + d)
        return guardarTipo(datos, n)
    }

    /** Quita el tipo de ese día: lo saca de las fechas y, si lo marca una repetición, lo deja como excepción. */
    fun quitarDia(datos: Datos, tipoId: String, d: LocalDate): Datos {
        val t = datos.tipo(tipoId) ?: return datos
        var n = t.copy(fechas = t.fechas - d)
        if (n.porRepeticion(d)) n = n.copy(excepciones = n.excepciones + d)
        return guardarTipo(datos, n)
    }

    /** Próximos días marcados con el tipo, desde [desde] (incluido), hasta [maxDias] días adelante. */
    fun proximosDias(t: TipoDia, desde: LocalDate, cuantos: Int = 5, maxDias: Int = 400): List<LocalDate> {
        val r = ArrayList<LocalDate>()
        var d = desde
        var n = 0
        while (r.size < cuantos && n <= maxDias) {
            if (t.aplica(d)) r += d
            d = d.plusDays(1); n++
        }
        return r
    }

    /** Borra estados guardados de días anteriores a [antesDe] (para que el archivo no crezca sin fin). */
    fun limpiarViejos(datos: Datos, antesDe: LocalDate): Datos {
        fun vieja(clave: String): Boolean {
            if (!clave.startsWith("t:")) return false
            val f = runCatching { LocalDate.parse(clave.substringAfterLast(':')) }.getOrNull() ?: return false
            return f.isBefore(antesDe)
        }
        val sueltasVivas = datos.sueltas.map { Instancia.claveSuelta(it.id) }.toSet()
        return datos.copy(
            hechas = datos.hechas.filterNot { vieja(it) || (it.startsWith("s:") && it !in sueltasVivas) }.toSet(),
            omitidas = datos.omitidas.filterNot { vieja(it) }.toSet(),
            vistos = datos.vistos.filterNot { v ->
                val f = runCatching { LocalDate.parse(v.split(':').getOrNull(1) ?: "") }.getOrNull()
                f != null && f.isBefore(antesDe)
            }.toSet(),
        )
    }
}
