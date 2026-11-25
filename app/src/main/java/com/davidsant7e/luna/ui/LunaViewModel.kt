package com.davidsant7e.luna.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davidsant7e.luna.avisos.Avisos
import com.davidsant7e.luna.datos.Ajustes
import com.davidsant7e.luna.datos.Almacen
import com.davidsant7e.luna.nucleo.Agenda
import com.davidsant7e.luna.nucleo.Datos
import com.davidsant7e.luna.nucleo.Formato
import com.davidsant7e.luna.nucleo.Recordatorios
import com.davidsant7e.luna.nucleo.Suelta
import com.davidsant7e.luna.nucleo.TipoDia
import com.davidsant7e.luna.registro.Registro
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Pestana { DIA, TIPOS, REVISAR }

data class Estado(
    val datos: Datos = Datos(),
    val hoy: LocalDate = LocalDate.now(),
    /** Día que se está viendo. */
    val dia: LocalDate = LocalDate.now(),
    val pestana: Pestana = Pestana.DIA,
    /** Tipo abierto en el editor (copia de trabajo; null = ninguno). */
    val editando: TipoDia? = null,
    val avisos: Recordatorios.Ajustes = Recordatorios.Ajustes(),
    /** Respaldo que llegó y espera confirmación para reemplazar. */
    val porImportar: Datos? = null,
    val mensaje: String? = null,
)

class LunaViewModel(app: Application) : AndroidViewModel(app) {
    private val almacen = Almacen(app)
    private val ajustes = Ajustes(app)

    private val _estado = MutableStateFlow(
        Estado(datos = Agenda.limpiarViejos(almacen.cargar(), LocalDate.now().minusDays(DIAS_GUARDADOS)), avisos = ajustes.avisos),
    )
    val estado: StateFlow<Estado> = _estado

    private val ctx get() = getApplication<Application>()

    init {
        viewModelScope.launch(Dispatchers.IO) { Avisos.programar(ctx) }
    }

    private fun cambiar(f: (Datos) -> Datos) {
        val nuevo = f(_estado.value.datos)
        _estado.update { it.copy(datos = nuevo) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                almacen.guardar(nuevo)
            } catch (e: Exception) {
                Registro.error("ALMACEN", e)
            }
            Avisos.programar(ctx)
        }
    }

    fun mensaje(t: String) = _estado.update { it.copy(mensaje = t) }
    fun cerrarMensaje() = _estado.update { it.copy(mensaje = null) }

    /** Al volver a la app (puede haber cambiado el día). */
    fun refrescarHoy() {
        val h = LocalDate.now()
        if (h != _estado.value.hoy) _estado.update { it.copy(hoy = h, dia = if (it.dia == it.hoy) h else it.dia) }
    }

    // ---- Navegación ----
    fun pestana(p: Pestana) = _estado.update { it.copy(pestana = p) }
    fun irA(d: LocalDate) = _estado.update { it.copy(dia = d, pestana = Pestana.DIA) }
    fun moverDias(n: Long) = _estado.update { it.copy(dia = it.dia.plusDays(n)) }
    fun irAHoy() = _estado.update { it.copy(dia = LocalDate.now(), hoy = LocalDate.now()) }

    // ---- Día ----
    fun alternar(clave: String) = cambiar { Agenda.alternarHecha(it, clave) }
    fun omitir(clave: String) = cambiar { Agenda.omitir(it, clave) }
    fun restaurar(clave: String) = cambiar { Agenda.restaurar(it, clave) }
    fun guardarSuelta(s: Suelta) = cambiar { Agenda.guardarSuelta(it, s) }
    fun borrarSuelta(id: String) = cambiar { Agenda.borrarSuelta(it, id) }
    fun marcarDia(tipoId: String, d: LocalDate) = cambiar { Agenda.marcarDia(it, tipoId, d) }
    fun quitarDia(tipoId: String, d: LocalDate) = cambiar { Agenda.quitarDia(it, tipoId, d) }
    fun visto(clave: String, si: Boolean = true) = cambiar { it.copy(vistos = if (si) it.vistos + clave else it.vistos - clave) }

    // ---- Tipos ----
    fun nuevoTipo() {
        val usados = _estado.value.datos.tipos.map { it.color }.toSet()
        val color = TipoDia.COLORES.firstOrNull { it !in usados } ?: TipoDia.COLORES.first()
        _estado.update { it.copy(editando = TipoDia(Formato.nuevoId(), "", color), pestana = Pestana.TIPOS) }
    }

    fun editarTipo(id: String) = _estado.update { e -> e.copy(editando = e.datos.tipo(id), pestana = Pestana.TIPOS) }
    fun cambiarEditando(t: TipoDia) = _estado.update { it.copy(editando = t) }
    fun cerrarEditor() = _estado.update { it.copy(editando = null) }

    fun guardarEditando() {
        val t = _estado.value.editando ?: return
        if (t.nombre.isBlank()) { mensaje("Ponle un nombre al tipo de día."); return }
        cambiar { Agenda.guardarTipo(it, t.copy(nombre = t.nombre.trim())) }
        _estado.update { it.copy(editando = null) }
    }

    fun borrarTipo(id: String) {
        cambiar { Agenda.borrarTipo(it, id) }
        _estado.update { it.copy(editando = null) }
    }

    // ---- Avisos ----
    fun cambiarAvisos(a: Recordatorios.Ajustes) {
        ajustes.avisos = a
        // Que no se disparen de golpe avisos viejos al activar.
        ajustes.avisadoHasta = System.currentTimeMillis()
        _estado.update { it.copy(avisos = a) }
        viewModelScope.launch(Dispatchers.IO) { Avisos.programar(ctx) }
    }

    // ---- Respaldo ----
    fun respaldo(): String = Formato.aJson(_estado.value.datos)

    fun recibirRespaldo(texto: String) {
        try {
            _estado.update { it.copy(porImportar = Formato.deJson(texto)) }
        } catch (e: Exception) {
            Registro.error("IMPORTAR", e)
            mensaje("No es un respaldo de La Luna: ${e.message}")
        }
    }

    fun confirmarImportar(si: Boolean) {
        val d = _estado.value.porImportar ?: return
        _estado.update { it.copy(porImportar = null) }
        if (si) {
            cambiar { d }
            Registro.evento("IMPORTAR", "hecho", "tipos" to d.tipos.size, "sueltas" to d.sueltas.size)
            mensaje("Respaldo cargado: ${d.tipos.size} tipos y ${d.sueltas.size} actividades.")
        }
    }

    private companion object {
        /** Los estados (hecho, omitido, visto) de hace más de un año se borran al abrir. */
        const val DIAS_GUARDADOS = 400L
    }
}
