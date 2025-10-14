package com.kt.webconteinerapp

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ErroresActivity : AppCompatActivity() {

    private lateinit var retryHandler: Handler
    private lateinit var buttonReintentar: Button
    private lateinit var progressReintentar: ProgressBar
    private lateinit var textViewMsjError: TextView

    private var lastRetryTime = 0L
    private val retryTimeout = 2000L // tiempo entre reintentos manuales
    private val watchdogInterval = 20000L // tiempo entre verificaciones automáticas

    private var watchdogEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pagina_error)

        retryHandler = Handler(Looper.getMainLooper())

        // SharedPreferences
        val preferences = getSharedPreferences("mis_preferencias", Context.MODE_PRIVATE)
        val nombreEquipo = preferences.getString("nombreEquipo", "")
        val nombreServidor = preferences.getString("nombreServidor", "")

        // VersionName
        val versionNameTextView: TextView = findViewById(R.id.versionNameTextView)
        versionNameTextView.text = "V${BuildConfig.VERSION_NAME}"

        // TextViews
        textViewMsjError = findViewById(R.id.TextViewMensajeError)
        val textViewSucursal: TextView = findViewById(R.id.TextViewSucursal)
        val textViewNombreDispositivo: TextView = findViewById(R.id.TextViewNombreDispositivo)

        textViewSucursal.text = "Servidor: $nombreServidor"
        textViewNombreDispositivo.text = "Dispositivo: $nombreEquipo"

        // Mensaje de error recibido
        val receivedText = intent.getStringExtra("EXTRA_TEXT") ?: "Error desconocido"
        textViewMsjError.text = receivedText

        // Botón y spinner
        buttonReintentar = findViewById(R.id.ButtonReintentar)
        progressReintentar = findViewById(R.id.retryProgress)

        buttonReintentar.setOnClickListener { handleRetry(nombreServidor, nombreEquipo) }

        // Iniciar watchdog automático
        startWatchdog(nombreServidor, nombreEquipo)
    }

    private fun handleRetry(nombreServidor: String?, nombreEquipo: String?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRetryTime < retryTimeout) {
            val remainingTime = (retryTimeout - (currentTime - lastRetryTime)) / 1000
            safeLog("ErroresActivity", "Debe esperar $remainingTime segundos antes de reintentar", Log.WARN)
            return
        }

        lastRetryTime = currentTime
        showLoadingState(true)

        safeLog("ErroresActivity", "Reintentando conexión manual...", Log.DEBUG)

        if (!isInternetAvailable()) {
            safeLog("ErroresActivity", "Sin Internet: permanecer en pantalla de error", Log.ERROR)
            textViewMsjError.text = "Sin conexión de red. Verifique Wi-Fi o cable Ethernet del tótem y vuelva a intentar."
            retryHandler.postDelayed({ showLoadingState(false) }, retryTimeout)
            return
        }

        // Confirmar conexión estable (2s)
        retryHandler.postDelayed({
            if (isInternetAvailable()) {
                safeLog("ErroresActivity", "Conexión estable, iniciando transición...", Log.INFO)
                startMainActivity()
            } else {
                safeLog("ErroresActivity", "Conexión inestable, permaneciendo en error", Log.WARN)
                showLoadingState(false)
            }
        }, 2000)
    }


    /** Watchdog automático: reintenta solo si detecta que hay Internet disponible */
    private fun startWatchdog(nombreServidor: String?, nombreEquipo: String?) {
        retryHandler.post(object : Runnable {
            override fun run() {
                if (!watchdogEnabled) return

                safeLog("ErroresActivity", "Watchdog: verificando conexión...", Log.DEBUG)

                if (isInternetAvailable()) {
                    safeLog("ErroresActivity", "Watchdog: conexión detectada, verificando estabilidad...", Log.INFO)

                    // Espera 2 segundos para confirmar que la conexión sigue activa
                    retryHandler.postDelayed({
                        if (isInternetAvailable()) {
                            safeLog("ErroresActivity", "Watchdog: conexión estable confirmada, reintentando...", Log.INFO)
                            showLoadingState(true)

                            // Espera 800ms para suavizar visualmente el cambio
                            retryHandler.postDelayed({
                                startMainActivity()
                            }, 800)
                        } else {
                            safeLog("ErroresActivity", "Watchdog: conexión se perdió durante verificación", Log.WARN)
                            retryHandler.postDelayed(this, watchdogInterval)
                        }
                    }, watchdogInterval)
                } else {
                    safeLog("ErroresActivity", "Watchdog: sin conexión, reintenta en ${watchdogInterval / 1000}s", Log.DEBUG)
                    retryHandler.postDelayed(this, watchdogInterval)
                }
            }
        })
    }


    private fun showLoadingState(loading: Boolean) {
        if (loading) {
            buttonReintentar.isEnabled = false
            buttonReintentar.text = ""
            progressReintentar.visibility = View.VISIBLE
        } else {
            buttonReintentar.isEnabled = true
            buttonReintentar.text = "REINTENTAR"
            progressReintentar.visibility = View.GONE
        }
    }

    /** Transición suave hacia MainActivity */
    private fun startMainActivity() {
        showLoadingState(true)

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent)

        // Animación de fundido suave
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        finish()
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun hideSystemUI() {
        val decorView = window.decorView
        decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        watchdogEnabled = false
        retryHandler.removeCallbacksAndMessages(null)
        safeLog("ErroresActivity", "Watchdog pausado", Log.DEBUG)
    }

    override fun onResume() {
        super.onResume()
        watchdogEnabled = true
        safeLog("ErroresActivity", "Actividad reanudada, watchdog activo", Log.DEBUG)
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogEnabled = false
        retryHandler.removeCallbacksAndMessages(null)
        safeLog("ErroresActivity", "Handler destruido", Log.DEBUG)
    }

    private fun safeLog(tag: String, msg: String, level: Int = Log.DEBUG) {
        if (BuildConfig.DEBUG) {
            when (level) {
                Log.ERROR -> Log.e(tag, msg)
                Log.WARN -> Log.w(tag, msg)
                Log.INFO -> Log.i(tag, msg)
                else -> Log.d(tag, msg)
            }
        }
    }
}
