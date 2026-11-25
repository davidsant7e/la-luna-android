package com.davidsant7e.luna.datos

import android.content.Context
import com.davidsant7e.luna.nucleo.Recordatorios

/** Ajustes de avisos (fuera del respaldo: son de este celular). */
class Ajustes(ctx: Context) {
    private val p = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var avisos: Recordatorios.Ajustes
        get() = Recordatorios.Ajustes(
            actividades = p.getBoolean("actividades", false),
            minutosAntes = p.getInt("minutosAntes", 10),
            resumen = p.getBoolean("resumen", false),
            horaResumen = p.getInt("horaResumen", 7 * 60),
        )
        set(a) {
            p.edit()
                .putBoolean("actividades", a.actividades)
                .putInt("minutosAntes", a.minutosAntes)
                .putBoolean("resumen", a.resumen)
                .putInt("horaResumen", a.horaResumen)
                .apply()
        }

    /** Hasta qué momento (epoch ms) ya se mostraron avisos. */
    var avisadoHasta: Long
        get() = p.getLong("avisadoHasta", 0L)
        set(v) { p.edit().putLong("avisadoHasta", v).apply() }
}
