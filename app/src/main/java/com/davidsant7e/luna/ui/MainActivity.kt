package com.davidsant7e.luna.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidsant7e.luna.avisos.Avisos
import com.davidsant7e.luna.registro.Registro
import java.io.File
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: LunaViewModel by viewModels()

    private val abrirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { leerArchivo(it) }
    }

    private var alPermiso: ((Boolean) -> Unit)? = null
    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        alPermiso?.invoke(ok)
        alPermiso = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) recibir(intent)
        setContent {
            TemaLuna {
                val e by vm.estado.collectAsStateWithLifecycle()
                App(
                    e, vm,
                    Acciones(
                        compartirRespaldo = ::compartirRespaldo,
                        abrirArchivo = { abrirArchivo.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        pedirPermisoAvisos = ::pedirPermisoAvisos,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refrescarHoy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recibir(intent)
    }

    /** Desde una notificación (abre ese día) o un respaldo .json compartido o abierto. */
    private fun recibir(i: Intent?) {
        i?.getStringExtra(Avisos.EXTRA_FECHA)?.let { f -> runCatching { LocalDate.parse(f) }.getOrNull()?.let(vm::irA) }
        when (i?.action) {
            Intent.ACTION_SEND -> {
                val archivo = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                val texto = i.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    archivo != null -> leerArchivo(archivo)
                    !texto.isNullOrBlank() -> vm.recibirRespaldo(texto)
                }
            }
            Intent.ACTION_VIEW -> i.data?.let { leerArchivo(it) }
        }
    }

    private fun leerArchivo(uri: Uri) {
        val texto = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().let { b -> String(b, 0, minOf(b.size, MAX_ARCHIVO), Charsets.UTF_8) } }
        } catch (e: Exception) {
            Registro.error("ARCHIVO", e)
            null
        }
        if (texto.isNullOrBlank()) Toast.makeText(this, "No pude leer el archivo.", Toast.LENGTH_LONG).show()
        else vm.recibirRespaldo(texto)
    }

    private fun compartirRespaldo() {
        val nombre = "la-luna-${LocalDate.now()}.json"
        val carpeta = File(cacheDir, "exportar").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }
        val archivo = File(carpeta, nombre).apply { writeText(vm.respaldo()) }
        val uri = FileProvider.getUriForFile(this, "$packageName.archivos", archivo)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nombre)
            clipData = ClipData.newRawUri(nombre, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Registro.evento("RESPALDO", "exportar")
        startActivity(Intent.createChooser(i, "Guardar o enviar $nombre"))
    }

    private fun pedirPermisoAvisos(listo: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || Avisos.hayPermiso(this)) { listo(true); return }
        alPermiso = listo
        pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val MAX_ARCHIVO = 5_000_000
    }
}

/** Lo que la interfaz le pide a la actividad. */
class Acciones(
    val compartirRespaldo: () -> Unit,
    val abrirArchivo: () -> Unit,
    val pedirPermisoAvisos: (listo: (Boolean) -> Unit) -> Unit,
)
