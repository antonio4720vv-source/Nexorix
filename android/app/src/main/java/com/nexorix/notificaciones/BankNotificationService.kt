package com.nexorix.notificaciones

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Lee las notificaciones del celular y envia a Nexorix SOLO las que parecen de un banco:
 * mencionan un banco conocido y traen un monto con "$". Todo lo demas (chats, correos,
 * fotos...) se descarta aqui mismo y nunca sale del telefono.
 */
class BankNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        // El nombre que ve la persona en su celular ("Nequi", "Bancolombia"): la forma mas segura de saber el banco.
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            ""
        }

        if (!looksLikeBank(sbn.packageName, appName, title, text)) return

        val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        val url = prefs.getString(Config.URL, null)?.takeIf { it.isNotBlank() } ?: return
        val token = prefs.getString(Config.TOKEN, null)?.takeIf { it.isNotBlank() } ?: return

        val body = JSONObject()
            .put("packageName", sbn.packageName)
            .put("appName", appName)
            .put("title", title)
            .put("text", text)
            .put("postedAt", sbn.postTime)
            .toString()

        thread(name = "nexorix-send") { send(url, token, body) }
    }

    private fun looksLikeBank(pkg: String, appName: String, title: String, text: String): Boolean {
        if (!text.contains('$') && !title.contains('$')) return false
        val haystack = " ${pkg.replace('.', ' ')} $appName $title $text ".lowercase()
            .replace(Regex("[^\\p{L}0-9]+"), " ")
        return BANKS.any { haystack.contains(" $it ") }
    }

    private fun send(url: String, token: String, body: String) {
        // Si no hay conexion se reintenta un par de veces; el servidor ignora repetidos.
        repeat(3) { attempt ->
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                connection.disconnect()
                if (code in 200..299 || code in 400..499) return
            } catch (_: Exception) {
                // sin conexion: se reintenta
            }
            Thread.sleep(2_000L * (attempt + 1))
        }
    }

    private companion object {
        val BANKS = listOf(
            "bancolombia", "davivienda", "nequi", "daviplata", "nu", "nubank", "lulo", "bbva",
            "banco de bogota", "banco popular", "colpatria", "scotiabank", "caja social",
            "banco de occidente", "av villas", "itau", "banco agrario", "banco falabella",
            "banco pichincha", "bancamia", "banco w", "serfinanza", "sudameris", "bancoomeva",
            "finandina", "movii", "rappipay", "rappi pay"
        )
    }
}
