package com.nexorix.notificaciones

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Pantalla unica: pega la direccion y la llave de Nexorix y da acceso a las notificaciones. */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        layout.addView(TextView(this).apply {
            text = "En Nexorix (Panel > Notificaciones del celular) activa la función y copia la dirección y la llave."
        })

        val url = EditText(this).apply {
            hint = "Dirección (https://.../api/notifications/ingest)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString(Config.URL, ""))
        }
        val token = EditText(this).apply {
            hint = "Llave (nxr_...)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString(Config.TOKEN, ""))
        }
        layout.addView(url)
        layout.addView(token)

        layout.addView(Button(this).apply {
            text = "Guardar"
            setOnClickListener {
                val address = url.text.toString().trim()
                if (!address.startsWith("https://")) {
                    Toast.makeText(context, "La dirección debe empezar por https://", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit()
                    .putString(Config.URL, address)
                    .putString(Config.TOKEN, token.text.toString().trim())
                    .apply()
                Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
            }
        })

        layout.addView(Button(this).apply {
            text = "Dar acceso a las notificaciones"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })

        status = TextView(this)
        layout.addView(status)
        layout.addView(TextView(this).apply {
            text = "Solo se envían avisos que mencionan un banco y traen un monto. Lo demás nunca sale del celular."
        })
        setContentView(layout)
        handleLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /**
     * nexorix://conectar?url=https://...&token=nxr_... : guarda los datos y lleva a la persona
     * directo a dar el acceso a las notificaciones. Asi no tiene que copiar nada a mano.
     */
    private fun handleLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "nexorix" || data.host != "conectar") return
        val address = data.getQueryParameter("url")?.trim().orEmpty()
        val key = data.getQueryParameter("token")?.trim().orEmpty()
        if (!address.startsWith("https://") || !key.startsWith("nxr_")) {
            Toast.makeText(this, "El enlace de Nexorix no es válido.", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).edit()
            .putString(Config.URL, address)
            .putString(Config.TOKEN, key)
            .apply()
        Toast.makeText(this, "Conectado a Nexorix. Activa el acceso a las notificaciones.", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(ComponentName(this, BankNotificationService::class.java).flattenToString()) == true
        status.text = if (enabled) "Acceso a notificaciones: ACTIVO" else "Acceso a notificaciones: falta activarlo"
    }
}
