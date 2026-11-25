package com.davidsant7e.luna.datos

import android.content.Context
import com.davidsant7e.luna.nucleo.Datos
import com.davidsant7e.luna.nucleo.Formato
import com.davidsant7e.luna.registro.Registro
import java.io.File

/** Todo se guarda como un JSON en la memoria privada de la app (el mismo formato del respaldo). */
class Almacen(private val ctx: Context) {
    private val archivo = File(ctx.filesDir, "luna.json")

    fun cargar(): Datos = try {
        if (archivo.exists()) Formato.deJson(archivo.readText()) else Datos()
    } catch (e: Exception) {
        Registro.error("ALMACEN", e)
        // Un archivo dañado no debe impedir abrir la app: se aparta y se empieza de nuevo.
        archivo.renameTo(File(ctx.filesDir, "luna-danada-${System.currentTimeMillis()}.json"))
        Datos()
    }

    /** Escritura atómica: primero a un temporal y luego se renombra. */
    @Synchronized
    fun guardar(d: Datos) {
        val tmp = File(ctx.filesDir, "luna.json.tmp")
        tmp.writeText(Formato.aJson(d))
        if (!tmp.renameTo(archivo)) {
            archivo.delete()
            tmp.renameTo(archivo)
        }
    }
}
