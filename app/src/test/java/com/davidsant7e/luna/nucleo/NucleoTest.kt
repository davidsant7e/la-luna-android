package com.davidsant7e.luna.nucleo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun f(s: String) = LocalDate.parse(s)
private val LV = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

/** Viaje cada 15 días desde el sáb 28 nov 2026: empacar el día antes, revisar el mismo día, deshacer maleta el día después. */
private fun viaje(agrupar: Boolean = true) = TipoDia(
    id = "viaje", nombre = "Viaje", agruparSeguidos = agrupar,
    plantillas = listOf(
        Plantilla("empacar", "Empacar a, b", -1, Franja.de(20, 0, 21, 0)),
        Plantilla("revisar", "Revisar a, c", 0, Franja.hora(6, 30)),
        Plantilla("carro", "Revisar el carro", 0),
        Plantilla("deshacer", "Deshacer maleta", 1, Franja.de(18, 0, 19, 0)),
    ),
    repeticiones = listOf(Repeticion.CadaNDias(f("2026-11-28"), 15)),
)

/** Trabajo de lunes a viernes, 8 a 17. */
private fun trabajo() = TipoDia(
    id = "trabajo", nombre = "Trabajo",
    plantillas = listOf(Plantilla("jornada", "Jornada", 0, Franja.de(8, 0, 17, 0)), Plantilla("correo", "Revisar correo", 0, Franja.hora(8, 0))),
    repeticiones = listOf(Repeticion.Semanal(LV)),
)

class FranjaTest {
    @Test fun `solapes de horas`() {
        assertTrue(Franja.de(9, 0, 10, 0).solapaCon(Franja.de(9, 30, 11, 0)))
        assertFalse("tocar el borde no es choque", Franja.de(9, 0, 10, 0).solapaCon(Franja.de(10, 0, 11, 0)))
        assertTrue(Franja.de(9, 0, 10, 0).solapaCon(Franja.hora(9, 0)))
        assertFalse(Franja.de(9, 0, 10, 0).solapaCon(Franja.hora(10, 0)))
        assertTrue(Franja.hora(7, 0).solapaCon(Franja.hora(7, 0)))
        assertFalse(Franja.hora(7, 0).solapaCon(Franja.hora(7, 1)))
        assertFalse("todo el día no choca con horas", Franja.TODO_EL_DIA.solapaCon(Franja.hora(7, 0)))
        assertTrue(Franja.de(8, 0, 17, 0).solapaCon(Franja.de(12, 0, 13, 0)))
    }

    @Test fun `franjas no validas`() {
        for (crear in listOf({ Franja(600, 600) }, { Franja(600, 500) }, { Franja(null, 500) }, { Franja(1440) }, { Franja(-1) })) {
            try { crear(); fail("debía fallar") } catch (_: IllegalArgumentException) {}
        }
        assertEquals(1440, Franja(1380, 1440).fin)
    }
}

class RepeticionTest {
    @Test fun `cada 15 dias`() {
        val r = Repeticion.CadaNDias(f("2026-11-28"), 15)
        assertTrue(r.incluye(f("2026-11-28")))
        assertTrue(r.incluye(f("2026-12-13")))
        assertTrue(r.incluye(f("2026-12-28")))
        assertFalse(r.incluye(f("2026-12-14")))
        assertFalse("antes del inicio no", r.incluye(f("2026-11-13")))
    }

    @Test fun `cada n dias con hasta y cruzando anio`() {
        val r = Repeticion.CadaNDias(f("2026-12-30"), 2, hasta = f("2027-01-05"))
        assertEquals(listOf("2026-12-30", "2027-01-01", "2027-01-03", "2027-01-05"),
            (0..10).map { f("2026-12-28").plusDays(it.toLong()) }.filter(r::incluye).map { it.toString() })
    }

    @Test fun `semanal lunes a viernes con rango`() {
        val r = Repeticion.Semanal(LV, desde = f("2026-11-25"), hasta = f("2026-12-02"))
        assertFalse(r.incluye(f("2026-11-24"))) // martes, antes del rango
        assertTrue(r.incluye(f("2026-11-25"))) // miércoles
        assertFalse(r.incluye(f("2026-11-28"))) // sábado
        assertTrue(r.incluye(f("2026-12-02")))
        assertFalse(r.incluye(f("2026-12-03")))
    }

    @Test fun `tipo con fechas sueltas y excepciones`() {
        val t = viaje().copy(fechas = setOf(f("2026-12-24")), excepciones = setOf(f("2026-12-13")))
        assertTrue(t.aplica(f("2026-11-28")))
        assertFalse("excepción", t.aplica(f("2026-12-13")))
        assertTrue("fecha suelta", t.aplica(f("2026-12-24")))
        assertEquals(listOf(f("2026-11-28"), f("2026-12-24"), f("2026-12-28")), Agenda.proximosDias(t, f("2026-11-24"), 3))
        assertEquals(-1, t.alcanceAntes); assertEquals(1, t.alcanceDespues)
    }
}

class AgendaTest {
    private val datos = Datos(tipos = listOf(viaje(), trabajo()))

    @Test fun `antes, mismo dia y despues del viaje`() {
        assertEquals(listOf("Empacar a, b"), Agenda.instancias(datos, f("2026-11-27")).filter { it.tipoId == "viaje" }.map { it.titulo })
        val sab = Agenda.instancias(datos, f("2026-11-28"))
        assertEquals(listOf("Revisar el carro", "Revisar a, c"), sab.map { it.titulo }) // todo el día primero, luego por hora
        val dom = Agenda.instancias(datos, f("2026-11-29"))
        assertEquals(listOf("Deshacer maleta"), dom.map { it.titulo })
        assertEquals(f("2026-11-28"), dom[0].ancla)
        assertEquals("t:viaje:deshacer:2026-11-28", dom[0].clave)
        assertTrue(Agenda.instancias(datos, f("2026-11-30")).none { it.tipoId == "viaje" })
    }

    @Test fun `varios tipos en un dia y sueltas ordenadas por hora`() {
        // Viernes 11 dic: trabajo + «empacar» del viaje del sábado 12 dic? No: el viaje es el 13 (domingo). Usamos el sábado 12.
        val d = datos.copy(sueltas = listOf(Suelta("s1", "Gimnasio", f("2026-12-11"), Franja.hora(6, 0)), Suelta("s2", "Llamar a mamá", f("2026-12-11"))))
        val l = Agenda.instancias(d, f("2026-12-11"))
        assertEquals(listOf("Llamar a mamá", "Gimnasio", "Revisar correo", "Jornada"), l.map { it.titulo })
        assertEquals(listOf("Trabajo"), Agenda.tiposDelDia(d, f("2026-12-11")).map { it.nombre })
        // Lunes 28 dic: viaje (cada 15) + trabajo.
        val lun = Agenda.instancias(d, f("2026-12-28"))
        assertEquals(setOf("Viaje", "Trabajo"), Agenda.tiposDelDia(d, f("2026-12-28")).map { it.nombre }.toSet())
        assertEquals(listOf("Revisar el carro", "Revisar a, c", "Revisar correo", "Jornada"), lun.map { it.titulo })
    }

    @Test fun `excepcion quita tambien el antes y el despues`() {
        val d = Agenda.quitarDia(datos, "viaje", f("2026-12-13"))
        assertTrue(f("2026-12-13") in d.tipo("viaje")!!.excepciones)
        assertTrue(Agenda.instancias(d, f("2026-12-12")).none { it.tipoId == "viaje" })
        assertTrue(Agenda.instancias(d, f("2026-12-14")).none { it.tipoId == "viaje" })
        // Volver a marcarlo quita la excepción y no lo agrega a fechas.
        val d2 = Agenda.marcarDia(d, "viaje", f("2026-12-13"))
        assertTrue(d2.tipo("viaje")!!.excepciones.isEmpty())
        assertTrue(d2.tipo("viaje")!!.fechas.isEmpty())
    }

    @Test fun `marcar y quitar un dia suelto`() {
        val d = Agenda.marcarDia(datos, "viaje", f("2026-12-01"))
        assertEquals(setOf(f("2026-12-01")), d.tipo("viaje")!!.fechas)
        assertEquals(listOf("Empacar a, b"), Agenda.instancias(d, f("2026-11-30")).filter { it.tipoId == "viaje" }.map { it.titulo })
        val d2 = Agenda.quitarDia(d, "viaje", f("2026-12-01"))
        assertTrue(d2.tipo("viaje")!!.fechas.isEmpty())
        assertTrue("no deja excepción si no había repetición", d2.tipo("viaje")!!.excepciones.isEmpty())
        assertEquals(d, Agenda.marcarDia(datos, "viaje", f("2026-12-01")))
        assertEquals(datos, Agenda.marcarDia(datos, "noexiste", f("2026-12-01")))
    }

    @Test fun `tramo de dias seguidos`() {
        // Viaje de tres días (28, 29, 30): empacar el 27, deshacer el 1.
        val t = viaje().copy(repeticiones = emptyList(), fechas = setOf(f("2026-11-28"), f("2026-11-29"), f("2026-11-30")))
        val d = Datos(tipos = listOf(t))
        fun titulos(s: String) = Agenda.instancias(d, f(s)).map { it.titulo }
        assertEquals(listOf("Empacar a, b"), titulos("2026-11-27"))
        assertEquals(listOf("Revisar el carro", "Revisar a, c"), titulos("2026-11-29"))
        assertEquals(listOf("Revisar el carro", "Revisar a, c"), titulos("2026-11-30"))
        assertEquals(listOf("Deshacer maleta"), titulos("2026-12-01"))
        // Sin tramos, cada día del viaje genera su antes y su después.
        val d2 = Datos(tipos = listOf(t.copy(agruparSeguidos = false)))
        assertEquals(listOf("Revisar el carro", "Revisar a, c", "Deshacer maleta", "Empacar a, b"),
            Agenda.instancias(d2, f("2026-11-29")).map { it.titulo })
    }

    @Test fun `desplazamientos de varios dias`() {
        val t = TipoDia("x", "Examen", plantillas = listOf(Plantilla("a", "Repasar", -3), Plantilla("b", "Ver notas", 7)), fechas = setOf(f("2026-12-10")))
        val d = Datos(tipos = listOf(t))
        assertEquals(listOf("Repasar"), Agenda.instancias(d, f("2026-12-07")).map { it.titulo })
        assertEquals(listOf("Ver notas"), Agenda.instancias(d, f("2026-12-17")).map { it.titulo })
        assertTrue(Agenda.instancias(d, f("2026-12-10")).isEmpty())
        assertEquals(-3, t.alcanceAntes); assertEquals(7, t.alcanceDespues)
    }

    @Test fun `hecho y omitido por instancia`() {
        val sab = f("2026-11-28")
        val clave = Agenda.instancias(datos, sab).first { it.plantillaId == "revisar" }.clave
        var d = Agenda.alternarHecha(datos, clave)
        assertEquals(Agenda.Progreso(1, 2), Agenda.progreso(d, sab))
        // El mismo «revisar» del viaje siguiente no está hecho.
        assertEquals(0, Agenda.progreso(d, f("2026-12-13")).hechas)
        d = Agenda.omitir(d, Agenda.instancias(d, sab).first { it.plantillaId == "carro" }.clave)
        assertEquals(Agenda.Progreso(1, 1), Agenda.progreso(d, sab))
        assertTrue(Agenda.progreso(d, sab).completo)
        assertEquals(2, Agenda.instanciasConOmitidas(d, sab).size)
        d = Agenda.alternarHecha(d, clave)
        assertEquals(0, Agenda.progreso(d, sab).hechas)
        assertEquals(0f, Agenda.Progreso(0, 0).fraccion)
        // Omitir quita también el «hecho».
        val d3 = Agenda.omitir(Agenda.alternarHecha(datos, clave), clave)
        assertFalse(clave in d3.hechas)
        assertEquals(datos.copy(omitidas = emptySet()), Agenda.restaurar(d3, clave).copy(omitidas = emptySet()))
    }

    @Test fun `sueltas guardar editar borrar`() {
        var d = Agenda.guardarSuelta(datos, Suelta("s1", "Cita", f("2026-11-25"), Franja.de(9, 0, 10, 0)))
        d = Agenda.alternarHecha(d, "s:s1")
        d = Agenda.guardarSuelta(d, Suelta("s1", "Cita médica", f("2026-11-25"), Franja.de(9, 0, 10, 0)))
        assertEquals(1, d.sueltas.size); assertEquals("Cita médica", d.sueltas[0].titulo)
        assertTrue("s:s1" in d.hechas)
        d = Agenda.borrarSuelta(d, "s1")
        assertTrue(d.sueltas.isEmpty()); assertTrue(d.hechas.isEmpty())
    }

    @Test fun `borrar tipo limpia sus estados`() {
        var d = Agenda.alternarHecha(datos, "t:viaje:revisar:2026-11-28")
        d = Agenda.alternarHecha(d, "t:trabajo:jornada:2026-11-30")
        d = Agenda.omitir(d, "t:viaje:carro:2026-11-28")
        d = Agenda.borrarTipo(d, "viaje")
        assertEquals(listOf("trabajo"), d.tipos.map { it.id })
        assertEquals(setOf("t:trabajo:jornada:2026-11-30"), d.hechas)
        assertTrue(d.omitidas.isEmpty())
    }

    @Test fun `limpiar estados viejos`() {
        val d = datos.copy(
            sueltas = listOf(Suelta("s1", "x", f("2026-01-01"))),
            hechas = setOf("t:viaje:revisar:2026-01-01", "t:viaje:revisar:2026-11-28", "s:s1", "s:borrada"),
            omitidas = setOf("t:viaje:carro:2026-01-01"),
            vistos = setOf("solape:2026-01-01:a|b", "cruce:2026-12-01:x:y"),
        )
        val l = Agenda.limpiarViejos(d, f("2026-06-01"))
        assertEquals(setOf("t:viaje:revisar:2026-11-28", "s:s1"), l.hechas)
        assertTrue(l.omitidas.isEmpty())
        assertEquals(setOf("cruce:2026-12-01:x:y"), l.vistos)
    }
}

class ConflictosTest {
    private val base = Datos(tipos = listOf(viaje(), trabajo()))

    @Test fun `choque de horas entre suelta y tipo`() {
        val d = base.copy(sueltas = listOf(Suelta("s1", "Médico", f("2026-11-30"), Franja.de(10, 0, 11, 0))))
        val c = Conflictos.delDia(d, f("2026-11-30")) // lunes: trabajo 8-17
        val choques = c.filter { it.clase == Conflictos.Clase.SOLAPE }
        assertEquals(1, choques.size)
        assertEquals(Conflictos.Nivel.CHOQUE, choques[0].nivel)
        assertTrue(choques[0].texto, choques[0].texto.contains("Médico") && choques[0].texto.contains("Jornada"))
        assertEquals(listOf("s:s1", "t:trabajo:jornada:2026-11-30"), choques[0].instancias)
    }

    @Test fun `hora puntual dentro de un segmento choca, al borde no`() {
        // «Revisar correo» (8:00) está dentro de la jornada 8-17, pero son del mismo tipo y día: no se avisa.
        assertTrue(Conflictos.delDia(base, f("2026-12-01")).isEmpty())
        val d = base.copy(sueltas = listOf(Suelta("s", "Salir", f("2026-12-01"), Franja.hora(17, 0)), Suelta("t", "Café", f("2026-12-01"), Franja.hora(16, 59))))
        val c = Conflictos.delDia(d, f("2026-12-01"))
        assertEquals(1, c.size)
        assertTrue(c[0].texto.contains("Café"))
    }

    @Test fun `mismo tipo pero distinto dia marcado si choca`() {
        // Sin tramos: viaje el 1 y el 2; «Deshacer maleta» (18-19, del 1) choca el 2 con un «Vuelo» 18:30 del viaje del 2.
        val t = TipoDia("v", "Viaje", agruparSeguidos = false, fechas = setOf(f("2026-12-01"), f("2026-12-02")),
            plantillas = listOf(Plantilla("d", "Deshacer maleta", 1, Franja.de(18, 0, 19, 0)), Plantilla("vu", "Vuelo", 0, Franja.hora(18, 30))))
        val c = Conflictos.delDia(Datos(tipos = listOf(t)), f("2026-12-02"))
        assertEquals(1, c.size)
        assertEquals(Conflictos.Clase.SOLAPE, c[0].clase)
    }

    @Test fun `solape entre dos tipos en el mismo dia`() {
        // Lunes 28 dic: viaje («Revisar a, c» 6:30) y trabajo. Agregamos al viaje algo a las 9.
        val v = viaje().let { it.copy(plantillas = it.plantillas + Plantilla("vuelo", "Vuelo", 0, Franja.de(9, 0, 11, 0))) }
        val d = base.copy(tipos = listOf(v, trabajo()))
        val c = Conflictos.delDia(d, f("2026-12-28")).filter { it.clase == Conflictos.Clase.SOLAPE }
        assertTrue(c.any { it.texto.contains("Vuelo") && it.texto.contains("Jornada") })
    }

    @Test fun `despues del viaje cae en un dia de trabajo`() {
        // Viaje el sáb 12 dic? No es fecha de viaje. Viaje del dom 13 dic → «Deshacer maleta» el lun 14 (trabajo).
        val c = Conflictos.delDia(base, f("2026-12-14")).filter { it.clase == Conflictos.Clase.CRUCE }
        assertEquals(1, c.size)
        assertEquals(Conflictos.Nivel.AVISO, c[0].nivel)
        assertTrue(c[0].texto, c[0].texto.contains("Deshacer maleta") && c[0].texto.contains("Trabajo") && c[0].texto.contains("1 día después"))
        // Sábado 12 dic («Empacar» del viaje del 13) no es día de trabajo: sin cruce.
        assertTrue(Conflictos.delDia(base, f("2026-12-12")).none { it.clase == Conflictos.Clase.CRUCE })
    }

    @Test fun `dos viajes cuyos antes y despues se pisan`() {
        // Viajes el 1 y el 3: el «después» del 1 (día 2) y el «antes» del 3 (día 2) caen el mismo día.
        val t = viaje().copy(repeticiones = emptyList(), fechas = setOf(f("2026-12-01"), f("2026-12-03")))
        val d = Datos(tipos = listOf(t))
        val dia2 = Agenda.instancias(d, f("2026-12-02")).map { it.titulo }
        assertEquals(listOf("Deshacer maleta", "Empacar a, b"), dia2)
        // 20-21 y 18-19 no se solapan, pero si el día 2 fuera viaje… lo comprobamos con un viaje de un día en medio.
        val d3 = Datos(tipos = listOf(t.copy(fechas = setOf(f("2026-12-01"), f("2026-12-03"), f("2026-12-05")))))
        assertTrue(Conflictos.delDia(d3, f("2026-12-02")).isEmpty())
        // Viaje el 1 y el 2 por separado (sin tramos): el «después» del 1 cae en el viaje del 2.
        val sinTramo = Datos(tipos = listOf(t.copy(agruparSeguidos = false, fechas = setOf(f("2026-12-01"), f("2026-12-02")))))
        assertTrue("sin tramos, mismo tipo seguido no avisa", Conflictos.delDia(sinTramo, f("2026-12-02")).none { it.clase == Conflictos.Clase.CRUCE })
        // Con tramos y un hueco de un día: viaje 1 y 3, con «antes» de 2 días → cae el 1, que es viaje.
        val dos = t.copy(plantillas = listOf(Plantilla("e", "Empacar", -2)))
        val dd = Datos(tipos = listOf(dos))
        val c = Conflictos.delDia(dd, f("2026-12-01")).filter { it.clase == Conflictos.Clase.CRUCE }
        assertEquals(1, c.size)
        assertTrue(c[0].texto, c[0].texto.contains("otro día de Viaje"))
    }

    @Test fun `actividad suelta el dia antes o despues del viaje`() {
        val d = base.copy(sueltas = listOf(
            Suelta("a", "Cita médica", f("2026-11-27")), // día antes del viaje del 28
            Suelta("b", "Cumpleaños", f("2026-11-29")), // día después
            Suelta("c", "Cine", f("2026-11-28")), // el mismo día: no es «cerca»
            Suelta("e", "Otra", f("2026-11-26")), // dos días antes: fuera del alcance (-1)
        ))
        val antes = Conflictos.delDia(d, f("2026-11-27")).filter { it.clase == Conflictos.Clase.CERCA }
        assertEquals(1, antes.size)
        assertEquals("«Cita médica» es 1 día antes de Viaje (sáb 28 nov)", antes[0].texto)
        val despues = Conflictos.delDia(d, f("2026-11-29")).filter { it.clase == Conflictos.Clase.CERCA }
        assertEquals("«Cumpleaños» es 1 día después de Viaje (sáb 28 nov)", despues.single().texto)
        assertTrue(Conflictos.delDia(d, f("2026-11-28")).none { it.clase == Conflictos.Clase.CERCA })
        assertTrue(Conflictos.delDia(d, f("2026-11-26")).none { it.clase == Conflictos.Clase.CERCA })
    }

    @Test fun `cerca cubre los dias intermedios del alcance y respeta tramos`() {
        val t = TipoDia("x", "Mudanza", plantillas = listOf(Plantilla("a", "Cajas", -3)), fechas = setOf(f("2026-12-10"), f("2026-12-11")))
        val d = Datos(tipos = listOf(t), sueltas = listOf(Suelta("s", "Cena", f("2026-12-09")), Suelta("s2", "Cine", f("2026-12-12"))))
        val c = Conflictos.delDia(d, f("2026-12-09")).filter { it.clase == Conflictos.Clase.CERCA }
        assertEquals("solo el primer día del tramo cuenta para «antes»", 1, c.size)
        assertTrue(c[0].texto, c[0].texto.contains("1 día antes de Mudanza (jue 10 dic)"))
        assertTrue("sin plantillas de después, nada después", Conflictos.delDia(d, f("2026-12-12")).isEmpty())
    }

    @Test fun `trabajo sin antes ni despues no avisa en el fin de semana`() {
        val d = base.copy(sueltas = listOf(Suelta("s", "Paseo", f("2026-12-05"))))
        assertTrue(Conflictos.delDia(d, f("2026-12-05")).isEmpty())
    }

    @Test fun `omitidas no cuentan para conflictos`() {
        val d = base.copy(sueltas = listOf(Suelta("s1", "Médico", f("2026-11-30"), Franja.de(10, 0, 11, 0))))
        val d2 = Agenda.omitir(d, "t:trabajo:jornada:2026-11-30")
        assertTrue(Conflictos.delDia(d2, f("2026-11-30")).none { it.clase == Conflictos.Clase.SOLAPE && it.texto.contains("Médico") })
    }

    @Test fun `lista revisar entre fechas y vistos`() {
        val d = base.copy(sueltas = listOf(Suelta("s1", "Médico", f("2026-11-30"), Franja.de(10, 0, 11, 0))))
        val todos = Conflictos.entre(d, f("2026-11-28"), f("2026-12-14"))
        assertTrue(todos.isNotEmpty())
        assertEquals("claves únicas", todos.size, todos.map { it.clave }.toSet().size)
        val visto = todos.first()
        val d2 = d.copy(vistos = setOf(visto.clave))
        assertEquals(todos.size - 1, Conflictos.entre(d2, f("2026-11-28"), f("2026-12-14")).size)
        assertEquals(todos.size, Conflictos.entre(d2, f("2026-11-28"), f("2026-12-14"), ocultarVistos = false).size)
        // La clave es estable entre cálculos.
        assertEquals(todos.map { it.clave }, Conflictos.entre(d, f("2026-11-28"), f("2026-12-14")).map { it.clave })
    }
}

class FormatoTest {
    @Test fun `ida y vuelta completo`() {
        val d = Datos(
            tipos = listOf(
                viaje().copy(fechas = setOf(f("2026-12-24")), excepciones = setOf(f("2026-12-13")), color = 0xFF5B6BBF, agruparSeguidos = false),
                trabajo().copy(repeticiones = listOf(Repeticion.Semanal(LV, f("2026-01-01"), f("2026-12-31")), Repeticion.CadaNDias(f("2026-11-01"), 7, f("2027-01-01")))),
            ),
            sueltas = listOf(
                Suelta("s1", "Cita \"médica\"\nconsultorio 3", f("2026-11-27"), Franja.de(9, 0, 10, 30), "llevar exámenes"),
                Suelta("s2", "Llamar", f("2026-11-27")),
                Suelta("s3", "Fin de día", f("2026-11-27"), Franja(23 * 60, 24 * 60)),
            ),
            hechas = setOf("s:s1", "t:viaje:revisar:2026-11-28"),
            omitidas = setOf("t:viaje:carro:2026-11-28"),
            vistos = setOf("solape:2026-11-30:a|b"),
        )
        val json = Formato.aJson(d)
        assertTrue(json.contains("\"app\":\"la-luna\""))
        assertTrue(json.contains("\"inicio\":\"09:00\""))
        assertTrue(json.contains("\"fin\":\"24:00\""))
        assertTrue(json.contains("\"color\":\"#5B6BBF\""))
        assertEquals(d, Formato.deJson(json))
    }

    @Test fun `vacio y errores`() {
        assertEquals(Datos(), Formato.deJson(Formato.aJson(Datos())))
        for (malo in listOf("[]", "{\"app\":\"la-emperatriz\",\"productos\":[]}", "{\"x\":1}", "no es json")) {
            try { Formato.deJson(malo); fail("debía fallar: $malo") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun `ignora elementos danados y corrige horas`() {
        val json = """
            {"app":"la-luna","version":1,
             "sueltas":[{"id":"a","titulo":"Bien","fecha":"2026-11-27","inicio":"07:00","fin":"06:00"},
                        {"id":"b","titulo":"Sin fecha"},
                        {"titulo":"Sin id","fecha":"2026-11-27","inicio":"25:00"}],
             "tipos":[{"id":"t","nombre":"T","color":"zzz","plantillas":[{"titulo":"P","dias":-2}],
                       "repeticiones":[{"clase":"rara"},{"clase":"semanal","dias":[1,9]},{"clase":"cada","inicio":"2026-11-01","cada":0}],
                       "fechas":["2026-11-01","malo"]}]}
        """.trimIndent()
        val d = Formato.deJson(json)
        assertEquals(2, d.sueltas.size)
        assertEquals(Franja.hora(7, 0), d.sueltas[0].franja)
        assertEquals(Franja.TODO_EL_DIA, d.sueltas[1].franja)
        assertTrue(d.sueltas[1].id.isNotBlank())
        val t = d.tipos.single()
        assertEquals(TipoDia.COLORES.first(), t.color)
        assertEquals(-2, t.plantillas.single().desplazamiento)
        assertEquals(listOf(Repeticion.Semanal(setOf(DayOfWeek.MONDAY))), t.repeticiones)
        assertEquals(setOf(f("2026-11-01")), t.fechas)
        assertTrue(t.agruparSeguidos)
    }

    @Test fun `horas y colores`() {
        assertEquals(450, Formato.textoAHora("07:30"))
        assertEquals(1440, Formato.textoAHora("24:00"))
        assertNull(Formato.textoAHora("24:30"))
        assertNull(Formato.textoAHora("7"))
        assertEquals("#F4A582", Formato.colorATexto(0xFFF4A582))
        assertEquals(0xFFF4A582, Formato.textoAColor("#f4a582"))
        assertNotNull(Formato.nuevoId())
        assertEquals(12, Formato.nuevoId().length)
    }
}

class TextosTest {
    @Test fun `textos en espanol`() {
        assertEquals("07:05", Textos.hora(425))
        assertEquals("Todo el día", Textos.franja(Franja.TODO_EL_DIA))
        assertEquals("09:00–10:30", Textos.franja(Franja.de(9, 0, 10, 30)))
        assertEquals("sáb 28 nov", Textos.fechaCorta(f("2026-11-28")))
        assertEquals("sábado 28 nov 2026", Textos.fechaLarga(f("2026-11-28")))
        assertEquals("1 día antes", Textos.desplazamiento(-1))
        assertEquals("3 días después", Textos.desplazamiento(3))
        assertEquals("el mismo día", Textos.desplazamiento(0))
        assertEquals("L a V", Textos.diasSemana(LV))
        assertEquals("L X V", Textos.diasSemana(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
        assertEquals("todos los días", Textos.diasSemana(DayOfWeek.entries.toSet()))
        assertEquals("Cada 15 días desde 28 nov 2026", Textos.repeticion(Repeticion.CadaNDias(f("2026-11-28"), 15)))
        assertEquals("Cada semana: L a V hasta 31 dic 2026", Textos.repeticion(Repeticion.Semanal(LV, hasta = f("2026-12-31"))))
        val d = Datos(tipos = listOf(viaje()))
        val i = Agenda.instancias(d, f("2026-11-27")).single()
        assertEquals("Viaje · 1 día antes (sáb 28 nov)", Textos.origen(i, d))
        assertEquals("Viaje", Textos.origen(Agenda.instancias(d, f("2026-11-28")).first(), d))
    }
}

class RecordatoriosTest {
    private val datos = Datos(tipos = listOf(viaje(), trabajo()), sueltas = listOf(Suelta("s1", "Médico", f("2026-11-27"), Franja.de(10, 0, 11, 0))))
    private val aj = Recordatorios.Ajustes(actividades = true, minutosAntes = 10, resumen = true, horaResumen = 7 * 60)

    @Test fun `avisos del dia con resumen y minutos antes`() {
        val l = Recordatorios.delDia(datos, f("2026-11-27"), aj) // viernes: trabajo, médico, empacar
        assertEquals(LocalDateTime.parse("2026-11-27T07:00"), l[0].momento)
        assertTrue(l[0].esResumen)
        assertTrue(l[0].titulo, l[0].titulo.startsWith("Hoy: 4 actividades"))
        assertEquals(listOf("07:50", "07:50", "09:50", "19:50"), l.drop(1).map { it.momento.toLocalTime().toString() })
        assertTrue(l.last().texto, l.last().texto.contains("Viaje · 1 día antes"))
        assertTrue(l[0].texto, l[0].texto.contains("choque"))
    }

    @Test fun `hechas no avisan y sin ajustes no hay nada`() {
        val d = Agenda.alternarHecha(datos, "s:s1")
        assertTrue(Recordatorios.delDia(d, f("2026-11-27"), aj).none { it.titulo == "Médico" })
        assertTrue(Recordatorios.delDia(datos, f("2026-11-27"), Recordatorios.Ajustes()).isEmpty())
        assertNull(Recordatorios.siguiente(datos, Recordatorios.Ajustes(), LocalDateTime.parse("2026-11-27T00:00")))
        assertTrue("sin actividades no hay resumen", Recordatorios.delDia(Datos(), f("2026-11-27"), aj).isEmpty())
    }

    @Test fun `siguiente y entre`() {
        val s = Recordatorios.siguiente(datos, aj, LocalDateTime.parse("2026-11-27T09:00"))!!
        assertEquals("Médico", s.titulo); assertEquals(LocalDateTime.parse("2026-11-27T09:50"), s.momento)
        val e = Recordatorios.entre(datos, aj, LocalDateTime.parse("2026-11-27T07:00"), LocalDateTime.parse("2026-11-27T10:00"))
        assertEquals(listOf("Revisar correo", "Jornada", "Médico").sorted(), e.map { it.titulo }.sorted())
        assertTrue(Recordatorios.entre(datos, aj, LocalDateTime.parse("2026-11-27T10:00"), LocalDateTime.parse("2026-11-27T10:00")).isEmpty())
    }

    @Test fun `aviso de pasada la medianoche cae el dia anterior`() {
        val d = Datos(sueltas = listOf(Suelta("n", "Medicina", f("2026-11-28"), Franja.hora(0, 5))))
        val a = Recordatorios.Ajustes(actividades = true, minutosAntes = 10)
        val s = Recordatorios.siguiente(d, a, LocalDateTime.parse("2026-11-27T12:00"))!!
        assertEquals(LocalDateTime.parse("2026-11-27T23:55"), s.momento)
        assertEquals(1, Recordatorios.entre(d, a, LocalDateTime.parse("2026-11-27T23:00"), LocalDateTime.parse("2026-11-27T23:59")).size)
    }
}
