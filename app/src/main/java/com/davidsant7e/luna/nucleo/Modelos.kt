package com.davidsant7e.luna.nucleo

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Segmento de hora de una actividad, en minutos desde la medianoche (0..1439).
 * Sin [inicio] = «todo el día». Con inicio y sin [fin] = a una hora puntual.
 * No cruza la medianoche: si hace falta, son dos actividades.
 */
data class Franja(val inicio: Int? = null, val fin: Int? = null) {
    init {
        require(inicio == null || inicio in 0 until MINUTOS_DIA) { "Hora de inicio fuera del día: $inicio" }
        require(fin == null || (inicio != null && fin in 1..MINUTOS_DIA && fin > inicio)) { "La hora final debe ser después de la inicial" }
    }

    val todoElDia get() = inicio == null

    /** Final efectivo para comparar: una hora puntual ocupa un minuto. */
    val finEfectivo: Int? get() = inicio?.let { fin ?: (it + 1) }

    /** Dos franjas con hora se solapan si comparten algún minuto (el final no cuenta: 9–10 y 10–11 no chocan). */
    fun solapaCon(otra: Franja): Boolean {
        val a = inicio ?: return false
        val b = otra.inicio ?: return false
        return a < otra.finEfectivo!! && b < finEfectivo!!
    }

    companion object {
        const val MINUTOS_DIA = 24 * 60
        val TODO_EL_DIA = Franja()
        fun hora(h: Int, m: Int = 0) = Franja(h * 60 + m)
        fun de(hIni: Int, mIni: Int, hFin: Int, mFin: Int) = Franja(hIni * 60 + mIni, hFin * 60 + mFin)
    }
}

/** Actividad suelta: un día concreto. */
data class Suelta(
    val id: String,
    val titulo: String,
    val fecha: LocalDate,
    val franja: Franja = Franja.TODO_EL_DIA,
    val nota: String = "",
)

/**
 * Actividad plantilla de un tipo de día. [desplazamiento] en días respecto al día marcado:
 * negativo = N días antes, 0 = el mismo día, positivo = N días después.
 */
data class Plantilla(
    val id: String,
    val titulo: String,
    val desplazamiento: Int = 0,
    val franja: Franja = Franja.TODO_EL_DIA,
    val nota: String = "",
)

/** Repetición de un tipo de día. */
sealed class Repeticion {
    abstract val desde: LocalDate?
    abstract val hasta: LocalDate?

    abstract fun incluye(d: LocalDate): Boolean

    protected fun enRango(d: LocalDate) = (desde == null || !d.isBefore(desde)) && (hasta == null || !d.isAfter(hasta))

    /** Cada [cada] días a partir de [inicio] (inicio incluido). */
    data class CadaNDias(val inicio: LocalDate, val cada: Int, override val hasta: LocalDate? = null) : Repeticion() {
        init { require(cada >= 1) { "Cada cuántos días debe ser 1 o más" } }
        override val desde: LocalDate get() = inicio
        override fun incluye(d: LocalDate): Boolean {
            if (!enRango(d)) return false
            return Math.floorMod(d.toEpochDay() - inicio.toEpochDay(), cada.toLong()) == 0L
        }
    }

    /** Ciertos días de la semana (p. ej. lunes a viernes). */
    data class Semanal(
        val dias: Set<DayOfWeek>,
        override val desde: LocalDate? = null,
        override val hasta: LocalDate? = null,
    ) : Repeticion() {
        override fun incluye(d: LocalDate) = enRango(d) && d.dayOfWeek in dias
    }
}

/**
 * Tipo de día (plantilla): «Viaje», «Trabajo»… Se asigna a días concretos ([fechas]) o con
 * [repeticiones], menos las [excepciones]. Cada día marcado genera las actividades de [plantillas].
 */
data class TipoDia(
    val id: String,
    val nombre: String,
    /** Color ARGB, p. ej. 0xFFF4A582. */
    val color: Long = COLORES.first(),
    val plantillas: List<Plantilla> = emptyList(),
    val fechas: Set<LocalDate> = emptySet(),
    val repeticiones: List<Repeticion> = emptyList(),
    val excepciones: Set<LocalDate> = emptySet(),
    /** Días seguidos de este tipo = un tramo: «antes» desde el primero, «después» desde el último. */
    val agruparSeguidos: Boolean = true,
) {
    /** ¿El día [d] está marcado con este tipo? */
    fun aplica(d: LocalDate): Boolean = d !in excepciones && (d in fechas || repeticiones.any { it.incluye(d) })

    /** ¿Lo marca una repetición (y no solo una fecha suelta)? */
    fun porRepeticion(d: LocalDate) = repeticiones.any { it.incluye(d) }

    /** Días antes (≤ 0) y después (≥ 0) que cubren sus plantillas. */
    val alcanceAntes: Int get() = plantillas.minOfOrNull { it.desplazamiento }?.coerceAtMost(0) ?: 0
    val alcanceDespues: Int get() = plantillas.maxOfOrNull { it.desplazamiento }?.coerceAtLeast(0) ?: 0

    companion object {
        /** Paleta para elegir (duotono de los Arcanos + algunos acompañantes). */
        val COLORES = listOf(
            0xFFF4A582, 0xFF5B6BBF, 0xFF4E9A7A, 0xFFB4532A, 0xFF8E5BA8, 0xFFC9A227, 0xFF3C8DBC, 0xFFD0607A,
        )
    }
}

/** Todo lo que guarda la app (también es el formato del respaldo JSON). */
data class Datos(
    val sueltas: List<Suelta> = emptyList(),
    val tipos: List<TipoDia> = emptyList(),
    /** Claves de las instancias marcadas como hechas (ver [Instancia.clave]). */
    val hechas: Set<String> = emptySet(),
    /** Claves de instancias de tipos que se quitaron solo ese día. */
    val omitidas: Set<String> = emptySet(),
    /** Claves de avisos de conflicto que ya se revisaron (no vuelven a la lista «Revisar»). */
    val vistos: Set<String> = emptySet(),
) {
    fun tipo(id: String) = tipos.firstOrNull { it.id == id }
}

/** Una actividad ya calculada para un día. */
data class Instancia(
    val clave: String,
    val titulo: String,
    val fecha: LocalDate,
    val franja: Franja,
    val nota: String = "",
    /** null = actividad suelta. */
    val tipoId: String? = null,
    val plantillaId: String? = null,
    /** Día marcado con el tipo del que sale (para las de antes/después es otro día). */
    val ancla: LocalDate? = null,
    val desplazamiento: Int = 0,
    val sueltaId: String? = null,
) {
    val esSuelta get() = sueltaId != null

    companion object {
        fun claveSuelta(id: String) = "s:$id"
        fun claveTipo(tipoId: String, plantillaId: String, ancla: LocalDate) = "t:$tipoId:$plantillaId:$ancla"
    }
}
