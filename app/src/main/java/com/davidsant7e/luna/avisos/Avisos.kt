package com.davidsant7e.luna.avisos

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.davidsant7e.luna.R
import com.davidsant7e.luna.datos.Ajustes
import com.davidsant7e.luna.datos.Almacen
import com.davidsant7e.luna.nucleo.Recordatorios
import com.davidsant7e.luna.registro.Registro
import com.davidsant7e.luna.ui.MainActivity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Notificaciones: una sola alarma (AlarmManager, inexacta, sin permiso de alarmas exactas) para el siguiente aviso.
 * Al sonar, muestra lo que tocaba desde la última vez (máx. 30 min atrás) y programa el siguiente.
 * Puede llegar unos minutos tarde si el celular está en reposo profundo.
 */
object Avisos {
    private const val CANAL_ACTIVIDADES = "actividades"
    private const val CANAL_RESUMEN = "resumen"
    const val EXTRA_FECHA = "fecha"
    private const val ATRAS_MAX_MS = 30 * 60_000L

    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun fecha(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    fun hayPermiso(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun intentAlarma(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AvisoReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Programa (o cancela) la alarma del siguiente aviso. Se llama al cambiar datos o ajustes, al arrancar y al sonar. */
    fun programar(ctx: Context) {
        try {
            val ajustes = Ajustes(ctx)
            val a = ajustes.avisos
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = intentAlarma(ctx)
            if (!a.alguno) { am.cancel(pi); return }
            val ahora = System.currentTimeMillis()
            val base = maxOf(ahora, ajustes.avisadoHasta)
            val sig = Recordatorios.siguiente(Almacen(ctx).cargar(), a, fecha(base))
            if (sig == null) { am.cancel(pi); return }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms(sig.momento), pi)
            Registro.evento("AVISOS", "programado", "cuando" to sig.momento.toString(), "que" to sig.titulo)
        } catch (e: Exception) {
            Registro.error("AVISOS", e)
        }
    }

    /** La alarma sonó: muestra los avisos pendientes y programa el siguiente. */
    fun alSonar(ctx: Context) {
        try {
            val ajustes = Ajustes(ctx)
            val ahora = System.currentTimeMillis()
            val desde = maxOf(ajustes.avisadoHasta, ahora - ATRAS_MAX_MS)
            val hasta = ahora + 60_000L
            val lista = Recordatorios.entre(Almacen(ctx).cargar(), ajustes.avisos, fecha(desde), fecha(hasta))
            ajustes.avisadoHasta = hasta
            if (lista.isNotEmpty() && hayPermiso(ctx)) {
                crearCanales(ctx)
                lista.forEach { mostrar(ctx, it) }
            }
            Registro.evento("AVISOS", "sonó", "mostrados" to lista.size, "permiso" to hayPermiso(ctx))
        } catch (e: Exception) {
            Registro.error("AVISOS", e)
        }
        programar(ctx)
    }

    fun crearCanales(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CANAL_ACTIVIDADES, "Actividades", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso antes de cada actividad con hora"
        })
        nm.createNotificationChannel(NotificationChannel(CANAL_RESUMEN, "Resumen de la mañana", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Las actividades del día y los avisos para revisar"
        })
    }

    private fun mostrar(ctx: Context, a: Recordatorios.Aviso) {
        val fecha = a.clave.split(':').getOrNull(1).orEmpty()
        val abrir = Intent(ctx, MainActivity::class.java).apply {
            putExtra(EXTRA_FECHA, fecha)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(ctx, a.clave.hashCode(), abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, if (a.esResumen) CANAL_RESUMEN else CANAL_ACTIVIDADES)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(a.titulo)
            .setContentText(a.texto.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.texto))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(0xFF1F2A5C.toInt())
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(a.clave.hashCode(), n)
        } catch (e: SecurityException) {
            Registro.error("AVISOS", e)
        }
    }
}

/** Suena la alarma del siguiente aviso. */
class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Avisos.alSonar(context)
    }
}

/** Al prender el celular, actualizar la app o cambiar la hora: volver a programar la alarma. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Registro.evento("AVISOS", "reprogramar", "por" to intent?.action)
        Avisos.programar(context)
    }
}
